/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_keyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("ViewConstructor")
public class DigitalPad extends VirtualKeyboardElement {
    public final static int DIGITAL_PAD_DIRECTION_NO_DIRECTION = 0;
    int direction = DIGITAL_PAD_DIRECTION_NO_DIRECTION;
    public final static int DIGITAL_PAD_DIRECTION_LEFT = 1;
    public final static int DIGITAL_PAD_DIRECTION_UP = 2;
    public final static int DIGITAL_PAD_DIRECTION_RIGHT = 4;
    public final static int DIGITAL_PAD_DIRECTION_DOWN = 8;
    List<DigitalPadListener> listeners = new ArrayList<>();

    private static final int DPAD_MARGIN = 5;

    private final Paint paint = new Paint();

    public DigitalPad(VirtualKeyboard virtualKeyboard, Context context, int elementId, int layer) {
        super(virtualKeyboard, context, elementId, layer);
    }

    public void addDigitalPadListener(DigitalPadListener listener) {
        listeners.add(listener);
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        paint.setTextSize(getPercent(getCorrectWidth(), 20));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());

        // 读取与 DigitalButton 一致的外观样式键
        boolean borderEnabled = true;
        int borderWidthPx = getDefaultStrokeWidth();
        int borderColor = getDefaultColor();
        int fillColor = getDefaultColor();
        int bgAlphaPct = 100;
        int bgPressedAlphaPct = 100;
        boolean overallEnabled = false;
        int overallColorNormal = fillColor;
        int overallColorPressed = fillColor;
        int overallAlphaPct = 100;
        try {
            if (buttonData != null) {
                if (buttonData.has("BORDER_ENABLED")) borderEnabled = buttonData.getBoolean("BORDER_ENABLED");
                if (buttonData.has("BORDER_WIDTH_PX")) borderWidthPx = buttonData.getInt("BORDER_WIDTH_PX");
                if (buttonData.has("BORDER_COLOR")) borderColor = buttonData.getInt("BORDER_COLOR");
                if (buttonData.has("BORDER_ALPHA")) {
                    int a = Math.max(0, Math.min(100, buttonData.getInt("BORDER_ALPHA")));
                    int baseA = (borderColor >>> 24) & 0xFF;
                    int composedA = (int)(baseA * (a / 100f));
                    borderColor = (composedA << 24) | (borderColor & 0x00FFFFFF);
                }
                if (buttonData.has("BG_COLOR")) fillColor = buttonData.getInt("BG_COLOR");
                if (buttonData.has("BG_COLOR_PRESSED") && isPressed()) fillColor = buttonData.getInt("BG_COLOR_PRESSED");
                if (buttonData.has("BG_ALPHA")) bgAlphaPct = Math.max(0, Math.min(100, buttonData.getInt("BG_ALPHA")));
                if (buttonData.has("BG_ALPHA_PRESSED") && isPressed()) bgPressedAlphaPct = Math.max(0, Math.min(100, buttonData.getInt("BG_ALPHA_PRESSED")));
                if (buttonData.has("OVERALL_ENABLED")) overallEnabled = buttonData.getBoolean("OVERALL_ENABLED");
                if (buttonData.has("OVERALL_COLOR")) overallColorNormal = buttonData.getInt("OVERALL_COLOR");
                if (buttonData.has("OVERALL_COLOR_PRESSED")) overallColorPressed = buttonData.getInt("OVERALL_COLOR_PRESSED");
                if (buttonData.has("OVERALL_ALPHA")) overallAlphaPct = Math.max(0, Math.min(100, buttonData.getInt("OVERALL_ALPHA")));
            }
        } catch (Exception ignored) {}

        // 计算填充色合成透明度：先根据 BG_ALPHA，然后如启用 overall 再覆盖
        {
            int a = isPressed() ? bgPressedAlphaPct : bgAlphaPct;
            a = Math.max(0, Math.min(100, a));
            int baseA = (fillColor >>> 24) & 0xFF;
            int composedA = (int)(baseA * (a / 100f));
            fillColor = (composedA << 24) | (fillColor & 0x00FFFFFF);
        }
        if (overallEnabled) {
            int ov = isPressed() ? overallColorPressed : overallColorNormal;
            int baseA = (ov >>> 24) & 0xFF;
            int composedA = (int)(baseA * (overallAlphaPct / 100f));
            fillColor = (composedA << 24) | (ov & 0x00FFFFFF);
        }

        if (direction == DIGITAL_PAD_DIRECTION_NO_DIRECTION) {
            // draw center rect with fill and optional border
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fillColor);
            canvas.drawRect(
                    getPercent(getWidth(), 36), getPercent(getHeight(), 36),
                    getPercent(getWidth(), 63), getPercent(getHeight(), 63),
                    paint
            );
            if (borderEnabled && borderWidthPx > 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(borderWidthPx);
                paint.setColor(borderColor);
                canvas.drawRect(
                        getPercent(getWidth(), 36), getPercent(getHeight(), 36),
                        getPercent(getWidth(), 63), getPercent(getHeight(), 63),
                        paint
                );
            }
        }

        // draw left rect
        paint.setColor((direction & DIGITAL_PAD_DIRECTION_LEFT) > 0 ? pressedColor : fillColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(
                borderEnabled ? (borderWidthPx + DPAD_MARGIN) : (paint.getStrokeWidth()+DPAD_MARGIN), getPercent(getHeight(), 33),
                getPercent(getWidth(), 33), getPercent(getHeight(), 66),
                paint
        );
        if (borderEnabled && borderWidthPx > 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(borderWidthPx);
            paint.setColor(borderColor);
            canvas.drawRect(
                    borderWidthPx + DPAD_MARGIN, getPercent(getHeight(), 33),
                    getPercent(getWidth(), 33), getPercent(getHeight(), 66),
                    paint
            );
        }


        // draw up rect
        paint.setColor((direction & DIGITAL_PAD_DIRECTION_UP) > 0 ? pressedColor : fillColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(
                getPercent(getWidth(), 33), borderEnabled ? (borderWidthPx + DPAD_MARGIN) : (paint.getStrokeWidth()+DPAD_MARGIN),
                getPercent(getWidth(), 66), getPercent(getHeight(), 33),
                paint
        );
        if (borderEnabled && borderWidthPx > 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(borderWidthPx);
            paint.setColor(borderColor);
            canvas.drawRect(
                    getPercent(getWidth(), 33), borderWidthPx + DPAD_MARGIN,
                    getPercent(getWidth(), 66), getPercent(getHeight(), 33),
                    paint
            );
        }

        // draw right rect
        paint.setColor((direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0 ? pressedColor : fillColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(
                getPercent(getWidth(), 66), getPercent(getHeight(), 33),
                getWidth() - (borderEnabled ? (borderWidthPx + DPAD_MARGIN) : (paint.getStrokeWidth()+DPAD_MARGIN)), getPercent(getHeight(), 66),
                paint
        );
        if (borderEnabled && borderWidthPx > 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(borderWidthPx);
            paint.setColor(borderColor);
            canvas.drawRect(
                    getPercent(getWidth(), 66), getPercent(getHeight(), 33),
                    getWidth() - (borderWidthPx + DPAD_MARGIN), getPercent(getHeight(), 66),
                    paint
            );
        }

        // draw down rect
        paint.setColor((direction & DIGITAL_PAD_DIRECTION_DOWN) > 0 ? pressedColor : fillColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(
                getPercent(getWidth(), 33), getPercent(getHeight(), 66),
                getPercent(getWidth(), 66), getHeight() - (borderEnabled ? (borderWidthPx + DPAD_MARGIN) : (paint.getStrokeWidth()+DPAD_MARGIN)),
                paint
        );
        if (borderEnabled && borderWidthPx > 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(borderWidthPx);
            paint.setColor(borderColor);
            canvas.drawRect(
                    getPercent(getWidth(), 33), getPercent(getHeight(), 66),
                    getPercent(getWidth(), 66), getHeight() - (borderWidthPx + DPAD_MARGIN),
                    paint
            );
        }

        // draw left up line
        paint.setColor(((direction & DIGITAL_PAD_DIRECTION_LEFT) > 0 && (direction & DIGITAL_PAD_DIRECTION_UP) > 0) ? pressedColor : borderColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(borderEnabled ? borderWidthPx : getDefaultStrokeWidth());
        canvas.drawLine(
                (borderEnabled ? borderWidthPx : (int)paint.getStrokeWidth())+DPAD_MARGIN, getPercent(getHeight(), 33),
                getPercent(getWidth(), 33), (borderEnabled ? borderWidthPx : (int)paint.getStrokeWidth())+DPAD_MARGIN,
                paint
        );

        // draw up right line
        paint.setColor(((direction & DIGITAL_PAD_DIRECTION_UP) > 0 && (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0) ? pressedColor : borderColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(borderEnabled ? borderWidthPx : getDefaultStrokeWidth());
        canvas.drawLine(
                getPercent(getWidth(), 66), (borderEnabled ? borderWidthPx : (int)paint.getStrokeWidth())+DPAD_MARGIN,
                getWidth() - ((borderEnabled ? borderWidthPx : (int)paint.getStrokeWidth())+DPAD_MARGIN), getPercent(getHeight(), 33),
                paint
        );

        // draw right down line
        paint.setColor(((direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0 && (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0) ? pressedColor : borderColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(borderEnabled ? borderWidthPx : getDefaultStrokeWidth());
        canvas.drawLine(
                getWidth()-(borderEnabled ? borderWidthPx : (int)paint.getStrokeWidth()), getPercent(getHeight(), 66),
                getPercent(getWidth(), 66), getHeight()-((borderEnabled ? borderWidthPx : (int)paint.getStrokeWidth())+DPAD_MARGIN),
                paint
        );

        // draw down left line
        paint.setColor(((direction & DIGITAL_PAD_DIRECTION_DOWN) > 0 && (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0) ? pressedColor : borderColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(borderEnabled ? borderWidthPx : getDefaultStrokeWidth());
        canvas.drawLine(
                getPercent(getWidth(), 33), getHeight()-((borderEnabled ? borderWidthPx : (int)paint.getStrokeWidth())+DPAD_MARGIN),
                (borderEnabled ? borderWidthPx : (int)paint.getStrokeWidth())+DPAD_MARGIN, getPercent(getHeight(), 66),
                paint
        );
    }

    private void newDirectionCallback(int direction) {
//        _DBG("direction: " + direction);

        // notify listeners
        for (DigitalPadListener listener : listeners) {
            listener.onDirectionChange(direction);
        }
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // get masked (not specific to a pointer) action
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                direction = 0;

                if (event.getX() < getPercent(getWidth(), 33)) {
                    direction |= DIGITAL_PAD_DIRECTION_LEFT;
                }
                if (event.getX() > getPercent(getWidth(), 66)) {
                    direction |= DIGITAL_PAD_DIRECTION_RIGHT;
                }
                if (event.getY() > getPercent(getHeight(), 66)) {
                    direction |= DIGITAL_PAD_DIRECTION_DOWN;
                }
                if (event.getY() < getPercent(getHeight(), 33)) {
                    direction |= DIGITAL_PAD_DIRECTION_UP;
                }
                newDirectionCallback(direction);
                invalidate();

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                direction = 0;
                newDirectionCallback(direction);
                invalidate();

                return true;
            }
            default: {
            }
        }

        return true;
    }

    public interface DigitalPadListener {
        void onDirectionChange(int direction);
    }
}
