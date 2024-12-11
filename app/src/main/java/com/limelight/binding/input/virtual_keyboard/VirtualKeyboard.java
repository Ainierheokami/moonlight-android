/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_keyboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

import java.util.ArrayList;
import java.util.List;

public class VirtualKeyboard {
    public static class ControllerInputContext {
        public short key = 0;
        public byte modifier = (byte) 0;
    }

    public enum ControllerMode {
        Active,
        MoveButtons,
        ResizeButtons,
        SettingsButtons
    }

    private static final boolean _PRINT_DEBUG_INFORMATION = false;

    private final NvConnection conn;
    private final Context context;
    private final Handler handler;

//    private final Runnable delayedRetransmitRunnable = new Runnable() {
//        @Override
//        public void run() {
//            sendControllerInputContextInternal();
//        }
//    };

    private FrameLayout frame_layout = null;

    ControllerMode currentMode = ControllerMode.Active;
    ControllerInputContext inputContext = new ControllerInputContext();

    private Button buttonConfigure = null;

    private List<VirtualKeyboardElement> elements = new ArrayList<>();
    public List<String> historyElements = new ArrayList<>();
    public int historyIndex = 0;

    public VirtualKeyboard(NvConnection conn, FrameLayout layout, final Context context) {
        this.conn = conn;
        this.frame_layout = layout;
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());

        buttonConfigure = new Button(context);
        buttonConfigure.setAlpha(0.25f);
        buttonConfigure.setFocusable(false);
        buttonConfigure.setBackgroundResource(R.drawable.ic_settings);
        buttonConfigure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message;

                if (currentMode == ControllerMode.Active){
                    currentMode = ControllerMode.MoveButtons;
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
                    message = context.getString(R.string.controller_mode_active_buttons);
                }

                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();

                buttonConfigure.invalidate();

                for (VirtualKeyboardElement element : elements) {
                    element.invalidate();
                }
            }
        });

    }

    Handler getHandler() {
        return handler;
    }

    public void hide() {
        for (VirtualKeyboardElement element : elements) {
            element.setVisibility(View.INVISIBLE);
        }

        buttonConfigure.setVisibility(View.INVISIBLE);
    }

    public void show() {
        for (VirtualKeyboardElement element : elements) {
            element.setVisibility(View.VISIBLE);
        }

        buttonConfigure.setVisibility(View.VISIBLE);
    }

    public void removeElements() {
        for (VirtualKeyboardElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();

        frame_layout.removeView(buttonConfigure);
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

    public void setOpacity(int opacity) {
        for (VirtualKeyboardElement element : elements) {
            element.setOpacity(opacity);
        }
    }


    public void addElement(VirtualKeyboardElement element, int x, int y, int width, int height) {
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);

        frame_layout.addView(element, layoutParams);
    }

    public List<VirtualKeyboardElement> getElements() {
        return elements;
    }

    public Integer getLastElementId() {
        if (!elements.isEmpty()) {
            return elements.get(elements.size() - 1).elementId;
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

    private static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            LimeLog.info("VirtualController: " + text);
        }
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
        removeElements();

        DisplayMetrics screen = context.getResources().getDisplayMetrics();

        int buttonSize = (int)(screen.heightPixels*0.06f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        params.leftMargin = 15;
        params.topMargin = 15;
        frame_layout.addView(buttonConfigure, params);

        // Start with the default layout
//        VirtualKeyboardConfigurationLoader.createDefaultLayout(this, context);

        // Apply user preferences onto the default layout
        VirtualKeyboardConfigurationLoader.loadFromPreferences(this, context);
    }

    public ControllerMode getControllerMode() {
        return currentMode;
    }

    public ControllerInputContext getControllerInputContext() {
        return inputContext;
    }

    public void sendDownKey(short key){
        conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, inputContext.modifier, (byte) 0);
    }

    public void sendUpKey(short key){
        conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, inputContext.modifier, (byte) 0);
    }

    public void gethistoryElements() {
        Log.d("vk", "撤回："+historyIndex+" size: "+historyElements.size());
        String nowElements = historyElements.get(historyIndex);
        VirtualKeyboardConfigurationLoader.loadForFile(context, nowElements);
        refreshLayout();
    }

    // 撤回
    public void quashHistory() {
        Log.d("vk", "撤回："+historyIndex+" size: "+historyElements.size());
        if (historyIndex > 0 && !historyElements.isEmpty()){
            historyIndex--;
        }
        Log.d("vk", "撤回后："+historyIndex+" size: "+historyElements.size());
        gethistoryElements();
    }

    // 前进
    public void forwardHistory() {
        Log.d("vk", "前进："+historyIndex+" size: "+historyElements.size());
        if (historyIndex < historyElements.size() - 1){
            historyIndex++;
        }
        Log.d("vk", "前进后："+historyIndex+" size: "+historyElements.size());
        gethistoryElements();
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

}
