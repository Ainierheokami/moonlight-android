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
    private static final String PREFS_NAME = "portal_configs";
    private static final String KEY_CONFIGS = "portal_configs_json";

    private List<PortalConfig> portalConfigs = new ArrayList<>();
    private Map<Integer, PortalOverlayView> portalViews = new HashMap<>();
    private Game game;
    private StreamView streamView;
    private BitmapCaptureThread captureThread;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Gson gson = new Gson();

    public PortalManagerView(Context context, Game game) {
        super(context);
        this.game = game;
        this.streamView = game.findViewById(R.id.surfaceView);
        loadConfigs();
        createViews();
        startCaptureThread();
    }

    private void loadConfigs() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_CONFIGS, null);
        if (json == null || json.isEmpty()) {
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
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = gson.toJson(portalConfigs);
        prefs.edit().putString(KEY_CONFIGS, json).apply();
        Log.d(TAG, "保存传送门配置，数量: " + portalConfigs.size());
    }

    public int generateNewId() {
        int maxId = 0;
        for (PortalConfig config : portalConfigs) {
            if (config.id > maxId) {
                maxId = config.id;
            }
        }
        return maxId + 1;
    }

    private void createViews() {
        for (PortalConfig config : portalConfigs) {
            if (config.enabled) {
                PortalOverlayView view = new PortalOverlayView(getContext());
                view.setConfig(config);
                view.setGame(game);
                portalViews.put(config.id, view);
                addView(view);
            }
        }
    }

    public void addPortal(PortalConfig config) {
        portalConfigs.add(config);
        if (config.enabled) {
            PortalOverlayView view = new PortalOverlayView(getContext());
            view.setConfig(config);
            view.setGame(game);
            portalViews.put(config.id, view);
            addView(view);
        }
        saveConfigs();
    }

    public void removePortal(int id) {
        PortalOverlayView view = portalViews.remove(id);
        if (view != null) {
            removeView(view);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            portalConfigs.removeIf(config -> config.id == id);
        }
        saveConfigs();
    }

    public void updatePortalBitmap(int id, Bitmap bitmap) {
        PortalOverlayView view = portalViews.get(id);
        if (view != null) {
            view.updatePortalBitmap(bitmap);
        }
    }

    private RectF getVideoContentRect() {
        int viewWidth = streamView.getWidth();
        int viewHeight = streamView.getHeight();
        if (viewWidth == 0 || viewHeight == 0) {
            Log.d(TAG, "getVideoContentRect: view size zero");
            return new RectF(0, 0, viewWidth, viewHeight);
        }
        PreferenceConfiguration prefConfig = game.getPrefConfig();
        if (prefConfig == null) {
            Log.d(TAG, "getVideoContentRect: prefConfig null");
            return new RectF(0, 0, viewWidth, viewHeight);
        }
        int videoWidth = prefConfig.width;
        int videoHeight = prefConfig.height;
        if (prefConfig.stretchVideo) {
            // 拉伸视频，填充整个视图
            Log.d(TAG, String.format("getVideoContentRect: stretchVideo true, view=%dx%d", viewWidth, viewHeight));
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
        Log.d(TAG, String.format("getVideoContentRect: view=%dx%d video=%dx%d aspect=%.3f content=(%d,%d,%d,%d)",
                viewWidth, viewHeight, videoWidth, videoHeight, videoAspect,
                contentLeft, contentTop, contentLeft + contentWidth, contentTop + contentHeight));
        return rect;
    }

    private void startCaptureThread() {
        // 启动一个线程定期截取源区域画面
        captureThread = new BitmapCaptureThread();
        captureThread.start();
    }

    private void stopCaptureThread() {
        if (captureThread != null) {
            captureThread.stopCapture();
            captureThread = null;
        }
    }

    private class BitmapCaptureThread extends Thread {
        private volatile boolean running = true;

        @Override
        public void run() {
            while (running && !isInterrupted()) {
                try {
                    Thread.sleep(33); // 约30 FPS
                } catch (InterruptedException e) {
                    break;
                }
                // 对每个激活的传送门截取源区域
                for (PortalConfig config : portalConfigs) {
                    if (!config.enabled) continue;
                    Bitmap bitmap = captureSourceRegion(config.srcRect);
                    if (bitmap != null) {
                        updatePortalBitmap(config.id, bitmap);
                    }
                }
            }
        }

        private Bitmap captureSourceRegion(RectF srcRect) {
            if (streamView == null || streamView.getWidth() == 0 || streamView.getHeight() == 0) {
                Log.d(TAG, "captureSourceRegion: streamView null or zero size");
                return null;
            }
            // 获取视频内容区域（考虑黑边和拉伸）
            RectF videoRect = getVideoContentRect();
            // 获取视频原始分辨率
            PreferenceConfiguration prefConfig = game.getPrefConfig();
            int videoWidth = 0, videoHeight = 0;
            boolean stretchVideo = false;
            if (prefConfig != null) {
                videoWidth = prefConfig.width;
                videoHeight = prefConfig.height;
                stretchVideo = prefConfig.stretchVideo;
            }
            Log.d(TAG, String.format("captureSourceRegion: streamView=%dx%d videoRect=(%.1f,%.1f,%.1f,%.1f) videoRect size=%.1fx%.1f",
                    streamView.getWidth(), streamView.getHeight(),
                    videoRect.left, videoRect.top, videoRect.right, videoRect.bottom,
                    videoRect.width(), videoRect.height()));
            Log.d(TAG, String.format("captureSourceRegion: video original=%dx%d stretchVideo=%b",
                    videoWidth, videoHeight, stretchVideo));
            // 将归一化坐标映射到视频内容区域（当前方法）
            float left = videoRect.left + srcRect.left * videoRect.width();
            float top = videoRect.top + srcRect.top * videoRect.height();
            float right = videoRect.left + srcRect.right * videoRect.width();
            float bottom = videoRect.top + srcRect.bottom * videoRect.height();
            // 转换为整数像素坐标
            int leftPx = (int) left;
            int topPx = (int) top;
            int rightPx = (int) right;
            int bottomPx = (int) bottom;
            // 确保矩形在视图范围内
            if (leftPx < 0) leftPx = 0;
            if (topPx < 0) topPx = 0;
            if (rightPx > streamView.getWidth()) rightPx = streamView.getWidth();
            if (bottomPx > streamView.getHeight()) bottomPx = streamView.getHeight();
            if (leftPx >= rightPx || topPx >= bottomPx) {
                Log.d(TAG, "captureSourceRegion: invalid capture rectangle");
                return null;
            }
            final int finalLeft = leftPx;
            final int finalTop = topPx;
            final int finalRight = rightPx;
            final int finalBottom = bottomPx;
            final int width = finalRight - finalLeft;
            final int height = finalBottom - finalTop;
            if (width <= 0 || height <= 0) {
                Log.d(TAG, "captureSourceRegion: zero width or height");
                return null;
            }

            // 调试日志
            Log.d(TAG, String.format("captureSourceRegion: srcRect=(%.3f,%.3f,%.3f,%.3f) captureRect=(%d,%d,%d,%d) size=%dx%d",
                    srcRect.left, srcRect.top, srcRect.right, srcRect.bottom,
                    finalLeft, finalTop, finalRight, finalBottom, width, height));
            // 计算基于原始视频分辨率的捕获矩形（用于诊断）
            boolean outOfBounds = false;
            if (videoWidth > 0 && videoHeight > 0) {
                int srcLeftPx = (int) (srcRect.left * videoWidth);
                int srcTopPx = (int) (srcRect.top * videoHeight);
                int srcRightPx = (int) (srcRect.right * videoWidth);
                int srcBottomPx = (int) (srcRect.bottom * videoHeight);
                Log.d(TAG, String.format("captureSourceRegion: original video capture rect=(%d,%d,%d,%d) size=%dx%d",
                        srcLeftPx, srcTopPx, srcRightPx, srcBottomPx,
                        srcRightPx - srcLeftPx, srcBottomPx - srcTopPx));
                // 检查是否部分超出原始视频缓冲区
                if (srcLeftPx < 0 || srcTopPx < 0 || srcRightPx > videoWidth || srcBottomPx > videoHeight) {
                    outOfBounds = true;
                    Log.d(TAG, "captureSourceRegion: source rectangle partially out of original video bounds, using fallback");
                }
            }

            // 始终使用回退方法，确保捕获的画面与用户看到的拉伸画面一致
            Log.d(TAG, "captureSourceRegion: using fallback method for all regions");
            return captureFallback(srcRect, videoRect, videoWidth, videoHeight, finalLeft, finalTop, width, height);
        }

        private Bitmap captureFallback(RectF srcRect, RectF videoRect, int videoWidth, int videoHeight,
                                       int viewLeft, int viewTop, int viewWidth, int viewHeight) {
            Log.d(TAG, "captureFallback: using fallback method");
            // 截取整个视频区域
            int fullWidth = (int) videoRect.width();
            int fullHeight = (int) videoRect.height();
            if (fullWidth <= 0 || fullHeight <= 0) {
                Log.w(TAG, "captureFallback: invalid videoRect size");
                return null;
            }
            final Bitmap[] fullBitmapHolder = new Bitmap[1];
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                fullBitmapHolder[0] = Bitmap.createBitmap(fullWidth, fullHeight, Bitmap.Config.ARGB_8888);
                final CountDownLatch latch = new CountDownLatch(1);
                final int[] copyResult = new int[]{PixelCopy.SUCCESS};
                mainHandler.post(() -> {
                    if (streamView.getHolder().getSurface().isValid()) {
                        Rect srcRectPx = new Rect((int) videoRect.left, (int) videoRect.top,
                                (int) videoRect.right, (int) videoRect.bottom);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            PixelCopy.request(streamView, srcRectPx, fullBitmapHolder[0], (copyResultValue) -> {
                                copyResult[0] = copyResultValue;
                                latch.countDown();
                            }, new Handler(Looper.getMainLooper()));
                        }
                    } else {
                        copyResult[0] = PixelCopy.ERROR_SOURCE_NO_DATA;
                        latch.countDown();
                    }
                });
                try {
                    if (!latch.await(500, TimeUnit.MILLISECONDS)) {
                        Log.w(TAG, "captureFallback: PixelCopy timeout");
                        fullBitmapHolder[0].recycle();
                        return null;
                    }
                    if (copyResult[0] != PixelCopy.SUCCESS) {
                        Log.w(TAG, "captureFallback: PixelCopy failed with error " + copyResult[0]);
                        fullBitmapHolder[0].recycle();
                        return null;
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "captureFallback interrupted", e);
                    if (fullBitmapHolder[0] != null) {
                        fullBitmapHolder[0].recycle();
                    }
                    return null;
                }
            } else {
                // 低版本Android：返回一个纯色位图
                fullBitmapHolder[0] = Bitmap.createBitmap(fullWidth, fullHeight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(fullBitmapHolder[0]);
                canvas.drawColor(Color.argb(255, 100, 100, 255));
            }
            // 从完整位图中裁剪出目标区域
            // 计算目标区域在完整位图中的相对位置
            int cropLeft = viewLeft - (int) videoRect.left;
            int cropTop = viewTop - (int) videoRect.top;
            // 确保裁剪区域在边界内
            if (cropLeft < 0) cropLeft = 0;
            if (cropTop < 0) cropTop = 0;
            if (cropLeft + viewWidth > fullWidth) viewWidth = fullWidth - cropLeft;
            if (cropTop + viewHeight > fullHeight) viewHeight = fullHeight - cropTop;
            if (viewWidth <= 0 || viewHeight <= 0) {
                Log.w(TAG, "captureFallback: crop area out of bounds");
                fullBitmapHolder[0].recycle();
                return null;
            }
            Bitmap cropped = Bitmap.createBitmap(fullBitmapHolder[0], cropLeft, cropTop, viewWidth, viewHeight);
            fullBitmapHolder[0].recycle();
            Log.d(TAG, String.format("captureFallback: cropped bitmap size=%dx%d", cropped.getWidth(), cropped.getHeight()));
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
        int newEditMode;
        boolean newEditingState;
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
        // 通知视图更新
        for (PortalOverlayView view : portalViews.values()) {
            view.invalidate();
        }
        Log.d(TAG, "切换编辑模式: editing=" + newEditingState + ", editMode=" + newEditMode);
    }

    /**
     * 返回当前是否处于编辑模式（任意传送门处于编辑状态即视为编辑模式）。
     */
    public boolean isEditingMode() {
        for (PortalConfig config : portalConfigs) {
            if (config.editing) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回当前编辑模式（1=源区域，2=目标区域，0=无编辑）。
     * 如果传送门之间模式不一致，返回第一个找到的编辑模式。
     */
    public int getCurrentEditMode() {
        for (PortalConfig config : portalConfigs) {
            if (config.editing) {
                return config.editMode;
            }
        }
        return 0;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopCaptureThread();
    }
}