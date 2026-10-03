package com.limelight.heokami;

import android.app.Activity;
import android.view.View;

import com.limelight.Game;
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboard;
import com.limelight.heokami.layout.LayoutProfileDialogs;
import com.limelight.nvstream.NvConnection;
import com.limelight.portal.PortalManagerView;
import com.limelight.preferences.PreferenceConfiguration;

/**
 * What the game menu needs from whatever shows it: the real stream ({@link Game}) or the
 * settings test mode, which shows the same menu with sample data and no connection.
 */
public interface GameMenuHost {
    /** Activity used for views, dialogs, strings and toasts. */
    Activity menuActivity();

    /** The stream activity, or null when the menu is shown by the settings test mode. */
    Game getStreamGame();

    /** True in the settings test mode: actions that need a stream only explain themselves. */
    boolean isMenuDemo();

    /** Null without a stream. */
    NvConnection getMenuConnection();

    PreferenceConfiguration getPrefConfig();

    PortalManagerView getPortalManagerView();

    boolean arePortalsEnabled();

    VirtualKeyboard getVirtualKeyboard();

    View getStreamView();

    int getCurrentTouchMode();

    void changeTouchMode(int mode);

    int getTouchpadSensitivityPercent();

    void setTouchpadSensitivityPercent(int percent);

    int getStreamAudioGainPercent();

    void setStreamAudioGainPercent(int percent);

    String getStreamAudioGainLabel();

    void postNotification(String text, int duration);

    void onGameMenuShown();

    void onGameMenuHidden();

    void toggleVirtualKeyboard();

    void toggleVirtualController();

    void togglePerfOverlay();

    void toggleKeyboard();

    void quitAndDisconnect();

    void recreateConnectionWithDisplay(String displayName);

    LayoutProfileDialogs.StreamContext createKeyboardLayoutContext();

    void reloadVirtualKeyboardLayout();
}
