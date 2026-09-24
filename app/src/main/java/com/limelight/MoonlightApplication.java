package com.limelight;

import android.app.Application;

import com.limelight.utils.OverlayManager;

/** Application entry point used to install the shared overlay lifecycle early. */
public class MoonlightApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        OverlayManager.getInstance().initialize(this);
    }
}
