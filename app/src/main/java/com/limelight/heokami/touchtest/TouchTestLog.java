package com.limelight.heokami.touchtest;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Event log of one touch test session. Every layer of the input pipeline writes here: raw touches,
 * routing between floating panels and the stream, gestures, and what would be sent to the host.
 * Sessions are saved as text files so earlier runs can be compared and shared.
 */
public final class TouchTestLog {
    public enum Category {
        TOUCH, ROUTE, GESTURE, HOST, KEY, INFO
    }

    public static final class Entry {
        public final long time;
        public final Category category;
        final String key;
        String text;
        int count = 1;
        long sumA;
        long sumB;

        Entry(long time, Category category, String key, String text) {
            this.time = time;
            this.category = category;
            this.key = key;
            this.text = text;
        }

        public String format(long sessionStart) {
            String line = String.format(Locale.US, "%7.3f [%s] %s",
                    (time - sessionStart) / 1000.0, category.name(), text);
            return count > 1 ? line + " ×" + count : line;
        }
    }

    public interface Listener {
        void onLogChanged();
    }

    private static final int MAX_ENTRIES = 4000;
    private static final int MAX_SAVED_SESSIONS = 10;
    private static final long MERGE_WINDOW_MS = 250;
    private static final String DIR_NAME = "touch_test_logs";

    private static TouchTestLog active;

    private final List<Entry> entries = new ArrayList<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final long sessionStart = System.currentTimeMillis();
    private boolean paused;
    private boolean notifyPosted;

    /** The log of the running test session, or null when no test is running. */
    public static TouchTestLog active() {
        return active;
    }

    public static TouchTestLog start() {
        active = new TouchTestLog();
        return active;
    }

    public static void finish(Context context) {
        TouchTestLog log = active;
        active = null;
        if (log != null && context != null) {
            log.save(context);
        }
    }

    public long getSessionStart() {
        return sessionStart;
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    public synchronized void setPaused(boolean paused) {
        this.paused = paused;
    }

    public void log(Category category, String text) {
        upsert(category, null, text);
    }

    /**
     * Adds an entry, or replaces the previous one when it has the same key and arrived within the
     * merge window, so high-rate events (moves) collapse into one counted line.
     * Returns true when the text replaced the previous entry.
     */
    public boolean upsert(Category category, String key, String text) {
        boolean merged;
        synchronized (this) {
            if (paused) {
                return false;
            }
            long now = System.currentTimeMillis();
            Entry last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
            if (key != null && last != null && key.equals(last.key) && now - last.time <= MERGE_WINDOW_MS) {
                last.text = text;
                last.count++;
                merged = true;
            }
            else {
                entries.add(new Entry(now, category, key, text));
                if (entries.size() > MAX_ENTRIES) {
                    entries.subList(0, entries.size() - MAX_ENTRIES).clear();
                }
                merged = false;
            }
        }
        postNotify();
        return merged;
    }

    /**
     * Like {@link #upsert} for deltas: merged entries keep running sums, and the text is
     * {@code String.format(format, sumA, sumB)}.
     */
    public void accumulate(Category category, String key, String format, long deltaA, long deltaB) {
        synchronized (this) {
            if (paused) {
                return;
            }
            long now = System.currentTimeMillis();
            Entry last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
            if (last != null && key.equals(last.key) && now - last.time <= MERGE_WINDOW_MS) {
                last.sumA += deltaA;
                last.sumB += deltaB;
                last.count++;
                last.text = String.format(Locale.US, format, last.sumA, last.sumB);
            }
            else {
                Entry entry = new Entry(now, category, key,
                        String.format(Locale.US, format, deltaA, deltaB));
                entry.sumA = deltaA;
                entry.sumB = deltaB;
                entries.add(entry);
                if (entries.size() > MAX_ENTRIES) {
                    entries.subList(0, entries.size() - MAX_ENTRIES).clear();
                }
            }
        }
        postNotify();
    }

    public synchronized void clear() {
        entries.clear();
        postNotify();
    }

    public synchronized List<Entry> snapshot() {
        return new ArrayList<>(entries);
    }

    public String export(boolean[] enabledCategories) {
        StringBuilder sb = new StringBuilder();
        sb.append("Moonlight touch test ")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(sessionStart)))
                .append('\n');
        for (Entry entry : snapshot()) {
            if (enabledCategories == null || enabledCategories[entry.category.ordinal()]) {
                sb.append(entry.format(sessionStart)).append('\n');
            }
        }
        return sb.toString();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void postNotify() {
        synchronized (this) {
            if (notifyPosted) {
                return;
            }
            notifyPosted = true;
        }
        // Coalesce bursts of events into at most one UI refresh per frame-ish interval.
        mainHandler.postDelayed(() -> {
            synchronized (TouchTestLog.this) {
                notifyPosted = false;
            }
            for (Listener listener : listeners) {
                listener.onLogChanged();
            }
        }, 60);
    }

    private void save(Context context) {
        if (snapshot().isEmpty()) {
            return;
        }
        File dir = sessionDir(context);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return;
        }
        String name = "touch-test-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date(sessionStart)) + ".txt";
        try (OutputStream out = new FileOutputStream(new File(dir, name))) {
            out.write(export(null).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return;
        }
        List<File> saved = savedSessions(context);
        for (int i = MAX_SAVED_SESSIONS; i < saved.size(); i++) {
            //noinspection ResultOfMethodCallIgnored
            saved.get(i).delete();
        }
    }

    private static File sessionDir(Context context) {
        return new File(context.getFilesDir(), DIR_NAME);
    }

    /** Saved sessions, newest first. */
    public static List<File> savedSessions(Context context) {
        File[] files = sessionDir(context).listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null) {
            return Collections.emptyList();
        }
        List<File> list = new ArrayList<>(Arrays.asList(files));
        Collections.sort(list, (a, b) -> b.getName().compareTo(a.getName()));
        return list;
    }

    public static String read(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), 4 * 1024 * 1024)];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return new String(data, 0, read, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
