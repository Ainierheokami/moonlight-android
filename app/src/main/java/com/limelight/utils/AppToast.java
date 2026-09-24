package com.limelight.utils;

import android.content.Context;

import java.lang.ref.WeakReference;

/**
 * Compatibility facade for the app's in-app toast API.
 *
 * <p>The actual rendering and lifecycle management lives in {@link ToastManager}. Keeping this
 * small value object preserves the existing {@code AppToast.makeText(...).show()} call sites
 * while moving every toast into the Activity's shared overlay hierarchy.</p>
 */
public final class AppToast {
    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;

    private final Context applicationContext;
    private final WeakReference<Context> sourceContext;
    private final CharSequence message;
    private final int duration;

    private AppToast(Context context, CharSequence message, int duration) {
        Context appContext = context.getApplicationContext();
        this.applicationContext = appContext == null ? context : appContext;
        this.sourceContext = new WeakReference<>(context);
        this.message = message;
        this.duration = duration;
    }

    public static AppToast makeText(Context context, CharSequence message, int duration) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        return new AppToast(context, message, duration);
    }

    public static AppToast makeText(Context context, int messageResId, int duration) {
        return makeText(context, context.getString(messageResId), duration);
    }

    public void show() {
        Context source = sourceContext.get();
        ToastManager.getInstance().enqueue(source == null ? applicationContext : source, this);
    }

    public void dismiss() {
        ToastManager.getInstance().dismiss(this);
    }

    Context getContext() {
        Context source = sourceContext.get();
        return source == null ? applicationContext : source;
    }

    CharSequence getMessage() {
        return message == null ? "" : message;
    }

    int getDuration() {
        return duration;
    }
}
