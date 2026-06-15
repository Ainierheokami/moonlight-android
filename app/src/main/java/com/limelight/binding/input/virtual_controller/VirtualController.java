/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VirtualController {
    public static class ControllerInputContext {
        public short inputMap = 0x0000;
        public byte leftTrigger = 0x00;
        public byte rightTrigger = 0x00;
        public short rightStickX = 0x0000;
        public short rightStickY = 0x0000;
        public short leftStickX = 0x0000;
        public short leftStickY = 0x0000;
    }

    public enum ControllerMode {
        Active,
        MoveButtons,
        ResizeButtons
    }

    private static final boolean _PRINT_DEBUG_INFORMATION = false;

    private final ControllerHandler controllerHandler;
    private final Context context;
    private final Handler handler;

    private final Runnable delayedRetransmitRunnable = new Runnable() {
        @Override
        public void run() {
            sendControllerInputContextInternal();
        }
    };

    private FrameLayout frame_layout = null;
    private View edgeHandleView = null;
    private boolean edgeHideEnabled = false;
    private boolean isCollapsed = false;
    private int lastTouchDownX = -1;
    private int lastTouchDownY = -1;
    private int lastActiveEdge = EDGE_NONE;
    private int pendingEdgeReveal = EDGE_NONE;
    private final Map<VirtualControllerElement, FrameLayout.LayoutParams> expandedLayoutParams = new HashMap<>();

    private static final int EDGE_NONE = 0;
    private static final int EDGE_LEFT = 1;
    private static final int EDGE_RIGHT = 2;

    ControllerMode currentMode = ControllerMode.Active;
    ControllerInputContext inputContext = new ControllerInputContext();

    private Button buttonConfigure = null;

    private List<VirtualControllerElement> elements = new ArrayList<>();

    public VirtualController(final ControllerHandler controllerHandler, FrameLayout layout, final Context context) {
        this.controllerHandler = controllerHandler;
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
                } else {
                    currentMode = ControllerMode.Active;
                    VirtualControllerConfigurationLoader.saveProfile(VirtualController.this, context);
                    message = context.getString(R.string.controller_mode_active_buttons);
                }

                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();

                buttonConfigure.invalidate();

                for (VirtualControllerElement element : elements) {
                    element.invalidate();
                }
            }
        });

    }

    private void ensureEdgeHandle() {
        if (edgeHandleView != null) {
            return;
        }

        edgeHandleView = new View(context);
        edgeHandleView.setBackgroundColor(0xFFFFFFFF);
        edgeHandleView.setAlpha(0.85f);
        edgeHandleView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        edgeHandleView.setFocusable(false);
        edgeHandleView.setClickable(false);
    }

    private void updateEdgeHandleVisibility() {
        ensureEdgeHandle();
        if (edgeHandleView == null) {
            return;
        }
        edgeHandleView.setVisibility(edgeHideEnabled && isCollapsed ? View.VISIBLE : View.GONE);
    }

    private void updateEdgeHandleLayout() {
        ensureEdgeHandle();
        if (edgeHandleView == null || frame_layout == null) {
            return;
        }

        ViewGroup.LayoutParams existing = edgeHandleView.getLayoutParams();
        FrameLayout.LayoutParams params = existing instanceof FrameLayout.LayoutParams
                ? (FrameLayout.LayoutParams) existing
                : new FrameLayout.LayoutParams(10, ViewGroup.LayoutParams.MATCH_PARENT);
        params.width = Math.max(4, Math.min(10, frame_layout.getWidth() / 120));
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.leftMargin = lastActiveEdge == EDGE_RIGHT
                ? Math.max(0, frame_layout.getWidth() - params.width)
                : 0;
        params.topMargin = 0;
        params.rightMargin = 0;
        params.bottomMargin = 0;
        edgeHandleView.setLayoutParams(params);
    }

    private void attachEdgeHandle() {
        ensureEdgeHandle();
        if (edgeHandleView == null || frame_layout == null) {
            return;
        }

        if (edgeHandleView.getParent() == frame_layout) {
            updateEdgeHandleLayout();
            return;
        }

        if (edgeHandleView.getParent() instanceof ViewGroup) {
            ((ViewGroup) edgeHandleView.getParent()).removeView(edgeHandleView);
        }

        frame_layout.addView(edgeHandleView);
        updateEdgeHandleLayout();
        updateEdgeHandleVisibility();
    }

    private void refreshEdgeStateFromPreferences() {
        PreferenceConfiguration prefConfig = PreferenceConfiguration.readPreferences(context);
        edgeHideEnabled = prefConfig != null && prefConfig.onscreenControllerEdgeHide;
        if (!edgeHideEnabled) {
            isCollapsed = false;
            expandedLayoutParams.clear();
        }
        updateEdgeHandleVisibility();
    }

    private void collapseToEdge(int edge) {
        if (!edgeHideEnabled || frame_layout == null) {
            return;
        }

        isCollapsed = true;
        lastActiveEdge = edge;
        pendingEdgeReveal = edge;
        expandedLayoutParams.clear();

        int offset = Math.max(20, Math.min(frame_layout.getWidth(), frame_layout.getHeight()) / 18);
        for (VirtualControllerElement element : elements) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) element.getLayoutParams();
            if (params == null) {
                continue;
            }
            expandedLayoutParams.put(element, new FrameLayout.LayoutParams(params));
            if (edge == EDGE_LEFT) {
                params.leftMargin = -Math.max(1, params.width - offset);
            } else if (edge == EDGE_RIGHT) {
                params.leftMargin = frame_layout.getWidth() - offset;
            }
            element.requestLayout();
        }
        updateEdgeHandleVisibility();
        updateEdgeHandleLayout();
    }

    private void expandFromEdge() {
        if (!edgeHideEnabled || !isCollapsed) {
            return;
        }

        isCollapsed = false;
        pendingEdgeReveal = EDGE_NONE;
        for (VirtualControllerElement element : elements) {
            FrameLayout.LayoutParams savedParams = expandedLayoutParams.get(element);
            if (savedParams != null) {
                FrameLayout.LayoutParams currentParams = (FrameLayout.LayoutParams) element.getLayoutParams();
                currentParams.leftMargin = savedParams.leftMargin;
                currentParams.topMargin = savedParams.topMargin;
                currentParams.rightMargin = savedParams.rightMargin;
                currentParams.bottomMargin = savedParams.bottomMargin;
                currentParams.width = savedParams.width;
                currentParams.height = savedParams.height;
                element.requestLayout();
            }
        }
        expandedLayoutParams.clear();
        updateEdgeHandleVisibility();
    }

    private boolean isNearEdge(int x, int y) {
        if (frame_layout == null) {
            return false;
        }

        int edgeZone = Math.max(40, frame_layout.getWidth() / 20);
        return x <= edgeZone || x >= frame_layout.getWidth() - edgeZone;
    }

    public boolean handleEdgeGestureDown(int x, int y) {
        lastTouchDownX = x;
        lastTouchDownY = y;
        if (!edgeHideEnabled) {
            return false;
        }

        if (isCollapsed) {
            int edgeZone = Math.max(20, frame_layout.getWidth() / 30);
            if (pendingEdgeReveal == EDGE_LEFT && x <= edgeZone) {
                return true;
            }
            if (pendingEdgeReveal == EDGE_RIGHT && x >= frame_layout.getWidth() - edgeZone) {
                return true;
            }
        }

        if (!isCollapsed && isNearEdge(x, y)) {
            lastActiveEdge = x < frame_layout.getWidth() / 2 ? EDGE_LEFT : EDGE_RIGHT;
        }

        return false;
    }

    public boolean handleEdgeGestureMove(int x, int y) {
        if (!edgeHideEnabled) {
            return false;
        }

        if (isCollapsed) {
            int revealDistance = Math.max(24, frame_layout.getWidth() / 25);
            if (pendingEdgeReveal == EDGE_LEFT && x - lastTouchDownX > revealDistance) {
                expandFromEdge();
                return true;
            }
            if (pendingEdgeReveal == EDGE_RIGHT && lastTouchDownX - x > revealDistance) {
                expandFromEdge();
                return true;
            }
            return true;
        }

        int threshold = Math.max(48, frame_layout.getWidth() / 12);
        int edgeZone = Math.max(40, frame_layout.getWidth() / 20);
        int travelX = x - lastTouchDownX;
        int travelY = y - lastTouchDownY;
        if ((lastActiveEdge == EDGE_LEFT || x <= edgeZone) && travelX < -threshold && Math.abs(travelX) > Math.abs(travelY)) {
            collapseToEdge(EDGE_LEFT);
            return true;
        }
        if ((lastActiveEdge == EDGE_RIGHT || x >= frame_layout.getWidth() - edgeZone) && travelX > threshold && Math.abs(travelX) > Math.abs(travelY)) {
            collapseToEdge(EDGE_RIGHT);
            return true;
        }
        return false;
    }

    public boolean handleEdgeGestureUp() {
        lastTouchDownX = -1;
        lastTouchDownY = -1;
        if (isCollapsed) {
            return true;
        }
        lastActiveEdge = EDGE_NONE;
        return false;
    }

    Handler getHandler() {
        return handler;
    }

    public void hide() {
        currentMode = ControllerMode.Active;
        for (VirtualControllerElement element : elements) {
            element.setVisibility(View.INVISIBLE);
        }

        buttonConfigure.setVisibility(View.INVISIBLE);
        if (edgeHandleView != null) {
            edgeHandleView.setVisibility(View.GONE);
        }
    }

    public void show() {
        currentMode = ControllerMode.Active;
        for (VirtualControllerElement element : elements) {
            element.setVisibility(View.VISIBLE);
        }

        buttonConfigure.setVisibility(View.VISIBLE);
        attachEdgeHandle();
        refreshEdgeStateFromPreferences();
    }

    public void removeElements() {
        for (VirtualControllerElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();

        frame_layout.removeView(buttonConfigure);
        if (edgeHandleView != null && edgeHandleView.getParent() instanceof ViewGroup) {
            ((ViewGroup) edgeHandleView.getParent()).removeView(edgeHandleView);
        }
    }

    public void setOpacity(int opacity) {
        for (VirtualControllerElement element : elements) {
            element.setOpacity(opacity);
        }
    }


    public void addElement(VirtualControllerElement element, int x, int y, int width, int height) {
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);

        frame_layout.addView(element, layoutParams);
    }

    public List<VirtualControllerElement> getElements() {
        return elements;
    }

    private static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            LimeLog.info("VirtualController: " + text);
        }
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
        VirtualControllerConfigurationLoader.createDefaultLayout(this, context);

        // Apply user preferences onto the default layout
        VirtualControllerConfigurationLoader.loadFromPreferences(this, context);
        refreshEdgeStateFromPreferences();
        attachEdgeHandle();
        updateEdgeHandleVisibility();
    }

    public ControllerMode getControllerMode() {
        return currentMode;
    }

    public ControllerInputContext getControllerInputContext() {
        return inputContext;
    }

    private void sendControllerInputContextInternal() {
        _DBG("INPUT_MAP + " + inputContext.inputMap);
        _DBG("LEFT_TRIGGER " + inputContext.leftTrigger);
        _DBG("RIGHT_TRIGGER " + inputContext.rightTrigger);
        _DBG("LEFT STICK X: " + inputContext.leftStickX + " Y: " + inputContext.leftStickY);
        _DBG("RIGHT STICK X: " + inputContext.rightStickX + " Y: " + inputContext.rightStickY);

        if (controllerHandler != null) {
            controllerHandler.reportOscState(
                    inputContext.inputMap,
                    inputContext.leftStickX,
                    inputContext.leftStickY,
                    inputContext.rightStickX,
                    inputContext.rightStickY,
                    inputContext.leftTrigger,
                    inputContext.rightTrigger
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
