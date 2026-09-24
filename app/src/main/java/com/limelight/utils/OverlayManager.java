package com.limelight.utils;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.limelight.LimeLog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Owns one {@link OverlayContainer} per Activity and forwards lifecycle changes to its users. */
public final class OverlayManager {
    public interface LifecycleListener {
        void onActivityResumed(Activity activity);

        void onActivityDestroyed(Activity activity);
    }

    private static final OverlayManager INSTANCE = new OverlayManager();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final WeakHashMap<Activity, OverlayContainer> containers = new WeakHashMap<>();
    private final ArrayList<LifecycleListener> lifecycleListeners = new ArrayList<>();
    private final Application.ActivityLifecycleCallbacks activityLifecycleCallbacks =
            new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    if (isUsable(activity)) {
                        getContainerInternal(activity);
                    }
                }

                @Override
                public void onActivityStarted(Activity activity) {
                }

                @Override
                public void onActivityResumed(Activity activity) {
                    if (!isUsable(activity)) {
                        return;
                    }

                    resumedActivity = new WeakReference<>(activity);
                    OverlayContainer container = getContainerInternal(activity);
                    if (container != null) {
                        container.bringToFront();
                    }
                    notifyActivityResumed(activity);
                }

                @Override
                public void onActivityPaused(Activity activity) {
                    if (resumedActivity != null && resumedActivity.get() == activity) {
                        resumedActivity = null;
                    }
                }

                @Override
                public void onActivityStopped(Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    if (resumedActivity != null && resumedActivity.get() == activity) {
                        resumedActivity = null;
                    }

                    OverlayContainer container = containers.remove(activity);
                    if (container != null) {
                        ViewParent parentView = container.getParent();
                        if (parentView instanceof ViewGroup) {
                            ViewGroup parent = (ViewGroup) parentView;
                            parent.removeView(container);
                        }
                    }
                    notifyActivityDestroyed(activity);
                }
            };

    private Application application;
    private WeakReference<Activity> resumedActivity;
    private boolean callbacksRegistered;

    private OverlayManager() {
    }

    public static OverlayManager getInstance() {
        return INSTANCE;
    }

    /** Must be called from the main thread. */
    public void initialize(Context context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> initialize(context));
            return;
        }

        Context applicationContext = context.getApplicationContext();
        if (!(applicationContext instanceof Application)) {
            LimeLog.warning("Unable to initialize overlay manager without an Application context");
            return;
        }

        Application candidate = (Application) applicationContext;
        if (application == null) {
            application = candidate;
        }
        if (!callbacksRegistered) {
            application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks);
            callbacksRegistered = true;
        }
    }

    public void addLifecycleListener(LifecycleListener listener) {
        if (listener == null || lifecycleListeners.contains(listener)) {
            return;
        }
        lifecycleListeners.add(listener);
    }

    public void removeLifecycleListener(LifecycleListener listener) {
        if (listener == null) {
            return;
        }
        lifecycleListeners.remove(listener);
    }

    public Activity getResumedActivity() {
        Activity activity = resumedActivity == null ? null : resumedActivity.get();
        return isUsable(activity) ? activity : null;
    }

    /**
     * Resolves the Activity that should host an app overlay. A Context passed from a view or
     * themed wrapper is preferred; otherwise the Activity most recently reported as resumed is
     * used. This fallback matters when the manager is initialized after the Activity's resume
     * callback has already happened.
     */
    public Activity resolveActivity(Context context) {
        Activity activity = findActivity(context);
        if (isUsable(activity)) {
            return activity;
        }
        return getResumedActivity();
    }

    public OverlayContainer getContainer(Activity activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            LimeLog.warning("Overlay container requested off the main thread");
            return null;
        }
        if (!isUsable(activity)) {
            return null;
        }
        initialize(activity);
        return getContainerInternal(activity);
    }

    public OverlayContainer.DialogHandle showDialog(Activity activity, View dialogView,
                                                    int maxWidthDp, boolean cancelable,
                                                    Runnable onBackPressed) {
        OverlayContainer container = getContainer(activity);
        return container == null
                ? null
                : container.addDialogView(dialogView, maxWidthDp, cancelable, onBackPressed);
    }

    public OverlayContainer.DialogHandle showDialog(Activity activity, View dialogView,
                                                    int maxWidthDp, int maxHeightDp,
                                                    boolean cancelable,
                                                    boolean cancelOnTouchOutside,
                                                    Runnable onDismiss) {
        OverlayContainer container = getContainer(activity);
        return container == null
                ? null
                : container.addDialogView(dialogView, maxWidthDp, maxHeightDp, cancelable,
                cancelOnTouchOutside, onDismiss);
    }

    public OverlayContainer.DialogHandle showDialog(Context context, View dialogView,
                                                    int maxWidthDp, int maxHeightDp,
                                                    boolean cancelable, Runnable onBackPressed) {
        Activity activity = resolveActivity(context);
        OverlayContainer container = getContainer(activity);
        return container == null
                ? null
                : container.addDialogView(dialogView, maxWidthDp, maxHeightDp,
                cancelable, onBackPressed);
    }

    public OverlayContainer.DialogHandle showOverlay(Activity activity, View overlayView,
                                                     int widthPx, int heightPx,
                                                     int leftPx, int topPx,
                                                     boolean cancelable,
                                                     Runnable onBackPressed) {
        OverlayContainer container = getContainer(activity);
        return container == null
                ? null
                : container.addOverlayView(overlayView, widthPx, heightPx, leftPx, topPx,
                cancelable, onBackPressed);
    }

    public OverlayContainer.DialogHandle showFullScreenDialog(Activity activity, View dialogView,
                                                              boolean cancelable,
                                                              boolean cancelOnTouchOutside,
                                                              Runnable onDismiss) {
        OverlayContainer container = getContainer(activity);
        return container == null
                ? null
                : container.addFullScreenDialogView(dialogView, cancelable,
                cancelOnTouchOutside, onDismiss);
    }

    private OverlayContainer getContainerInternal(Activity activity) {
        OverlayContainer container = containers.get(activity);
        if (container == null) {
            container = new OverlayContainer(activity);
            containers.put(activity, container);
        }

        View decorView = activity.getWindow().getDecorView();
        if (!(decorView instanceof ViewGroup)) {
            LimeLog.warning("Unable to attach overlay container: Activity decor is not a ViewGroup");
            return null;
        }

        ViewGroup decor = (ViewGroup) decorView;
        if (container.getParent() != decor) {
            if (container.getParent() instanceof ViewGroup) {
                ((ViewGroup) container.getParent()).removeView(container);
            }
            decor.addView(container, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        container.bringToFront();
        return container;
    }

    private void notifyActivityResumed(Activity activity) {
        ArrayList<LifecycleListener> listeners = new ArrayList<>(lifecycleListeners);
        for (LifecycleListener listener : listeners) {
            listener.onActivityResumed(activity);
        }
    }

    private void notifyActivityDestroyed(Activity activity) {
        ArrayList<LifecycleListener> listeners = new ArrayList<>(lifecycleListeners);
        for (LifecycleListener listener : listeners) {
            listener.onActivityDestroyed(activity);
        }
    }

    static boolean isUsable(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
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
