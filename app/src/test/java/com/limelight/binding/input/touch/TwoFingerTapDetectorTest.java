package com.limelight.binding.input.touch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TwoFingerTapDetectorTest {
    private static final float SLOP = 20f;

    @Test
    public void quickTwoFingerTapIsRecognised() {
        TwoFingerTapDetector detector = new TwoFingerTapDetector(SLOP);
        detector.onDown(1000, 0, 100, 200);
        detector.onPointerDown(1040, 1, 300, 200, 2);
        detector.onMove(0, 103, 201);
        assertTrue(detector.onUp(1180));
        assertEquals(100f, detector.getPrimaryX(), 0f);
        assertEquals(200f, detector.getPrimaryY(), 0f);
    }

    @Test
    public void singleFingerTapIsNotATwoFingerTap() {
        TwoFingerTapDetector detector = new TwoFingerTapDetector(SLOP);
        detector.onDown(1000, 0, 100, 200);
        assertFalse(detector.onUp(1100));
    }

    @Test
    public void movingAFingerMakesItAScrollNotATap() {
        TwoFingerTapDetector detector = new TwoFingerTapDetector(SLOP);
        detector.onDown(1000, 0, 100, 200);
        detector.onPointerDown(1020, 1, 300, 200, 2);
        detector.onMove(1, 300, 260);
        assertFalse(detector.onUp(1150));
    }

    @Test
    public void slowGestureIsNotATap() {
        TwoFingerTapDetector detector = new TwoFingerTapDetector(SLOP);
        detector.onDown(1000, 0, 100, 200);
        detector.onPointerDown(1020, 1, 300, 200, 2);
        assertFalse(detector.onUp(1000 + TwoFingerTapDetector.MAX_TAP_MS + 1));
    }

    @Test
    public void threeFingersAreNotATwoFingerTap() {
        TwoFingerTapDetector detector = new TwoFingerTapDetector(SLOP);
        detector.onDown(1000, 0, 100, 200);
        detector.onPointerDown(1010, 1, 300, 200, 2);
        detector.onPointerDown(1020, 2, 500, 200, 3);
        assertFalse(detector.onUp(1150));
    }

    @Test
    public void secondFingerLateIsNotATap() {
        TwoFingerTapDetector detector = new TwoFingerTapDetector(SLOP);
        detector.onDown(1000, 0, 100, 200);
        detector.onPointerDown(1000 + TwoFingerTapDetector.MAX_TAP_MS + 50, 1, 300, 200, 2);
        assertFalse(detector.onUp(1400));
    }

    @Test
    public void cancelledGestureIsNotATap() {
        TwoFingerTapDetector detector = new TwoFingerTapDetector(SLOP);
        detector.onDown(1000, 0, 100, 200);
        detector.onPointerDown(1020, 1, 300, 200, 2);
        detector.cancel();
        assertFalse(detector.onUp(1100));
    }
}
