package com.limelight.binding.input.touch;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TouchGestureRulesTest {
    @Test
    public void tapBoundsAreInclusiveOnBothAxes() {
        assertTrue(TouchGestureRules.isWithinTapBounds(0, 0, 8));
        assertTrue(TouchGestureRules.isWithinTapBounds(8, -8, 8));
        assertFalse(TouchGestureRules.isWithinTapBounds(9, 0, 8));
        assertFalse(TouchGestureRules.isWithinTapBounds(0, -9, 8));
    }

    @Test
    public void pathOnlyAccumulatesInsideInitialWindow() {
        assertTrue(TouchGestureRules.shouldAccumulatePath(0, 200));
        assertTrue(TouchGestureRules.shouldAccumulatePath(200, 200));
        assertFalse(TouchGestureRules.shouldAccumulatePath(201, 200));
    }

    @Test
    public void swallowsJitterOnlyForRestingPrimaryFingerAfterHoldTime() {
        assertTrue(TouchGestureRules.shouldSwallowJitter(true, 0, 1, false, false, 301, 300));
        assertFalse("not yet held long enough",
                TouchGestureRules.shouldSwallowJitter(true, 0, 1, false, false, 300, 300));
        assertFalse("filter disabled",
                TouchGestureRules.shouldSwallowJitter(false, 0, 1, false, false, 500, 300));
        assertFalse("secondary pointer",
                TouchGestureRules.shouldSwallowJitter(true, 1, 1, false, false, 500, 300));
        assertFalse("two fingers (scroll)",
                TouchGestureRules.shouldSwallowJitter(true, 0, 2, false, false, 500, 300));
        assertFalse("real move already confirmed",
                TouchGestureRules.shouldSwallowJitter(true, 0, 1, true, false, 500, 300));
        assertFalse("dragging",
                TouchGestureRules.shouldSwallowJitter(true, 0, 1, false, true, 500, 300));
    }
}
