package com.limelight.heokami;

import android.content.Context;

import com.limelight.LimeLog;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.http.NvHTTP;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stream-scoped DPI bookkeeping for one host.
 *
 * <ul>
 *     <li>{@link #autoApply} sets the saved DPI (see {@link DisplayScaleSettings}) once the
 *     stream is up.</li>
 *     <li>Before the client changes a <em>physical</em> display, its original value is
 *     remembered so {@link #restorePhysicalDisplays} can put it back when the stream ends.
 *     Virtual displays are torn down after streaming, so they are never restored.</li>
 * </ul>
 *
 * <p>The blocking methods do network I/O and must run off the main thread.</p>
 */
public final class DisplayScaleSession {
    private static final int QUERY_ATTEMPTS = 5;

    public interface Listener {
        void onAutoApplied(int percent);

        void onAutoApplyFailed(String reason);
    }

    private static final class Original {
        final NvHTTP.DisplayInfo display;
        final int percent;

        Original(NvHTTP.DisplayInfo display, int percent) {
            this.display = display;
            this.percent = percent;
        }
    }

    private final Context context;
    private final String hostUuid;
    private final Map<String, Original> physicalOriginals = new LinkedHashMap<>();

    public DisplayScaleSession(Context context, String hostUuid) {
        this.context = context.getApplicationContext();
        this.hostUuid = hostUuid;
    }

    public String getHostUuid() {
        return hostUuid;
    }

    private static String key(NvHTTP.DisplayInfo display) {
        String id = display.deviceId != null && !display.deviceId.trim().isEmpty()
                ? display.deviceId : display.displayName;
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    /** Call before the client changes {@code display}'s scale (auto or manual). */
    public synchronized void rememberOriginal(NvHTTP.DisplayInfo display) {
        if (display == null || display.currentScalePercent == null
                || DisplayScaleSettings.isVirtualDisplay(display)) {
            return;
        }
        String key = key(display);
        if (!physicalOriginals.containsKey(key)) {
            physicalOriginals.put(key, new Original(display, display.currentScalePercent));
        }
    }

    /** A manual change from the game menu succeeded: save it as this host's value. */
    public void onUserChangedScale(NvHTTP.DisplayInfo display, int appliedPercent) {
        DisplayScaleSettings.setHostValue(context, hostUuid,
                DisplayScaleSettings.isVirtualDisplay(display), appliedPercent);
    }

    /**
     * Blocking. Applies the saved DPI to the display being streamed, if one is configured and it
     * differs from the host's current value.
     *
     * @param virtual       whether this stream uses the virtual display
     * @param targetDisplay the requested display name/device id, empty for the host default
     */
    public void autoApply(NvConnection conn, boolean virtual, String targetDisplay, Listener listener) {
        int desired = DisplayScaleSettings.resolveTarget(context, hostUuid, virtual);
        if (desired <= 0 || conn == null) {
            return;
        }

        try {
            NvHTTP.DisplayInfo target = findStreamDisplay(conn, virtual, targetDisplay);
            if (target == null) {
                LimeLog.warning("DPI auto-apply: stream display not found (virtual=" + virtual + ")");
                return;
            }
            NvHTTP.DisplayInfo fresh = conn.refreshDisplayScaleOptions(target, QUERY_ATTEMPTS);
            if (!fresh.supportsScale()) {
                if (listener != null) {
                    listener.onAutoApplyFailed(fresh.scaleStatusMessage);
                }
                return;
            }

            int percent = DisplayScaleSettings.nearestSupported(fresh.supportedScalePercents, desired);
            if (percent <= 0 || (fresh.currentScalePercent != null && fresh.currentScalePercent == percent)) {
                return;
            }

            rememberOriginal(fresh);
            NvHTTP.DisplayScaleResult result = conn.setDisplayScaleBlocking(fresh, percent);
            if (result.success) {
                LimeLog.info("DPI auto-apply: set " + fresh.displayName + " to " + percent + "%");
                if (listener != null) {
                    listener.onAutoApplied(result.currentScalePercent != null ? result.currentScalePercent : percent);
                }
            } else if (listener != null) {
                listener.onAutoApplyFailed(result.message);
            }
        } catch (Exception e) {
            LimeLog.warning("DPI auto-apply failed: " + e.getMessage());
            if (listener != null) {
                listener.onAutoApplyFailed(e.getMessage());
            }
        }
    }

    /** Blocking. Puts back the original DPI of every physical display this session changed. */
    public void restorePhysicalDisplays(NvConnection conn) {
        List<Original> originals;
        synchronized (this) {
            originals = new ArrayList<>(physicalOriginals.values());
            physicalOriginals.clear();
        }
        if (conn == null) {
            return;
        }
        for (Original original : originals) {
            try {
                NvHTTP.DisplayInfo fresh = conn.refreshDisplayScaleOptions(original.display, 2);
                if (!fresh.supportsScale()
                        || (fresh.currentScalePercent != null && fresh.currentScalePercent == original.percent)) {
                    continue;
                }
                NvHTTP.DisplayScaleResult result = conn.setDisplayScaleBlocking(fresh, original.percent);
                LimeLog.info("DPI restore: " + fresh.displayName + " -> " + original.percent
                        + "% (" + (result.success ? "ok" : result.message) + ")");
            } catch (Exception e) {
                LimeLog.warning("DPI restore failed: " + e.getMessage());
            }
        }
    }

    private NvHTTP.DisplayInfo findStreamDisplay(NvConnection conn, boolean virtual, String targetDisplay)
            throws Exception {
        List<NvHTTP.DisplayInfo> displays = conn.getDisplays();
        if (displays == null) {
            displays = new ArrayList<>();
        }
        if (virtual) {
            for (NvHTTP.DisplayInfo info : displays) {
                if (DisplayScaleSettings.isVirtualDisplay(info)) {
                    return info;
                }
            }
            return null;
        }

        String wanted = normalize(targetDisplay);
        if (!wanted.isEmpty()) {
            for (NvHTTP.DisplayInfo info : displays) {
                if (info != null && (wanted.equalsIgnoreCase(normalize(info.displayName))
                        || wanted.equalsIgnoreCase(normalize(info.deviceId)))) {
                    return info;
                }
            }
        }
        // Host default: let the host resolve its primary display.
        return new NvHTTP.DisplayInfo("", "", "");
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        int suffix = normalized.indexOf(" (");
        return suffix >= 0 ? normalized.substring(0, suffix).trim() : normalized;
    }
}
