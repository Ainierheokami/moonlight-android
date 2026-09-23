package com.limelight.utils;

import android.app.Activity;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;

import java.lang.ref.WeakReference;

/**
 * An in-app toast that stays readable without intercepting touches in normal mode.
 * Debug mode makes the whole toast clickable to copy the message with recent logs.
 */
public final class AppToast {
    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;

    private static final long SHORT_DURATION_MS = 3500L;
    private static final long LONG_DURATION_MS = 6500L;
    private static final long COPIED_DURATION_MS = 1800L;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static WeakReference<Activity> lastActivity;
    private static AppToast activeToast;
    private static boolean lifecycleCallbacksRegistered;

    private static final Application.ActivityLifecycleCallbacks ACTIVITY_LIFECYCLE_CALLBACKS =
            new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityResumed(Activity activity) {
                    if (!isUsable(activity)) {
                        return;
                    }

                    lastActivity = new WeakReference<>(activity);
                    if (activeToast != null) {
                        activeToast.attachToActivity(activity);
                    }
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    if (lastActivity != null && lastActivity.get() == activity) {
                        lastActivity = null;
                    }
                    if (activeToast != null && activeToast.hostActivity == activity) {
                        activeToast.detachWindow();
                    }
                }

                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                }

                @Override
                public void onActivityStarted(Activity activity) {
                }

                @Override
                public void onActivityPaused(Activity activity) {
                }

                @Override
                public void onActivityStopped(Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                }
            };

    private final Context context;
    private final CharSequence message;
    private final int duration;

    private View toastView;
    private View windowView;
    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private Activity hostActivity;
    private Runnable dismissRunnable;

    private AppToast(Context context, CharSequence message, int duration) {
        this.context = context;
        this.message = message;
        this.duration = duration;
    }

    public static AppToast makeText(Context context, CharSequence message, int duration) {
        return new AppToast(context, message, duration);
    }

    public static AppToast makeText(Context context, int messageResId, int duration) {
        return makeText(context, context.getString(messageResId), duration);
    }

    public void show() {
        final Activity activity = findUsableActivity(context);
        if (activity == null) {
            LimeLog.warning("Unable to show in-app toast without an active Activity: " + message);
            return;
        }

        registerActivityLifecycleCallbacks(activity);

        Runnable showRunnable = new Runnable() {
            @Override
            public void run() {
                showOnActivity(activity);
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            showRunnable.run();
        }
        else {
            activity.runOnUiThread(showRunnable);
        }
    }

    private void showOnActivity(final Activity activity) {
        if (!isUsable(activity)) {
            return;
        }

        dismissActiveToast();
        activeToast = this;
        attachToActivity(activity);

        dismissRunnable = new Runnable() {
            @Override
            public void run() {
                if (activeToast == AppToast.this) {
                    dismiss();
                }
            }
        };
        MAIN_HANDLER.postDelayed(dismissRunnable,
                duration == LENGTH_LONG ? LONG_DURATION_MS : SHORT_DURATION_MS);
    }

    /**
     * Attach the toast as an Activity sub-panel so it can appear above app dialogs.
     */
    private void attachToActivity(final Activity activity) {
        if (activeToast != this || !isUsable(activity)) {
            return;
        }

        if (hostActivity == activity && windowView != null && windowManager != null) {
            bringWindowToFront();
            return;
        }

        IBinder activityWindowToken = activity.getWindow().getDecorView().getWindowToken();
        if (activityWindowToken == null) {
            LimeLog.warning("Unable to attach in-app toast because the Activity window is not attached");
            detachWindow();
            retryAttach(activity);
            return;
        }

        detachWindow();
        hostActivity = activity;

        FrameLayout windowContent = new FrameLayout(activity);
        windowContent.setClipChildren(false);
        int horizontalMargin = dp(activity, 16);
        windowContent.setPadding(horizontalMargin, 0, horizontalMargin, 0);

        toastView = LayoutInflater.from(activity).inflate(R.layout.app_toast, windowContent, false);
        FrameLayout.LayoutParams toastLayoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL);
        windowContent.addView(toastView, toastLayoutParams);

        boolean debugMode = PreferenceConfiguration.isDebugToastEnabled(activity);

        TextView messageView = toastView.findViewById(R.id.appToastMessage);
        messageView.setText(message);

        TextView copyHint = toastView.findViewById(R.id.appToastCopyHint);
        copyHint.setVisibility(debugMode ? View.VISIBLE : View.GONE);

        View.OnClickListener copyListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyDiagnostics(activity, copyHint);
            }
        };
        toastView.setClickable(debugMode);
        toastView.setFocusable(debugMode);
        toastView.setOnClickListener(debugMode ? copyListener : null);

        int windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        if (!debugMode) {
            windowFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
                windowFlags,
                PixelFormat.TRANSLUCENT);
        layoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        layoutParams.y = dp(activity, 48);
        // A sub-panel is layered above its attached Activity window, keeping the toast
        // visible over normal application-layer dialogs without leaving this app.
        layoutParams.token = activityWindowToken;
        layoutParams.packageName = activity.getPackageName();
        layoutParams.setTitle("Moonlight AppToast");

        windowView = windowContent;
        windowManager = activity.getWindowManager();
        windowParams = layoutParams;

        try {
            windowManager.addView(windowView, windowParams);
        }
        catch (RuntimeException e) {
            LimeLog.warning("Unable to attach in-app toast window: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            detachWindow();
            retryAttach(activity);
        }
    }

    private void retryAttach(final Activity activity) {
        MAIN_HANDLER.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (activeToast == AppToast.this && windowView == null && isUsable(activity)) {
                    attachToActivity(activity);
                }
            }
        }, 150L);
    }

    private void bringWindowToFront() {
        if (windowManager == null || windowView == null || windowParams == null) {
            return;
        }

        try {
            windowManager.removeViewImmediate(windowView);
            windowManager.addView(windowView, windowParams);
        }
        catch (RuntimeException e) {
            LimeLog.warning("Unable to raise in-app toast window: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            detachWindow();
        }
    }

    private void copyDiagnostics(Activity activity, TextView copyHint) {
        ClipboardManager clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null) {
            LimeLog.warning("Unable to copy in-app toast diagnostics: clipboard service unavailable");
            return;
        }

        clipboardManager.setPrimaryClip(ClipData.newPlainText(
                activity.getString(R.string.app_toast_clipboard_label), buildDiagnostics(activity)));
        copyHint.setText(R.string.app_toast_copied);
        LimeLog.info("Copied in-app toast diagnostics");

        if (dismissRunnable != null) {
            MAIN_HANDLER.removeCallbacks(dismissRunnable);
        }
        dismissRunnable = new Runnable() {
            @Override
            public void run() {
                if (activeToast == AppToast.this) {
                    dismiss();
                }
            }
        };
        MAIN_HANDLER.postDelayed(dismissRunnable, COPIED_DURATION_MS);
    }

    private String buildDiagnostics(Activity activity) {
        StringBuilder diagnostics = new StringBuilder();
        diagnostics.append("Moonlight in-app toast diagnostics\n");
        diagnostics.append("message: ").append(message).append('\n');
        diagnostics.append("activity: ").append(activity.getClass().getName()).append('\n');
        diagnostics.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        diagnostics.append("android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n\n");
        diagnostics.append("recent logs:\n").append(LimeLog.getRecentLogs());
        return diagnostics.toString();
    }

    public void dismiss() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissInternal();
        }
        else {
            MAIN_HANDLER.post(new Runnable() {
                @Override
                public void run() {
                    dismissInternal();
                }
            });
        }
    }

    private void dismissInternal() {
        if (dismissRunnable != null) {
            MAIN_HANDLER.removeCallbacks(dismissRunnable);
            dismissRunnable = null;
        }
        detachWindow();
        if (activeToast == this) {
            activeToast = null;
        }
    }

    private void detachWindow() {
        if (windowManager != null && windowView != null) {
            try {
                windowManager.removeViewImmediate(windowView);
            }
            catch (RuntimeException ignored) {
                // The Activity may already have removed its windows during destruction.
            }
        }
        windowView = null;
        windowManager = null;
        windowParams = null;
        toastView = null;
        hostActivity = null;
    }

    private static void dismissActiveToast() {
        if (activeToast != null) {
            activeToast.dismissInternal();
        }
    }

    private static Activity findUsableActivity(Context context) {
        Activity activity = findActivity(context);
        if (!isUsable(activity)) {
            activity = lastActivity == null ? null : lastActivity.get();
        }

        if (isUsable(activity)) {
            lastActivity = new WeakReference<>(activity);
            return activity;
        }
        return null;
    }

    private static synchronized void registerActivityLifecycleCallbacks(Activity activity) {
        if (!lifecycleCallbacksRegistered) {
            activity.getApplication().registerActivityLifecycleCallbacks(ACTIVITY_LIFECYCLE_CALLBACKS);
            lifecycleCallbacksRegistered = true;
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

    private static boolean isUsable(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
