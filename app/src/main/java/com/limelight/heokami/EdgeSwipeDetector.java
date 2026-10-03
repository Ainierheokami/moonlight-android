package com.limelight.heokami;

/**
 * Pure state machine for the "swipe in from a screen edge to open the game menu" gesture.
 *
 * <p>It owns no Android types so the in-stream {@code Game} activity and the settings test mode
 * run the exact same decisions, and so the rules can be unit tested. The caller feeds it single
 * pointer events in view coordinates (px) and acts on the result: nothing, take over the touch
 * (cancel whatever the stream has started), or open the menu on a side.</p>
 *
 * <p>Rules (unchanged from the original in-{@code Game} implementation):</p>
 * <ul>
 *   <li>A gesture is a candidate only if it starts inside the left or right edge zone.</li>
 *   <li>It takes over the touch once it moved more than the intent threshold <em>inwards</em>
 *       and the motion is mostly horizontal (|dx| &gt; 1.2 * |dy|).</li>
 *   <li>It opens the menu once the inward travel exceeds the swipe threshold while still mostly
 *       horizontal. The menu opens on the side the swipe started from.</li>
 *   <li>A second pointer abandons the gesture.</li>
 * </ul>
 */
public final class EdgeSwipeDetector {
    public enum Side { LEFT, RIGHT }

    public enum MoveResult {
        /** The gesture did not start in an edge zone; leave the event to the stream. */
        NOT_TRACKING,
        /** Candidate, but not (yet) a deliberate edge swipe; leave the event to the stream. */
        PENDING,
        /** Just became a deliberate edge swipe; cancel stream touches, swallow the event. */
        TAKEOVER,
        /** Menu should open on the left. Reported once per gesture. */
        TRIGGERED_LEFT,
        /** Menu should open on the right. Reported once per gesture. */
        TRIGGERED_RIGHT,
        /** Already taken over (or triggered); keep swallowing events. */
        CONSUMING
    }

    /** Why a finished gesture did not open the menu. Used by the settings test mode. */
    public enum Miss {
        NONE,
        NOT_IN_EDGE_ZONE,
        WRONG_DIRECTION,
        NOT_HORIZONTAL,
        TOO_SHORT,
        MULTI_TOUCH
    }

    public static final float HORIZONTAL_RATIO = 1.2f;
    /** The intent threshold never exceeds this, so long swipe distances still feel responsive. */
    public static final int INTENT_THRESHOLD_CAP_DP = 10;
    /** Minimum width of the strip excluded from the system back gesture. */
    public static final int SYSTEM_GESTURE_EXCLUSION_MIN_DP = 32;

    private int edgeZonePx = 1;
    private int thresholdPx = 1;
    private int intentThresholdPx = 1;

    private int viewWidth;
    private float downX = -1;
    private float downY = -1;
    private float travelX;
    private float travelY;
    private boolean candidate;
    private boolean consuming;
    private boolean triggered;
    private Miss lastMiss = Miss.NONE;

    /** The intent threshold is the swipe threshold, capped so short thresholds stay responsive. */
    public static int intentThresholdFor(int thresholdPx, int intentCapPx) {
        return Math.min(thresholdPx, intentCapPx);
    }

    /** Width of the strip excluded from the system back gesture, shared by Game and test mode. */
    public static int systemGestureExclusionWidthPx(int hotZonePx, int minExclusionPx, int viewWidthPx) {
        return Math.min(Math.max(minExclusionPx, hotZonePx), Math.max(1, viewWidthPx / 3));
    }

    public void configure(int edgeZonePx, int thresholdPx, int intentThresholdPx) {
        this.edgeZonePx = edgeZonePx;
        this.thresholdPx = thresholdPx;
        this.intentThresholdPx = intentThresholdPx;
    }

    /** Starts tracking a new gesture. Returns whether it is an edge-zone candidate. */
    public boolean down(float x, float y, int viewWidth) {
        this.viewWidth = viewWidth;
        downX = x;
        downY = y;
        travelX = 0;
        travelY = 0;
        triggered = false;
        consuming = false;
        lastMiss = Miss.NONE;
        candidate = x <= edgeZonePx || x >= viewWidth - edgeZonePx;
        return candidate;
    }

    public MoveResult move(float x, float y) {
        if (!candidate && !consuming) {
            return MoveResult.NOT_TRACKING;
        }

        travelX = x - downX;
        travelY = y - downY;
        boolean left = startedLeft();
        boolean right = startedRight();
        boolean horizontal = isHorizontalIntent();

        boolean wasConsuming = consuming;
        if (!consuming && horizontal
                && ((left && travelX > intentThresholdPx) || (right && travelX < -intentThresholdPx))) {
            consuming = true;
        }
        if (!consuming) {
            return MoveResult.PENDING;
        }

        if (!triggered && horizontal
                && ((left && travelX > thresholdPx) || (right && travelX < -thresholdPx))) {
            triggered = true;
            candidate = false;
            return left ? MoveResult.TRIGGERED_LEFT : MoveResult.TRIGGERED_RIGHT;
        }
        return wasConsuming ? MoveResult.CONSUMING : MoveResult.TAKEOVER;
    }

    /** Ends the gesture. Returns whether the stream must not see this event (it was ours). */
    public boolean up() {
        boolean wasConsuming = consuming;
        lastMiss = classifyMiss();
        clearTracking();
        return wasConsuming;
    }

    /** Abandons the gesture because a second pointer arrived. Returns whether it was consuming. */
    public boolean cancelForMultiTouch() {
        boolean wasConsuming = consuming;
        if (candidate || consuming) {
            lastMiss = Miss.MULTI_TOUCH;
        }
        clearTracking();
        return wasConsuming;
    }

    /** Silently drops any gesture (feature disabled, menu already showing...). */
    public boolean reset() {
        boolean wasConsuming = consuming;
        clearTracking();
        return wasConsuming;
    }

    private void clearTracking() {
        candidate = false;
        consuming = false;
        triggered = false;
        downX = -1;
        downY = -1;
    }

    private Miss classifyMiss() {
        if (triggered) {
            return Miss.NONE;
        }
        if (downX < 0) {
            return Miss.NONE;
        }
        boolean left = startedLeft();
        boolean right = startedRight();
        if (!left && !right) {
            return Miss.NOT_IN_EDGE_ZONE;
        }
        boolean inward = (left && travelX > 0) || (right && travelX < 0);
        if (!inward) {
            return Miss.WRONG_DIRECTION;
        }
        if (Math.abs(travelX) > intentThresholdPx && !isHorizontalIntent()) {
            return Miss.NOT_HORIZONTAL;
        }
        return Miss.TOO_SHORT;
    }

    private boolean startedLeft() {
        return downX >= 0 && downX <= edgeZonePx;
    }

    private boolean startedRight() {
        return downX >= 0 && downX >= viewWidth - edgeZonePx;
    }

    private boolean isHorizontalIntent() {
        return Math.abs(travelX) > Math.abs(travelY) * HORIZONTAL_RATIO;
    }

    public boolean isCandidate() {
        return candidate;
    }

    public boolean isConsuming() {
        return consuming;
    }

    public boolean isTriggered() {
        return triggered;
    }

    /** The edge this gesture started from, or null if it did not start in a zone. */
    public Side getStartSide() {
        if (startedLeft()) {
            return Side.LEFT;
        }
        if (startedRight()) {
            return Side.RIGHT;
        }
        return null;
    }

    public boolean startedInLeftZone() {
        return startedLeft();
    }

    public float getTravelX() {
        return travelX;
    }

    public float getTravelY() {
        return travelY;
    }

    public boolean isHorizontal() {
        return isHorizontalIntent();
    }

    /** Inward distance still missing to open the menu (0 once reached), or -1 if not a candidate. */
    public float remainingPx() {
        Side side = getStartSide();
        if (side == null) {
            return -1;
        }
        float inward = side == Side.LEFT ? travelX : -travelX;
        return Math.max(0, thresholdPx - Math.max(0, inward));
    }

    public Miss getLastMiss() {
        return lastMiss;
    }

    public int getEdgeZonePx() {
        return edgeZonePx;
    }

    public int getThresholdPx() {
        return thresholdPx;
    }
}
