package com.limelight.utils;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;

/**
 * The in-activity surface shared by app-owned dialogs and toasts.
 *
 * <p>Keeping both layers in the same view hierarchy is intentional. Android dialogs created
 * with {@code Dialog(Activity)} are separate windows and cannot be reliably covered by another
 * application window. App-owned dialogs therefore use this container too, with the toast layer
 * always added after the dialog layer.</p>
 */
public final class OverlayContainer extends FrameLayout {
    private static final int DIM_COLOR = 0x66000000;
    private static final int DIALOG_HORIZONTAL_MARGIN_DP = 20;
    private static final int DEFAULT_DIALOG_MAX_WIDTH_DP = 560;

    private final FrameLayout dialogLayer;
    private final FrameLayout toastLayer;
    private final ArrayList<DialogEntry> dialogEntries = new ArrayList<>();
    private View dimView;

    public OverlayContainer(Context context) {
        super(context);

        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        dialogLayer = new FrameLayout(context);
        dialogLayer.setClipChildren(false);
        dialogLayer.setClipToPadding(false);
        dialogLayer.setClickable(false);
        addView(dialogLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        toastLayer = new FrameLayout(context);
        toastLayer.setClipChildren(false);
        toastLayer.setClipToPadding(false);
        toastLayer.setClickable(false);
        addView(toastLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        toastLayer.bringToFront();
    }

    /**
     * Adds an app-owned dialog below the toast layer.
     *
     * @param dialogView the already inflated dialog content
     * @param maxWidthDp maximum width of the dialog content
     * @param cancelable whether the back key may invoke {@code onBackPressed}
     * @param onBackPressed callback for a cancelable back press
     */
    public DialogHandle addDialogView(View dialogView, int maxWidthDp,
                                      boolean cancelable, Runnable onBackPressed) {
        return addDialogView(dialogView, maxWidthDp, 0, cancelable, onBackPressed);
    }

    /**
     * Adds a dialog with an optional fixed maximum height. A fixed height is useful for large
     * editor panels that already contain their own scrolling child.
     */
    public DialogHandle addDialogView(View dialogView, int maxWidthDp, int maxHeightDp,
                                      boolean cancelable, Runnable onBackPressed) {
        if (dialogView == null) {
            throw new IllegalArgumentException("dialogView must not be null");
        }
        if (dialogView.getParent() != null) {
            throw new IllegalStateException("dialogView already has a parent");
        }

        ensureDimView();

        int width = calculateDialogWidth(maxWidthDp <= 0
                ? DEFAULT_DIALOG_MAX_WIDTH_DP : maxWidthDp);
        int height = calculateDialogHeight(maxHeightDp);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                width,
                height,
                android.view.Gravity.CENTER);
        dialogView.setClickable(true);
        dialogView.setFocusable(true);
        dialogView.setFocusableInTouchMode(true);
        dialogView.setElevation(dp(16));
        dialogLayer.addView(dialogView, layoutParams);

        DialogEntry entry = new DialogEntry(dialogView, cancelable, onBackPressed);
        dialogEntries.add(entry);

        setFocusableInTouchMode(true);
        requestFocus();
        bringToFront();
        toastLayer.bringToFront();
        return new DialogHandle(this, entry);
    }

    /** Adds or replaces the single currently visible toast view. */
    public void setToastView(View toastView) {
        if (toastView == null) {
            clearToastView(null);
            return;
        }
        if (toastView.getParent() != null) {
            throw new IllegalStateException("toastView already has a parent");
        }

        toastLayer.removeAllViews();
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        int horizontalMargin = dp(16);
        layoutParams.leftMargin = horizontalMargin;
        layoutParams.rightMargin = horizontalMargin;
        layoutParams.bottomMargin = dp(48);
        toastLayer.addView(toastView, layoutParams);
        toastLayer.bringToFront();
    }

    /** Removes the toast if it is the current child, or all toast children when expected is null. */
    public void clearToastView(View expected) {
        if (expected == null) {
            toastLayer.removeAllViews();
            return;
        }
        if (expected.getParent() == toastLayer) {
            toastLayer.removeView(expected);
        }
    }

    public boolean hasToastView() {
        return toastLayer.getChildCount() > 0;
    }

    public boolean hasDialogs() {
        return !dialogEntries.isEmpty();
    }

    private void removeDialog(DialogHandle handle) {
        if (handle == null || handle.owner != this || handle.entry == null) {
            return;
        }

        DialogEntry entry = handle.entry;
        if (!dialogEntries.remove(entry)) {
            handle.entry = null;
            return;
        }

        if (entry.view.getParent() == dialogLayer) {
            dialogLayer.removeView(entry.view);
        }
        handle.entry = null;

        if (dialogEntries.isEmpty()) {
            if (dimView != null && dimView.getParent() == dialogLayer) {
                dialogLayer.removeView(dimView);
            }
            clearFocus();
            setFocusableInTouchMode(false);
        }
        else {
            setFocusableInTouchMode(true);
            requestFocus();
        }
    }

    private void ensureDimView() {
        if (dimView == null) {
            dimView = new View(getContext());
            dimView.setBackground(new ColorDrawable(DIM_COLOR));
            dimView.setClickable(true);
            dimView.setFocusable(false);
        }
        if (dimView.getParent() != dialogLayer) {
            dialogLayer.addView(dimView, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private int calculateDialogWidth(int maxWidthDp) {
        int availableWidth = getResources().getDisplayMetrics().widthPixels
                - (2 * dp(DIALOG_HORIZONTAL_MARGIN_DP));
        return Math.max(dp(240), Math.min(dp(maxWidthDp), availableWidth));
    }

    private int calculateDialogHeight(int maxHeightDp) {
        if (maxHeightDp <= 0) {
            return ViewGroup.LayoutParams.WRAP_CONTENT;
        }

        int availableHeight = getResources().getDisplayMetrics().heightPixels
                - (2 * dp(DIALOG_HORIZONTAL_MARGIN_DP));
        return Math.max(dp(160), Math.min(dp(maxHeightDp), availableHeight));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && !dialogEntries.isEmpty()) {
            DialogEntry entry = dialogEntries.get(dialogEntries.size() - 1);
            if (event.getAction() == KeyEvent.ACTION_UP
                    && entry.cancelable && entry.onBackPressed != null) {
                entry.onBackPressed.run();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    public static final class DialogHandle {
        private final OverlayContainer owner;
        private DialogEntry entry;

        private DialogHandle(OverlayContainer owner, DialogEntry entry) {
            this.owner = owner;
            this.entry = entry;
        }

        public void remove() {
            owner.removeDialog(this);
        }

        public boolean isShowing() {
            return entry != null && entry.view.getParent() == owner.dialogLayer;
        }
    }

    private static final class DialogEntry {
        private final View view;
        private final boolean cancelable;
        private final Runnable onBackPressed;

        private DialogEntry(View view, boolean cancelable, Runnable onBackPressed) {
            this.view = view;
            this.cancelable = cancelable;
            this.onBackPressed = onBackPressed;
        }
    }
}
