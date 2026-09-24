package com.limelight.utils;

import android.app.Activity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;

/** Small action-dialog helper that keeps dialogs in the Activity overlay hierarchy. */
public final class OverlayDialog {
    public interface ItemClickListener {
        void onItemClick(int which);
    }

    private OverlayDialog() {
    }

    public static OverlayContainer.DialogHandle show(
            Activity activity,
            CharSequence title,
            CharSequence message,
            CharSequence negativeText,
            Runnable negativeAction,
            CharSequence neutralText,
            Runnable neutralAction,
            CharSequence positiveText,
            Runnable positiveAction,
            boolean cancelable) {
        if (!OverlayManager.isUsable(activity)) {
            return null;
        }

        OverlayManager overlayManager = OverlayManager.getInstance();
        overlayManager.initialize(activity);

        View content = LayoutInflater.from(activity).inflate(
                R.layout.dialog_modern_message, null, false);
        ((TextView) content.findViewById(R.id.dialogTitleText)).setText(title);
        ((TextView) content.findViewById(R.id.dialogMessageText)).setText(message);

        final Button negativeButton = content.findViewById(R.id.dialogHelpButton);
        final Button neutralButton = content.findViewById(R.id.dialogNeutralButton);
        final Button positiveButton = content.findViewById(R.id.dialogOkButton);
        final LinearLayout buttonRow = content.findViewById(R.id.dialogButtonRow);
        final OverlayContainer.DialogHandle[] handle = new OverlayContainer.DialogHandle[1];

        configureButton(negativeButton, negativeText, negativeAction, handle);
        configureButton(neutralButton, neutralText, neutralAction, handle);
        configureButton(positiveButton, positiveText, positiveAction, handle);
        buttonRow.setVisibility(
                hasText(negativeText) || hasText(neutralText) || hasText(positiveText)
                        ? View.VISIBLE : View.GONE);

        Runnable dismiss = new Runnable() {
            @Override
            public void run() {
                if (handle[0] != null) {
                    handle[0].remove();
                }
            }
        };
        handle[0] = overlayManager.showDialog(activity, content, 560, cancelable, dismiss);
        return handle[0];
    }

    public static OverlayContainer.DialogHandle showCustom(
            Activity activity,
            View content,
            int maxWidthDp,
            int maxHeightDp,
            boolean cancelable,
            boolean cancelOnTouchOutside,
            Runnable onDismiss) {
        if (!OverlayManager.isUsable(activity) || content == null) {
            return null;
        }

        OverlayManager overlayManager = OverlayManager.getInstance();
        overlayManager.initialize(activity);
        return overlayManager.showDialog(activity, content, maxWidthDp, maxHeightDp,
                cancelable, cancelOnTouchOutside, onDismiss);
    }

    /** Shows a small, app-styled action list without creating an Android Dialog window. */
    public static OverlayContainer.DialogHandle showList(
            Activity activity,
            CharSequence title,
            CharSequence[] items,
            final ItemClickListener listener) {
        if (!OverlayManager.isUsable(activity) || items == null || items.length == 0) {
            return null;
        }

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.modern_dialog_background);
        int horizontalPadding = dp(activity, 20);
        content.setPadding(horizontalPadding, dp(activity, 20), horizontalPadding,
                dp(activity, 16));

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextColor(0xFFF5F8FC);
        titleView.setTextSize(20);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout itemList = new LinearLayout(activity);
        itemList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams itemListParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        itemListParams.topMargin = dp(activity, 14);
        content.addView(itemList, itemListParams);

        final OverlayContainer.DialogHandle[] handle = new OverlayContainer.DialogHandle[1];
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            Button itemButton = new Button(activity);
            itemButton.setAllCaps(false);
            itemButton.setText(items[i]);
            itemButton.setTextColor(0xFFDCE6F1);
            itemButton.setTextSize(14);
            itemButton.setGravity(android.view.Gravity.CENTER_VERTICAL
                    | android.view.Gravity.START);
            itemButton.setMinHeight(0);
            itemButton.setMinWidth(0);
            itemButton.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
            itemButton.setBackgroundResource(R.drawable.pc_action_button_background);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                itemButton.setStateListAnimator(null);
            }
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 48));
            if (i > 0) {
                itemParams.topMargin = dp(activity, 8);
            }
            itemList.addView(itemButton, itemParams);
            itemButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (handle[0] != null) {
                        handle[0].remove();
                    }
                    if (listener != null) {
                        listener.onItemClick(index);
                    }
                }
            });
        }

        Runnable dismiss = new Runnable() {
            @Override
            public void run() {
                if (handle[0] != null) {
                    handle[0].remove();
                }
            }
        };
        handle[0] = showCustom(activity, content, 560, 0, true, true, dismiss);
        return handle[0];
    }

    private static void configureButton(
            Button button,
            CharSequence text,
            final Runnable action,
            final OverlayContainer.DialogHandle[] handle) {
        if (!hasText(text)) {
            button.setVisibility(View.GONE);
            button.setOnClickListener(null);
            return;
        }

        button.setVisibility(View.VISIBLE);
        button.setText(text);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (handle[0] != null) {
                    handle[0].remove();
                }
                if (action != null) {
                    action.run();
                }
            }
        });
    }

    private static boolean hasText(CharSequence text) {
        return !TextUtils.isEmpty(text);
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
