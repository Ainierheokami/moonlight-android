package com.limelight.utils;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.limelight.R;

import java.util.ArrayList;
import java.util.Iterator;

/** App-owned message dialog rendered in the Activity overlay. */
public class Dialog implements Runnable {
    private final String title;
    private final String message;
    private final Activity activity;
    private final Runnable runOnDismiss;

    private OverlayContainer.DialogHandle overlayHandle;

    private static final ArrayList<Dialog> rundownDialogs = new ArrayList<>();
    private static boolean lifecycleCallbacksRegistered;
    private static final OverlayManager.LifecycleListener LIFECYCLE_LISTENER =
            new OverlayManager.LifecycleListener() {
                @Override
                public void onActivityResumed(Activity activity) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    synchronized (rundownDialogs) {
                        Iterator<Dialog> iterator = rundownDialogs.iterator();
                        while (iterator.hasNext()) {
                            Dialog dialog = iterator.next();
                            if (dialog.activity == activity) {
                                dialog.overlayHandle = null;
                                iterator.remove();
                            }
                        }
                    }
                }
            };

    private Dialog(Activity activity, String title, String message, Runnable runOnDismiss) {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.runOnDismiss = runOnDismiss;
    }

    public static void closeDialogs() {
        final ArrayList<Dialog> dialogs;
        synchronized (rundownDialogs) {
            dialogs = new ArrayList<>(rundownDialogs);
            rundownDialogs.clear();
        }

        for (final Dialog dialog : dialogs) {
            dialog.activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dialog.dismissInternal();
                }
            });
        }
    }

    public static void displayDialog(final Activity activity, String title, String message,
                                     final boolean endAfterDismiss) {
        activity.runOnUiThread(new Dialog(activity, title, message, new Runnable() {
            @Override
            public void run() {
                if (endAfterDismiss) {
                    activity.finish();
                }
            }
        }));
    }

    public static void displayDialog(Activity activity, String title, String message,
                                     Runnable runOnDismiss) {
        activity.runOnUiThread(new Dialog(activity, title, message, runOnDismiss));
    }

    @Override
    public void run() {
        if (!OverlayManager.isUsable(activity)) {
            return;
        }

        registerLifecycleCallbacks(activity);

        View content = LayoutInflater.from(activity).inflate(
                R.layout.dialog_modern_message, null, false);
        ((TextView) content.findViewById(R.id.dialogTitleText)).setText(title);
        ((TextView) content.findViewById(R.id.dialogMessageText)).setText(message);

        Button okButton = content.findViewById(R.id.dialogOkButton);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismissInternal();
                if (runOnDismiss != null) {
                    runOnDismiss.run();
                }
            }
        });

        Button helpButton = content.findViewById(R.id.dialogHelpButton);
        helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismissInternal();
                if (runOnDismiss != null) {
                    runOnDismiss.run();
                }
                HelpLauncher.launchTroubleshooting(activity);
            }
        });

        overlayHandle = OverlayManager.getInstance().showDialog(
                activity, content, 560, false, null);
        if (overlayHandle == null) {
            return;
        }

        synchronized (rundownDialogs) {
            rundownDialogs.add(this);
        }

        okButton.setFocusable(true);
        okButton.setFocusableInTouchMode(true);
        okButton.requestFocus();
    }

    private void dismissInternal() {
        OverlayContainer.DialogHandle handle = overlayHandle;
        overlayHandle = null;
        if (handle != null) {
            handle.remove();
        }
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
