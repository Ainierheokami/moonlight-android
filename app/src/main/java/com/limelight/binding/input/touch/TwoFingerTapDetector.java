package com.limelight.binding.input.touch;

import java.util.HashMap;
import java.util.Map;

/**
 * Recognises a quick two-finger tap (both fingers down and up again without moving) for
 * mouse mode, where it means a right click. It works on whole gestures because the per-finger
 * touch contexts lose track of which finger is which once the first one lifts.
 */
public final class TwoFingerTapDetector {
    public static final long MAX_TAP_MS = 300;

    private final Map<Integer, float[]> starts = new HashMap<>();
    private final float slopPx;
    private long downTime;
    private int maxPointers;
    private boolean invalid = true;
    private float primaryX;
    private float primaryY;

    public TwoFingerTapDetector(float slopPx) {
        this.slopPx = slopPx;
    }

    public void onDown(long time, int pointerId, float x, float y) {
        starts.clear();
        starts.put(pointerId, new float[] {x, y});
        downTime = time;
        maxPointers = 1;
        invalid = false;
        primaryX = x;
        primaryY = y;
    }

    public void onPointerDown(long time, int pointerId, float x, float y, int pointerCount) {
        starts.put(pointerId, new float[] {x, y});
        maxPointers = Math.max(maxPointers, pointerCount);
        if (pointerCount > 2 || time - downTime > MAX_TAP_MS) {
            invalid = true;
        }
    }

    public void onMove(int pointerId, float x, float y) {
        float[] start = starts.get(pointerId);
        if (start != null && Math.hypot(x - start[0], y - start[1]) > slopPx) {
            invalid = true;
        }
    }

    /** Last finger up: returns true when the whole gesture was a two-finger tap. */
    public boolean onUp(long time) {
        boolean tap = !invalid && maxPointers == 2 && time - downTime <= MAX_TAP_MS;
        invalid = true;
        return tap;
    }

    public void cancel() {
        invalid = true;
    }

    /** Where the first finger went down; the right click lands there. */
    public float getPrimaryX() {
        return primaryX;
    }

    public float getPrimaryY() {
        return primaryY;
    }
}
