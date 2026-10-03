package com.limelight.heokami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.limelight.heokami.EdgeSwipeDetector.MoveResult;
import com.limelight.heokami.EdgeSwipeDetector.Miss;
import com.limelight.heokami.EdgeSwipeDetector.Side;

public class EdgeSwipeDetectorTest {
    private static final int WIDTH = 2000;
    private static final int ZONE = 48;
    private static final int THRESHOLD = 168;
    private static final int INTENT = 30;

    private EdgeSwipeDetector detector;

    @Before
    public void setUp() {
        detector = new EdgeSwipeDetector();
        detector.configure(ZONE, THRESHOLD, INTENT);
    }

    @Test
    public void leftEdgeSwipeTakesOverThenOpensLeft() {
        assertTrue(detector.down(10, 500, WIDTH));
        assertEquals(MoveResult.PENDING, detector.move(25, 502));
        assertEquals(MoveResult.TAKEOVER, detector.move(60, 505));
        assertEquals(MoveResult.CONSUMING, detector.move(120, 505));
        assertEquals(MoveResult.TRIGGERED_LEFT, detector.move(190, 505));
        assertTrue(detector.up());
        assertEquals(Miss.NONE, detector.getLastMiss());
    }

    @Test
    public void rightEdgeSwipeOpensRight() {
        assertTrue(detector.down(WIDTH - 5, 500, WIDTH));
        assertEquals(MoveResult.TAKEOVER, detector.move(WIDTH - 60, 500));
        assertEquals(MoveResult.TRIGGERED_RIGHT, detector.move(WIDTH - 200, 500));
    }

    @Test
    public void triggerIsReportedOnlyOncePerGesture() {
        detector.down(10, 500, WIDTH);
        detector.move(60, 500);
        assertEquals(MoveResult.TRIGGERED_LEFT, detector.move(200, 500));
        assertEquals(MoveResult.CONSUMING, detector.move(260, 500));
        assertEquals(MoveResult.CONSUMING, detector.move(300, 500));
    }

    @Test
    public void jumpingPastBothThresholdsInOneMoveStillTriggers() {
        detector.down(10, 500, WIDTH);
        assertFalse(detector.isConsuming());
        assertEquals(MoveResult.TRIGGERED_LEFT, detector.move(400, 500));
        assertTrue(detector.isConsuming());
    }

    @Test
    public void gestureStartingOutsideTheZoneIsNeverTracked() {
        assertFalse(detector.down(ZONE + 1, 500, WIDTH));
        assertEquals(MoveResult.NOT_TRACKING, detector.move(400, 500));
        assertFalse(detector.up());
        assertEquals(Miss.NOT_IN_EDGE_ZONE, detector.getLastMiss());
    }

    @Test
    public void zoneBoundariesAreInclusive() {
        assertTrue(detector.down(ZONE, 0, WIDTH));
        assertTrue(detector.down(WIDTH - ZONE, 0, WIDTH));
        assertFalse(detector.down(ZONE + 0.5f, 0, WIDTH));
        assertFalse(detector.down(WIDTH - ZONE - 0.5f, 0, WIDTH));
    }

    @Test
    public void mostlyVerticalSwipeDoesNotTakeOver() {
        detector.down(10, 500, WIDTH);
        assertEquals(MoveResult.PENDING, detector.move(60, 300));
        assertEquals(MoveResult.PENDING, detector.move(100, 150));
        assertFalse(detector.up());
        assertEquals(Miss.NOT_HORIZONTAL, detector.getLastMiss());
    }

    @Test
    public void horizontalRatioIsStrict() {
        detector.down(10, 500, WIDTH);
        // |dx| == 1.2 * |dy| is not "more than" the ratio
        assertEquals(MoveResult.PENDING, detector.move(10 + 120, 500 + 100));
        assertEquals(MoveResult.TAKEOVER, detector.move(10 + 121, 500 + 100));
    }

    @Test
    public void swipingOutwardFromTheEdgeIsWrongDirection() {
        detector.down(WIDTH - 10, 500, WIDTH);
        assertEquals(MoveResult.PENDING, detector.move(WIDTH - 5, 500));
        detector.up();
        assertEquals(Miss.WRONG_DIRECTION, detector.getLastMiss());
    }

    @Test
    public void shortSwipeIsTooShortAndNotConsumed() {
        detector.down(10, 500, WIDTH);
        assertEquals(MoveResult.PENDING, detector.move(25, 500));
        assertFalse(detector.up());
        assertEquals(Miss.TOO_SHORT, detector.getLastMiss());
    }

    @Test
    public void takenOverButShortOfThresholdStillSwallowsTheRelease() {
        detector.down(10, 500, WIDTH);
        assertEquals(MoveResult.TAKEOVER, detector.move(60, 500));
        assertTrue(detector.up());
        assertEquals(Miss.TOO_SHORT, detector.getLastMiss());
    }

    @Test
    public void secondPointerAbandonsTheGesture() {
        detector.down(10, 500, WIDTH);
        detector.move(60, 500);
        assertTrue(detector.cancelForMultiTouch());
        assertEquals(Miss.MULTI_TOUCH, detector.getLastMiss());
        assertEquals(MoveResult.NOT_TRACKING, detector.move(200, 500));
    }

    @Test
    public void resetDropsStateWithoutReportingAMiss() {
        detector.down(10, 500, WIDTH);
        detector.move(60, 500);
        assertTrue(detector.reset());
        assertFalse(detector.isCandidate());
        assertFalse(detector.isConsuming());
        assertEquals(Miss.NONE, detector.getLastMiss());
    }

    @Test
    public void newGestureStartsClean() {
        detector.down(10, 500, WIDTH);
        detector.move(60, 500);
        detector.move(200, 500);
        detector.up();
        assertTrue(detector.down(10, 500, WIDTH));
        assertFalse(detector.isConsuming());
        assertFalse(detector.isTriggered());
        assertEquals(Miss.NONE, detector.getLastMiss());
    }

    @Test
    public void startSideAndRemainingDistance() {
        assertNull(detector.getStartSide());
        detector.down(10, 500, WIDTH);
        assertEquals(Side.LEFT, detector.getStartSide());
        detector.move(10 + 100, 500);
        assertEquals(68f, detector.remainingPx(), 0.001f);
        detector.down(WIDTH - 10, 500, WIDTH);
        assertEquals(Side.RIGHT, detector.getStartSide());
        assertEquals(THRESHOLD, detector.remainingPx(), 0.001f);
    }

    @Test
    public void thresholdsBelowTheIntentCapUseTheThreshold() {
        assertEquals(20, EdgeSwipeDetector.intentThresholdFor(20, 30));
        assertEquals(30, EdgeSwipeDetector.intentThresholdFor(168, 30));
    }

    @Test
    public void systemGestureExclusionIsClampedToAThirdOfTheScreen() {
        assertEquals(90, EdgeSwipeDetector.systemGestureExclusionWidthPx(48, 90, 2000));
        assertEquals(160, EdgeSwipeDetector.systemGestureExclusionWidthPx(160, 90, 2000));
        assertEquals(100, EdgeSwipeDetector.systemGestureExclusionWidthPx(160, 90, 300));
    }
}
