package com.limelight.binding.audio;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class Pcm16AudioLimiterTest {
    private static final int SAMPLE_RATE = 48_000;
    private static final int CHANNEL_COUNT = 2;

    @Test
    public void unityGainReturnsOriginalSamplesWithoutCopying() {
        Pcm16AudioLimiter limiter = new Pcm16AudioLimiter(SAMPLE_RATE, CHANNEL_COUNT);
        short[] input = {Short.MIN_VALUE, -1234, 0, 1234, Short.MAX_VALUE};

        short[] output = limiter.apply(input, 100);

        assertSame(input, output);
        assertArrayEquals(input, output);
    }

    @Test
    public void quietAudioReceivesRequestedGain() {
        Pcm16AudioLimiter limiter = new Pcm16AudioLimiter(SAMPLE_RATE, CHANNEL_COUNT);
        short[] input = {-1000, 0, 1000, 2000};

        short[] output = limiter.apply(input, 200);

        assertNotSame(input, output);
        assertArrayEquals(new short[] {-2000, 0, 2000, 4000}, output);
    }

    @Test
    public void loudAudioIsPeakLimitedInsteadOfHardClipped() {
        Pcm16AudioLimiter limiter = new Pcm16AudioLimiter(SAMPLE_RATE, CHANNEL_COUNT);
        short[] input = {20_000, 10_000, -20_000, -10_000};

        short[] output = limiter.apply(input, 200);

        assertArrayEquals(new short[] {32_767, 16_384, -32_767, -16_384}, output);
    }

    @Test
    public void minimumNegativeSampleCannotOverflowPeakDetection() {
        Pcm16AudioLimiter limiter = new Pcm16AudioLimiter(SAMPLE_RATE, CHANNEL_COUNT);
        short[] input = {Short.MIN_VALUE, Short.MAX_VALUE};

        short[] output = limiter.apply(input, 2000);

        assertEquals(-32_767, output[0]);
        assertEquals(32_766, output[1]);
    }

    @Test
    public void limiterReleasesGraduallyAfterLoudFrame() {
        Pcm16AudioLimiter limiter = new Pcm16AudioLimiter(SAMPLE_RATE, CHANNEL_COUNT);
        short[] loudFrame = filledFrame((short) 30_000);
        short[] quietFrame = filledFrame((short) 1_000);

        limiter.apply(loudFrame, 200);
        int firstQuietPeak = limiter.apply(quietFrame, 200)[0];

        assertTrue(firstQuietPeak > 1_092);
        assertTrue(firstQuietPeak < 2_000);

        int recoveredPeak = firstQuietPeak;
        for (int i = 0; i < 100; i++) {
            recoveredPeak = limiter.apply(quietFrame, 200)[0];
        }
        assertTrue(recoveredPeak > 1_980);
        assertTrue(recoveredPeak <= 2_000);
    }

    @Test
    public void attenuationDoesNotInvokeLimiter() {
        Pcm16AudioLimiter limiter = new Pcm16AudioLimiter(SAMPLE_RATE, CHANNEL_COUNT);
        short[] input = {Short.MIN_VALUE, -1000, 1000, Short.MAX_VALUE};

        short[] output = limiter.apply(input, 50);

        assertArrayEquals(new short[] {-16_384, -500, 500, 16_384}, output);
    }

    private static short[] filledFrame(short value) {
        short[] frame = new short[960];
        for (int i = 0; i < frame.length; i++) {
            frame[i] = value;
        }
        return frame;
    }
}
