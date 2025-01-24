/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_keyboard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import com.limelight.heokami.MacroEditor;
import com.limelight.heokami.VirtualKeyboardVkCode;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
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

        DigitalButton button = new DigitalButton(virtualKeyboard, context, elementId, layer);

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
                        if (vk_code == 0) return;
                        VirtualKeyboard.KeyboardInputContext inputContext = virtualKeyboard.getKeyboardInputContext();
                        inputContext.modifier |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(vk_code);
                        virtualKeyboard.sendDownKey(vk_code);
                    }

                    @Override
                    public void onLongClick() {
                        if (vk_code == 0) return;
                        VirtualKeyboard.KeyboardInputContext inputContext =
                                virtualKeyboard.getKeyboardInputContext();
                        inputContext.modifier |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(vk_code);

                        virtualKeyboard.sendDownKey(vk_code);
                    }

                    @Override
                    public void onRelease() {
                        if (vk_code == 0) return;
                        VirtualKeyboard.KeyboardInputContext inputContext =
                                virtualKeyboard.getKeyboardInputContext();
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
            case JoyStick:
                button.addDigitalButtonListener(new DigitalButton.DigitalButtonListener() {
                    @Override
                    public void onClick() {
                        Log.d("vk", "onClick"+ vk_code);
                        VirtualKeyboard.ControllerInputContext inputContext = virtualKeyboard.getControllerInputContext();
                        inputContext.inputMap |= vk_code;
                        virtualKeyboard.sendControllerInputContext();
                    }

                    @Override
                    public void onLongClick() {
                        Log.d("vk", "onLongClick"+ vk_code);
                        VirtualKeyboard.ControllerInputContext inputContext = virtualKeyboard.getControllerInputContext();
                        inputContext.inputMap |= 0;
                        virtualKeyboard.sendControllerInputContext();
                    }

                    @Override
                    public void onRelease() {
                        Log.d("vk", "onRelease"+ vk_code);
                        VirtualKeyboard.ControllerInputContext inputContext = virtualKeyboard.getControllerInputContext();
                        inputContext.inputMap &= (short) ~vk_code;
                        inputContext.inputMap &= ~0;
                        virtualKeyboard.sendControllerInputContext();
                    }
                });
                break;
            default:
                Toast.makeText(context, "default", Toast.LENGTH_SHORT).show();
                break;
        }

        return button;
    }

    private static TouchPad createTouchPad(
            final VirtualKeyboard virtualKeyboard,
            final Context context,
            final int elementId,
            final int layer,
            final VirtualKeyboardElement.ButtonType buttonType,
            final JSONObject buttonData
    ){
        TouchPad touchPad = new RelativeTouchPad(virtualKeyboard, context, elementId, layer);
        touchPad.setType(buttonType);
        touchPad.setButtonData(buttonData);
        return touchPad;
    }

    private static DigitalButton createLeftTrigger(
            final VirtualKeyboard virtualKeyboard,
            final Context context,
            final int elementId,
            final int layer,
            final String vkCode,
            final VirtualKeyboardElement.ButtonType buttonType,
            final JSONObject buttonData) {
        LeftTrigger trigger = new LeftTrigger(virtualKeyboard, context, elementId, layer);
        trigger.setType(buttonType);
        trigger.setButtonData(buttonData);
        trigger.setVkCode(vkCode);
        trigger.setText("LT");
        return trigger;
    }

    private static DigitalButton createRightTrigger(
            final VirtualKeyboard virtualKeyboard,
            final Context context,
            final int elementId,
            final int layer,
            final String vkCode,
            final VirtualKeyboardElement.ButtonType buttonType,
            final JSONObject buttonData) {
        RightTrigger trigger = new RightTrigger(virtualKeyboard, context, elementId, layer);
        trigger.setType(buttonType);
        trigger.setButtonData(buttonData);
        trigger.setVkCode(vkCode);
        trigger.setText("RT");
        return trigger;
    }

    private static AnalogStick createLeftStick(
            final VirtualKeyboard virtualKeyboard,
            final Context context,
            final int elementId,
            final int layer,
            final String vkCode,
            final VirtualKeyboardElement.ButtonType buttonType,
            final JSONObject buttonData) {
        LeftAnalogStick analogStick = new LeftAnalogStick(virtualKeyboard, context, elementId, layer);
        analogStick.setType(buttonType);
        analogStick.setButtonData(buttonData);
        analogStick.setVkCode(vkCode);
        return analogStick;
    }

    private static AnalogStick createRightStick(
            final VirtualKeyboard virtualKeyboard,
            final Context context,
            final int elementId,
            final int layer,
            final String vkCode,
            final VirtualKeyboardElement.ButtonType buttonType,
            final JSONObject buttonData) {
        RightAnalogStick analogStick = new RightAnalogStick(virtualKeyboard, context, elementId, layer);
        analogStick.setType(buttonType);
        analogStick.setButtonData(buttonData);
        analogStick.setVkCode(vkCode);
        return analogStick;
    }


    private static DigitalPad createDigitalPad(
            final VirtualKeyboard virtualKeyboard,
            final Context context,
            final int elementId,
            final int layer,
            final String vkCode,
            final VirtualKeyboardElement.ButtonType buttonType,
            final JSONObject buttonData
    ) {
        DigitalPad digitalPad = new DigitalPad(virtualKeyboard, context, elementId, layer);
        digitalPad.setType(buttonType);
        digitalPad.setButtonData(buttonData);
        digitalPad.setVkCode(vkCode);
        if (buttonData != null && (buttonData.has("LEFT_VK_CODE") || buttonData.has("RIGHT_VK_CODE") || buttonData.has("UP_VK_CODE") || buttonData.has("DOWN_VK_CODE") )) {
            digitalPad.addDigitalPadListener(new DigitalPad.DigitalPadListener() {
                @Override
                public void onDirectionChange(int direction){
                    try {
                        VirtualKeyboard.KeyboardInputContext inputContext = virtualKeyboard.getKeyboardInputContext();
                        if (buttonData.has("LEFT_VK_CODE") && !buttonData.getString("LEFT_VK_CODE").isEmpty()){
                            short leftVKCode = Short.parseShort(buttonData.getString("LEFT_VK_CODE"));
                            if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_LEFT) != 0) {
                                inputContext.modifier |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(leftVKCode);
                                virtualKeyboard.sendDownKey(leftVKCode);
                            }
                            else {
                                inputContext.modifier &= (byte) ~VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(leftVKCode);
                                virtualKeyboard.sendUpKey(leftVKCode);
                            }
                        }

                        if (buttonData.has("RIGHT_VK_CODE") && !buttonData.getString("RIGHT_VK_CODE").isEmpty()) {
                            short rightVKCode = Short.parseShort(buttonData.getString("RIGHT_VK_CODE"));
                            if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT) != 0) {
                                inputContext.modifier |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(rightVKCode);
                                virtualKeyboard.sendDownKey(rightVKCode);
                            } else {
                                inputContext.modifier &= (byte) ~VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(rightVKCode);
                                virtualKeyboard.sendUpKey(rightVKCode);
                            }
                        }

                        if (buttonData.has("UP_VK_CODE") && !buttonData.getString("UP_VK_CODE").isEmpty()){
                            short upVKCode = Short.parseShort(buttonData.getString("UP_VK_CODE"));
                            if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_UP) != 0) {
                                inputContext.modifier |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(upVKCode);
                                virtualKeyboard.sendDownKey(upVKCode);
                            }
                            else {
                                inputContext.modifier &= (byte) ~VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(upVKCode);
                                virtualKeyboard.sendUpKey(upVKCode);
                            }
                        }

                        if (buttonData.has("DOWN_VK_CODE") && !buttonData.getString("DOWN_VK_CODE").isEmpty()) {
                            short downVKCode = Short.parseShort(buttonData.getString("DOWN_VK_CODE"));
                            if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_DOWN) != 0) {
                                inputContext.modifier |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(downVKCode);
                                virtualKeyboard.sendDownKey(downVKCode);
                            } else {
                                inputContext.modifier &= (byte) ~VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(downVKCode);
                                virtualKeyboard.sendUpKey(downVKCode);
                            }
                        }
                    }catch (JSONException e){
                        Log.e("heokami", e.toString(), e);
                    } catch (Exception e) {
                        Log.e("heokami", e.toString(), e);
                    }
                }
            });
        }else {
            digitalPad.addDigitalPadListener(new DigitalPad.DigitalPadListener() {
                @Override
                public void onDirectionChange(int direction) {
                    VirtualKeyboard.ControllerInputContext inputContext = virtualKeyboard.getControllerInputContext();

                    if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_LEFT) != 0) {
                        inputContext.inputMap |= ControllerPacket.LEFT_FLAG;
                    }
                    else {
                        inputContext.inputMap &= ~ControllerPacket.LEFT_FLAG;
                    }
                    if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT) != 0) {
                        inputContext.inputMap |= ControllerPacket.RIGHT_FLAG;
                    }
                    else {
                        inputContext.inputMap &= ~ControllerPacket.RIGHT_FLAG;
                    }
                    if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_UP) != 0) {
                        inputContext.inputMap |= ControllerPacket.UP_FLAG;
                    }
                    else {
                        inputContext.inputMap &= ~ControllerPacket.UP_FLAG;
                    }
                    if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_DOWN) != 0) {
                        inputContext.inputMap |= ControllerPacket.DOWN_FLAG;
                    }
                    else {
                        inputContext.inputMap &= ~ControllerPacket.DOWN_FLAG;
                    }

                    virtualKeyboard.sendControllerInputContext();
                }
            });
        }

        return digitalPad;
    }

    private static final int TEST_X = 35;
    private static final int TEST_Y = 35;

    private static final int TEST_WIDTH = 10;
    private static final int TEST_HEIGHT = 5;

    public static void createElement(final VirtualKeyboard virtualKeyboard, final Context context,
                                     Integer elementId,
                                     String vkCode,
                                     String elementName,
                                     VirtualKeyboardElement.ButtonType elementType,
                                     JSONObject elementData,
                                     int elementX,
                                     int elementY,
                                     int elementWidth,
                                     int elementHeight
    ) throws JSONException {
        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);
        Integer lastElementId = virtualKeyboard.getLastElementId();

        if (Objects.equals(elementId, lastElementId)){
            elementId = lastElementId + 1;
        }

        if (Objects.requireNonNull(elementType) == VirtualKeyboardElement.ButtonType.TouchPad) {
            virtualKeyboard.addElement(
                    createTouchPad(
                            virtualKeyboard,
                            context,
                            elementId,
                            1,
                            elementType,
                            elementData
                    ),
                    elementX,
                    elementY,
                    elementWidth,
                    elementHeight
            );
        }
        else if (Objects.requireNonNull(elementType) == VirtualKeyboardElement.ButtonType.JoyStick) {
            if (Objects.equals(vkCode, VirtualKeyboardVkCode.JoyCode.JOY_LS.getCode())) {
                virtualKeyboard.addElement(
                        createLeftStick(
                                virtualKeyboard,
                                context,
                                elementId,
                                1,
                                vkCode,
                                elementType,
                                elementData
                        ),
                        elementX,
                        elementY,
                        elementWidth,
                        elementHeight
                );
            } else if (Objects.equals(vkCode, VirtualKeyboardVkCode.JoyCode.JOY_RS.getCode())) {
                virtualKeyboard.addElement(
                        createRightStick(
                                virtualKeyboard,
                                context,
                                elementId,
                                1,
                                vkCode,
                                elementType,
                                elementData
                        ),
                        elementX,
                        elementY,
                        elementWidth,
                        elementHeight
                );
            } else if (Objects.equals(vkCode, VirtualKeyboardVkCode.JoyCode.JOY_LT.getCode())) {
                virtualKeyboard.addElement(
                        createLeftTrigger(
                                virtualKeyboard,
                                context,
                                elementId,
                                1,
                                vkCode,
                                elementType,
                                elementData
                        ),
                        elementX,
                        elementY,
                        elementWidth,
                        elementHeight
                );
            } else if (Objects.equals(vkCode, VirtualKeyboardVkCode.JoyCode.JOY_RT.getCode())) {
                virtualKeyboard.addElement(
                        createRightTrigger(
                                virtualKeyboard,
                                context,
                                elementId,
                                1,
                                vkCode,
                                elementType,
                                elementData
                        ),
                        elementX,
                        elementY,
                        elementWidth,
                        elementHeight
                );
            } else if (Objects.equals(vkCode, VirtualKeyboardVkCode.JoyCode.JOY_PAD.getCode())) {
                virtualKeyboard.addElement(
                        createDigitalPad(
                                virtualKeyboard,
                                context,
                                elementId,
                                1,
                                vkCode,
                                elementType,
                                elementData
                        ),
                        elementX,
                        elementY,
                        elementWidth,
                        elementHeight
                );
            } else {
                int newCode = 0;
                switch (vkCode) {
                    case "A":
                        newCode = ControllerPacket.A_FLAG;
                        break;
                    case "B":
                        newCode = ControllerPacket.B_FLAG;
                        break;
                    case "X":
                        newCode = ControllerPacket.X_FLAG;
                        break;
                    case "Y":
                        newCode = ControllerPacket.Y_FLAG;
                        break;
                    case "LB":
                        newCode = ControllerPacket.LB_FLAG;
                        break;
                    case "RB":
                        newCode = ControllerPacket.RB_FLAG;
                        break;
                    case "BACK":
                        newCode = ControllerPacket.BACK_FLAG;
                        break;
                    case "START":
                        newCode = ControllerPacket.PLAY_FLAG;
                        break;
                }
                virtualKeyboard.addElement(
                        createDigitalButton(
                                virtualKeyboard,
                                context,
                                elementId,
                                (short)newCode,
                                1,
                                elementName,
                                -1,
                                elementType,
                                elementData
                        ),
                        elementX,
                        elementY,
                        elementWidth,
                        elementHeight
                );
            }
        } else {
            short buttonVkCode = 0;
            try {
                buttonVkCode = Short.parseShort(vkCode);
            }catch (Exception e){
                Log.e("heokami", e.toString(), e);
            }
            virtualKeyboard.addElement(
                    createDigitalButton(
                            virtualKeyboard,
                            context,
                            elementId,
                            buttonVkCode,
                            1,
                            elementName,
                            -1,
                            elementType,
                            elementData
                    ),
                    elementX,
                    elementY,
                    elementWidth,
                    elementHeight
            );
        }

        virtualKeyboard.setOpacity(config.oscOpacity);
    }

    public static void addButton(final VirtualKeyboard virtualKeyboard, final Context context,
                                 Integer buttonId,
                                 String vkCode,
                                 String buttonName,
                                 VirtualKeyboardElement.ButtonType buttonType,
                                 JSONObject buttonData
    ) throws JSONException {
        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        int rightDisplacement = screen.widthPixels - screen.heightPixels * 16 / 9;
        int height = screen.heightPixels;
        createElement(
                virtualKeyboard,
                context,
                buttonId,
                vkCode,
                buttonName,
                buttonType,
                buttonData,
                screenScale(TEST_X, height) + rightDisplacement,
                screenScale(TEST_Y, height),
                screenScale(TEST_WIDTH, height),
                screenScale(TEST_HEIGHT, height)
        );
    }

    public static void copyButton(final VirtualKeyboard virtualKeyboard, final VirtualKeyboardElement element, final Context context){
        int newElementId = virtualKeyboard.getLastElementId() + 1;

        try {
            Log.d("copyButton", String.format("elementId: %s vk_code: %s text: %s buttonType: %s buttonData: %s", element.elementId, element.vk_code, element.text, element.buttonType, element.buttonData));
            Log.d("copyButton2", String.format("x: %s y: %s width: %s height: %s", element.getLeftMargin(), element.getTopMargin(), element.getWidth(), element.getHeight()));
             createElement(
                    virtualKeyboard,
                    context,
                    newElementId,
                    element.vk_code,
                    element.text,
                    element.buttonType,
                    element.buttonData,
                    element.getLeftMargin() + 10,
                    element.getTopMargin(),
                    element.getWidth(),
                    element.getHeight()
            );
        }catch (JSONException e){
            Log.e("heokami", e.toString(), e);
        }
        VirtualKeyboardElement newElement = virtualKeyboard.getElementByElementId(newElementId);

        newElement.setLayer(element.layer);
        newElement.setColors(element.normalColor, element.pressedColor);
        newElement.setGroup(element.group);
        newElement.setRadius(element.radius);
        newElement.setOpacity(element.opacity);
        newElement.setHide(element.isHide);
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
                try {
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
                }catch (Exception e){
                    Toast.makeText(context, String.format("elementId %s 载入异常", elementId) + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("heokami", String.format("elementId %s 载入异常 \n Json: %s", elementId, jsonConfig), e);

                    Log.e("heokami", e.toString(), e);
                }
            }
        } catch (Exception e) {
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

    public static void loadForFileAdd(final Context context, final String data) {
        SharedPreferences pref = context.getSharedPreferences(OSK_PREFERENCE, Activity.MODE_PRIVATE);
        @SuppressLint("CommitPrefEdits")
        SharedPreferences.Editor editor = pref.edit(); // 获取Editor，一次性提交所有修改

        try {
            JSONObject json = new JSONObject(data);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String elementId = keys.next();
                String jsonConfig = json.getString(elementId);
                if (pref.contains(elementId)){
                    // 处理冲突：创建新的elementId
                    int counter = 1;
                    int newElementId;
                    do {
                        newElementId = Integer.parseInt(elementId) + counter; // 例如：1_1, 1_2, 1_3...
                        counter++;
                    } while (pref.contains(String.valueOf(newElementId))); // 确保新ID不重复

                    Log.w("SharedPreferencesUtils", "ElementId冲突: " + elementId + "，已重命名为: " + newElementId);
                    editor.putString(String.valueOf(newElementId), jsonConfig); // 存储到新的ID
                }else {
                    editor.putString(elementId, jsonConfig);
                }
            editor.apply(); // 提交所有修改
            }
        }catch (JSONException e){
            Log.e("heokami", e.toString(), e);
        }
    }
}
