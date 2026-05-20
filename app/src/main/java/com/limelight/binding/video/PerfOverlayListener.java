package com.limelight.binding.video;

public interface PerfOverlayListener {
    void onPerfUpdate(final String text);
    void onVideoSizeChanged(int width, int height);
}
