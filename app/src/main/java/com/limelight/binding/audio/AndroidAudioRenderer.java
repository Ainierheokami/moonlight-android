package com.limelight.binding.audio;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.Process;

import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.jni.MoonBridge;

public class AndroidAudioRenderer implements AudioRenderer {

    private final Context context;
    private final boolean enableAudioFx;

    private AudioTrack track;
    private volatile int volumeGainPercent = 100;
    private Pcm16AudioLimiter audioLimiter;

    // 音频流量统计
    private long totalAudioInputBytes = 0;  // 累计输入音频流量（字节）
    private long totalAudioOutputBytes = 0; // 累计输出音频流量（字节）

    // 音频速率统计
    private long lastAudioUpdateTimeMs = 0; // 上次更新的时间戳
    public double audioInputRateMBps = 0; // 输入音频速率（MB/s）
    public double audioOutputRateMBps = 0; // 输出音频速率（MB/s）

    // 最近一次统计的音频速率，供视频性能叠加层跨线程读取
    private static volatile float lastAudioInputRateMBps = 0;
    private static volatile float lastAudioOutputRateMBps = 0;

    public static float getLastAudioInputRateMBps() {
        return lastAudioInputRateMBps;
    }

    public static float getLastAudioOutputRateMBps() {
        return lastAudioOutputRateMBps;
    }

    // Only touched by the native audio thread calling playDecodedAudio()
    private boolean playbackThreadPriorityRaised = false;

    // Adaptive buffering (Android 7+): the track is created with room for up to
    // MAX_ADAPTIVE_BUFFER_MS of audio, but starts with a small buffer for low latency.
    // Every underrun (heard as a crackle or pop) grows the buffer by one packet until it
    // stops underrunning, so a jittery Wi-Fi link settles on a buffer that plays cleanly.
    private static final int MAX_ADAPTIVE_BUFFER_MS = 80;
    private static final int ADAPTIVE_CHECK_INTERVAL_MS = 100;
    private boolean adaptiveBuffer;
    private int samplesPerPacket;
    private int bufferSizeFrames;
    private int maxBufferSizeFrames;
    private int lastUnderrunCount;
    private long lastUnderrunCheckMs;
    // Underruns while the stream is still starting up are expected and do not count.
    private long adaptiveGraceUntilMs;
    private int sampleRateHz;

    public AndroidAudioRenderer(Context context, boolean enableAudioFx) {
        this.context = context;
        this.enableAudioFx = enableAudioFx;
    }

    public void setVolumeGainPercent(int volumeGainPercent) {
        this.volumeGainPercent = Math.max(Game.STREAM_AUDIO_GAIN_MIN_PERCENT,
                Math.min(Game.STREAM_AUDIO_GAIN_MAX_PERCENT, volumeGainPercent));
    }

    private void updateAudioBitrateStats(Context context) {
        long currentTimeMs = System.currentTimeMillis();

        // 每秒更新一次速率
        if (currentTimeMs - lastAudioUpdateTimeMs >= 1000) {
            long elapsedTimeMs = currentTimeMs - lastAudioUpdateTimeMs;

            // 计算速率（MB/s）
            audioInputRateMBps = (totalAudioInputBytes / 1024.0 / 1024.0) / (elapsedTimeMs / 1000.0);
            audioOutputRateMBps = (totalAudioOutputBytes / 1024.0 / 1024.0) / (elapsedTimeMs / 1000.0);

            // 仅保存在内存中供性能叠加层读取。之前每秒写一次 SharedPreferences，
            // 会在串流期间反复把整个首选项文件写回磁盘，造成无谓的 IO 与 CPU 抖动。
            lastAudioInputRateMBps = (float) audioInputRateMBps;
            lastAudioOutputRateMBps = (float) audioOutputRateMBps;


            // 打印速率日志（可选）
//            LimeLog.info(String.format("Audio Input Rate: %.2f MB/s", audioInputRateMBps));
//            LimeLog.info(String.format("Audio Output Rate: %.2f MB/s", audioOutputRateMBps));

            // 重置统计数据
            totalAudioInputBytes = 0;
            totalAudioOutputBytes = 0;
            lastAudioUpdateTimeMs = currentTimeMs;
        }
    }

    private AudioTrack createAudioTrack(int channelConfig, int sampleRate, int bufferSize, boolean lowLatency) {
        AudioAttributes.Builder attributesBuilder = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME);
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Use FLAG_LOW_LATENCY on L through N
            if (lowLatency) {
                attributesBuilder.setFlags(AudioAttributes.FLAG_LOW_LATENCY);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioTrack.Builder trackBuilder = new AudioTrack.Builder()
                    .setAudioFormat(format)
                    .setAudioAttributes(attributesBuilder.build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferSize);

            // Use PERFORMANCE_MODE_LOW_LATENCY on O and later
            if (lowLatency) {
                trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
            }

            return trackBuilder.build();
        }
        else {
            return new AudioTrack(attributesBuilder.build(),
                    format,
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
        }
    }

    @Override
    public int setup(MoonBridge.AudioConfiguration audioConfiguration, int sampleRate, int samplesPerFrame) {
        int channelConfig;
        int bytesPerFrame;

        // 新会话不沿用上一次串流的音频速率
        lastAudioInputRateMBps = 0;
        lastAudioOutputRateMBps = 0;

        switch (audioConfiguration.channelCount)
        {
            case 2:
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
                break;
            case 4:
                channelConfig = AudioFormat.CHANNEL_OUT_QUAD;
                break;
            case 6:
                channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
                break;
            case 8:
                // AudioFormat.CHANNEL_OUT_7POINT1_SURROUND isn't available until Android 6.0,
                // yet the CHANNEL_OUT_SIDE_LEFT and CHANNEL_OUT_SIDE_RIGHT constants were added
                // in 5.0, so just hardcode the constant so we can work on Lollipop.
                channelConfig = 0x000018fc; // AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
                break;
            default:
                LimeLog.severe("Decoder returned unhandled channel count");
                return -1;
        }

        LimeLog.info("Audio channel config: "+String.format("0x%X", channelConfig));

        bytesPerFrame = audioConfiguration.channelCount * samplesPerFrame * 2;
        audioLimiter = new Pcm16AudioLimiter(sampleRate, audioConfiguration.channelCount);
        samplesPerPacket = samplesPerFrame;
        sampleRateHz = sampleRate;
        adaptiveBuffer = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && setupAdaptiveTrack(channelConfig, sampleRate, bytesPerFrame,
                        audioConfiguration.channelCount)) {
            return 0;
        }

        // We're not supposed to request less than the minimum
        // buffer size for our buffer, but it appears that we can
        // do this on many devices and it lowers audio latency.
        // We'll try the small buffer size first and if it fails,
        // use the recommended larger buffer size.

        for (int i = 0; i < 4; i++) {
            boolean lowLatency;
            int bufferSize;

            // We will try:
            // 1) Small buffer, low latency mode
            // 2) Large buffer, low latency mode
            // 3) Small buffer, standard mode
            // 4) Large buffer, standard mode

            switch (i) {
                case 0:
                case 1:
                    lowLatency = true;
                    break;
                case 2:
                case 3:
                    lowLatency = false;
                    break;
                default:
                    // Unreachable
                    throw new IllegalStateException();
            }

            switch (i) {
                case 0:
                case 2:
                    bufferSize = bytesPerFrame * 2;
                    break;

                case 1:
                case 3:
                    // Try the larger buffer size
                    bufferSize = Math.max(AudioTrack.getMinBufferSize(sampleRate,
                            channelConfig,
                            AudioFormat.ENCODING_PCM_16BIT),
                            bytesPerFrame * 2);

                    // Round to next frame
                    bufferSize = (((bufferSize + (bytesPerFrame - 1)) / bytesPerFrame) * bytesPerFrame);
                    break;
                default:
                    // Unreachable
                    throw new IllegalStateException();
            }

            // Skip low latency options if hardware sample rate doesn't match the content
            if (AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) != sampleRate && lowLatency) {
                continue;
            }

            // Skip low latency options when using audio effects, since low latency mode
            // precludes the use of the audio effect pipeline (as of Android 13).
            if (enableAudioFx && lowLatency) {
                continue;
            }

            try {
                track = createAudioTrack(channelConfig, sampleRate, bufferSize, lowLatency);
                track.play();

                // Successfully created working AudioTrack. We're done here.
                LimeLog.info("Audio track configuration: "+bufferSize+" "+lowLatency);
                break;
            } catch (Exception e) {
                // Try to release the AudioTrack if we got far enough
                LimeLog.warning(e);
                try {
                    if (track != null) {
                        track.release();
                        track = null;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (track == null) {
            // Couldn't create any audio track for playback
            return -2;
        }

        return 0;
    }

    /**
     * Creates a track whose capacity allows the buffer to grow later, starting at the same
     * small size as the classic low-latency path. Returns false to fall back to that path.
     */
    @android.annotation.TargetApi(Build.VERSION_CODES.N)
    private boolean setupAdaptiveTrack(int channelConfig, int sampleRate, int bytesPerPacket,
                                       int channelCount) {
        int bytesPerSampleFrame = channelCount * 2;
        int packetsForMax = Math.max(2, (int) Math.ceil(
                MAX_ADAPTIVE_BUFFER_MS * sampleRate / 1000.0 / samplesPerPacket));
        int capacityBytes = Math.max(AudioTrack.getMinBufferSize(sampleRate, channelConfig,
                AudioFormat.ENCODING_PCM_16BIT), bytesPerPacket * packetsForMax);
        capacityBytes = ((capacityBytes + bytesPerPacket - 1) / bytesPerPacket) * bytesPerPacket;

        boolean nativeRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) == sampleRate;
        boolean[] modes = (nativeRate && !enableAudioFx) ? new boolean[] {true, false} : new boolean[] {false};
        for (boolean lowLatency : modes) {
            try {
                track = createAudioTrack(channelConfig, sampleRate, capacityBytes, lowLatency);
                maxBufferSizeFrames = capacityBytes / bytesPerSampleFrame;
                // Start where the classic path starts: two packets. The track may round it up
                // to what its mixer path requires.
                int applied = track.setBufferSizeInFrames(samplesPerPacket * 2);
                bufferSizeFrames = applied > 0 ? applied : track.getBufferSizeInFrames();
                track.play();
                lastUnderrunCount = track.getUnderrunCount();
                lastUnderrunCheckMs = System.currentTimeMillis();
                adaptiveGraceUntilMs = lastUnderrunCheckMs + 1000;
                adaptiveBuffer = true;
                LimeLog.info("Audio track: adaptive buffer " + framesToMs(bufferSizeFrames)
                        + " ms (max " + framesToMs(maxBufferSizeFrames) + " ms), lowLatency=" + lowLatency);
                return true;
            } catch (Exception e) {
                LimeLog.warning(e);
                if (track != null) {
                    try {
                        track.release();
                    } catch (Exception ignored) {}
                    track = null;
                }
            }
        }
        return false;
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.N)
    private void growBufferOnUnderrun() {
        long now = System.currentTimeMillis();
        if (now - lastUnderrunCheckMs < ADAPTIVE_CHECK_INTERVAL_MS) {
            return;
        }
        lastUnderrunCheckMs = now;

        int underruns = track.getUnderrunCount();
        if (now < adaptiveGraceUntilMs) {
            lastUnderrunCount = underruns;
            return;
        }
        if (underruns > lastUnderrunCount && bufferSizeFrames < maxBufferSizeFrames) {
            int requested = Math.min(maxBufferSizeFrames, bufferSizeFrames + samplesPerPacket);
            int applied = track.setBufferSizeInFrames(requested);
            if (applied > 0) {
                bufferSizeFrames = applied;
            }
            LimeLog.info("Audio underrun (" + underruns + " total): buffer now "
                    + framesToMs(bufferSizeFrames) + " ms");
        }
        lastUnderrunCount = underruns;
    }

    private int framesToMs(int frames) {
        return sampleRateHz > 0 ? (int) (frames * 1000L / sampleRateHz) : 0;
    }

    @Override
    public void playDecodedAudio(short[] audioData) {
        if (!playbackThreadPriorityRaised) {
            // Called on the native audio thread, which runs at the default priority otherwise
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            playbackThreadPriorityRaised = true;
        }

        // 累加输入流量
        totalAudioInputBytes += audioData.length * 2; // short 为 2 字节
        short[] outputAudioData = applyVolumeGain(audioData);

        // Only queue up to 40 ms of pending audio data in addition to what AudioTrack is buffering for us.
        if (MoonBridge.getPendingAudioDuration() < 40) {
            // This will block until the write is completed. That can cause a backlog
            // of pending audio data, so we do the above check to be able to bound
            // latency at 40 ms in that situation.
//            track.write(audioData, 0, audioData.length);
            int bytesWritten = track.write(outputAudioData, 0, audioData.length);
            totalAudioOutputBytes += bytesWritten * 2; // short 为 2 字节
        }
        else {
            LimeLog.info("Too much pending audio data: " + MoonBridge.getPendingAudioDuration() +" ms");
        }
        if (adaptiveBuffer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            growBufferOnUnderrun();
        }
        updateAudioBitrateStats(context);
    }

    private short[] applyVolumeGain(short[] audioData) {
        return audioLimiter.apply(audioData, volumeGainPercent);
    }

    @Override
    public void start() {
        if (enableAudioFx) {
            // Open an audio effect control session to allow equalizers to apply audio effects
            Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, track.getAudioSessionId());
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
            i.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_GAME);
            context.sendBroadcast(i);
        }
    }

    @Override
    public void stop() {
        if (enableAudioFx) {
            // Close our audio effect control session when we're stopping
            Intent i = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, track.getAudioSessionId());
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
            context.sendBroadcast(i);
        }
    }

    @Override
    public void cleanup() {
        // Immediately drop all pending data
        track.pause();
        track.flush();

        track.release();
    }
}
