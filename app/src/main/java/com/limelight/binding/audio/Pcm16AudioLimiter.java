package com.limelight.binding.audio;

/**
 * Applies software gain to interleaved signed 16-bit PCM while preventing hard clipping.
 *
 * <p>The limiter uses one gain value for the whole decoded audio frame. It reacts immediately
 * when a frame would clip, then releases gradually so consecutive frames do not rapidly jump
 * between limited and unlimited gain.</p>
 */
final class Pcm16AudioLimiter {
    private static final double RELEASE_TIME_SECONDS = 0.250;

    private final int sampleRate;
    private final int channelCount;

    private short[] outputBuffer;
    private double appliedGain = 1.0;
    private double lastRequestedGain = 1.0;

    Pcm16AudioLimiter(int sampleRate, int channelCount) {
        if (sampleRate <= 0 || channelCount <= 0) {
            throw new IllegalArgumentException("Invalid PCM format");
        }

        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
    }

    short[] apply(short[] input, int gainPercent) {
        if (input.length == 0) {
            return input;
        }

        double requestedGain = gainPercent / 100.0;
        if (requestedGain == 1.0) {
            appliedGain = 1.0;
            lastRequestedGain = 1.0;
            return input;
        }

        ensureOutputCapacity(input.length);

        double targetGain = requestedGain;
        if (requestedGain > 1.0) {
            int peak = findPeakMagnitude(input);
            if (peak != 0) {
                targetGain = Math.min(requestedGain, Short.MAX_VALUE / (double) peak);
            }
        }

        if (requestedGain != lastRequestedGain) {
            // Apply explicit user changes immediately. The frame peak still caps the new value.
            appliedGain = targetGain;
        }
        else if (targetGain < appliedGain) {
            // Attack immediately so no sample in this frame can hard clip.
            appliedGain = targetGain;
        }
        else if (targetGain > appliedGain) {
            // Recover slowly after a loud transient to avoid audible gain pumping.
            double frameCount = input.length / (double) channelCount;
            double releaseAmount = 1.0 - Math.exp(
                    -frameCount / (sampleRate * RELEASE_TIME_SECONDS));
            appliedGain += (targetGain - appliedGain) * releaseAmount;
        }
        lastRequestedGain = requestedGain;

        for (int i = 0; i < input.length; i++) {
            double scaledSample = input[i] * appliedGain;
            long sample = scaledSample < 0
                    ? -Math.round(-scaledSample)
                    : Math.round(scaledSample);
            outputBuffer[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
        }

        return outputBuffer;
    }

    private void ensureOutputCapacity(int sampleCount) {
        if (outputBuffer == null || outputBuffer.length < sampleCount) {
            outputBuffer = new short[sampleCount];
        }
    }

    private static int findPeakMagnitude(short[] input) {
        int peak = 0;
        for (short sample : input) {
            // Promote to int before negating so Short.MIN_VALUE becomes 32768 rather than wrapping.
            peak = Math.max(peak, Math.abs((int) sample));
        }
        return peak;
    }
}
