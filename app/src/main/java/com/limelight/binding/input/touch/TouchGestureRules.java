package com.limelight.binding.input.touch;

/**
 * Pure decision rules for relative-touch gestures, kept free of Android types so they can be
 * unit tested.
 */
final class TouchGestureRules {
    private TouchGestureRules() {}

    /** True while both axis displacements from the touch-down point stay within the tap bounds. */
    static boolean isWithinTapBounds(int dx, int dy, int threshold) {
        return Math.abs(dx) <= threshold && Math.abs(dy) <= threshold;
    }

    /** Path length only counts towards "move" during the initial window of a gesture. */
    static boolean shouldAccumulatePath(long elapsedMs, int windowMs) {
        return elapsedMs <= windowMs;
    }

    /**
     * A resting single finger that has stayed inside the tap bounds for longer than
     * {@code holdJitterMs} has its sub-threshold motion swallowed instead of sent to the host.
     */
    static boolean shouldSwallowJitter(boolean filterEnabled, int actionIndex, int pointerCount,
                                       boolean confirmedMove, boolean confirmedDrag,
                                       long elapsedMs, int holdJitterMs) {
        return filterEnabled && actionIndex == 0 && pointerCount == 1
                && !confirmedMove && !confirmedDrag
                && elapsedMs > holdJitterMs;
    }
}
