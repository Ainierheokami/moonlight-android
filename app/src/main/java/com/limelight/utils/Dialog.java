package com.limelight.utils;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import com.limelight.R;

public class Dialog implements Runnable {
    private final String title;
    private final String message;
    private final Activity activity;
    private final Runnable runOnDismiss;

    private AlertDialog alert;

    private static final ArrayList<Dialog> rundownDialogs = new ArrayList<>();

    private Dialog(Activity activity, String title, String message, Runnable runOnDismiss)
    {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.runOnDismiss = runOnDismiss;
    }

    public static void closeDialogs()
    {
        synchronized (rundownDialogs) {
            for (Dialog d : rundownDialogs) {
                if (d.alert.isShowing()) {
                    d.alert.dismiss();
                }
            }

            rundownDialogs.clear();
        }
    }

    public static void displayDialog(final Activity activity, String title, String message, final boolean endAfterDismiss)
    {
        activity.runOnUiThread(new Dialog(activity, title, message, new Runnable() {
            @Override
            public void run() {
                if (endAfterDismiss) {
                    activity.finish();
                }
            }
        }));
    }

    public static void displayDialog(Activity activity, String title, String message, Runnable runOnDismiss)
    {
        activity.runOnUiThread(new Dialog(activity, title, message, runOnDismiss));
    }

    @Override
    public void run() {
        // If we're dying, don't bother creating a dialog
        if (activity.isFinishing())
            return;

        alert = new AlertDialog.Builder(activity).create();

        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_modern_message, null);
        ((TextView) content.findViewById(R.id.dialogTitleText)).setText(title);
        ((TextView) content.findViewById(R.id.dialogMessageText)).setText(message);

        Button okButton = content.findViewById(R.id.dialogOkButton);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                synchronized (rundownDialogs) {
                    rundownDialogs.remove(Dialog.this);
                    alert.dismiss();
                }

                runOnDismiss.run();
            }
        });

        Button helpButton = content.findViewById(R.id.dialogHelpButton);
        helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                synchronized (rundownDialogs) {
                    rundownDialogs.remove(Dialog.this);
                    alert.dismiss();
                }

                runOnDismiss.run();

                HelpLauncher.launchTroubleshooting(activity);
            }
        });

        alert.setView(content);
        alert.setCancelable(false);
        alert.setCanceledOnTouchOutside(false);

        synchronized (rundownDialogs) {
            rundownDialogs.add(this);
            alert.show();
        }

        Window window = alert.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        okButton.setFocusable(true);
        okButton.setFocusableInTouchMode(true);
        okButton.requestFocus();
    }
}
