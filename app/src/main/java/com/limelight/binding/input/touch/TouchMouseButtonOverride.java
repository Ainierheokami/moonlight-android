package com.limelight.binding.input.touch;

import com.limelight.nvstream.input.MouseButtonPacket;

public final class TouchMouseButtonOverride {
    private static int rightClickHolders;
    private static boolean rightClickRequested;

    private TouchMouseButtonOverride() {}

    public static synchronized void holdRightClick() {
        rightClickHolders++;
    }

    public static synchronized void releaseRightClick() {
        if (rightClickHolders > 0) {
            rightClickHolders--;
        }
    }

    public static synchronized void requestRightClick() {
        rightClickRequested = true;
    }

    public static synchronized void clearRightClick() {
        rightClickRequested = false;
    }

    public static synchronized byte getPrimaryButton() {
        return rightClickHolders > 0 || rightClickRequested ? MouseButtonPacket.BUTTON_RIGHT : MouseButtonPacket.BUTTON_LEFT;
    }
}
