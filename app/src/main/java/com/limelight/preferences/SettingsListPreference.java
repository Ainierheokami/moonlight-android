package com.limelight.preferences;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.limelight.R;

/**
 * ListPreference that shows the selected entry on the right side of the row, so the
 * summary can keep describing what the option does.
 */
public class SettingsListPreference extends ListPreference {
    public SettingsListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setWidgetLayoutResource(R.layout.settings_pref_value);
    }

    public SettingsListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWidgetLayoutResource(R.layout.settings_pref_value);
    }

    public SettingsListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.settings_pref_value);
    }

    public SettingsListPreference(Context context) {
        super(context);
        setWidgetLayoutResource(R.layout.settings_pref_value);
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);

        // Older framework versions don't rebind the row when the value changes
        notifyChanged();
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        TextView valueView = view.findViewById(R.id.settings_value);
        if (valueView != null) {
            CharSequence entry = getEntry();
            valueView.setText(entry);
            valueView.setVisibility(entry != null ? View.VISIBLE : View.GONE);
        }
    }
}
