package com.limelight.heokami;

import android.annotation.SuppressLint;
import android.net.TrafficStats;

public class NetSpeedMonitor {
    private TrafficStats mTrafficStats;
    private long lastTotalRxBytes = 0;
    private long lastTotalTxBytes = 0;
    private long lastTimeStamp = 0;

    public static class SpeedInfo {
        public final long downloadSpeed; // bytes per second
        public final long uploadSpeed;   // bytes per second

        public SpeedInfo(long downloadSpeed, long uploadSpeed) {
            this.downloadSpeed = downloadSpeed;
            this.uploadSpeed = uploadSpeed;
        }
    }

    public NetSpeedMonitor() {
        mTrafficStats = new TrafficStats();
        reset();
    }

    public void reset() {
        lastTotalRxBytes = TrafficStats.getUidRxBytes(android.os.Process.myUid());
        lastTotalTxBytes = TrafficStats.getUidTxBytes(android.os.Process.myUid());
        lastTimeStamp = System.currentTimeMillis();
    }

    public SpeedInfo getNetSpeed() {
        long currentRxBytes = TrafficStats.getUidRxBytes(android.os.Process.myUid());
        long currentTxBytes = TrafficStats.getUidTxBytes(android.os.Process.myUid());
        long currentTimeStamp = System.currentTimeMillis();

        // 计算时间差(秒)
        long timePeriod = (currentTimeStamp - lastTimeStamp) / 1000;
        if (timePeriod == 0) {
            return new SpeedInfo(0, 0);
        }

        // 计算下载速度
        long downloadSpeed = (currentRxBytes - lastTotalRxBytes) / timePeriod;
        // 计算上传速度
        long uploadSpeed = (currentTxBytes - lastTotalTxBytes) / timePeriod;

        // 更新数据
        lastTotalRxBytes = currentRxBytes;
        lastTotalTxBytes = currentTxBytes;
        lastTimeStamp = currentTimeStamp;

        return new SpeedInfo(downloadSpeed, uploadSpeed);
    }

    // 将字节速度转换为可读的字符串
    @SuppressLint("DefaultLocale")
    public static String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return bytesPerSecond + " B/s";
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format("%.2f KB/s", bytesPerSecond / 1024.0);
        } else {
            return String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024));
        }
    }
}