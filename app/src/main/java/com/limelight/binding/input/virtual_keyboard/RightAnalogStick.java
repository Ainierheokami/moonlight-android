/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_keyboard;

import android.annotation.SuppressLint;
import android.content.Context;

import com.limelight.nvstream.input.ControllerPacket;

@SuppressLint("ViewConstructor")
public class RightAnalogStick extends AnalogStick {
    public RightAnalogStick(final VirtualKeyboard virtualKeyboard, final Context context, final int elementId, final int layer) {
        super(virtualKeyboard, context, elementId, layer);

        addAnalogStickListener(new AnalogStickListener() {
            @Override
            public void onMovement(float x, float y) {
                VirtualKeyboard.ControllerInputContext inputContext =
                        virtualKeyboard.getControllerInputContext();
                inputContext.rightStickX = (short) (x * 0x7FFE);
                inputContext.rightStickY = (short) (y * 0x7FFE);

                virtualKeyboard.sendControllerInputContext();
            }

            @Override
            public void onClick() {
            }

            @Override
            public void onDoubleClick() {
                VirtualKeyboard.ControllerInputContext inputContext =
                        virtualKeyboard.getControllerInputContext();
                inputContext.inputMap |= ControllerPacket.RS_CLK_FLAG;

                virtualKeyboard.sendControllerInputContext();
            }

            @Override
            public void onRevoke() {
                VirtualKeyboard.ControllerInputContext inputContext =
                        virtualKeyboard.getControllerInputContext();
                inputContext.inputMap &= ~ControllerPacket.RS_CLK_FLAG;

                virtualKeyboard.sendControllerInputContext();
            }
        });
    }
}
