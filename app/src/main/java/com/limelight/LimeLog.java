package com.limelight;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

public class LimeLog {
    private static final Logger LOGGER = Logger.getLogger(LimeLog.class.getName());
    private static final int MAX_RECENT_LOGS = 120;
    private static final ArrayDeque<String> RECENT_LOGS = new ArrayDeque<>();
    private static final Object LOG_LOCK = new Object();

    public static void info(String msg) {
        log("INFO", msg);
    }
    
    public static void warning(String msg) {
        log("WARNING", msg);
    }
    
    public static void severe(String msg) {
        log("SEVERE", msg);
    }

    private static void log(String level, String msg) {
        String safeMessage = msg == null ? "(null)" : msg;
        synchronized (LOG_LOCK) {
            if (RECENT_LOGS.size() >= MAX_RECENT_LOGS) {
                RECENT_LOGS.removeFirst();
            }
            RECENT_LOGS.addLast(level + ": " + safeMessage);
        }

        switch (level) {
            case "WARNING":
                LOGGER.warning(safeMessage);
                break;
            case "SEVERE":
                LOGGER.severe(safeMessage);
                break;
            default:
                LOGGER.info(safeMessage);
                break;
        }
    }

    public static String getRecentLogs() {
        StringBuilder logs = new StringBuilder();
        synchronized (LOG_LOCK) {
            for (String entry : RECENT_LOGS) {
                if (logs.length() > 0) {
                    logs.append('\n');
                }
                logs.append(entry);
            }
        }
        return logs.length() == 0 ? "(no recent LimeLog entries)" : logs.toString();
    }
    
    public static void setFileHandler(String fileName) throws IOException {
        LOGGER.addHandler(new FileHandler(fileName));
    }
}
