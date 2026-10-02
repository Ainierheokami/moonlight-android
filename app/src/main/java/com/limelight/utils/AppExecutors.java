package com.limelight.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared pool for short-lived background work (network calls, binder waits, cleanup).
 * Long-running loop threads (polling, discovery, rendering) should keep their own thread.
 */
public class AppExecutors {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static final ExecutorService IO = Executors.newCachedThreadPool(r ->
            new Thread(r, "ml-worker-" + COUNTER.incrementAndGet()));

    private AppExecutors() {}

    public static void execute(Runnable task) {
        IO.execute(task);
    }
}
