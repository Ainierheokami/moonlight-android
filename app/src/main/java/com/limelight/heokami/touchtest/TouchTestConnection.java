package com.limelight.heokami.touchtest;

import android.content.Context;

import com.limelight.binding.PlatformBinding;
import com.limelight.heokami.VirtualKeyboardVkCode;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.nvstream.input.MouseButtonPacket;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Stands in for the host connection during a touch test: nothing goes over the network, every
 * input packet the stream would send is written to the {@link TouchTestLog} instead.
 * Everything above it (touch routing, touch modes, keyboards, controllers, the menu) is the real
 * streaming code, so the log shows exactly what a real host would receive.
 */
public class TouchTestConnection extends NvConnection {
    private static final String[] TOUCH_TYPES = {
            "HOVER", "DOWN", "UP", "MOVE", "CANCEL", "BUTTON_ONLY", "HOVER_LEAVE", "CANCEL_ALL"
    };

    public TouchTestConnection(Context context, StreamConfiguration config) {
        super(context, new ComputerDetails.AddressTuple("127.0.0.1", NvHTTP.DEFAULT_HTTP_PORT),
                0, "touch-test", config, PlatformBinding.getCryptoProvider(context), null);
    }

    private static void log(String text) {
        TouchTestLog log = TouchTestLog.active();
        if (log != null) {
            log.log(TouchTestLog.Category.HOST, text);
        }
    }

    private static void upsert(String key, String text) {
        TouchTestLog log = TouchTestLog.active();
        if (log != null) {
            log.upsert(TouchTestLog.Category.HOST, key, text);
        }
    }

    private static void accumulate(String key, String format, long a, long b) {
        TouchTestLog log = TouchTestLog.active();
        if (log != null) {
            log.accumulate(TouchTestLog.Category.HOST, key, format, a, b);
        }
    }

    @Override
    public void start(AudioRenderer audioRenderer, VideoDecoderRenderer videoDecoderRenderer,
                      NvConnectionListener connectionListener) {
        // No host: the test drives the activity itself.
    }

    @Override
    public void stop() {
    }

    @Override
    public NvHTTP createNvHttp() throws IOException {
        throw new IOException("touch test mode has no host");
    }

    @Override
    public List<NvHTTP.DisplayInfo> getDisplays() throws IOException {
        throw new IOException("touch test mode has no host");
    }

    @Override
    public List<String> getDisplayNames() throws IOException {
        throw new IOException("touch test mode has no host");
    }

    @Override
    public void setBitrate(int bitrateKbps, BitrateAdjustmentCallback callback) {
        log("bitrate -> " + bitrateKbps + " kbps (not applied)");
        callback.onComplete(false, "touch test mode");
    }

    @Override
    public void rotateDisplay(int angle, DisplayRotationCallback callback) {
        log("rotate display " + angle + "° (not applied)");
        callback.onComplete(false, "touch test mode");
    }

    @Override
    public void sendMouseMove(short deltaX, short deltaY) {
        accumulate("mouse-move", "mouse move Σ(%d,%d)", deltaX, deltaY);
    }

    @Override
    public void sendMousePosition(short x, short y, short referenceWidth, short referenceHeight) {
        upsert("mouse-pos", String.format(Locale.US, "mouse position (%d,%d) / %dx%d",
                x, y, referenceWidth, referenceHeight));
    }

    @Override
    public void sendMouseMoveAsMousePosition(short deltaX, short deltaY, short referenceWidth, short referenceHeight) {
        upsert("mouse-pos-delta", String.format(Locale.US, "mouse move as position (%d,%d) / %dx%d",
                deltaX, deltaY, referenceWidth, referenceHeight));
    }

    @Override
    public void sendMouseButtonDown(byte mouseButton) {
        log("mouse " + buttonName(mouseButton) + " DOWN");
    }

    @Override
    public void sendMouseButtonUp(byte mouseButton) {
        log("mouse " + buttonName(mouseButton) + " UP");
    }

    @Override
    public void sendKeyboardInput(short keyMap, byte keyDirection, byte modifier, byte flags) {
        String direction = keyDirection == KeyboardPacket.KEY_DOWN ? "DOWN"
                : keyDirection == KeyboardPacket.KEY_UP ? "UP" : String.valueOf(keyDirection);
        log("key " + keyName(keyMap) + " " + direction + modifierText(modifier));
    }

    @Override
    public void sendMouseScroll(byte scrollClicks) {
        sendMouseHighResScroll((short) (scrollClicks * 120));
    }

    @Override
    public void sendMouseHScroll(byte scrollClicks) {
        sendMouseHighResHScroll((short) (scrollClicks * 120));
    }

    @Override
    public void sendMouseHighResScroll(short scrollAmount) {
        accumulate("scroll", "scroll Σ%d", scrollAmount, 0);
    }

    @Override
    public void sendMouseHighResHScroll(short scrollAmount) {
        accumulate("hscroll", "h-scroll Σ%d", scrollAmount, 0);
    }

    @Override
    public int sendTouchEvent(byte eventType, int pointerId, float x, float y, float pressureOrDistance,
                              float contactAreaMajor, float contactAreaMinor, short rotation) {
        String text = String.format(Locale.US, "touch %s id=%d (%.3f,%.3f)",
                touchType(eventType), pointerId, x, y);
        if (eventType == 0x03) {
            upsert("touch-move-" + pointerId, text);
        }
        else {
            log(text);
        }
        return 0;
    }

    @Override
    public int sendPenEvent(byte eventType, byte toolType, byte penButtons, float x, float y,
                            float pressureOrDistance, float contactAreaMajor, float contactAreaMinor,
                            short rotation, byte tilt) {
        String text = String.format(Locale.US, "pen %s tool=%d buttons=%d (%.3f,%.3f) p=%.2f",
                touchType(eventType), toolType, penButtons, x, y, pressureOrDistance);
        if (eventType == 0x03 || eventType == 0x00) {
            upsert("pen-move", text);
        }
        else {
            log(text);
        }
        return 0;
    }

    @Override
    public void sendControllerInput(short controllerNumber, short activeGamepadMask, int buttonFlags,
                                    byte leftTrigger, byte rightTrigger, short leftStickX, short leftStickY,
                                    short rightStickX, short rightStickY) {
        upsert("pad-" + controllerNumber, String.format(Locale.US,
                "pad %d buttons=0x%X LT=%d RT=%d L(%d,%d) R(%d,%d)", controllerNumber, buttonFlags,
                leftTrigger & 0xFF, rightTrigger & 0xFF, leftStickX, leftStickY, rightStickX, rightStickY));
    }

    @Override
    public int sendControllerArrivalEvent(byte controllerNumber, short activeGamepadMask, byte type,
                                          int supportedButtonFlags, short capabilities) {
        log("pad " + controllerNumber + " arrived (type " + type + ")");
        return 0;
    }

    @Override
    public int sendControllerTouchEvent(byte controllerNumber, byte eventType, int pointerId,
                                        float x, float y, float pressure) {
        upsert("pad-touch-" + controllerNumber, String.format(Locale.US, "pad %d touchpad %s id=%d (%.3f,%.3f)",
                controllerNumber, touchType(eventType), pointerId, x, y));
        return 0;
    }

    @Override
    public int sendControllerMotionEvent(byte controllerNumber, byte motionType, float x, float y, float z) {
        return 0;
    }

    @Override
    public void sendControllerBatteryEvent(byte controllerNumber, byte batteryState, byte batteryPercentage) {
    }

    @Override
    public void sendUtf8Text(String text) {
        log("text \"" + text + "\"");
    }

    private static String touchType(byte type) {
        return type >= 0 && type < TOUCH_TYPES.length ? TOUCH_TYPES[type] : String.valueOf(type);
    }

    private static String buttonName(byte button) {
        switch (button) {
            case MouseButtonPacket.BUTTON_LEFT: return "LEFT";
            case MouseButtonPacket.BUTTON_MIDDLE: return "MIDDLE";
            case MouseButtonPacket.BUTTON_RIGHT: return "RIGHT";
            case MouseButtonPacket.BUTTON_X1: return "X1";
            case MouseButtonPacket.BUTTON_X2: return "X2";
            default: return String.valueOf(button);
        }
    }

    static String keyName(short keyMap) {
        int code = keyMap & 0xFF;
        VirtualKeyboardVkCode.VKCode vk = VirtualKeyboardVkCode.VKCode.Companion.fromCode(code);
        String hex = String.format(Locale.US, "0x%02X", code);
        if (vk == null) {
            return hex;
        }
        String name = vk.getKeyName() != null ? vk.getKeyName() : vk.name().replace("VK_", "");
        return name + "(" + hex + ")";
    }

    private static String modifierText(byte modifier) {
        if (modifier == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" mods=");
        if ((modifier & 0x01) != 0) sb.append("Shift+");
        if ((modifier & 0x02) != 0) sb.append("Ctrl+");
        if ((modifier & 0x04) != 0) sb.append("Alt+");
        if ((modifier & 0x08) != 0) sb.append("Win+");
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }
}
