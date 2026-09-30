package com.limelight.preferences;

import android.content.Context;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

import com.limelight.R;
import com.limelight.utils.AppToast;
import com.limelight.utils.OverlayAlertDialog;

// Based on a Stack Overflow example: http://stackoverflow.com/questions/1974193/slider-on-my-preferencescreen
public class SeekBarPreference extends DialogPreference
{
    private static final String ANDROID_SCHEMA_URL = "http://schemas.android.com/apk/res/android";
    private static final String SEEKBAR_SCHEMA_URL = "http://schemas.moonlight-stream.com/apk/res/seekbar";

    // java.util.function.IntConsumer requires API 24
    private interface ValueConsumer {
        void accept(int value);
    }

    private final Context context;

    private final String dialogMessage;
    private final String suffix;
    private final int defaultValue;
    private final int maxValue;
    private final int minValue;
    private final int stepSize;
    private final int keyStepSize;
    private final int divisor;
    private int currentValue;

    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;

        // Read the message from XML
        int dialogMessageId = attrs.getAttributeResourceValue(ANDROID_SCHEMA_URL, "dialogMessage", 0);
        if (dialogMessageId == 0) {
            dialogMessage = attrs.getAttributeValue(ANDROID_SCHEMA_URL, "dialogMessage");
        }
        else {
            dialogMessage = context.getString(dialogMessageId);
        }

        // Get the suffix for the number displayed in the dialog
        int suffixId = attrs.getAttributeResourceValue(ANDROID_SCHEMA_URL, "text", 0);
        if (suffixId == 0) {
            suffix = attrs.getAttributeValue(ANDROID_SCHEMA_URL, "text");
        }
        else {
            suffix = context.getString(suffixId);
        }

        // Get default, min, and max seekbar values
        defaultValue = attrs.getAttributeIntValue(ANDROID_SCHEMA_URL, "defaultValue", PreferenceConfiguration.getDefaultBitrate(context));
        maxValue = attrs.getAttributeIntValue(ANDROID_SCHEMA_URL, "max", 100);
        minValue = attrs.getAttributeIntValue(SEEKBAR_SCHEMA_URL, "min", 1);
        stepSize = attrs.getAttributeIntValue(SEEKBAR_SCHEMA_URL, "step", 1);
        divisor = attrs.getAttributeIntValue(SEEKBAR_SCHEMA_URL, "divisor", 1);
        keyStepSize = attrs.getAttributeIntValue(SEEKBAR_SCHEMA_URL, "keyStep", 0);

        // Show the current value on the right side of the settings row
        setLayoutResource(R.layout.settings_pref_item);
        setWidgetLayoutResource(R.layout.settings_pref_value);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        TextView rowValueText = view.findViewById(R.id.settings_value);
        if (rowValueText != null) {
            rowValueText.setText(formatValue(currentValue));
        }
    }

    /**
     * Reloads the value after it was written to SharedPreferences directly
     * (for example when the bitrate is reset after a resolution change).
     */
    public void syncFromStorage() {
        if (shouldPersist()) {
            currentValue = getPersistedInt(defaultValue);
            notifyChanged();
        }
    }

    @Override
    protected void onSetInitialValue(boolean restore, Object defaultValue)
    {
        super.onSetInitialValue(restore, defaultValue);
        if (restore) {
            currentValue = shouldPersist() ? getPersistedInt(this.defaultValue) : 0;
        }
        else {
            currentValue = (Integer) defaultValue;
        }
    }

    public int getProgress() {
        return currentValue;
    }

    /**
     * Shows the slider dialog. The dialog edits a pending value that is only committed when
     * the user presses OK, so cancelling never changes what the settings row displays.
     */
    @Override
    public void showDialog(Bundle state) {
        if (shouldPersist()) {
            currentValue = getPersistedInt(defaultValue);
        }
        final int[] pendingValue = { clampAndRoundValue(currentValue) };

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        if (dialogMessage != null) {
            TextView messageText = new TextView(context);
            messageText.setText(dialogMessage);
            messageText.setTextColor(0xFFB8C4D2);
            messageText.setTextSize(14);
            layout.addView(messageText, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        // Value row: [ - ]  value  [ + ]
        LinearLayout valueRow = new LinearLayout(context);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setGravity(Gravity.CENTER_VERTICAL);

        Button minusButton = createStepButton("\u2212");
        Button plusButton = createStepButton("+");

        TextView valueView = new TextView(context);
        valueView.setGravity(Gravity.CENTER);
        valueView.setTextSize(30);
        valueView.setTextColor(0xFFF5F8FC);
        valueView.setTypeface(null, android.graphics.Typeface.BOLD);
        valueView.setBackgroundResource(android.R.drawable.list_selector_background);
        valueView.setContentDescription(context.getString(R.string.seekbar_input_dialog_hint));

        valueRow.addView(minusButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        valueRow.addView(valueView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        valueRow.addView(plusButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout.LayoutParams valueRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        valueRowParams.topMargin = dp(16);
        layout.addView(valueRow, valueRowParams);

        final SeekBar dialogSeekBar = new SeekBar(context);
        dialogSeekBar.setMax(valueToProgress(maxValue));
        // keyStep is expressed in value units, while the SeekBar counts steps
        dialogSeekBar.setKeyProgressIncrement(Math.max(1, (keyStepSize != 0 ? keyStepSize : stepSize) / stepSize));
        LinearLayout.LayoutParams seekBarParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        seekBarParams.topMargin = dp(12);
        layout.addView(dialogSeekBar, seekBarParams);

        final Runnable refresh = () -> {
            valueView.setText(formatValue(pendingValue[0]));
            minusButton.setEnabled(pendingValue[0] > minValue);
            plusButton.setEnabled(pendingValue[0] < maxValue);
        };

        dialogSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                pendingValue[0] = progressToValue(progress);
                refresh.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });

        final ValueConsumer setPending = value -> {
            pendingValue[0] = clampAndRoundValue(value);
            dialogSeekBar.setProgress(valueToProgress(pendingValue[0]));
            refresh.run();
        };

        minusButton.setOnClickListener(v -> setPending.accept(pendingValue[0] - stepSize));
        plusButton.setOnClickListener(v -> setPending.accept(pendingValue[0] + stepSize));
        valueView.setOnClickListener(v -> showValueInputDialog(pendingValue[0], setPending));

        setPending.accept(pendingValue[0]);

        new OverlayAlertDialog.Builder(context)
                .setTitle(getTitle())
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dialogInterface, which) -> {
                    int newValue = pendingValue[0];
                    if (callChangeListener(newValue)) {
                        currentValue = newValue;
                        if (shouldPersist()) {
                            persistInt(newValue);
                        }
                        notifyChanged();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setCancelable(true)
                .show();
    }

    private Button createStepButton(String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(20);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setStateListAnimator(null);
        button.setTextColor(0xFFF5F8FC);
        button.setBackgroundResource(R.drawable.modern_dialog_secondary_button_background);
        return button;
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private int valueToProgress(int value) {
        return Math.max(0, (clampAndRoundValue(value) - minValue) / stepSize);
    }

    private int progressToValue(int progress) {
        return clampAndRoundValue(minValue + progress * stepSize);
    }

    private int clampAndRoundValue(int value) {
        int clamped = Math.max(minValue, Math.min(maxValue, value));
        int rounded = ((clamped + (stepSize - 1)) / stepSize) * stepSize;
        return Math.max(minValue, Math.min(maxValue, rounded));
    }

    private String formatValue(int value) {
        String t;
        if (getKey() != null && getKey().equals("seekbar_background_reconnect_timeout") && value == 0) {
            // "Never" reads wrong with a unit suffix appended
            return context.getString(R.string.seekbar_never_timeout);
        } else if (divisor != 1) {
            float floatValue = value / (float) divisor;
            t = String.format(Locale.getDefault(), "%.1f", floatValue);
        } else {
            t = String.valueOf(value);
        }
        return suffix == null ? t : t.concat(suffix.length() > 1 ? " " + suffix : suffix);
    }

    // Manual entry uses the same unit that is displayed (e.g. Mbps for the bitrate), not the
    // raw stored unit, and accepts decimals when the value has a divisor.
    private void showValueInputDialog(int initialValue, ValueConsumer onValue) {
        OverlayAlertDialog.Builder dialog = new OverlayAlertDialog.Builder(context);
        EditText valueEditText = new EditText(context);
        int inputType = android.text.InputType.TYPE_CLASS_NUMBER;
        if (divisor != 1) {
            inputType |= android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;
        }
        valueEditText.setInputType(inputType);
        valueEditText.setText(formatNumber(initialValue));
        valueEditText.setSelection(valueEditText.getText().length());
        dialog.setTitle(getTitle());
        dialog.setMessage(context.getString(R.string.seekbar_input_dialog_range,
                formatValue(minValue), formatValue(maxValue)));
        dialog.setView(valueEditText);
        dialog.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
            try {
                String text = valueEditText.getText().toString().trim().replace(',', '.');
                onValue.accept(Math.round(Float.parseFloat(text) * divisor));
            } catch (NumberFormatException e) {
                AppToast.makeText(context, R.string.seekbar_input_number_error, AppToast.LENGTH_SHORT).show();
            }
        });
        dialog.setNegativeButton(android.R.string.cancel, null);
        dialog.show();
    }

    private String formatNumber(int value) {
        if (divisor == 1) {
            return String.valueOf(value);
        }
        float displayValue = value / (float) divisor;
        return displayValue == Math.round(displayValue) ?
                String.valueOf(Math.round(displayValue)) :
                String.format(Locale.US, "%.1f", displayValue);
    }
}
