package com.limelight.heokami;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.limelight.R;
import com.limelight.nvstream.http.NvHTTP;

import java.util.List;
import java.util.Locale;

/**
 * Saved host display DPI (Windows scaling) preferences.
 *
 * <p>Values are stored per host (by PC UUID) and per display <em>type</em> — virtual vs
 * physical — instead of per device id, because the virtual display's device id can change each
 * time it is recreated. A host without its own value follows the global default from Settings.</p>
 *
 * <p>Value encoding: a positive number is a scale percent. For a host, {@link #FOLLOW_GLOBAL}
 * means "use the global default" and {@link #OFF} means "never auto-adjust this host". For the
 * global default, {@link #OFF} (stored as "0") means "don't auto-adjust".</p>
 */
public final class DisplayScaleSettings {
    public static final int FOLLOW_GLOBAL = 0;
    public static final int OFF = -1;

    /** Common Windows scaling steps offered when the host's exact list isn't known. */
    public static final int[] COMMON_PERCENTS = {100, 125, 150, 175, 200, 225, 250, 300, 350};

    public static final String PREF_GLOBAL_VIRTUAL = "list_display_scale_default_virtual";
    public static final String PREF_GLOBAL_PHYSICAL = "list_display_scale_default_physical";

    private static final String HOST_PREF_FILE = "display_scale_per_host";

    private DisplayScaleSettings() {
    }

    private static String hostKey(String hostUuid, boolean virtual) {
        return hostUuid.trim().toLowerCase(Locale.ROOT) + (virtual ? ".virtual" : ".physical");
    }

    /** {@link #FOLLOW_GLOBAL}, {@link #OFF} or a scale percent. */
    public static int getHostValue(Context context, String hostUuid, boolean virtual) {
        if (context == null || hostUuid == null || hostUuid.trim().isEmpty()) {
            return FOLLOW_GLOBAL;
        }
        return context.getSharedPreferences(HOST_PREF_FILE, Context.MODE_PRIVATE)
                .getInt(hostKey(hostUuid, virtual), FOLLOW_GLOBAL);
    }

    public static void setHostValue(Context context, String hostUuid, boolean virtual, int value) {
        if (context == null || hostUuid == null || hostUuid.trim().isEmpty()) {
            return;
        }
        SharedPreferences.Editor editor = context.getSharedPreferences(HOST_PREF_FILE, Context.MODE_PRIVATE).edit();
        if (value == FOLLOW_GLOBAL) {
            editor.remove(hostKey(hostUuid, virtual));
        } else {
            editor.putInt(hostKey(hostUuid, virtual), value);
        }
        editor.apply();
    }

    /** {@link #OFF} or a scale percent. */
    public static int getGlobalValue(Context context, boolean virtual) {
        if (context == null) {
            return OFF;
        }
        String raw = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(virtual ? PREF_GLOBAL_VIRTUAL : PREF_GLOBAL_PHYSICAL, "0");
        try {
            int value = Integer.parseInt(raw == null ? "0" : raw.trim());
            return value > 0 ? value : OFF;
        } catch (NumberFormatException e) {
            return OFF;
        }
    }

    /** The percent to auto-apply for this host and display type, or 0 for "leave it alone". */
    public static int resolveTarget(Context context, String hostUuid, boolean virtual) {
        int hostValue = getHostValue(context, hostUuid, virtual);
        if (hostValue == OFF) {
            return 0;
        }
        if (hostValue > 0) {
            return hostValue;
        }
        int global = getGlobalValue(context, virtual);
        return global > 0 ? global : 0;
    }

    /** Picks the supported value closest to {@code desired} (ties go to the smaller one). */
    public static int nearestSupported(List<Integer> supported, int desired) {
        int best = -1;
        for (Integer value : supported) {
            if (value == null) {
                continue;
            }
            if (best < 0 || Math.abs(value - desired) < Math.abs(best - desired)) {
                best = value;
            }
        }
        return best;
    }

    public static boolean isVirtualDisplay(NvHTTP.DisplayInfo info) {
        if (info == null) {
            return false;
        }
        return containsVirtualHint(info.displayName) || containsVirtualHint(info.friendlyName)
                || containsVirtualHint(info.deviceId);
    }

    private static boolean containsVirtualHint(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("zako") || lower.contains("virtual");
    }

    /** Human label for a global value. */
    public static String describeGlobal(Context context, int value) {
        return value > 0 ? context.getString(R.string.display_scale_option, value)
                : context.getString(R.string.display_scale_auto_off);
    }

    /** Human label for a host value, including what "follow global" currently resolves to. */
    public static String describeHost(Context context, int hostValue, boolean virtual) {
        if (hostValue == OFF) {
            return context.getString(R.string.display_scale_auto_off);
        }
        if (hostValue > 0) {
            return context.getString(R.string.display_scale_option, hostValue);
        }
        return context.getString(R.string.display_scale_follow_global,
                describeGlobal(context, getGlobalValue(context, virtual)));
    }
}
