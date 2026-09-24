package com.limelight.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.limelight.R;

/**
 * An AlertDialog-shaped API backed by the Activity's {@link OverlayContainer}.
 *
 * <p>Android's {@code AlertDialog} owns a separate application window. That makes it impossible
 * for an in-Activity toast to reliably appear above it. This class intentionally provides the
 * small subset of the AlertDialog API used by Moonlight while keeping the dialog content and the
 * toast content in one overlay hierarchy.</p>
 */
public final class OverlayAlertDialog implements DialogInterface {
    public static final int BUTTON_POSITIVE = -1;
    public static final int BUTTON_NEGATIVE = -2;
    public static final int BUTTON_NEUTRAL = -3;

    private static final int MAX_WIDTH_DP = 560;
    private static final int MAX_HEIGHT_DP = 760;
    private static final int CARD_HORIZONTAL_PADDING_DP = 22;
    private static final int CARD_VERTICAL_PADDING_DP = 20;

    private final Builder builder;
    private final Context sourceContext;
    private final Activity activity;
    private final Button negativeButton;
    private final Button neutralButton;
    private final Button positiveButton;

    private OverlayContainer.DialogHandle handle;
    private View cardView;
    private ListView listView;
    private boolean showing;
    private boolean dismissNotified;

    private OverlayAlertDialog(Builder builder) {
        this.builder = builder;
        this.sourceContext = builder.context;
        this.activity = OverlayManager.getInstance().resolveActivity(sourceContext);

        negativeButton = createButton(false);
        neutralButton = createButton(false);
        positiveButton = createButton(true);
    }

    public static Builder builder(Context context) {
        return new Builder(context);
    }

    public void show() {
        if (showing || activity == null || !OverlayManager.isUsable(activity)) {
            return;
        }

        if (LooperGuard.isMainThread()) {
            showOnMainThread();
        }
        else {
            activity.runOnUiThread(this::showOnMainThread);
        }
    }

    private void showOnMainThread() {
        if (showing || !OverlayManager.isUsable(activity)) {
            return;
        }

        cardView = buildCardView();
        OverlayManager overlayManager = OverlayManager.getInstance();
        overlayManager.initialize(activity);
        handle = overlayManager.showDialog(
                activity,
                cardView,
                MAX_WIDTH_DP,
                MAX_HEIGHT_DP,
                builder.cancelable,
                builder.canceledOnTouchOutside,
                this::cancel);
        showing = handle != null;
        if (showing && builder.onShowListener != null) {
            builder.onShowListener.onShow(this);
        }
    }

    private View buildCardView() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.modern_dialog_background);
        int horizontalPadding = dp(CARD_HORIZONTAL_PADDING_DP);
        card.setPadding(horizontalPadding, dp(CARD_VERTICAL_PADDING_DP), horizontalPadding,
                dp(16));

        if (!TextUtils.isEmpty(builder.title)) {
            TextView title = new TextView(activity);
            title.setText(builder.title);
            title.setTextColor(Color.rgb(245, 248, 252));
            title.setTextSize(20);
            title.setTypeface(null, Typeface.BOLD);
            card.addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        if (!TextUtils.isEmpty(builder.message)) {
            TextView message = new TextView(activity);
            message.setText(builder.message);
            message.setTextColor(Color.rgb(184, 196, 210));
            message.setTextSize(15);
            message.setLineSpacing(dp(2), 1.0f);
            LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            messageParams.topMargin = dp(12);
            card.addView(message, messageParams);
        }

        if (builder.customView != null) {
            addCustomView(card, builder.customView);
        }
        else if (builder.items != null) {
            addListView(card);
        }

        LinearLayout buttonRow = new LinearLayout(activity);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        boolean hasButtons = builder.negativeText != null || builder.neutralText != null
                || builder.positiveText != null;
        if (hasButtons) {
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(22);
            card.addView(buttonRow, rowParams);

            configureButton(buttonRow, negativeButton, builder.negativeText,
                    builder.negativeListener, BUTTON_NEGATIVE, false);
            configureButton(buttonRow, neutralButton, builder.neutralText,
                    builder.neutralListener, BUTTON_NEUTRAL, false);
            configureButton(buttonRow, positiveButton, builder.positiveText,
                    builder.positiveListener, BUTTON_POSITIVE, true);
        }

        return card;
    }

    private void addCustomView(LinearLayout card, View customView) {
        if (customView.getParent() != null) {
            throw new IllegalStateException("Custom dialog view already has a parent");
        }
        LinearLayout.LayoutParams viewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        viewParams.topMargin = dp(TextUtils.isEmpty(builder.title)
                && TextUtils.isEmpty(builder.message) ? 0 : 12);
        card.addView(customView, viewParams);
    }

    private void addListView(LinearLayout card) {
        listView = new ListView(activity);
        listView.setDivider(new ColorDrawable(Color.TRANSPARENT));
        listView.setDividerHeight(dp(4));
        listView.setPadding(0, dp(8), 0, 0);
        listView.setClipToPadding(false);
        listView.setAdapter(new ArrayAdapter<CharSequence>(activity,
                android.R.layout.simple_list_item_1, builder.items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.rgb(220, 230, 241));
                view.setTextSize(15);
                view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                view.setMinHeight(dp(48));
                view.setPadding(dp(16), 0, dp(16), 0);
                view.setBackgroundResource(R.drawable.pc_action_button_background);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    view.setStateListAnimator(null);
                }
                return view;
            }
        });
        if (builder.singleChoice) {
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            if (builder.checkedItem >= 0 && builder.checkedItem < builder.items.length) {
                listView.setItemChecked(builder.checkedItem, true);
            }
        }
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (builder.itemListener == null) {
                dismiss();
                return;
            }
            if (!builder.singleChoice) {
                dismiss();
            }
            builder.itemListener.onClick(this, position);
        });
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        listParams.topMargin = dp(TextUtils.isEmpty(builder.title)
                && TextUtils.isEmpty(builder.message) ? 0 : 12);
        listParams.height = dp(Math.min(420, Math.max(120, builder.items.length * 52)));
        card.addView(listView, listParams);
    }

    private void configureButton(LinearLayout row, Button button, CharSequence text,
                                 DialogInterface.OnClickListener listener, int which,
                                 boolean primary) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        button.setText(text);
        button.setOnClickListener(view -> {
            dismiss();
            if (listener != null) {
                listener.onClick(this, which);
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        if (row.getChildCount() > 0) {
            params.leftMargin = dp(10);
        }
        row.addView(button, params);
    }

    private Button createButton(boolean primary) {
        Button button = new Button(activity == null ? sourceContext : activity);
        button.setAllCaps(false);
        button.setMinWidth(dp(CARD_HORIZONTAL_PADDING_DP * 4));
        button.setMinHeight(0);
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setTextSize(14);
        button.setTypeface(null, Typeface.BOLD);
        button.setStateListAnimator(null);
        button.setBackgroundResource(primary
                ? R.drawable.modern_dialog_primary_button_background
                : R.drawable.modern_dialog_secondary_button_background);
        return button;
    }

    public Button getButton(int whichButton) {
        switch (whichButton) {
            case BUTTON_POSITIVE:
                return positiveButton;
            case BUTTON_NEGATIVE:
                return negativeButton;
            case BUTTON_NEUTRAL:
                return neutralButton;
            default:
                return null;
        }
    }

    public ListView getListView() {
        return listView;
    }

    public boolean isShowing() {
        return showing && handle != null && handle.isShowing();
    }

    @Override
    public void dismiss() {
        if (!LooperGuard.isMainThread()) {
            if (activity != null) {
                activity.runOnUiThread(this::dismiss);
            }
            return;
        }
        dismissInternal(true);
    }

    @Override
    public void cancel() {
        if (!builder.cancelable) {
            return;
        }
        if (builder.onCancelListener != null) {
            builder.onCancelListener.onCancel(this);
        }
        dismiss();
    }

    private void dismissInternal(boolean notify) {
        OverlayContainer.DialogHandle currentHandle = handle;
        handle = null;
        showing = false;
        if (currentHandle != null) {
            currentHandle.remove();
        }
        if (notify && !dismissNotified) {
            dismissNotified = true;
            if (builder.onDismissListener != null) {
                builder.onDismissListener.onDismiss(this);
            }
        }
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
        builder.onDismissListener = listener;
    }

    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        builder.onCancelListener = listener;
    }

    private int dp(int value) {
        Context context = activity == null ? sourceContext : activity;
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Fluent builder for the subset of AlertDialog used by this application. */
    public static final class Builder {
        private final Context context;
        private CharSequence title;
        private CharSequence message;
        private View customView;
        private CharSequence[] items;
        private boolean singleChoice;
        private int checkedItem = -1;
        private DialogInterface.OnClickListener itemListener;
        private CharSequence positiveText;
        private CharSequence neutralText;
        private CharSequence negativeText;
        private DialogInterface.OnClickListener positiveListener;
        private DialogInterface.OnClickListener neutralListener;
        private DialogInterface.OnClickListener negativeListener;
        private boolean cancelable = true;
        private boolean canceledOnTouchOutside = true;
        private DialogInterface.OnShowListener onShowListener;
        private DialogInterface.OnDismissListener onDismissListener;
        private DialogInterface.OnCancelListener onCancelListener;

        public Builder(Context context) {
            if (context == null) {
                throw new IllegalArgumentException("context must not be null");
            }
            this.context = context;
        }

        public Builder(Context context, int themeResId) {
            this(context);
        }

        public Builder setTitle(CharSequence title) {
            this.title = title;
            return this;
        }

        public Builder setTitle(int titleId) {
            return setTitle(context.getString(titleId));
        }

        public Builder setMessage(CharSequence message) {
            this.message = message;
            return this;
        }

        public Builder setMessage(int messageId) {
            return setMessage(context.getString(messageId));
        }

        public Builder setView(View view) {
            this.customView = view;
            return this;
        }

        public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener listener) {
            this.items = items;
            this.itemListener = listener;
            this.singleChoice = false;
            return this;
        }

        public Builder setSingleChoiceItems(CharSequence[] items, int checkedItem,
                                            DialogInterface.OnClickListener listener) {
            this.items = items;
            this.checkedItem = checkedItem;
            this.itemListener = listener;
            this.singleChoice = true;
            return this;
        }

        public Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
            positiveText = text;
            positiveListener = listener;
            return this;
        }

        public Builder setPositiveButton(int textId, DialogInterface.OnClickListener listener) {
            return setPositiveButton(context.getString(textId), listener);
        }

        public Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener) {
            neutralText = text;
            neutralListener = listener;
            return this;
        }

        public Builder setNeutralButton(int textId, DialogInterface.OnClickListener listener) {
            return setNeutralButton(context.getString(textId), listener);
        }

        public Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) {
            negativeText = text;
            negativeListener = listener;
            return this;
        }

        public Builder setNegativeButton(int textId, DialogInterface.OnClickListener listener) {
            return setNegativeButton(context.getString(textId), listener);
        }

        public Builder setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            if (!cancelable) {
                this.canceledOnTouchOutside = false;
            }
            return this;
        }

        public Builder setCanceledOnTouchOutside(boolean canceledOnTouchOutside) {
            this.canceledOnTouchOutside = canceledOnTouchOutside;
            return this;
        }

        public Builder setOnShowListener(DialogInterface.OnShowListener listener) {
            onShowListener = listener;
            return this;
        }

        public Builder setOnDismissListener(DialogInterface.OnDismissListener listener) {
            onDismissListener = listener;
            return this;
        }

        public Builder setOnCancelListener(DialogInterface.OnCancelListener listener) {
            onCancelListener = listener;
            return this;
        }

        public OverlayAlertDialog create() {
            return new OverlayAlertDialog(this);
        }

        public OverlayAlertDialog show() {
            OverlayAlertDialog dialog = create();
            dialog.show();
            return dialog;
        }
    }

    /** Tiny local helper that avoids importing Looper in every call site above. */
    private static final class LooperGuard {
        private LooperGuard() {
        }

        static boolean isMainThread() {
            return android.os.Looper.myLooper() == android.os.Looper.getMainLooper();
        }
    }
}
