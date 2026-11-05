package com.limelight.heokami;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Disconnects the stream after a period without user input (e.g. the user fell asleep).
 *
 * <p>Input is tracked cheaply: callers only write a timestamp ({@link #markActivity()}), and a
 * main-thread tick compares it against the timeout. A countdown warning is raised during the
 * last minute (or the last half of very short timeouts); any input during the warning cancels
 * it.</p>
 */
public final class IdleDisconnectMonitor {
    private static final long CHECK_INTERVAL_MS = 5_000;
    private static final long WARNING_TICK_MS = 1_000;
    private static final long MAX_WARNING_MS = 60_000;

    public interface Callback {
        /** Latest input time from other sources (e.g. input forwarded to the host), uptime ms. */
        long lastExternalInputUptimeMs();

        /** False while idle time shouldn't accumulate (e.g. stream suspended in background). */
        boolean shouldCountIdle();

        void onIdleWarning(int secondsLeft);

        void onIdleWarningCancelled();

        void onIdleTimeout();
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Callback callback;
    private final long timeoutMs;
    private final long warningMs;

    private volatile long lastActivityUptimeMs;
    private boolean running;
    private boolean warning;

    private final Runnable tick = this::tick;

    public IdleDisconnectMonitor(long timeoutMs, Callback callback) {
        this.timeoutMs = timeoutMs;
        this.warningMs = Math.min(MAX_WARNING_MS, timeoutMs / 2);
        this.callback = callback;
    }

    /** Main thread. */
    public void start() {
        lastActivityUptimeMs = SystemClock.uptimeMillis();
        running = true;
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, CHECK_INTERVAL_MS);
    }

    /** Main thread. */
    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
        clearWarning();
    }

    /** Any thread; called for every input event, so it only stores a timestamp. */
    public void markActivity() {
        lastActivityUptimeMs = SystemClock.uptimeMillis();
        if (warning) {
            // Cancel the countdown right away instead of waiting for the next tick.
            handler.post(tick);
        }
    }

    private void clearWarning() {
        if (warning) {
            warning = false;
            callback.onIdleWarningCancelled();
        }
    }

    private void tick() {
        handler.removeCallbacks(tick);
        if (!running) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (!callback.shouldCountIdle()) {
            lastActivityUptimeMs = now;
            clearWarning();
            handler.postDelayed(tick, CHECK_INTERVAL_MS);
            return;
        }

        long last = Math.max(lastActivityUptimeMs, callback.lastExternalInputUptimeMs());
        long idle = now - last;
        if (idle >= timeoutMs) {
            running = false;
            warning = false;
            callback.onIdleTimeout();
            return;
        }

        long remaining = timeoutMs - idle;
        if (remaining <= warningMs) {
            warning = true;
            callback.onIdleWarning((int) ((remaining + 999) / 1000));
            handler.postDelayed(tick, WARNING_TICK_MS);
        } else {
            clearWarning();
            handler.postDelayed(tick, Math.min(CHECK_INTERVAL_MS, remaining - warningMs));
        }
    }
}
