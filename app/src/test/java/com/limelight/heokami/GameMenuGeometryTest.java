package com.limelight.heokami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void toolBoxTakesTheFreeSpaceUpToItsMaximum() {
        // 1600 - 640 (panel) - 3 * 24 (gaps) = 888, capped at 420dp = 840px
        assertEquals(840, GameMenuGeometry.toolBoxWidthPx(1600, 720, DENSITY, 640));
    }

    @Test
    public void toolBoxShrinksToWhatIsLeftButNotBelowItsMinimum() {
        // 1280 - 640 - 72 = 568, above the 260dp = 520px minimum
        assertEquals(568, GameMenuGeometry.toolBoxWidthPx(1280, 720, DENSITY, 640));
        // 1000 - 640 - 72 = 288 < 520: not enough room, tabs go into the panel
        assertEquals(0, GameMenuGeometry.toolBoxWidthPx(1000, 600, DENSITY, 640));
    }

    @Test
    public void thereIsNoToolBoxInPortrait() {
        assertEquals(0, GameMenuGeometry.toolBoxWidthPx(1080, 2400, DENSITY, 928));
        assertEquals(0, GameMenuGeometry.toolBoxWidthPx(1000, 1000, DENSITY, 640));
    }

    @Test
    public void boxPanelAndGapsAlwaysFitOnScreen() {
        for (int width = 900; width <= 3000; width += 100) {
            int panel = GameMenuGeometry.panelWidthPx(width, 720, DENSITY);
            int box = GameMenuGeometry.toolBoxWidthPx(width, 720, DENSITY, panel);
            if (box > 0) {
                int gaps = 3 * (int) (12 * DENSITY);
                assertTrue("width " + width, box + panel + gaps <= width);
            }
        }
    }
}
