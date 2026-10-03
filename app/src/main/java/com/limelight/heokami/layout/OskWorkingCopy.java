package com.limelight.heokami.layout;

import android.content.Context;
import android.content.SharedPreferences;

import com.limelight.binding.input.virtual_keyboard.VirtualKeyboardConfigurationLoader;

import java.util.Map;
import java.util.TreeMap;

/** The keyboard's current elements: the existing OSK SharedPreferences, element id to JSON. */
final class OskWorkingCopy implements LayoutProfileRepository.WorkingCopy {
    private final SharedPreferences prefs;

    OskWorkingCopy(Context context) {
        prefs = context.getSharedPreferences(
                VirtualKeyboardConfigurationLoader.OSK_PREFERENCE, Context.MODE_PRIVATE);
    }

    @Override
    public Map<Integer, String> read() {
        Map<Integer, String> out = new TreeMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!(entry.getValue() instanceof String)) {
                continue;
            }
            try {
                out.put(Integer.parseInt(entry.getKey()), (String) entry.getValue());
            } catch (NumberFormatException ignored) {
                // not an element entry
            }
        }
        return out;
    }

    @Override
    public void replace(Map<Integer, String> elements) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        for (Map.Entry<Integer, String> entry : elements.entrySet()) {
            editor.putString(String.valueOf(entry.getKey()), entry.getValue());
        }
        editor.apply();
    }
}
