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


import com.limelight.heokami.MacroEditor;
import com.limelight.heokami.VirtualKeyboardVkCode;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class VirtualKeyboardConfigurationLoader {
    public static final String OSK_PREFERENCE = "OSK";

    /*
    private static int getPercent(
            int percent,
            int total) {
        return (int) (((float) total / (float) 100) * (float) percent);
    }
    */

    // The default controls are specified using a grid of 128*72 cells at 16:9
    private static int screenScale(int units, int height) {
        return (int) (((float) height / (float) 72) * (float) units);
    }


    public static DigitalButton createDigitalButton(
            final VirtualKeyboard virtualKeyboard,
            final Context context,
            final int elementId, // 唯一标识
            final short vk_code, // 按键
            final int layer, // 层
            final String text, // 文本
            final int icon, // 图标
            final VirtualKeyboardElement.ButtonType buttonType,
            final JSONObject buttonData
            ) {

        DigitalButton button = new DigitalButton(virtualKeyboard, elementId, layer, context);

        button.setText(text);
        button.setIcon(icon);
        button.setVkCode(""+vk_code);
        button.setType(buttonType);
        button.setButtonData(buttonData);

        switch (buttonType) {
            case Button:
                button.addDigitalButtonListener(new DigitalButton.DigitalButtonListener() {
                    @Override
                    public void onClick() {
                        VirtualKeyboard.ControllerInputContext inputContext =
                                virtualKeyboard.getControllerInputContext();
                        inputContext.modifier |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(vk_code);
                        virtualKeyboard.sendDownKey(vk_code);
                    }

                    @Override
                    public void onLongClick() {
                        VirtualKeyboard.ControllerInputContext inputContext =
                                virtualKeyboard.getControllerInputContext();
                        inputContext.modifier |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(vk_code);

                        virtualKeyboard.sendDownKey(vk_code);
                    }

                    @Override
                    public void onRelease() {
                        VirtualKeyboard.ControllerInputContext inputContext =
                                virtualKeyboard.getControllerInputContext();
                        inputContext.modifier &= (byte) ~VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(vk_code);

                        virtualKeyboard.sendUpKey(vk_code);
                    }
                });
                break;
            case HotKeys:
                MacroEditor macroEditor = new MacroEditor(context, buttonData,null);

                button.addDigitalButtonListener(new DigitalButton.DigitalButtonListener() {
                    @Override
                    public void onClick() {
                        macroEditor.runMacroAction(virtualKeyboard);
//                        Toast.makeText(context, "HotKeys" + buttonData, Toast.LENGTH_SHORT).show();
                        Log.d("heokami", "HotKeys" + buttonData);
                    }

                    @Override
                    public void onLongClick() {
                        macroEditor.runMacroAction(virtualKeyboard);
                    }

                    @Override
                    public void onRelease() {
//                        macroEditor.runMacroAction(virtualKeyboard);
                    }
                });
                break;
            default:
                Toast.makeText(context, "default", Toast.LENGTH_SHORT).show();
                break;
        }

        return button;
    }

    private static final int TEST_X = 35;
    private static final int TEST_Y = 35;

    private static final int TEST_WIDTH = 10;
    private static final int TEST_HEIGHT = 5;

    public static void addButton(final VirtualKeyboard virtualKeyboard, final Context context,
                                 Integer buttonId,
                                 String VK_code,
                                 String buttonName,
                                 VirtualKeyboardElement.ButtonType buttonType,
                                 JSONObject buttonData
    ) throws JSONException {
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
                        virtualKeyboard,
                        context,
                        buttonId,
                        Short.parseShort(VK_code),
                        1,
                        buttonName,
                        -1,
                        buttonType,
                        buttonData
                ),
                screenScale(TEST_X, height) + rightDisplacement,
                screenScale(TEST_Y, height),
                screenScale(TEST_WIDTH, height),
                screenScale(TEST_HEIGHT, height)
        );

        virtualKeyboard.setOpacity(config.oscOpacity);
    }

    public static void copyButton(final VirtualKeyboard virtualKeyboard, final VirtualKeyboardElement element, final Context context){

        VirtualKeyboardElement button = createDigitalButton(
          virtualKeyboard,
          context,
          virtualKeyboard.getLastElementId() + 1,
          Short.parseShort(element.vk_code),
          1,
          element.text,
          -1,
          element.buttonType,
          element.buttonData
        );

        button.setHide(element.isHide);
        button.setGroup(element.group);

        virtualKeyboard.addElement(
                button,
                element.getLeftMargin() + 10,
                element.getTopMargin(),
                element.getWidth(),
                element.getHeight()
        );
    }

    public static void saveProfile(final VirtualKeyboard virtualKeyboard, final Context context) {
        deleteProfile(context);
        SharedPreferences.Editor prefEditor = context.getSharedPreferences(OSK_PREFERENCE, Activity.MODE_PRIVATE).edit();
        for (VirtualKeyboardElement element : virtualKeyboard.getElements()) {
            String prefKey = ""+element.elementId;
            try {
                prefEditor.putString(prefKey, element.getConfiguration().toString());
            } catch (JSONException e) {
                Log.e("heokami", e.toString(), e);
            }
        }

        prefEditor.apply();
    }

    public static void loadFromPreferences(final VirtualKeyboard virtualKeyboard, final Context context) {
        SharedPreferences pref = context.getSharedPreferences(OSK_PREFERENCE, Activity.MODE_PRIVATE);
        Map<String, ?> keys = pref.getAll();
        TreeMap<String, Object> sortedKeys = new TreeMap<>(keys);
        // 从小到大排序，解决id和Json加载错误
        Log.d("heokami", "keys:" + keys.toString());
        try {
            for (Map.Entry<String, Object> entry : sortedKeys.entrySet()) {
                String elementId = entry.getKey();
                String jsonConfig = (String) entry.getValue();
                Log.d("heokami", "elementId: "+ elementId + " jsonConfig: "+ jsonConfig);
                if (jsonConfig != null){
                    JSONObject json = new JSONObject(jsonConfig);
                    Log.d("heokami", " elementId:" + elementId + " buttonName:" + json.getString("TEXT") + " vk_code:" + json.getString("VK_CODE"));
                    addButton(
                            virtualKeyboard,
                            context,
                            Integer.parseInt(elementId),
                            json.getString("VK_CODE"),
                            json.getString("TEXT"),
                            VirtualKeyboardElement.ButtonType.valueOf(json.getString("TYPE")),
                            json.getJSONObject("BUTTON_DATA")
                    );
                    Log.d("heokami", "addButton -> "+ elementId);
                    // 重新加载配置
                    virtualKeyboard.getElementByElementId(Integer.parseInt(elementId)).loadConfiguration(json);
                }
            }
        }catch (JSONException e) {
            Log.d("heokami", e.toString(), e);
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
            return json.toString();
        }catch (JSONException e){
            Log.e("heokami", e.toString(), e);
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
                pref.edit()
                        .putString(elementId, jsonConfig)
                        .apply();
            }
        }catch (JSONException e){
            Log.e("heokami", e.toString(), e);
        }
    }
}
