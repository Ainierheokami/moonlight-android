/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_keyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a digital button on screen element. It is used to get click and double click user input.
 */
@SuppressLint("ViewConstructor")
public class DigitalButton extends VirtualKeyboardElement {

    /**
     * Listener interface to update registered observers.
     */
    public interface DigitalButtonListener {

        /**
         * onClick event will be fired on button click.
         */
        void onClick();

        /**
         * onLongClick event will be fired on button long click.
         */
        void onLongClick();

        /**
         * onRelease event will be fired on button unpress.
         */
        void onRelease();
    }


    private final List<DigitalButtonListener> listeners = new ArrayList<>();

    private final Runnable longClickRunnable = this::onLongClickCallback;

    private final Paint paint = new Paint();
    private final RectF rect = new RectF();

    private DigitalButton movingButton = null;

    boolean inRange(float x, float y) {
        return (this.getX() < x && this.getX() + this.getWidth() > x) &&
                (this.getY() < y && this.getY() + this.getHeight() > y);
    }

    public void checkMovement(float x, float y, DigitalButton movingButton) {
        // check if the movement happened in the same layer
        if (movingButton.layer != this.layer) {
            return;
        }

        // save current pressed state
        boolean wasPressed = isPressed();

        // check if the movement directly happened on the button
        if ((this.movingButton == null || movingButton == this.movingButton)
                && this.inRange(x, y)) {
            // set button pressed state depending on moving button pressed state
            if (this.isPressed() != movingButton.isPressed()) {
                this.setPressed(movingButton.isPressed());
            }
        }
        // check if the movement is outside of the range and the movement button
        // is the saved moving button
        else if (movingButton == this.movingButton) {
            this.setPressed(false);
        }

        // check if a change occurred
        if (wasPressed != isPressed()) {
            if (isPressed()) {
                // is pressed set moving button and emit click event
                this.movingButton = movingButton;
                onClickCallback();
            } else {
                // no longer pressed reset moving button and emit release event
                this.movingButton = null;
                onReleaseCallback();
            }

            invalidate();

        }

    }

    private void checkMovementForAllButtons(float x, float y) {
        for (VirtualKeyboardElement element : virtualKeyboard.getElements()) {
            if (element != this && element instanceof DigitalButton) {
                ((DigitalButton) element).checkMovement(x, y, this);
            }
        }
    }

    public DigitalButton(VirtualKeyboard virtualKeyboard, Context context, int elementId, int layer) {
        super(virtualKeyboard, context, elementId, layer);
    }

    public void addDigitalButtonListener(DigitalButtonListener listener) {
        listeners.add(listener);
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        paint.setTextSize(getPercent(getWidth(), 25));
        paint.setTextAlign(Paint.Align.CENTER);

        // 读取自定义描边配置（来自 buttonData）
        int fillColor = isPressed() ? pressedColor : getDefaultColor();
        int borderWidthPx = getDefaultStrokeWidth();
        int borderColor = fillColor;
        int textColor = Color.WHITE;
        int textAlphaPct = 100;
        boolean overallEnabled = false;
        int overallColorNormal = fillColor;
        int overallColorPressed = fillColor;
        int overallAlphaPct = 100;
        boolean borderEnabled = true;
        try {
            if (buttonData != null) {
                if (buttonData.has("BORDER_ENABLED")) {
                    borderEnabled = buttonData.getBoolean("BORDER_ENABLED");
                }
                if (buttonData.has("BORDER_WIDTH_PX")) {
                    borderWidthPx = buttonData.getInt("BORDER_WIDTH_PX");
                }
                if (buttonData.has("BORDER_COLOR")) {
                    borderColor = buttonData.getInt("BORDER_COLOR");
                }
                if (buttonData.has("BORDER_ALPHA")) {
                    int alphaPct = Math.max(0, Math.min(100, buttonData.getInt("BORDER_ALPHA")));
                    int baseA = (borderColor >>> 24) & 0xFF;
                    int composedA = (int)(baseA * (alphaPct / 100f));
                    borderColor = (composedA << 24) | (borderColor & 0x00FFFFFF);
                }
                if (buttonData.has("TEXT_COLOR")) {
                    textColor = buttonData.getInt("TEXT_COLOR");
                }
                if (buttonData.has("TEXT_ALPHA")) {
                    textAlphaPct = Math.max(0, Math.min(100, buttonData.getInt("TEXT_ALPHA")));
                }
                if (buttonData.has("BG_COLOR")) {
                    fillColor = buttonData.getInt("BG_COLOR");
                }
                if (buttonData.has("BG_COLOR_PRESSED") && isPressed()) {
                    fillColor = buttonData.getInt("BG_COLOR_PRESSED");
                }
                if (buttonData.has("BG_ALPHA")) {
                    int a = Math.max(0, Math.min(100, buttonData.getInt("BG_ALPHA")));
                    int baseA = (fillColor >>> 24) & 0xFF;
                    int composedA = (int)(baseA * (a / 100f));
                    fillColor = (composedA << 24) | (fillColor & 0x00FFFFFF);
                }
                if (buttonData.has("BG_ALPHA_PRESSED") && isPressed()) {
                    int a = Math.max(0, Math.min(100, buttonData.getInt("BG_ALPHA_PRESSED")));
                    int baseA = (fillColor >>> 24) & 0xFF;
                    int composedA = (int)(baseA * (a / 100f));
                    fillColor = (composedA << 24) | (fillColor & 0x00FFFFFF);
                }
                if (buttonData.has("OVERALL_ENABLED")) {
                    overallEnabled = buttonData.getBoolean("OVERALL_ENABLED");
                }
                if (buttonData.has("OVERALL_COLOR")) {
                    overallColorNormal = buttonData.getInt("OVERALL_COLOR");
                }
                if (buttonData.has("OVERALL_COLOR_PRESSED")) {
                    overallColorPressed = buttonData.getInt("OVERALL_COLOR_PRESSED");
                }
                if (buttonData.has("OVERALL_ALPHA")) {
                    overallAlphaPct = Math.max(0, Math.min(100, buttonData.getInt("OVERALL_ALPHA")));
                }
            }
        } catch (Exception ignored) {}

        // 应用整体覆盖（在所有细项之后最后合成，以便强制覆盖）
        if (overallEnabled) {
            int ov = isPressed() ? overallColorPressed : overallColorNormal;
            int baseA = (ov >>> 24) & 0xFF;
            int composedA = (int)(baseA * (overallAlphaPct / 100f));
            fillColor = (composedA << 24) | (ov & 0x00FFFFFF);
        }

        // 计算用于填充与描边的统一矩形（根据描边宽度内缩，确保边框与填充对齐）
        float strokeW = borderEnabled ? borderWidthPx : 0;
        rect.left = rect.top = strokeW;
        rect.right = getWidth() - rect.left;
        rect.bottom = getHeight() - rect.top;
        float cornerRadius = radius; // 圆角大小

        // 先画背景填充
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fillColor);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

        // 再画描边（完全关闭时不画）
        if (borderEnabled && strokeW > 0.5f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeW);
            paint.setColor(borderColor);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
        }

        if (icon != -1) {
            @SuppressLint("UseCompatLoadingForDrawables") Drawable d = getResources().getDrawable(icon);
            d.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
            d.draw(canvas);
        } else {
            // 文本使用文本颜色与透明度
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeWidth((float) getDefaultStrokeWidth() / 2);
            int baseA = (textColor >>> 24) & 0xFF;
            int composedA = (int)(baseA * (textAlphaPct / 100f));
            int composedTextColor = (composedA << 24) | (textColor & 0x00FFFFFF);
            paint.setColor(composedTextColor);
            // 若整体覆盖启用，优先文本颜色；否则保持文本颜色
            String ellipsizedText = (String) TextUtils.ellipsize(text, new TextPaint(paint), getWidth(), TextUtils.TruncateAt.END);
            canvas.drawText(ellipsizedText, getPercent(getWidth(), 50), getPercent(getHeight(), 63), paint);
        }
    }

    private void onClickCallback() {
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onClick();
        }

        virtualKeyboard.getHandler().removeCallbacks(longClickRunnable);
        long timerLongClickTimeout = 3000;
        virtualKeyboard.getHandler().postDelayed(longClickRunnable, timerLongClickTimeout);
    }

    private void onLongClickCallback() {
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onLongClick();
        }
    }

    private void onReleaseCallback() {
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onRelease();
        }

        // We may be called for a release without a prior click
        virtualKeyboard.getHandler().removeCallbacks(longClickRunnable);
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // get masked (not specific to a pointer) action
        float x = getX() + event.getX();
        float y = getY() + event.getY();
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                movingButton = null;
                setPressed(true);
                onClickCallback();

                invalidate();

                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                checkMovementForAllButtons(x, y);

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                setPressed(false);
                onReleaseCallback();

                checkMovementForAllButtons(x, y);

                invalidate();

                return true;
            }
            default: {
            }
        }
        return true;
    }
}
