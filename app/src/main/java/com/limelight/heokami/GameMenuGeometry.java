package com.limelight.heokami;

/** Sizing rules for the game menu panel, shared by the real menu and the gesture test mode. */
public final class GameMenuGeometry {
    private static final int MAX_WIDTH_DP = 480;
    private static final int MIN_WIDTH_DP = 320;
    private static final float LANDSCAPE_FRACTION = 0.38f;
    private static final float PORTRAIT_FRACTION = 0.86f;

    private GameMenuGeometry() {
    }

    public static int panelWidthPx(int screenWidthPx, int screenHeightPx, float density) {
        int maxWidthPx = (int) (MAX_WIDTH_DP * density);
        int minWidthPx = (int) (MIN_WIDTH_DP * density);
        int targetWidth = (int) (screenWidthPx
                * (screenWidthPx > screenHeightPx ? LANDSCAPE_FRACTION : PORTRAIT_FRACTION));
        return Math.min(Math.max(targetWidth, minWidthPx), Math.min(screenWidthPx, maxWidthPx));
    }
}
