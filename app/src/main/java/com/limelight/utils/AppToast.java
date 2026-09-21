package com.limelight.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import com.limelight.LimeLog;
import com.limelight.R;

import java.lang.ref.WeakReference;

/**
 * An in-app toast that stays readable and can copy the message with recent logs.
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

    private final Context context;
    private final CharSequence message;
    private final int duration;

    private View toastView;
    private ViewGroup host;
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
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        View contentView = activity.findViewById(android.R.id.content);
        if (!(contentView instanceof ViewGroup)) {
            LimeLog.warning("Unable to show in-app toast because the Activity content is not a ViewGroup");
            return;
        }

        dismissActiveToast();

        host = (ViewGroup) contentView;
        toastView = LayoutInflater.from(activity).inflate(R.layout.app_toast, host, false);
        TextView messageView = toastView.findViewById(R.id.appToastMessage);
        messageView.setText(message);

        final Button copyButton = toastView.findViewById(R.id.appToastCopyButton);
        final ImageButton dismissButton = toastView.findViewById(R.id.appToastDismissButton);

        View.OnClickListener copyListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyDiagnostics(activity, copyButton);
            }
        };
        toastView.setOnClickListener(copyListener);
        copyButton.setOnClickListener(copyListener);
        dismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });

        if (host instanceof FrameLayout) {
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM);
            int horizontalMargin = dp(activity, 14);
            layoutParams.setMargins(horizontalMargin, 0, horizontalMargin, dp(activity, 32));
            host.addView(toastView, layoutParams);
        }
        else {
            host.addView(toastView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        activeToast = this;
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

    private void copyDiagnostics(Activity activity, Button copyButton) {
        ClipboardManager clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null) {
            LimeLog.warning("Unable to copy in-app toast diagnostics: clipboard service unavailable");
            return;
        }

        clipboardManager.setPrimaryClip(ClipData.newPlainText(
                activity.getString(R.string.app_toast_clipboard_label), buildDiagnostics(activity)));
        copyButton.setText(R.string.app_toast_copied);
        copyButton.setEnabled(false);
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
        if (toastView != null && host != null) {
            host.removeView(toastView);
        }
        toastView = null;
        host = null;
        if (activeToast == this) {
            activeToast = null;
        }
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
