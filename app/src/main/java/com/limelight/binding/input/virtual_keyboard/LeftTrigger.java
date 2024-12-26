/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_keyboard;

import android.annotation.SuppressLint;
import android.content.Context;

@SuppressLint("ViewConstructor")
public class LeftTrigger extends DigitalButton {
    public LeftTrigger(final VirtualKeyboard virtualKeyboard, final Context context, final int elementId, final int layer) {
        super(virtualKeyboard, context, elementId, layer);
        addDigitalButtonListener(new DigitalButtonListener() {
            @Override
            public void onClick() {
                VirtualKeyboard.ControllerInputContext inputContext =
                        virtualKeyboard.getControllerInputContext();
                inputContext.leftTrigger = (byte) 0xFF;

                virtualKeyboard.sendControllerInputContext();
            }

            @Override
            public void onLongClick() {
            }

            @Override
            public void onRelease() {
                VirtualKeyboard.ControllerInputContext inputContext =
                        virtualKeyboard.getControllerInputContext();
                inputContext.leftTrigger = (byte) 0x00;

                virtualKeyboard.sendControllerInputContext();
            }
        });
    }
}
