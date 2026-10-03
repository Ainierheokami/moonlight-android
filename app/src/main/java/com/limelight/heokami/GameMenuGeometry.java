package com.limelight.heokami;

/** Sizing rules for the game menu panel, shared by the real menu and the gesture test mode. */
public final class GameMenuGeometry {
    private static final int MAX_WIDTH_DP = 480;
    private static final int MIN_WIDTH_DP = 320;
    private static final float LANDSCAPE_FRACTION = 0.38f;
    private static final float PORTRAIT_FRACTION = 0.86f;
    private static final int TOOL_BOX_MIN_WIDTH_DP = 260;
    private static final int TOOL_BOX_MAX_WIDTH_DP = 420;
    private static final int GAP_DP = 12;

    private GameMenuGeometry() {
    }

    public static int panelWidthPx(int screenWidthPx, int screenHeightPx, float density) {
        int maxWidthPx = (int) (MAX_WIDTH_DP * density);
        int minWidthPx = (int) (MIN_WIDTH_DP * density);
        int targetWidth = (int) (screenWidthPx
                * (screenWidthPx > screenHeightPx ? LANDSCAPE_FRACTION : PORTRAIT_FRACTION));
        return Math.min(Math.max(targetWidth, minWidthPx), Math.min(screenWidthPx, maxWidthPx));
    }

    /**
     * Width of the floating tool box that sits on the side opposite the panel, or 0 when there is
     * not enough room (portrait, or a landscape screen the panel already mostly fills). With 0 the
     * tabs live inside the panel instead.
     */
    public static int toolBoxWidthPx(int screenWidthPx, int screenHeightPx, float density, int panelWidthPx) {
        if (screenWidthPx <= screenHeightPx) {
            return 0;
        }
        int gap = (int) (GAP_DP * density);
        // screen edge | gap | box | gap | panel | gap | screen edge
        int free = screenWidthPx - panelWidthPx - 3 * gap;
        if (free < (int) (TOOL_BOX_MIN_WIDTH_DP * density)) {
            return 0;
        }
        return Math.min(free, (int) (TOOL_BOX_MAX_WIDTH_DP * density));
    }
}
