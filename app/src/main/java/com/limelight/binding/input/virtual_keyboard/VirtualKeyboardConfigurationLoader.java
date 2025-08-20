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
import android.content.ClipboardManager;
import android.content.ClipData;

import com.limelight.heokami.MacroEditor;
import com.limelight.heokami.VirtualKeyboardVkCode;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.NonNull;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class VirtualKeyboardConfigurationLoader {
    public static final String OSK_PREFERENCE = "OSK";

    // 样式剪贴板：仅存放外观相关的可序列化数据（颜色/透明度/圆角/部分按钮扩展样式）
    // 说明：不包含位置、尺寸、文本、VK_CODE 等非外观属性
    private static JSONObject sAppearanceStyleClipboard = null;
    // 系统剪贴板标识（label）
    private static final String CLIPBOARD_STYLE_LABEL = "VK_APPEARANCE_STYLE_JSON";

    // 允许复制/粘贴的 buttonData 样式键集合（仅外观相关）
    private static final String[] STYLE_BUTTON_DATA_KEYS = new String[]{
            "BORDER_ENABLED",
            "BORDER_WIDTH_PX",
            "BORDER_COLOR",
            "BORDER_ALPHA",
            "TEXT_COLOR",
            "TEXT_ALPHA",
            "BG_COLOR",
            "BG_ALPHA",
            "BG_COLOR_PRESSED",
            "BG_ALPHA_PRESSED",
            "OVERALL_ENABLED",
            "OVERALL_COLOR",
            "OVERALL_COLOR_PRESSED",
            "OVERALL_ALPHA"
    };

    /**
     * 从元素中提取外观样式（不含位置/尺寸/文本等），用于复制到样式剪贴板
     */
    private static JSONObject extractAppearanceFromElement(@NonNull VirtualKeyboardElement element) throws JSONException {
        JSONObject style = new JSONObject();
        style.put("NORMAL_COLOR", element.normalColor);
        style.put("PRESSED_COLOR", element.pressedColor);
        style.put("OPACITY", element.opacity);
        style.put("RADIUS", element.radius);

        // 仅提取外观相关的 BUTTON_DATA 子集
        JSONObject src = element.buttonData != null ? element.buttonData : new JSONObject();
        JSONObject data = new JSONObject();
        for (String key : STYLE_BUTTON_DATA_KEYS) {
            if (src.has(key)) {
                data.put(key, src.get(key));
            }
        }
        style.put("BUTTON_DATA", data);
        return style;
    }

    /**
     * 写入样式到系统剪贴板（文本形式，内容为 JSON）
     */
    private static void writeStyleToSystemClipboard(@NonNull Context context, @NonNull JSONObject style) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip = ClipData.newPlainText(CLIPBOARD_STYLE_LABEL, style.toString());
                cm.setPrimaryClip(clip);
            }
        } catch (Exception e) {
            Log.e("heokami", "写入系统剪贴板失败", e);
        }
    }

    /**
     * 从系统剪贴板尝试读取样式 JSON
     */
    private static JSONObject tryReadStyleFromSystemClipboard(@NonNull Context context) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData data = cm.getPrimaryClip();
                if (data != null && data.getItemCount() > 0) {
                    ClipData.Item item = data.getItemAt(0);
                    CharSequence text = item.getText();
                    if (text != null) {
                        String content = text.toString();
                        // 尝试解析 JSON；校验关键字段，避免解析到非本应用内容
                        JSONObject json = new JSONObject(content);
                        if (json.has("BUTTON_DATA") || json.has("NORMAL_COLOR")) {
                            return json;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("heokami", "读取系统剪贴板失败", e);
        }
        return null;
    }

    /**
     * 获取当前可用的外观样式（优先系统剪贴板，其次内存剪贴板）。
     * 返回 null 表示无可用样式。
     */
    public static JSONObject getAppearanceStyleFromClipboard(@NonNull Context context) {
        JSONObject style = tryReadStyleFromSystemClipboard(context);
        if (style != null) return style;
        return sAppearanceStyleClipboard;
    }

    /**
     * 将样式 JSON 应用到指定元素（仅外观相关字段）
     */
    private static void applyAppearanceToElement(@NonNull VirtualKeyboardElement element, @NonNull JSONObject style) throws JSONException {
        if (style.has("NORMAL_COLOR") && style.has("PRESSED_COLOR")) {
            element.setColors(style.getInt("NORMAL_COLOR"), style.getInt("PRESSED_COLOR"));
        }
        if (style.has("RADIUS")) {
            // RADIUS 可能是整数或浮点，使用 double 读取后转换
            float radius = (float) style.getDouble("RADIUS");
            element.setRadius(radius);
        }
        if (style.has("OPACITY")) {
            element.setOpacity(style.getInt("OPACITY"));
        }
        // 合并外观相关的 BUTTON_DATA
        JSONObject target = element.buttonData != null ? element.buttonData : new JSONObject();
        JSONObject data = style.optJSONObject("BUTTON_DATA");
        if (data != null) {
            for (String key : STYLE_BUTTON_DATA_KEYS) {
                if (data.has(key)) {
                    target.put(key, data.get(key));
                }
            }
            element.setButtonData(target);
        }
        element.invalidate();
    }

    /**
     * 复制“外观样式”到样式剪贴板
     * 仅包含颜色、透明度、圆角、以及 buttonData 中的外观扩展字段。
     */
    public static void copyAppearanceStyle(final VirtualKeyboard virtualKeyboard,
                                           final VirtualKeyboardElement element,
                                           final Context context) {
        if (element == null) return;
        try {
            sAppearanceStyleClipboard = extractAppearanceFromElement(element);
            // 写入系统剪贴板
            writeStyleToSystemClipboard(context, sAppearanceStyleClipboard);
            Toast.makeText(context, "已复制外观样式（已写入系统剪贴板）", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("heokami", "复制外观样式失败", e);
            Toast.makeText(context, "复制外观样式失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 将样式剪贴板中的外观样式粘贴到当前元素
     */
    public static void pasteAppearanceStyle(final VirtualKeyboard virtualKeyboard,
                                            final VirtualKeyboardElement element,
                                            final Context context) {
        if (element == null) return;
        try {
            // 优先从系统剪贴板读取；如果不可用则回退到内存剪贴板
            JSONObject style = tryReadStyleFromSystemClipboard(context);
            if (style == null) {
                if (sAppearanceStyleClipboard == null) {
                    Toast.makeText(context, "样式剪贴板为空，无法粘贴", Toast.LENGTH_SHORT).show();
                    return;
                }
                style = sAppearanceStyleClipboard;
            }
            applyAppearanceToElement(element, style);
            saveProfile(virtualKeyboard, context);
            Toast.makeText(context, "已粘贴外观样式", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("heokami", "粘贴外观样式失败", e);
            Toast.makeText(context, "粘贴外观样式失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 将“当前元素的外观样式”应用到相同组的所有元素（不含当前元素）
     */
    public static void applyAppearanceStyleToSameGroup(final VirtualKeyboard virtualKeyboard,
                                                       final VirtualKeyboardElement element,
                                                       final Context context) {
        if (element == null) return;
        if (element.group == -1) {
            Toast.makeText(context, "该按钮未设置分组，无法应用样式到同组", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject style = extractAppearanceFromElement(element);
            int groupId = element.group;
            for (VirtualKeyboardElement e : virtualKeyboard.getElements()) {
                if (e != element && e.group == groupId) {
                    applyAppearanceToElement(e, style);
                }
            }
            saveProfile(virtualKeyboard, context);
            Toast.makeText(context, "已将外观样式应用到同组", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("heokami", "应用外观样式到同组失败", e);
            Toast.makeText(context, "应用外观样式到同组失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

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
                // --- BUGFIX: 修复宏无法保存、无法执行最新宏的问题 ---
                // 为 MacroEditor 提供一个回调，用于在宏数据变化时更新按钮的 buttonData
                // 同时，将宏的执行逻辑移到 onClick/onLongClick 内部，确保每次都执行最新的宏
                button.addDigitalButtonListener(new DigitalButton.DigitalButtonListener() {
                    @Override
                    public void onClick() {
                        // 在每次点击时都创建一个新的、临时的 MacroEditor 实例来执行宏。
                        // 这可以确保总是使用最新的 buttonData。
                        // 因为只用于执行，所以监听器传入 null。
                        MacroEditor runner = new MacroEditor(context, button.buttonData, null);
                        runner.runMacroAction(virtualKeyboard);
                    }

                    @Override
                    public void onLongClick() {
                        // 同样，长按时也创建一个新实例来执行。
                        MacroEditor runner = new MacroEditor(context, button.buttonData, null);
                        runner.runMacroAction(virtualKeyboard);
                    }

                    @Override
                    public void onRelease() {
                        // 对于宏按钮，通常在释放时不需要执行任何操作。
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
                // 默认行为：不做特殊处理
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

        // 使用匿名内部类，可以在其中维护状态
        DigitalPad.DigitalPadListener listener = new DigitalPad.DigitalPadListener() {
            // 添加一个字段来存储上一次的方向状态，初始为 0
            private int previousDirection = 0;

            @Override
            public void onDirectionChange(int direction){
                try {
                    // 只处理 VK_CODE 相关的逻辑，因为用户的问题集中在这里
                    if (buttonData != null && (buttonData.has("LEFT_VK_CODE") || buttonData.has("RIGHT_VK_CODE") || buttonData.has("UP_VK_CODE") || buttonData.has("DOWN_VK_CODE") )) {
                        VirtualKeyboard.KeyboardInputContext inputContext = virtualKeyboard.getKeyboardInputContext();

                        // 处理 LEFT 方向
                        if (buttonData.has("LEFT_VK_CODE") && !buttonData.getString("LEFT_VK_CODE").isEmpty()){
                            short leftVKCode = Short.parseShort(buttonData.getString("LEFT_VK_CODE"));
                            boolean currentLeft = (direction & DigitalPad.DIGITAL_PAD_DIRECTION_LEFT) != 0;
                            boolean previousLeft = (previousDirection & DigitalPad.DIGITAL_PAD_DIRECTION_LEFT) != 0;

                            if (currentLeft && !previousLeft) { // LEFT 从未按变为按下
                                inputContext.modifier |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(leftVKCode);
                                virtualKeyboard.sendDownKey(leftVKCode);
                            } else if (!currentLeft && previousLeft) { // LEFT 从按下变为未按
                                inputContext.modifier &= (byte) ~VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(leftVKCode);
                                virtualKeyboard.sendUpKey(leftVKCode);
                            }
                        }

                        // 处理 RIGHT 方向 (逻辑同上)
                        if (buttonData.has("RIGHT_VK_CODE") && !buttonData.getString("RIGHT_VK_CODE").isEmpty()) {
                            short rightVKCode = Short.parseShort(buttonData.getString("RIGHT_VK_CODE"));
                            boolean currentRight = (direction & DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT) != 0;
                            boolean previousRight = (previousDirection & DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT) != 0;

                            if (currentRight && !previousRight) { // RIGHT 从未按变为按下
                                inputContext.modifier |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(rightVKCode);
                                virtualKeyboard.sendDownKey(rightVKCode);
                            } else if (!currentRight && previousRight) { // RIGHT 从按下变为未按
                                inputContext.modifier &= (byte) ~VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(rightVKCode);
                                virtualKeyboard.sendUpKey(rightVKCode);
                            }
                        }

                        // 处理 UP 方向 (逻辑同上)
                        if (buttonData.has("UP_VK_CODE") && !buttonData.getString("UP_VK_CODE").isEmpty()){
                            short upVKCode = Short.parseShort(buttonData.getString("UP_VK_CODE"));
                            boolean currentUp = (direction & DigitalPad.DIGITAL_PAD_DIRECTION_UP) != 0;
                            boolean previousUp = (previousDirection & DigitalPad.DIGITAL_PAD_DIRECTION_UP) != 0;

                            if (currentUp && !previousUp) { // UP 从未按变为按下
                                inputContext.modifier |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(upVKCode);
                                virtualKeyboard.sendDownKey(upVKCode);
                            } else if (!currentUp && previousUp) { // UP 从按下变为未按
                                inputContext.modifier &= (byte) ~VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(upVKCode);
                                virtualKeyboard.sendUpKey(upVKCode);
                            }
                        }

                        // 处理 DOWN 方向 (逻辑同上)
                        if (buttonData.has("DOWN_VK_CODE") && !buttonData.getString("DOWN_VK_CODE").isEmpty()) {
                            short downVKCode = Short.parseShort(buttonData.getString("DOWN_VK_CODE"));
                            boolean currentDown = (direction & DigitalPad.DIGITAL_PAD_DIRECTION_DOWN) != 0;
                            boolean previousDown = (previousDirection & DigitalPad.DIGITAL_PAD_DIRECTION_DOWN) != 0;

                            if (currentDown && !previousDown) { // DOWN 从未按变为按下
                                inputContext.modifier |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(downVKCode);
                                virtualKeyboard.sendDownKey(downVKCode);
                            } else if (!currentDown && previousDown) { // DOWN 从按下变为未按
                                inputContext.modifier &= (byte) ~VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(downVKCode);
                                virtualKeyboard.sendUpKey(downVKCode);
                            }
                        }

                        // 在处理完所有方向后，更新上一次的方向状态
                        previousDirection = direction;

                    } else {
                        // Controller 输入的处理保持不变，因为它似乎是通过更新状态并发送一次性包来工作的
                        VirtualKeyboard.ControllerInputContext inputContext = virtualKeyboard.getControllerInputContext();

                        // 重置 inputMap，只设置当前按下的方向
                        inputContext.inputMap = 0; // 清除所有方向标志

                        if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_LEFT) != 0) {
                            inputContext.inputMap |= ControllerPacket.LEFT_FLAG;
                        }
                        if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT) != 0) {
                            inputContext.inputMap |= ControllerPacket.RIGHT_FLAG;
                        }
                        if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_UP) != 0) {
                            inputContext.inputMap |= ControllerPacket.UP_FLAG;
                        }
                        if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_DOWN) != 0) {
                            inputContext.inputMap |= ControllerPacket.DOWN_FLAG;
                        }

                        // 每次方向变化时发送一次 controller 输入上下文
                        virtualKeyboard.sendControllerInputContext();

                        // 对于 Controller 模式，也更新 previousDirection，虽然在这个逻辑分支中它没有被使用，
                        // 但如果将来需要基于状态变化进行更精细的控制，保留它是有益的。
                        previousDirection = direction;
                    }

                } catch (JSONException e){
                    Log.e("heokami", e.toString(), e);
                } catch (Exception e) {
                    Log.e("heokami", e.toString(), e);
                }
            }
        };

        digitalPad.addDigitalPadListener(listener);


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
                // 支持两种表示方式：
                // 1) 文本标识（A/B/X/Y/LB/RB/BACK/START）
                // 2) 直接存储的整型位标志（历史版本保存的字符串数字，如 "4096"）
                try {
                    // 尝试将 vkCode 作为整型解析
                    int parsed = Integer.parseInt(vkCode);
                    if (parsed != 0) {
                        newCode = parsed;
                    }
                } catch (NumberFormatException ignored) {
                    // 非数字则按名称映射
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

//        virtualKeyboard.setOpacity(config.oscOpacity);
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

    public static void deleteElement(final Context context, final int elementId) {
        SharedPreferences pref = context.getSharedPreferences(OSK_PREFERENCE, Activity.MODE_PRIVATE);
        if (pref.contains(String.valueOf(elementId))) {
            pref.edit().remove(String.valueOf(elementId)).apply();
        }
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
