package com.limelight.portal;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.limelight.Game;
import com.limelight.R;
import com.limelight.ui.StreamView;
import com.limelight.preferences.PreferenceConfiguration;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 传送门管理器视图
 * 负责创建、销毁、更新各个PortalOverlayView
 */
public class PortalManagerView extends FrameLayout {
    private static final String TAG = "PortalManagerView";
    private static final boolean DEBUG_CAPTURE = false;
    private static final long ACTIVE_CAPTURE_INTERVAL_MS = 33;
    private static final long IDLE_CAPTURE_INTERVAL_MS = 250;
    private static final String PREFS_NAME = "portal_configs";
    private static final String KEY_CONFIGS = "portal_configs_json";
    private static final String KEY_PORTALS_ENABLED = "portals_enabled";

    private List<PortalConfig> portalConfigs = new ArrayList<>();
    private Map<Integer, PortalOverlayView> portalViews = new HashMap<>();
    private final Object portalLock = new Object();
    private Game game;
    private StreamView streamView;
    private BitmapCaptureThread captureThread;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Gson gson = new Gson();
    private boolean portalsEnabled = true;
    private boolean portalsSuppressed = false;
    private boolean isPaused = false;

    public PortalManagerView(Context context, Game game) {
        super(context);
        this.game = game;
        this.streamView = game.findViewById(R.id.surfaceView);
        loadConfigs();
        createViews();
        startCaptureThread();
    }


    private void loadConfigs() {
        SharedPreferences prefs = game.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        portalsEnabled = prefs.getBoolean(KEY_PORTALS_ENABLED, true); // Load portalsEnabled state
        String json = prefs.getString(KEY_CONFIGS, null);
        if (json == null || json.isEmpty() || json.equals("[]")) { // Check for empty array string too
            // 没有保存的配置，创建一个默认传送门用于测试
            PortalConfig config = new PortalConfig();
            config.id = generateNewId();
            config.srcRect = new RectF(0.1f, 0.1f, 0.3f, 0.3f); // 归一化坐标
            config.dstRect = new RectF(800, 100, 1000, 300); // 像素坐标
            config.enabled = true;
            config.name = "传送门1";
            portalConfigs.add(config);
            saveConfigs(); // 保存默认配置
        } else {
            // 使用Gson反序列化
            Type type = new TypeToken<List<PortalConfig>>(){}.getType();
            List<PortalConfig> loaded = gson.fromJson(json, type);
            if (loaded != null) {
                portalConfigs.clear();
                portalConfigs.addAll(loaded);
            } else {
                Log.e(TAG, "Failed to parse portal configs from JSON");
            }
        }
        // 游戏开始时重置所有传送门的编辑状态为非编辑模式
        for (PortalConfig config : portalConfigs) {
            config.editing = false;
            config.editMode = 0;
        }
        Log.d(TAG, "重置传送门编辑状态为非编辑模式");
    }

    public void saveConfigs() {
        SharedPreferences prefs = game.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json;
        synchronized (portalLock) {
            json = gson.toJson(portalConfigs);
        }
        prefs.edit()
                .putString(KEY_CONFIGS, json)
                .putBoolean(KEY_PORTALS_ENABLED, portalsEnabled) // Save portalsEnabled state
                .apply();
        Log.d(TAG, "保存传送门配置，数量: " + portalConfigs.size());
    }

    public int generateNewId() {
        int maxId = 0;
        synchronized (portalLock) {
            for (PortalConfig config : portalConfigs) {
                if (config.id > maxId) {
                    maxId = config.id;
                }
            }
        }
        return maxId + 1;
    }

    private void createViews() {
        boolean visible = shouldShowPortalViews();
        synchronized (portalLock) {
            for (PortalConfig config : portalConfigs) {
                if (config.enabled) {
                    PortalOverlayView view = new PortalOverlayView(getContext());
                    view.setConfig(config);
                    view.setGame(game);
                    view.setVisibility(visible ? View.VISIBLE : View.GONE);
                    portalViews.put(config.id, view);
                    addView(view);
                }
            }
        }
    }

    public void addPortal(PortalConfig config) {
        boolean visible = shouldShowPortalViews();
        synchronized (portalLock) {
            portalConfigs.add(config);
            if (config.enabled) {
                PortalOverlayView view = new PortalOverlayView(getContext());
                view.setConfig(config);
                view.setGame(game);
                view.setVisibility(visible ? View.VISIBLE : View.GONE);
                portalViews.put(config.id, view);
                addView(view);
            }
        }
        saveConfigs();
    }

    public void removePortal(int id) {
        synchronized (portalLock) {
            PortalOverlayView view = portalViews.remove(id);
            if (view != null) {
                removeView(view);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                portalConfigs.removeIf(config -> config.id == id);
            } else {
                // For older Android versions, manually iterate and remove
                for (int i = 0; i < portalConfigs.size(); i++) {
                    if (portalConfigs.get(i).id == id) {
                        portalConfigs.remove(i);
                        break;
                    }
                }
            }
        }
        saveConfigs();
    }

    public void updatePortalBitmap(int id, Bitmap bitmap) {
        PortalOverlayView view;
        boolean canShow = shouldShowPortalViews();
        synchronized (portalLock) {
            view = portalViews.get(id);
        }
        if (view == null || !canShow || view.getVisibility() != View.VISIBLE) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            return;
        }
        if (view.getHandler() == null) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            return;
        }
        view.post(() -> {
            if (!shouldShowPortalViews() || view.getVisibility() != View.VISIBLE || !view.isAttachedToWindow()) {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
                return;
            }
            view.updatePortalBitmap(bitmap);
        });
    }

    private RectF getVideoContentRect() {
        int viewWidth = streamView.getWidth();
        int viewHeight = streamView.getHeight();
        if (viewWidth == 0 || viewHeight == 0) {
            logCapture("getVideoContentRect: view size zero");
            return new RectF(0, 0, viewWidth, viewHeight);
        }
        PreferenceConfiguration prefConfig = game.getPrefConfig();
        if (prefConfig == null) {
            logCapture("getVideoContentRect: prefConfig null");
            return new RectF(0, 0, viewWidth, viewHeight);
        }
        int videoWidth = prefConfig.width;
        int videoHeight = prefConfig.height;
        if (prefConfig.stretchVideo) {
            // 拉伸视频，填充整个视图
            logCapture(String.format("getVideoContentRect: stretchVideo true, view=%dx%d", viewWidth, viewHeight));
            return new RectF(0, 0, viewWidth, viewHeight);
        }
        // 计算保持宽高比的视频区域（居中）
        float viewAspect = (float) viewWidth / viewHeight;
        float videoAspect = (float) videoWidth / videoHeight;
        int contentWidth, contentHeight, contentLeft, contentTop;
        if (viewAspect > videoAspect) {
            // 视图更宽，视频在水平方向有黑边
            contentHeight = viewHeight;
            contentWidth = (int) (contentHeight * videoAspect);
            contentLeft = (viewWidth - contentWidth) / 2;
            contentTop = 0;
        } else {
            // 视图更高，视频在垂直方向有黑边
            contentWidth = viewWidth;
            contentHeight = (int) (contentWidth / videoAspect);
            contentLeft = 0;
            contentTop = (viewHeight - contentHeight) / 2;
        }
        RectF rect = new RectF(contentLeft, contentTop, contentLeft + contentWidth, contentTop + contentHeight);
        logCapture(String.format("getVideoContentRect: view=%dx%d video=%dx%d aspect=%.3f content=(%d,%d,%d,%d)",
                viewWidth, viewHeight, videoWidth, videoHeight, videoAspect,
                contentLeft, contentTop, contentLeft + contentWidth, contentTop + contentHeight));
        return rect;
    }

    private void startCaptureThread() {
        if (isPaused) return;

        if (captureThread != null && captureThread.isAlive()) {
            return;
        }
        captureThread = new BitmapCaptureThread();
        captureThread.start();
    }

    private void stopCaptureThread() {
        if (captureThread != null) {
            captureThread.stopCapture();
            try {
                captureThread.join(500); // Wait for the thread to finish, with a timeout
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for capture thread to join", e);
                Thread.currentThread().interrupt(); // Restore the interrupted status
            }
            captureThread = null;
        }
    }

    public void onPause() {
        isPaused = true;
        stopCaptureThread();
    }

    public void onResume() {
        isPaused = false;
        if (shouldCapture()) {
            startCaptureThread();
        }
    }

    private boolean shouldShowPortalViews() {
        return portalsEnabled && !portalsSuppressed;
    }

    private boolean shouldCapture() {
        if (!portalsEnabled || portalsSuppressed) {
            return false;
        }
        if (!isShown() || streamView == null || streamView.getWidth() == 0 || streamView.getHeight() == 0) {
            return false;
        }
        synchronized (portalLock) {
            for (PortalConfig config : portalConfigs) {
                if (config.enabled) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updatePortalVisibility() {
        boolean visible = shouldShowPortalViews();
        synchronized (portalLock) {
            for (PortalOverlayView view : portalViews.values()) {
                view.setVisibility(visible ? View.VISIBLE : View.GONE);
                if (!visible) {
                    view.clearPortalBitmap();
                }
            }
        }
    }

    public boolean arePortalsEnabled() {
        return portalsEnabled;
    }

    public boolean togglePortalsEnabled() {
        setPortalsEnabled(!portalsEnabled);
        return portalsEnabled;
    }

    public void setPortalsEnabled(boolean enabled) {
        if (portalsEnabled == enabled) {
            return;
        }
        portalsEnabled = enabled;
        saveConfigs(); // Save current state
        updatePortalVisibility();
        if (shouldCapture()) {
            startCaptureThread();
        } else {
            stopCaptureThread();
        }
    }

    public void setPortalsSuppressed(boolean suppressed) {
        if (portalsSuppressed == suppressed) {
            return;
        }
        portalsSuppressed = suppressed;
        updatePortalVisibility();
        if (shouldCapture()) {
            startCaptureThread();
        } else {
            stopCaptureThread();
        }
    }

    private void logCapture(String message) {
        if (DEBUG_CAPTURE) {
            Log.d(TAG, message);
        }
    }

    private class BitmapCaptureThread extends Thread {
        private volatile boolean running = true;

        @Override
        public void run() {
            int consecutiveFailures = 0;
            while (running && !isInterrupted()) {
                try {
                    if (!shouldCapture()) {
                        try {
                            Thread.sleep(IDLE_CAPTURE_INTERVAL_MS);
                        } catch (InterruptedException e) {
                            break;
                        }
                        consecutiveFailures = 0;
                        continue;
                    }
                    long startTime = System.currentTimeMillis();
                    RectF videoRect = getVideoContentRect();
                    Bitmap fullBitmap = captureFullFrame(videoRect);
                    if (fullBitmap != null) {
                        consecutiveFailures = 0;
                        List<PortalConfig> configsSnapshot;
                        synchronized (portalLock) {
                            configsSnapshot = new ArrayList<>(portalConfigs);
                        }
                        for (PortalConfig config : configsSnapshot) {
                            if (!config.enabled) continue;
                            if (!shouldShowPortalViews()) {
                                break;
                            }
                            Bitmap bitmap = cropBitmapFromFullFrame(fullBitmap, config.srcRect);
                            if (bitmap != null) {
                                updatePortalBitmap(config.id, bitmap);
                            }
                        }
                        if (!fullBitmap.isRecycled()) {
                            fullBitmap.recycle();
                        }
                    } else {
                        consecutiveFailures++;
                    }

                    long elapsed = System.currentTimeMillis() - startTime;
                    long sleepMs = ACTIVE_CAPTURE_INTERVAL_MS - elapsed;
                    
                    // If we failed, back off a bit to avoid spinning tight loops on errors
                    if (consecutiveFailures > 0) {
                        sleepMs = Math.min(250, 33 * (1 << Math.min(consecutiveFailures, 3))); // Exponential backoff up to 250ms
                    }

                    try {
                        if (sleepMs > 0) {
                            Thread.sleep(sleepMs);
                        }
                    } catch (InterruptedException e) {
                        break;
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "capture thread error", t);
                    try {
                        Thread.sleep(IDLE_CAPTURE_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }

        private Bitmap captureFullFrame(RectF videoRect) {
            if (streamView == null || streamView.getWidth() == 0 || streamView.getHeight() == 0) {
                // logCapture("captureFullFrame: streamView null or zero size");
                return null;
            }
            int fullWidth = (int) videoRect.width();
            int fullHeight = (int) videoRect.height();
            if (fullWidth <= 0 || fullHeight <= 0) {
                // Log.w(TAG, "captureFullFrame: invalid videoRect size");
                return null;
            }
            final Bitmap[] fullBitmapHolder = new Bitmap[1];
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                fullBitmapHolder[0] = Bitmap.createBitmap(fullWidth, fullHeight, Bitmap.Config.ARGB_8888);
                final CountDownLatch latch = new CountDownLatch(1);
                final int[] copyResult = new int[]{PixelCopy.SUCCESS};
                final Object copyLock = new Object();
                final java.util.concurrent.atomic.AtomicBoolean isCancelled = new java.util.concurrent.atomic.AtomicBoolean(false);

                mainHandler.post(() -> {
                    synchronized (copyLock) {
                        if (isCancelled.get()) {
                            return;
                        }
                        if (streamView.getHolder().getSurface().isValid()) {
                            Rect srcRectPx = new Rect((int) videoRect.left, (int) videoRect.top,
                                    (int) videoRect.right, (int) videoRect.bottom);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                try {
                                    PixelCopy.request(streamView, srcRectPx, fullBitmapHolder[0], (copyResultValue) -> {
                                        copyResult[0] = copyResultValue;
                                        latch.countDown();
                                    }, new Handler(Looper.getMainLooper()));
                                } catch (IllegalArgumentException e) {
                                    Log.w(TAG, "captureFullFrame: PixelCopy failed to request", e);
                                    copyResult[0] = PixelCopy.ERROR_UNKNOWN;
                                    latch.countDown();
                                }
                            }
                        } else {
                            copyResult[0] = PixelCopy.ERROR_SOURCE_NO_DATA;
                            latch.countDown();
                        }
                    }
                });
                try {
                    if (!latch.await(500, TimeUnit.MILLISECONDS)) {
                        Log.w(TAG, "captureFullFrame: PixelCopy timeout");
                        synchronized (copyLock) {
                            isCancelled.set(true);
                            fullBitmapHolder[0].recycle();
                        }
                        return null;
                    }
                    if (copyResult[0] != PixelCopy.SUCCESS) {
                        if (copyResult[0] != PixelCopy.ERROR_SOURCE_NO_DATA) {
                            Log.w(TAG, "captureFullFrame: PixelCopy failed with error " + copyResult[0]);
                        }
                        fullBitmapHolder[0].recycle();
                        return null;
                    }
                } catch (InterruptedException e) {
                    if (running) {
                        Log.w(TAG, "captureFullFrame interrupted");
                    }
                    Thread.currentThread().interrupt();
                    synchronized (copyLock) {
                        isCancelled.set(true);
                        if (fullBitmapHolder[0] != null) {
                            fullBitmapHolder[0].recycle();
                        }
                    }
                    return null;
                }

            } else {
                // 低版本Android：返回一个纯色位图
                fullBitmapHolder[0] = Bitmap.createBitmap(fullWidth, fullHeight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(fullBitmapHolder[0]);
                canvas.drawColor(Color.argb(255, 100, 100, 255));
            }
            return fullBitmapHolder[0];
        }

        private Bitmap cropBitmapFromFullFrame(Bitmap fullBitmap, RectF srcRect) {
            int fullWidth = fullBitmap.getWidth();
            int fullHeight = fullBitmap.getHeight();
            int left = (int) (srcRect.left * fullWidth);
            int top = (int) (srcRect.top * fullHeight);
            int right = (int) (srcRect.right * fullWidth);
            int bottom = (int) (srcRect.bottom * fullHeight);
            if (left < 0) left = 0;
            if (top < 0) top = 0;
            if (right > fullWidth) right = fullWidth;
            if (bottom > fullHeight) bottom = fullHeight;
            if (left >= right || top >= bottom) {
                return null;
            }
            int width = right - left;
            int height = bottom - top;
            Bitmap cropped = Bitmap.createBitmap(fullBitmap, left, top, width, height);
            // Must copy to a non-hardware config if we want to manipulate it safely on CPU if needed,
            // though createBitmap from a software bitmap usually returns a software bitmap.
            // But if fullBitmap is hardware (from PixelCopy on P+), we might need copy.
            // Note: PixelCopy request ensures ARGB_8888.
            return cropped;
        }

        public void stopCapture() {
            running = false;
            interrupt();
        }
    }

    /**
     * 切换所有传送门的编辑模式。
     * 编辑模式有三种状态循环：无编辑 -> 编辑源区域 -> 编辑目标区域 -> 无编辑 ...
     * 每个传送门共享相同的编辑模式。
     */
    public void toggleEditingMode() {
        boolean newEditingState;
        int newEditMode;
        synchronized (portalLock) {
            if (portalConfigs.isEmpty()) {
                Log.d(TAG, "toggleEditingMode: portalConfigs empty, skipping");
                return;
            }
            // 确定当前全局编辑状态
            boolean anyEditing = false;
            int currentEditMode = 0;
            for (PortalConfig config : portalConfigs) {
                if (config.editing) {
                    anyEditing = true;
                    currentEditMode = config.editMode;
                    break;
                }
            }
            Log.d(TAG, "toggleEditingMode: anyEditing=" + anyEditing + ", currentEditMode=" + currentEditMode);
            if (!anyEditing) {
                // 当前无编辑，进入编辑源区域模式
                newEditingState = true;
                newEditMode = 1;
            } else if (currentEditMode == 1) {
                // 当前编辑源区域，切换到编辑目标区域
                newEditingState = true;
                newEditMode = 2;
            } else {
                // 当前编辑目标区域，退出编辑模式
                newEditingState = false;
                newEditMode = 0;
            }
            // 应用新状态
            for (PortalConfig config : portalConfigs) {
                config.editing = newEditingState;
                config.editMode = newEditMode;
            }
        }
        // 通知视图更新
        synchronized (portalLock) {
            for (PortalOverlayView view : portalViews.values()) {
                view.invalidate();
            }
        }
        Log.d(TAG, "切换编辑模式: editing=" + newEditingState + ", editMode=" + newEditMode);
    }

    /**
     * 返回当前是否处于编辑模式（任意传送门处于编辑状态即视为编辑模式）。
     */
    public boolean isEditingMode() {
        synchronized (portalLock) {
            for (PortalConfig config : portalConfigs) {
                if (config.editing) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 直接设置所有传送门的编辑模式。
     * @param mode 0: 无编辑, 1: 编辑源区域, 2: 编辑目标区域
     */
    public void setEditingMode(int mode) {
        synchronized (portalLock) {
            if (portalConfigs.isEmpty()) {
                return;
            }
            boolean editing = (mode != 0);
            for (PortalConfig config : portalConfigs) {
                config.editing = editing;
                config.editMode = mode;
            }
        }
        // 通知视图更新
        synchronized (portalLock) {
            for (PortalOverlayView view : portalViews.values()) {
                view.invalidate();
            }
        }
        Log.d(TAG, "setEditingMode: mode=" + mode);
    }

    /**
     * 返回当前编辑模式（1=源区域，2=目标区域，0=无编辑）。
     * 如果传送门之间模式不一致，返回第一个找到的编辑模式。
     */
    public int getCurrentEditMode() {
        synchronized (portalLock) {
            for (PortalConfig config : portalConfigs) {
                if (config.editing) {
                    return config.editMode;
                }
            }
        }
        return 0;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopCaptureThread();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (shouldCapture()) {
            startCaptureThread();
        }
    }
}
