/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_keyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.view.Gravity;
import android.widget.Toast;
import android.view.ViewParent;

import com.limelight.Game;
import com.limelight.R;
import com.limelight.heokami.GameGridLines;
import com.limelight.heokami.VirtualKeyboardVkCode;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.binding.input.ControllerHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VirtualKeyboard {
    public static class KeyboardInputContext {
        public short key = 0;
        public byte modifier = (byte) 0;
    }

    public enum ControllerMode {
        Active,
        MoveButtons,
        ResizeButtons,
        SettingsButtons,
        NewSettingButtons
    }

    private static final boolean _PRINT_DEBUG_INFORMATION = false;

    private final NvConnection conn;
    private final Game context;
    private final Handler handler;

    private FrameLayout frame_layout = null;
    private View editingOverlay = null;
    private View editingContainer = null;
    private TextView editingTip = null;

    ControllerMode currentMode = ControllerMode.Active;
    KeyboardInputContext keyboardInputContext = new KeyboardInputContext();

    private Button buttonConfigure = null;

    private final List<VirtualKeyboardElement> elements = new ArrayList<>();
    public List<String> historyElements = new ArrayList<>();
    public int historyIndex = 0;

    public boolean groupMove = false;

    public VirtualKeyboard(ControllerHandler controllerHandler, NvConnection conn, FrameLayout layout, final Game context) {
        this.controllerHandler = controllerHandler;
        this.conn = conn;
        this.frame_layout = layout;
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());

        GameGridLines gameGridLines = context.getGameGridLines();
        PreferenceConfiguration pref = context.getPrefConfig();
        groupMove = pref.enableGroupMove;

        buttonConfigure = new Button(context);
        buttonConfigure.setAlpha(0.25f);
        buttonConfigure.setFocusable(false);
        buttonConfigure.setBackgroundResource(R.drawable.ic_settings);
        // 只在开关打开时添加设置按钮
        if (pref.enableNewSettingButton) {
            int buttonSize = (int)(context.getResources().getDisplayMetrics().heightPixels*0.06f);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(buttonSize, buttonSize);
            params.leftMargin = 15;
            params.topMargin = 15;
            frame_layout.addView(buttonConfigure, params);

            // 防误触：切换点击/长按行为
            if (pref.enableSafeSettingsButton) {
                buttonConfigure.setOnClickListener(null);
                buttonConfigure.setOnLongClickListener(v -> {
                    String message;
                    if (currentMode == ControllerMode.Active){
                        currentMode = ControllerMode.NewSettingButtons;
                        if (pref.enableGridLayout){
                            gameGridLines.show();
                        }
                        showEditingOverlay();
                        message = context.getString(R.string.controller_mode_new_setting_button);
                    }else {
                        currentMode = ControllerMode.Active;
                        VirtualKeyboardConfigurationLoader.saveProfile(VirtualKeyboard.this, context);
                        if (gameGridLines != null) {
                            gameGridLines.hide();
                        }
                        hideEditingOverlay();
                        message = context.getString(R.string.controller_mode_active_buttons);
                    }
                    context.postNotification(message, 2000);
                    buttonConfigure.invalidate();
                    for (VirtualKeyboardElement element : elements) {
                        element.invalidate();
                    }
                    return true;
                });
            } else {
                buttonConfigure.setOnLongClickListener(null);
                buttonConfigure.setOnClickListener(v -> {
                    String message;
                    if (pref.enableNewSettingButton){
                        if (currentMode == ControllerMode.Active){
                            currentMode = ControllerMode.NewSettingButtons;
                            if (pref.enableGridLayout){
                                gameGridLines.show();
                            }
                            showEditingOverlay();
                            message = context.getString(R.string.controller_mode_new_setting_button);
                        }else {
                            currentMode = ControllerMode.Active;
                            VirtualKeyboardConfigurationLoader.saveProfile(VirtualKeyboard.this, context);
                            if (gameGridLines != null) {
                                gameGridLines.hide();
                            }
                            hideEditingOverlay();
                            message = context.getString(R.string.controller_mode_active_buttons);
                        }
                    }else {
                        if (currentMode == ControllerMode.Active){
                            currentMode = ControllerMode.MoveButtons;
                            if (pref.enableGridLayout){
                                gameGridLines.show();
                            }
                            message = context.getString(R.string.controller_mode_move_buttons);
                        } else if (currentMode == ControllerMode.MoveButtons) {
                            currentMode = ControllerMode.ResizeButtons;
                            message = context.getString(R.string.controller_mode_resize_buttons);
                        }else if (currentMode == ControllerMode.ResizeButtons) {
                            currentMode = ControllerMode.SettingsButtons;
                            message = context.getString(R.string.controller_mode_settings_buttons);
                        }else {
                            currentMode = ControllerMode.Active;
                            VirtualKeyboardConfigurationLoader.saveProfile(VirtualKeyboard.this, context);
                            if (gameGridLines != null) {
                                gameGridLines.hide();
                            }
                            message = context.getString(R.string.controller_mode_active_buttons);
                        }
                    }
                    context.postNotification(message, 2000);
                    buttonConfigure.invalidate();
                    for (VirtualKeyboardElement element : elements) {
                        element.invalidate();
                    }
                });
            }
        }
    }

    Handler getHandler() {
        return handler;
    }

    public NvConnection getNvConnection() {
        return conn;
    }

    public Game getGameContext() {
        return context;
    }

    public void hide() {
        // 强制退回到非编辑态，确保遮罩与提示在任何“隐藏”路径都被移除
        currentMode = ControllerMode.Active;
        for (VirtualKeyboardElement element : elements) {
            element.setVisibility(View.INVISIBLE);
        }
        buttonConfigure.setVisibility(View.INVISIBLE);
        // 同步隐藏编辑提示叠加层与网格线，防止异常切到后台后遮罩残留
        try {
            hideEditingOverlay();
            GameGridLines gridLines = context.getGameGridLines();
            if (gridLines != null) {
                gridLines.hide();
            }
        } catch (Exception ignored) {}
    }

    public void show() {
        currentMode = ControllerMode.Active;
        for (VirtualKeyboardElement element : elements) {
            element.setVisibility(View.VISIBLE);
            element.setHide(element.isHide);
        }
        // 只在开关打开时显示设置按钮
        PreferenceConfiguration pref = context.getPrefConfig();
        if (pref.enableNewSettingButton) {
            buttonConfigure.setVisibility(View.VISIBLE);
        } else {
            buttonConfigure.setVisibility(View.GONE);
        }
        // 不主动隐藏编辑遮罩，只有真正退出编辑模式时隐藏
    }

    public void hideElement(VirtualKeyboardElement element) {
        for (VirtualKeyboardElement e : elements) {
            if (e == element) {
                e.setVisibility(View.INVISIBLE);
                return;
            }
        }
    }

    public void showElement(VirtualKeyboardElement element) {
        for (VirtualKeyboardElement e : elements) {
            if (e == element) {
                e.setVisibility(View.VISIBLE);
                return;
            }
        }
    }

    public void removeElements() {
        if (frame_layout != null) {
            for (VirtualKeyboardElement element : elements) {
                ViewParent parent = element.getParent();
                if (parent instanceof FrameLayout) {
                    ((FrameLayout) parent).removeView(element);
                }
            }
        }
        elements.clear();

        if (frame_layout != null && buttonConfigure != null) {
            ViewParent buttonParent = buttonConfigure.getParent();
            if (buttonParent instanceof FrameLayout) {
                ((FrameLayout) buttonParent).removeView(buttonConfigure);
            }
        }
        hideEditingOverlay();
    }

    public void removeElementByElementId(int elementId) {
        for (VirtualKeyboardElement element : elements) {
            if (element.elementId == elementId) {
                frame_layout.removeView(element);
                elements.remove(element);
                return;
            }
        }
    }

    public void removeElementByElement(VirtualKeyboardElement element) {
        frame_layout.removeView(element);
        elements.remove(element);
    }

    public void destroy() {
        removeElements();
        historyElements.clear();
        historyIndex = 0;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        buttonConfigure = null;
        editingOverlay = null;
        editingContainer = null;
        editingTip = null;
        frame_layout = null;
    }

    public void setOpacity(int opacity) {
        for (VirtualKeyboardElement element : elements) {
            element.setOpacity(opacity);
        }
    }

    public void setElementOpacity(VirtualKeyboardElement element, int opacity) {
        for (VirtualKeyboardElement e : elements) {
            if (e == element) {
                e.opacity = opacity;
                return;
            }
        }
    }

    public void setElementRadius(VirtualKeyboardElement element, float radius) {
        for (VirtualKeyboardElement e : elements) {
            if (e == element) {
                e.radius = radius;
                return;
            }
        }
    }


    public void addElement(VirtualKeyboardElement element, int x, int y, int width, int height) {
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);
        GameGridLines gameGridLines = context.getGameGridLines();
        element.setGridLines(gameGridLines);
        // 保证新增按钮位于遮罩之上、菜单之下：将其添加在容器末尾（高于遮罩/提示），菜单是独立Fragment层级更高
        frame_layout.addView(element, frame_layout.getChildCount(), layoutParams);
    }

    public List<VirtualKeyboardElement> getElements() {
        return elements;
    }

    public Integer getLastElementId() {
        if (!elements.isEmpty()) {
            int maxElementId = 0;
            for (VirtualKeyboardElement element : elements) {
                if (element.elementId > maxElementId) {
                    maxElementId = element.elementId;
                }
            }
            return maxElementId;
        }
        return 0;
    }

    public VirtualKeyboardElement getElementByElementId(int elementId) {
        for (VirtualKeyboardElement element : elements) {
            if (element.elementId == elementId) {
                return element;
            }
        }
        return null;
    }

    public void loadDefaultLayout() {
        removeElements();
        DisplayMetrics screen = context.getResources().getDisplayMetrics();

        int buttonSize = (int)(screen.heightPixels*0.06f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        params.leftMargin = 15;
        params.topMargin = 15;
        frame_layout.addView(buttonConfigure, params);

        VirtualKeyboardConfigurationLoader.deleteProfile(context);
        // Start with the default layout
//        VirtualKeyboardConfigurationLoader.createDefaultLayout(this, context);
    }

    public void refreshLayout() {
        // 记录是否处于编辑模式（新设置按钮模式），用于刷新后恢复遮罩
        boolean shouldRestoreEditingOverlay = (currentMode == ControllerMode.NewSettingButtons);

        removeElements();

        DisplayMetrics screen = context.getResources().getDisplayMetrics();

        int buttonSize = (int)(screen.heightPixels*0.06f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        params.leftMargin = 15;
        params.topMargin = 15;
        frame_layout.addView(buttonConfigure, params);
        // 刷新布局过程中不要永久关闭编辑遮罩；若处于编辑模式，稍后恢复
        hideEditingOverlay();

        // Start with the default layout
//        VirtualKeyboardConfigurationLoader.createDefaultLayout(this, context);

        // Apply user preferences onto the default layout
        VirtualKeyboardConfigurationLoader.loadFromPreferences(this, context);

        // 若仍处于编辑模式，则在重建元素后恢复编辑遮罩，避免用户保存后遮罩消失
        if (shouldRestoreEditingOverlay) {
            showEditingOverlay();
        }
    }

    private void showEditingOverlay() {
        if (editingOverlay != null && editingTip != null) return;
        // 层级目标（自上而下）：
        // 菜单 > 虚拟键盘（按钮+网格线） > 编辑模式提示（遮罩+文字） > 串流画面
        // 关键点：编辑提示不应叠加到虚拟键盘里，而应插入到 StreamView 之上、键盘与网格线之下
        View root = context.findViewById(android.R.id.content);
        if (!(root instanceof FrameLayout)) return;
        FrameLayout rootLayout = (FrameLayout) root;

        // 计算应当插入的位置：紧跟在 StreamView 之后
        View stream = context.findViewById(com.limelight.R.id.surfaceView);
        int insertIndex = 1; // 默认放在 very early，避免被放到底部
        if (stream != null) {
            int idx = rootLayout.indexOfChild(stream);
            if (idx >= 0) {
                insertIndex = idx + 1;
            }
        }

        // 遮罩（半透明全屏）
        editingOverlay = new View(context);
        editingOverlay.setBackgroundColor(0x4D000000);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        rootLayout.addView(editingOverlay, Math.min(insertIndex, rootLayout.getChildCount()), lp);

        // 文字提示容器（与遮罩同层，位于遮罩之上一点点）
        FrameLayout tipContainer = new FrameLayout(context);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        rootLayout.addView(tipContainer, Math.min(insertIndex + 1, rootLayout.getChildCount()), tlp);
        editingContainer = tipContainer;

        // 提示文字
        editingTip = new TextView(context);
        editingTip.setText("编辑模式中\n点击移动 长按缩放 双击设置");
        editingTip.setTextColor(0xFFFFFFFF);
        editingTip.setTextSize(18);
        editingTip.setGravity(Gravity.CENTER);
        tipContainer.addView(editingTip, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ));

        // 注意：不再 bringToFront，避免盖住虚拟键盘与菜单
        // 返回按钮逻辑保持原有，由菜单处理
    }

    private void hideEditingOverlay() {
        View root = context.findViewById(android.R.id.content);
        if (root instanceof FrameLayout) {
            FrameLayout rootLayout = (FrameLayout) root;
            if (editingOverlay != null) {
                rootLayout.removeView(editingOverlay);
                editingOverlay = null;
            }
            if (editingContainer != null) {
                rootLayout.removeView(editingContainer);
                editingContainer = null;
            }
        }
        editingTip = null;
    }

    /**
     * 进入虚拟键盘编辑模式
     * - 切换到 NewSettingButtons 模式
     * - 根据设置显示网格对齐线
     * - 显示编辑遮罩与提示
     * - 发送中文提示通知
     */
    public void enterEditMode() {
        PreferenceConfiguration pref = context.getPrefConfig();
        if (currentMode != ControllerMode.NewSettingButtons) {
            currentMode = ControllerMode.NewSettingButtons;
            if (pref.enableGridLayout) {
                GameGridLines gridLines = context.getGameGridLines();
                if (gridLines != null) {
                    gridLines.show();
                }
            }
            showEditingOverlay();
            context.postNotification(context.getString(R.string.controller_mode_new_setting_button), 2000);
            for (VirtualKeyboardElement element : elements) {
                element.invalidate();
            }
        }
    }

    // 提供统一退出编辑模式的方法，供菜单调用
    public void exitEditMode() {
        if (currentMode != ControllerMode.Active) {
            currentMode = ControllerMode.Active;
            VirtualKeyboardConfigurationLoader.saveProfile(VirtualKeyboard.this, context);
            GameGridLines gameGridLines = context.getGameGridLines();
            if (gameGridLines != null) gameGridLines.hide();
            hideEditingOverlay();
            context.postNotification(context.getString(R.string.controller_mode_active_buttons), 2000);
            for (VirtualKeyboardElement element : elements) {
                element.invalidate();
            }
        }
    }

    public ControllerMode getControllerMode() {
        return currentMode;
    }

    public KeyboardInputContext getKeyboardInputContext() {
        return keyboardInputContext;
    }

    public void sendDownKey(short key){
        VirtualKeyboardVkCode.VKCode vkCode = VirtualKeyboardVkCode.VKCode.Companion.fromCode((int)key);
        switch (Objects.requireNonNull(vkCode)){
            case VK_LBUTTON:
                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
                break;
            case VK_RBUTTON:
                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
                break;
            case VK_MBUTTON:
                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_MIDDLE);
                break;
            case VK_XBUTTON1:
                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X1);
                break;
            case VK_XBUTTON2:
                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X2);
                break;
            default:
                conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, keyboardInputContext.modifier, (byte) 0);
        }
    }

    public void sendUpKey(short key){
        VirtualKeyboardVkCode.VKCode vkCode = VirtualKeyboardVkCode.VKCode.Companion.fromCode((int)key);
        switch (Objects.requireNonNull(vkCode)){
            case VK_LBUTTON:
                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                break;
            case VK_RBUTTON:
                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
                break;
            case VK_MBUTTON:
                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE);
                break;
            case VK_XBUTTON1:
                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X1);
                break;
            case VK_XBUTTON2:
                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X2);
                break;
            default:
                conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, keyboardInputContext.modifier, (byte) 0);
        }
    }

    /**
     * 发送键盘输入事件
     * @param keyMap 按键映射
     * @param keyDirection 按键方向 (KEY_DOWN 或 KEY_UP)
     * @param modifier 修饰键
     * @param flags 标志位
     */
    public void sendKeyboardInput(short keyMap, byte keyDirection, byte modifier, byte flags) {
        conn.sendKeyboardInput(keyMap, keyDirection, modifier, flags);
    }

    public void sendKeys(short[] keys) {
        final byte[] modifier = {(byte) 0};

        for (short key : keys) {
            conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier[0], (byte) 0);

            // Apply the modifier of the pressed key, e.g. CTRL first issues a CTRL event (without
            // modifier) and then sends the following keys with the CTRL modifier applied
            modifier[0] |= VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(key);
        }

        new Handler().postDelayed((() -> {

            for (int pos = keys.length - 1; pos >= 0; pos--) {
                short key = keys[pos];

                // Remove the keys modifier before releasing the key
                modifier[0] &= (byte) ~ VirtualKeyboardVkCode.INSTANCE.replaceSpecialKeys(key);

                conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, modifier[0], (byte) 0);
            }
        }), 25);
    }

    public void historyElements() {
        if (historyElements.isEmpty()) {
            Log.d("vk", "history empty, skip");
            return;
        }
        Log.d("vk", "撤回："+historyIndex+" size: "+historyElements.size());
        if (historyIndex < 0 || historyIndex >= historyElements.size()) {
            historyIndex = Math.max(0, Math.min(historyIndex, historyElements.size() - 1));
        }
        String nowElements = historyElements.get(historyIndex);
        VirtualKeyboardConfigurationLoader.loadForFile(context, nowElements);
        refreshLayout();
    }

    // 撤回
    public void quashHistory() {
        if (historyElements.isEmpty()) {
            Log.d("vk", "history empty, skip quash");
            return;
        }
        Log.d("vk", "撤回："+historyIndex+" size: "+historyElements.size());
        if (historyIndex > 0 && !historyElements.isEmpty()){
            historyIndex--;
        }
        Log.d("vk", "撤回后："+historyIndex+" size: "+historyElements.size());
        historyElements();
    }

    // 前进
    public void forwardHistory() {
        if (historyElements.isEmpty()) {
            Log.d("vk", "history empty, skip forward");
            return;
        }
        Log.d("vk", "前进："+historyIndex+" size: "+historyElements.size());
        if (historyIndex < historyElements.size() - 1){
            historyIndex++;
        }
        Log.d("vk", "前进后："+historyIndex+" size: "+historyElements.size());
        historyElements();
    }

    public void addHistory() {
        try {
            VirtualKeyboardConfigurationLoader.saveProfile(this, context);
            // 清除旧索引之后的记录
            if (!historyElements.isEmpty() && historyIndex != historyElements.size() - 1) {
                Log.d("vk", "清除旧索引："+historyIndex+" size: "+historyElements.size());
                historyElements = historyElements.subList(0, historyIndex + 1);
                Log.d("vk", "清除旧索引后："+historyIndex+" size: "+historyElements.size());
            }

            // 添加新的历史记录
            Log.d("vk", "添加新的历史："+historyIndex+" size: "+historyElements.size());
            historyElements.add(VirtualKeyboardConfigurationLoader.saveToFile(context));  // 深拷贝元素
            historyIndex = historyElements.size() - 1;
            Log.d("vk", "添加新的历史后："+historyIndex+" size: "+historyElements.size());
        }catch (Exception e){
            Log.e("vk", "error", e);
        }

    }

    // 清空历史记录
    public void clearHistory() {
        historyElements.clear();
        historyIndex = 0;
    }

    // 手柄控制器相关
    private final ControllerHandler controllerHandler;
    private final ControllerInputContext controllerInputContext = new ControllerInputContext();

//    public void setControllerHandler(ControllerHandler controllerHandler){
//        this.controllerHandler = controllerHandler;
//    }

    public static class ControllerInputContext {
        public short inputMap = 0x0000;
        public byte leftTrigger = 0x00;
        public byte rightTrigger = 0x00;
        public short rightStickX = 0x0000;
        public short rightStickY = 0x0000;
        public short leftStickX = 0x0000;
        public short leftStickY = 0x0000;
    }

    public ControllerInputContext getControllerInputContext() {
        return controllerInputContext;
    }

    private final Runnable delayedRetransmitRunnable = new Runnable() {
        @Override
        public void run() {
            sendControllerInputContextInternal();
        }
    };

    private void sendControllerInputContextInternal() {
        Log.d("vk", "INPUT_MAP + " + controllerInputContext.inputMap);
        Log.d("vk", "LEFT_TRIGGER " + controllerInputContext.leftTrigger);
        Log.d("vk", "RIGHT_TRIGGER " + controllerInputContext.rightTrigger);
        Log.d("vk", "LEFT STICK X: " + controllerInputContext.leftStickX + " Y: " + controllerInputContext.leftStickY);
        Log.d("vk", "RIGHT STICK X: " + controllerInputContext.rightStickX + " Y: " + controllerInputContext.rightStickY);


        if (controllerHandler != null) {
            controllerHandler.reportOscState(
                    controllerInputContext.inputMap,
                    controllerInputContext.leftStickX,
                    controllerInputContext.leftStickY,
                    controllerInputContext.rightStickX,
                    controllerInputContext.rightStickY,
                    controllerInputContext.leftTrigger,
                    controllerInputContext.rightTrigger
            );
        }
    }

    void sendControllerInputContext() {
        // Cancel retransmissions of prior gamepad inputs
        handler.removeCallbacks(delayedRetransmitRunnable);

        sendControllerInputContextInternal();

        // HACK: GFE sometimes discards gamepad packets when they are received
        // very shortly after another. This can be critical if an axis zeroing packet
        // is lost and causes an analog stick to get stuck. To avoid this, we retransmit
        // the gamepad state a few times unless another input event happens before then.
        handler.postDelayed(delayedRetransmitRunnable, 25);
        handler.postDelayed(delayedRetransmitRunnable, 50);
        handler.postDelayed(delayedRetransmitRunnable, 75);
    }
}
