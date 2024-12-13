/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_keyboard;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;

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
            final short vk_code, // 按键
            final int layer, // 层
            final String text, // 文本
            final int icon, // 图标
            final VirtualKeyboard virtualKeyboard,
            final Context context) {

        DigitalButton button = new DigitalButton(virtualKeyboard, elementId, layer, context);

        button.setText(text);
        button.setIcon(icon);
        button.setVkCode(""+vk_code);

//        button.addDigitalButtonListener(new DigitalButton.DigitalButtonListener() {
//            @Override
//            public void onClick() {
//                VirtualKeyboard.ControllerInputContext inputContext =
//                        virtualKeyboard.getControllerInputContext();
//                inputContext.modifier |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(vk_code);
//
//                virtualKeyboard.sendDownKey(vk_code);
//            }
//
//            @Override
//            public void onLongClick() {
//                VirtualKeyboard.ControllerInputContext inputContext =
//                        virtualKeyboard.getControllerInputContext();
//                inputContext.modifier |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(vk_code);
//
//                virtualKeyboard.sendDownKey(vk_code);
//            }
//
//            @Override
//            public void onRelease() {
//                VirtualKeyboard.ControllerInputContext inputContext =
//                        virtualKeyboard.getControllerInputContext();
//                inputContext.modifier &= (byte) ~VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(vk_code);
//
//                virtualKeyboard.sendUpKey(vk_code);
//            }
//        });

        return button;
    }

    private static final int TEST_X = 35;
    private static final int TEST_Y = 35;

    private static final int TEST_WIDTH = 10;
    private static final int TEST_HEIGHT = 5;

    public static void addButton(final VirtualKeyboard virtualKeyboard, final Context context, Integer buttonId, String VK_code, String text){
        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);

        int rightDisplacement = screen.widthPixels - screen.heightPixels * 16 / 9;

        int height = screen.heightPixels;
        Integer lastElementId = virtualKeyboard.getLastElementId();
        if (buttonId <= lastElementId){
            buttonId = lastElementId + 1;
        }
        virtualKeyboard.addElement(
                createDigitalButton(
                        buttonId,
                        Short.parseShort(VK_code),
                        1, text, -1, virtualKeyboard, context
                ),
                screenScale(TEST_X, height) + rightDisplacement,
                screenScale(TEST_Y, height),
                screenScale(TEST_WIDTH, height),
                screenScale(TEST_HEIGHT, height)
        );

        virtualKeyboard.setOpacity(config.oscOpacity);
    }

    public static void addButton2(final VirtualKeyboard virtualKeyboard, final Context context, Integer buttonId, String VK_code, String text,
                                 final Integer x, final Integer y, final Integer width, final Integer height
    ){
        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);


        int screenHeight = screen.heightPixels;
        Integer lastElementId = virtualKeyboard.getLastElementId();
        if (buttonId <= lastElementId){
            buttonId = lastElementId + 1;
        }
        virtualKeyboard.addElement(
                createDigitalButton(
                        buttonId,
                        Short.parseShort(VK_code),
                        1, text, -1, virtualKeyboard, context
                ),
                x,
                y,
                width,
                height
        );

        virtualKeyboard.setOpacity(config.oscOpacity);
    }

//    public static void createDefaultLayout(final VirtualKeyboard virtualKeyboard, final Context context) {
//
//        DisplayMetrics screen = context.getResources().getDisplayMetrics();
//        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);
//
//        // Displace controls on the right by this amount of pixels to account for different aspect ratios
//        int rightDisplacement = screen.widthPixels - screen.heightPixels * 16 / 9;
//
//        int height = screen.heightPixels;
//
//
//        virtualKeyboard.addElement(
//                createDigitalButton(
//                        DEFAULT_ELEMENT_ID,
//                        (short) VirtualKeyboardVkCode.VKCode.VK_LWIN.getCode(),
//                        1, "测试按钮", -1, virtualKeyboard, context
//                ),
//                screenScale(TEST_X, height) + rightDisplacement,
//                screenScale(TEST_Y, height),
//                screenScale(TEST_WIDTH, height),
//                screenScale(TEST_HEIGHT, height)
//        );
//
//        virtualKeyboard.setOpacity(config.oscOpacity);
//    }

    public static void saveProfile(final VirtualKeyboard virtualKeyboard, final Context context) {
        deleteProfile(context);
        SharedPreferences.Editor prefEditor = context.getSharedPreferences(OSK_PREFERENCE, Activity.MODE_PRIVATE).edit();
        for (VirtualKeyboardElement element : virtualKeyboard.getElements()) {
            String prefKey = ""+element.elementId;
            try {
                prefEditor.putString(prefKey, element.getConfiguration().toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        prefEditor.apply();
    }

    public static void loadFromPreferences(final VirtualKeyboard virtualKeyboard, final Context context) {
        SharedPreferences pref = context.getSharedPreferences(OSK_PREFERENCE, Activity.MODE_PRIVATE);
        Map<String, ?> keys = pref.getAll();
        Log.d("heokami", "keys:" + keys.toString());
        try {
            for (Map.Entry<String, ?> entry : keys.entrySet()) {
                String elementId = entry.getKey();
                String jsonConfig = (String) entry.getValue();
                if (jsonConfig != null){
                    JSONObject json = new JSONObject(jsonConfig);
                    Log.d("heokami", " elementId:" + elementId + " buttonname:" + json.getString("TEXT") + " vk_code:" + json.getString("VK_CODE"));
                    // 忽略创建默认按钮
                    addButton(virtualKeyboard, context, Integer.parseInt(elementId), json.getString("VK_CODE"), json.getString("TEXT"));
                    Log.d("heokami", "addButton -> "+ elementId);
                    // 重新加载配置
                    virtualKeyboard.getElementByElementId(Integer.parseInt(elementId)).loadConfiguration(json);
                }
            }
        }catch (JSONException e) {
            e.printStackTrace();
            Log.d("heokami", e.toString());
            Toast.makeText(context, "JSONException" + e.getMessage(), Toast.LENGTH_SHORT).show();
            // 报错则还原默认
            virtualKeyboard.loadDefaultLayout();
        }catch (Exception e) {
            Toast.makeText(context, "载入异常，清空配置文件" + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("heokami", e.toString(), e);
            // 报错则还原默认
            virtualKeyboard.loadDefaultLayout();
        }
    }
    public static void deleteProfile(final Context context) {
        SharedPreferences.Editor prefEditor = context.getSharedPreferences(OSK_PREFERENCE, Activity.MODE_PRIVATE).edit();
        prefEditor.clear();
        prefEditor.apply();
    }

    public static String saveToFile(final Context context) {
        SharedPreferences pref = context.getSharedPreferences(OSK_PREFERENCE, Activity.MODE_PRIVATE);
        Map<String, ?> keys = pref.getAll();
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, ?> entry : keys.entrySet()) {
                String elementId = entry.getKey();
                String jsonConfig = (String) entry.getValue();
                if (jsonConfig != null){
                    json.put(elementId, jsonConfig);
                }
            }
            String jsonString = json.toString();
            return jsonString;
        }catch (JSONException e){
            e.printStackTrace();
        }

        return null;
    }

    public static void loadForFile(final Context context, final String data) {
        SharedPreferences pref = context.getSharedPreferences(OSK_PREFERENCE, Activity.MODE_PRIVATE);
        pref.edit().clear().apply();
        try {
            JSONObject json = new JSONObject(data);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String elementId = keys.next();
                String jsonConfig = json.getString(elementId);
                if (jsonConfig != null){
                    pref.edit()
                            .putString(elementId, jsonConfig)
                            .apply();
                }
            }
        }catch (JSONException e){
            e.printStackTrace();
        }
    }
}
