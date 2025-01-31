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
        paint.setStrokeWidth(getDefaultStrokeWidth());

        paint.setColor(isPressed() ? pressedColor : getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);

        rect.left = rect.top = paint.getStrokeWidth();
        rect.right = getWidth() - rect.left;
        rect.bottom = getHeight() - rect.top;

        float cornerRadius = radius; // 圆角大小
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius ,paint);

        if (icon != -1) {
            @SuppressLint("UseCompatLoadingForDrawables") Drawable d = getResources().getDrawable(icon);
            d.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
            d.draw(canvas);
        } else {
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeWidth((float) getDefaultStrokeWidth() /2);
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
