package com.limelight.heokami;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GameMenuGeometryTest {
    private static final float DENSITY = 2f;

    @Test
    public void landscapeUsesAboutAThirdOfTheScreen() {
        // 2400 * 0.38 = 912px, inside [640, 960]
        assertEquals(912, GameMenuGeometry.panelWidthPx(2400, 1080, DENSITY));
    }

    @Test
    public void widthIsClampedToTheMaximum() {
        assertEquals(960, GameMenuGeometry.panelWidthPx(3600, 1600, DENSITY));
    }

    @Test
    public void widthIsClampedToTheMinimumButNeverExceedsTheScreen() {
        assertEquals(640, GameMenuGeometry.panelWidthPx(1280, 720, DENSITY));
        assertEquals(500, GameMenuGeometry.panelWidthPx(500, 400, DENSITY));
    }

    @Test
    public void portraitUsesMostOfTheWidth() {
        // 1080 * 0.86 = 928px
        assertEquals(928, GameMenuGeometry.panelWidthPx(1080, 2400, DENSITY));
    }
}
