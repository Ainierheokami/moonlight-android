package com.limelight.heokami.activity

import android.app.Activity
import android.view.View
import com.limelight.Game
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboard
import com.limelight.heokami.GameMenuHost
import com.limelight.heokami.layout.LayoutProfileDialogs
import com.limelight.nvstream.NvConnection
import com.limelight.portal.PortalManagerView
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.utils.AppToast

/**
 * Host for the game menu when it is shown by the settings test mode: no stream, no connection.
 * The menu renders with the user's real settings and lets sliders and touch mode be tried; actions
 * that need a stream are intercepted by the menu itself.
 */
internal class DemoMenuHost(
    private val activity: Activity,
    private val onShown: () -> Unit,
    private val onHidden: () -> Unit
) : GameMenuHost {
    private var touchMode = 0
    private var audioGainPercent = Game.STREAM_AUDIO_GAIN_DEFAULT_PERCENT
    private var touchpadSensitivity = PreferenceConfiguration.DEFAULT_TOUCHPAD_SENSITIVITY

    override fun menuActivity(): Activity = activity
    override fun getStreamGame(): Game? = null
    override fun isMenuDemo(): Boolean = true
    override fun getMenuConnection(): NvConnection? = null
    override fun getPrefConfig(): PreferenceConfiguration = PreferenceConfiguration.readPreferences(activity)
    override fun getPortalManagerView(): PortalManagerView? = null
    override fun arePortalsEnabled(): Boolean = false
    override fun getVirtualKeyboard(): VirtualKeyboard? = null
    override fun getStreamView(): View? = null

    override fun getCurrentTouchMode(): Int = touchMode
    override fun changeTouchMode(mode: Int) {
        touchMode = mode
    }

    override fun getTouchpadSensitivityPercent(): Int = touchpadSensitivity
    override fun setTouchpadSensitivityPercent(percent: Int) {
        touchpadSensitivity = percent
    }

    override fun getStreamAudioGainPercent(): Int = audioGainPercent
    override fun setStreamAudioGainPercent(percent: Int) {
        audioGainPercent = percent
    }

    override fun getStreamAudioGainLabel(): String = "$audioGainPercent%"

    override fun postNotification(text: String, duration: Int) {
        AppToast.makeText(activity, text, AppToast.LENGTH_SHORT).show()
    }

    override fun onGameMenuShown() = onShown()
    override fun onGameMenuHidden() = onHidden()

    override fun toggleVirtualKeyboard() {}
    override fun toggleVirtualController() {}
    override fun togglePerfOverlay() {}
    override fun toggleKeyboard() {}
    override fun quitAndDisconnect() {}
    override fun recreateConnectionWithDisplay(displayName: String) {}
    override fun recreateConnectionWithDisplay(displayName: String, overrideUseVdd: Boolean?) {}

    override fun createKeyboardLayoutContext(): LayoutProfileDialogs.StreamContext =
        LayoutProfileDialogs.StreamContext(null, null, -1, null) {}

    override fun reloadVirtualKeyboardLayout() {}
}
