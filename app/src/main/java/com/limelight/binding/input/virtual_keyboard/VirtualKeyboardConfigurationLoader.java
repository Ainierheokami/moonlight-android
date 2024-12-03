/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_keyboard;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;

// import com.limelight.nvstream.input.ControllerPacket;
// import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.heokami.VirtualKeyboardVkCode;

import org.json.JSONException;
import org.json.JSONObject;

public class VirtualKeyboardConfigurationLoader {
    public static final String OSK_PREFERENCE = "OSK";

    private static int getPercent(
            int percent,
            int total) {
        return (int) (((float) total / (float) 100) * (float) percent);
    }

    // The default controls are specified using a grid of 128*72 cells at 16:9
    private static int screenScale(int units, int height) {
        return (int) (((float) height / (float) 72) * (float) units);
    }

    private static DigitalButton createDigitalButton(
            final int elementId,
            final short vk_key,
            final int layer,
            final String text,
            final int icon,
            final VirtualKeyboard controller,
            final Context context) {
        DigitalButton button = new DigitalButton(controller, elementId, layer, context);
        button.setText(text);
        button.setIcon(icon);

        button.addDigitalButtonListener(new DigitalButton.DigitalButtonListener() {
            @Override
            public void onClick() {
                VirtualKeyboard.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.modifier |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(vk_key);

                controller.sendDownKey(vk_key);
            }

            @Override
            public void onLongClick() {
                VirtualKeyboard.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.modifier |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(vk_key);

                controller.sendDownKey(vk_key);
            }

            @Override
            public void onRelease() {
                VirtualKeyboard.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.modifier &= (byte) ~VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(vk_key);

                controller.sendUpKey(vk_key);
            }
        });

        return button;
    }


    private static final int START_X = 83;
    private static final int BACK_X = 34;
    private static final int START_BACK_Y = 64;
    private static final int START_BACK_WIDTH = 12;
    private static final int START_BACK_HEIGHT = 7;

    // Make the Guide Menu be in the center of START and BACK menu
    private static final int GUIDE_X = START_X-BACK_X;
    private static final int GUIDE_Y = START_BACK_Y;

    public static void createDefaultLayout(final VirtualKeyboard controller, final Context context) {

        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);

        // Displace controls on the right by this amount of pixels to account for different aspect ratios
        int rightDisplacement = screen.widthPixels - screen.heightPixels * 16 / 9;

        int height = screen.heightPixels;

        // NOTE: Some of these getPercent() expressions seem like they can be combined
        // into a single call. Due to floating point rounding, this isn't actually possible.

        controller.addElement(
                createDigitalButton(
                        VirtualKeyboardElement.EID_GDB,
                        (short) VirtualKeyboardVkCode.VKCode.VK_LWIN.getCode(),
                        1, "测试按钮", -1, controller, context),
                screenScale(GUIDE_X, height)+ rightDisplacement,
                screenScale(GUIDE_Y, height),
                screenScale(START_BACK_WIDTH, height),
                screenScale(START_BACK_HEIGHT, height)
        );

        controller.setOpacity(config.oscOpacity);
    }

    public static void saveProfile(final VirtualKeyboard controller,
                                   final Context context) {
        SharedPreferences.Editor prefEditor = context.getSharedPreferences(OSK_PREFERENCE, Activity.MODE_PRIVATE).edit();

        for (VirtualKeyboardElement element : controller.getElements()) {
            String prefKey = ""+element.elementId;
            try {
                prefEditor.putString(prefKey, element.getConfiguration().toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        prefEditor.apply();
    }

    public static void loadFromPreferences(final VirtualKeyboard controller, final Context context) {
        SharedPreferences pref = context.getSharedPreferences(OSK_PREFERENCE, Activity.MODE_PRIVATE);

        for (VirtualKeyboardElement element : controller.getElements()) {
            String prefKey = ""+element.elementId;

            String jsonConfig = pref.getString(prefKey, null);
            if (jsonConfig != null) {
                try {
                    element.loadConfiguration(new JSONObject(jsonConfig));
                } catch (JSONException e) {
                    e.printStackTrace();

                    // Remove the corrupt element from the preferences
                    pref.edit().remove(prefKey).apply();
                }
            }
        }
    }
}
