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

//    private static int getPercent(
//            int percent,
//            int total) {
//        return (int) (((float) total / (float) 100) * (float) percent);
//    }

    // The default controls are specified using a grid of 128*72 cells at 16:9
    private static int screenScale(int units, int height) {
        return (int) (((float) height / (float) 72) * (float) units);
    }

    public static DigitalButton createDigitalButton(
            final int elementId, // 唯一标识
            final short vk_key, // 按键
            final int layer, // 层
            final String text, // 文本
            final int icon, // 图标
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


    private static final int TEST_X = 35;
    private static final int TEST_Y = 35;

    private static final int TEST_WIDTH = 10;
    private static final int TEST_HEIGHT = 5;

    public void addButton(final VirtualKeyboard controller, final Context context, Integer buttonId, String VK_code, String text){
        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);

        int rightDisplacement = screen.widthPixels - screen.heightPixels * 16 / 9;

        int height = screen.heightPixels;

        controller.addElement(
                createDigitalButton(
                        buttonId,
                        Short.parseShort(VK_code),
                        1, text, -1, controller, context
                ),
                screenScale(TEST_X, height) + rightDisplacement,
                screenScale(TEST_Y, height),
                screenScale(TEST_WIDTH, height),
                screenScale(TEST_HEIGHT, height)
        );

        controller.setOpacity(config.oscOpacity);
    }
    public static void createDefaultLayout(final VirtualKeyboard controller, final Context context) {

        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);

        // Displace controls on the right by this amount of pixels to account for different aspect ratios
        int rightDisplacement = screen.widthPixels - screen.heightPixels * 16 / 9;

        int height = screen.heightPixels;


        controller.addElement(
                createDigitalButton(
                        VirtualKeyboardVkCode.VKCode.VK_LWIN.getCode(),
                        (short) VirtualKeyboardVkCode.VKCode.VK_LWIN.getCode(),
                        1, "测试按钮", -1, controller, context
                ),
                screenScale(TEST_X, height) + rightDisplacement,
                screenScale(TEST_Y, height),
                screenScale(TEST_WIDTH, height),
                screenScale(TEST_HEIGHT, height)
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
