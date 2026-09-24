package com.limelight.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayDeque;

/** Queues in-app toasts and renders them in the current Activity's shared overlay. */
public final class ToastManager implements OverlayManager.LifecycleListener {
    private static final long SHORT_DURATION_MS = 3500L;
    private static final long LONG_DURATION_MS = 6500L;
    private static final long COPIED_DURATION_MS = 1800L;
    private static final long DEDUPLICATION_WINDOW_MS = 900L;

    private static final ToastManager INSTANCE = new ToastManager();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<AppToast> pendingToasts = new ArrayDeque<>();

    private AppToast activeToast;
    private View activeToastView;
    private OverlayContainer activeContainer;
    private Activity activeActivity;
    private long activeDeadline;
    private Runnable timeoutRunnable;
    private String lastEnqueuedMessage;
    private long lastEnqueuedAt;
    private boolean initialized;

    private ToastManager() {
    }

    public static ToastManager getInstance() {
        return INSTANCE;
    }

    public void enqueue(final Context context, final AppToast toast) {
        runOnMain(new Runnable() {
            @Override
            public void run() {
                initialize(context);

                String message = toast.getMessage().toString();
                long now = SystemClock.uptimeMillis();
                if (isDuplicate(message, now)) {
                    return;
                }

                pendingToasts.addLast(toast);
                lastEnqueuedMessage = message;
                lastEnqueuedAt = now;
                showNextIfPossible();
            }
        });
    }

    public void dismiss(final AppToast toast) {
        runOnMain(new Runnable() {
            @Override
            public void run() {
                if (activeToast == toast) {
                    finishActiveToast();
                }
                else {
                    pendingToasts.remove(toast);
                }
            }
        });
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (activeToast != null) {
            if (activeDeadline <= SystemClock.uptimeMillis()) {
                finishActiveToast();
            }
            else if (activeActivity != activity || activeToastView == null) {
                attachActiveToast(activity);
            }
            else if (activeContainer != null) {
                activeContainer.bringToFront();
            }
        }
        showNextIfPossible();
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (activeActivity == activity) {
            detachActiveToastView();
        }
    }

    private void initialize(Context context) {
        if (initialized) {
            return;
        }
        OverlayManager overlayManager = OverlayManager.getInstance();
        overlayManager.initialize(context);
        overlayManager.addLifecycleListener(this);
        initialized = true;
    }

    private void showNextIfPossible() {
        if (activeToast != null) {
            return;
        }

        Activity activity = OverlayManager.getInstance().getResumedActivity();
        if (activity == null && activeToast != null) {
            activity = findActivity(activeToast.getContext());
        }
        if (activity == null) {
            return;
        }

        activeToast = pendingToasts.pollFirst();
        if (activeToast == null) {
            return;
        }
        activeDeadline = SystemClock.uptimeMillis()
                + (activeToast.getDuration() == AppToast.LENGTH_LONG
                ? LONG_DURATION_MS : SHORT_DURATION_MS);
        attachActiveToast(activity);
    }

    private void attachActiveToast(Activity activity) {
        if (activeToast == null || !OverlayManager.isUsable(activity)) {
            return;
        }

        if (activeDeadline <= SystemClock.uptimeMillis()) {
            finishActiveToast();
            return;
        }

        detachActiveToastView();
        OverlayContainer container = OverlayManager.getInstance().getContainer(activity);
        if (container == null) {
            scheduleAttachRetry(activity);
            return;
        }

        final AppToast toast = activeToast;
        final View toastView = LayoutInflater.from(activity).inflate(R.layout.app_toast, null, false);
        TextView messageView = toastView.findViewById(R.id.appToastMessage);
        messageView.setText(toast.getMessage());

        final TextView copyHint = toastView.findViewById(R.id.appToastCopyHint);
        boolean debugMode = PreferenceConfiguration.isDebugToastEnabled(activity);
        copyHint.setVisibility(debugMode ? View.VISIBLE : View.GONE);

        if (debugMode) {
            toastView.setClickable(true);
            toastView.setFocusable(true);
            toastView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    copyDiagnostics(activity, toast, copyHint);
                }
            });
        }
        else {
            toastView.setClickable(false);
            toastView.setFocusable(false);
            toastView.setOnClickListener(null);
        }

        container.setToastView(toastView);
        activeToastView = toastView;
        activeContainer = container;
        activeActivity = activity;
        scheduleTimeout();
    }

    private void scheduleAttachRetry(final Activity activity) {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (activeToast != null && activeToastView == null
                        && OverlayManager.isUsable(activity)) {
                    attachActiveToast(activity);
                }
            }
        }, 120L);
    }

    private void scheduleTimeout() {
        cancelTimeout();
        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (activeToast == null) {
                    return;
                }
                long remaining = activeDeadline - SystemClock.uptimeMillis();
                if (remaining <= 0) {
                    finishActiveToast();
                }
                else {
                    mainHandler.postDelayed(this, remaining);
                }
            }
        };
        mainHandler.postDelayed(timeoutRunnable,
                Math.max(1L, activeDeadline - SystemClock.uptimeMillis()));
    }

    private void finishActiveToast() {
        cancelTimeout();
        detachActiveToastView();
        activeToast = null;
        activeDeadline = 0L;
        showNextIfPossible();
    }

    private void detachActiveToastView() {
        if (activeContainer != null) {
            activeContainer.clearToastView(activeToastView);
        }
        activeToastView = null;
        activeContainer = null;
        activeActivity = null;
    }

    private void copyDiagnostics(Activity activity, AppToast toast, TextView copyHint) {
        ClipboardManager clipboardManager = (ClipboardManager) activity.getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null) {
            LimeLog.warning("Unable to copy in-app toast diagnostics: clipboard service unavailable");
            return;
        }

        clipboardManager.setPrimaryClip(ClipData.newPlainText(
                activity.getString(R.string.app_toast_clipboard_label),
                buildDiagnostics(activity, toast)));
        copyHint.setText(R.string.app_toast_copied);
        LimeLog.info("Copied in-app toast diagnostics");

        activeDeadline = SystemClock.uptimeMillis() + COPIED_DURATION_MS;
        scheduleTimeout();
    }

    private String buildDiagnostics(Activity activity, AppToast toast) {
        StringBuilder diagnostics = new StringBuilder();
        diagnostics.append("Moonlight in-app toast diagnostics\n");
        diagnostics.append("message: ").append(toast.getMessage()).append('\n');
        diagnostics.append("activity: ").append(activity.getClass().getName()).append('\n');
        diagnostics.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append('\n');
        diagnostics.append("android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n\n");
        diagnostics.append("recent logs:\n").append(LimeLog.getRecentLogs());
        return diagnostics.toString();
    }

    private boolean isDuplicate(String message, long now) {
        if (message.equals(lastEnqueuedMessage)
                && now - lastEnqueuedAt < DEDUPLICATION_WINDOW_MS) {
            return true;
        }
        return activeToast != null && message.contentEquals(activeToast.getMessage())
                && now - lastEnqueuedAt < DEDUPLICATION_WINDOW_MS;
    }

    private void cancelTimeout() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    private void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        }
        else {
            mainHandler.post(runnable);
        }
    }

    private static Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            Context base = ((ContextWrapper) current).getBaseContext();
            if (base == current) {
                break;
            }
            current = base;
        }
        return current instanceof Activity ? (Activity) current : null;
    }
}
