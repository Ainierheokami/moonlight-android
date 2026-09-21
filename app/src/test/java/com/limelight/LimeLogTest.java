package com.limelight;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class LimeLogTest {
    @Test
    public void exposesRecentEntriesForDiagnostics() {
        String marker = "toast-diagnostics-test-" + System.nanoTime();

        LimeLog.warning(marker);

        String recentLogs = LimeLog.getRecentLogs();
        assertTrue(recentLogs.contains("WARNING: " + marker));
    }
}
