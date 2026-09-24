package com.limelight.utils;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.limelight.R;

import java.util.ArrayList;
import java.util.Iterator;

/** App-owned progress dialog rendered in the Activity overlay. */
public class SpinnerDialog implements Runnable {
    private final String title;
    private final String message;
    private final Activity activity;
    private final boolean finish;

    private OverlayContainer.DialogHandle overlayHandle;
    private TextView messageText;

    private static final ArrayList<SpinnerDialog> rundownDialogs = new ArrayList<>();
    private static boolean lifecycleCallbacksRegistered;
    private static final OverlayManager.LifecycleListener LIFECYCLE_LISTENER =
            new OverlayManager.LifecycleListener() {
                @Override
                public void onActivityResumed(Activity activity) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    synchronized (rundownDialogs) {
                        Iterator<SpinnerDialog> iterator = rundownDialogs.iterator();
                        while (iterator.hasNext()) {
                            SpinnerDialog dialog = iterator.next();
                            if (dialog.activity == activity) {
                                dialog.overlayHandle = null;
                                dialog.messageText = null;
                                iterator.remove();
                            }
                        }
                    }
                }
            };

    private SpinnerDialog(Activity activity, String title, String message, boolean finish) {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.finish = finish;
    }

    public static SpinnerDialog displayDialog(Activity activity, String title, String message,
                                              boolean finish) {
        SpinnerDialog spinner = new SpinnerDialog(activity, title, message, finish);
        activity.runOnUiThread(spinner);
        return spinner;
    }

    public static void closeDialogs(final Activity activity) {
        final ArrayList<SpinnerDialog> dialogs = new ArrayList<>();
        synchronized (rundownDialogs) {
            Iterator<SpinnerDialog> iterator = rundownDialogs.iterator();
            while (iterator.hasNext()) {
                SpinnerDialog dialog = iterator.next();
                if (dialog.activity == activity) {
                    dialogs.add(dialog);
                    iterator.remove();
                }
            }
        }

        for (final SpinnerDialog dialog : dialogs) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dialog.dismissInternal();
                }
            });
        }
    }

    public void dismiss() {
        activity.runOnUiThread(this);
    }

    public void setMessage(final String message) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (messageText != null) {
                    messageText.setText(message);
                }
            }
        });
    }

    @Override
    public void run() {
        if (!OverlayManager.isUsable(activity)) {
            return;
        }

        registerLifecycleCallbacks(activity);

        if (overlayHandle == null) {
            View content = LayoutInflater.from(activity).inflate(
                    R.layout.dialog_modern_spinner, null, false);
            ((TextView) content.findViewById(R.id.spinnerTitleText)).setText(title);
            messageText = content.findViewById(R.id.spinnerMessageText);
            messageText.setText(message);

            overlayHandle = OverlayManager.getInstance().showDialog(
                    activity,
                    content,
                    520,
                    finish,
                    finish ? new Runnable() {
                        @Override
                        public void run() {
                            dismissInternal();
                            activity.finish();
                        }
                    } : null);
            if (overlayHandle != null) {
                synchronized (rundownDialogs) {
                    rundownDialogs.add(this);
                }
            }
        }
        else {
            dismissInternal();
        }
    }

    private void dismissInternal() {
        OverlayContainer.DialogHandle handle = overlayHandle;
        overlayHandle = null;
        if (handle != null) {
            handle.remove();
        }
        messageText = null;
        synchronized (rundownDialogs) {
            rundownDialogs.remove(this);
        }
    }

    private static void registerLifecycleCallbacks(Activity activity) {
        OverlayManager overlayManager = OverlayManager.getInstance();
        overlayManager.initialize(activity);
        if (!lifecycleCallbacksRegistered) {
            overlayManager.addLifecycleListener(LIFECYCLE_LISTENER);
            lifecycleCallbacksRegistered = true;
        }
    }
}
