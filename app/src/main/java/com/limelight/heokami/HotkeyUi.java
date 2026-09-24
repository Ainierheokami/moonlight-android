package com.limelight.heokami;

import android.content.Context;
import android.graphics.Color;
import android.widget.Button;
import android.widget.EditText;

import com.limelight.R;
import com.limelight.utils.OverlayAlertDialog;

final class HotkeyUi {
    private HotkeyUi() {
    }

    static OverlayAlertDialog.Builder dialogBuilder(Context context) {
        return new OverlayAlertDialog.Builder(context, R.style.ModernHotkeyDialogTheme);
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static void styleInput(EditText input) {
        input.setBackgroundResource(R.drawable.add_pc_input_background);
        input.setTextColor(Color.rgb(245, 248, 252));
        input.setHintTextColor(Color.rgb(143, 160, 178));
        int horizontal = dp(input.getContext(), 12);
        int vertical = dp(input.getContext(), 10);
        input.setPadding(horizontal, vertical, horizontal, vertical);
    }

    static void styleButton(Button button, boolean danger) {
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setStateListAnimator(null);
        button.setBackgroundResource(danger
                ? R.drawable.button_background_red_dark
                : R.drawable.modern_dialog_secondary_button_background);
    }

    static void finishDialog(OverlayAlertDialog dialog) {
        int accent = Color.rgb(106, 203, 255);
        Button positive = dialog.getButton(OverlayAlertDialog.BUTTON_POSITIVE);
        Button neutral = dialog.getButton(OverlayAlertDialog.BUTTON_NEUTRAL);
        Button negative = dialog.getButton(OverlayAlertDialog.BUTTON_NEGATIVE);
        if (positive != null) positive.setTextColor(accent);
        if (neutral != null) neutral.setTextColor(accent);
        if (negative != null) negative.setTextColor(Color.rgb(184, 196, 210));
    }
}
