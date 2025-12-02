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
        portalConfigs.removeIf(config -> config.id == id);
        saveConfigs();
    }

    public void updatePortalBitmap(int id, Bitmap bitmap) {
        PortalOverlayView view = portalViews.get(id);
        if (view != null) {
            view.updatePortalBitmap(bitmap);
        }
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
                return null;
            }
            // 将归一化坐标转换为像素坐标
            int viewWidth = streamView.getWidth();
            int viewHeight = streamView.getHeight();
            int left = (int) (srcRect.left * viewWidth);
            int top = (int) (srcRect.top * viewHeight);
            int right = (int) (srcRect.right * viewWidth);
            int bottom = (int) (srcRect.bottom * viewHeight);
            // 确保矩形在视图范围内
            if (left < 0) left = 0;
            if (top < 0) top = 0;
            if (right > viewWidth) right = viewWidth;
            if (bottom > viewHeight) bottom = viewHeight;
            if (left >= right || top >= bottom) {
                return null;
            }
            final int finalLeft = left;
            final int finalTop = top;
            final int finalRight = right;
            final int finalBottom = bottom;
            final int width = finalRight - finalLeft;
            final int height = finalBottom - finalTop;
            if (width <= 0 || height <= 0) {
                return null;
            }

            // 使用PixelCopy API（Android 7.0+）截取SurfaceView区域
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    final CountDownLatch latch = new CountDownLatch(1);
                    final int[] copyResult = new int[]{PixelCopy.SUCCESS};
                    // PixelCopy.request必须在UI线程调用
                    mainHandler.post(() -> {
                        if (streamView.getHolder().getSurface().isValid()) {
                            Rect srcRectPx = new Rect(finalLeft, finalTop, finalRight, finalBottom);
                            PixelCopy.request(streamView, srcRectPx, bitmap, (copyResultValue) -> {
                                copyResult[0] = copyResultValue;
                                latch.countDown();
                            }, new Handler(Looper.getMainLooper()));
                        } else {
                            copyResult[0] = PixelCopy.ERROR_SOURCE_NO_DATA;
                            latch.countDown();
                        }
                    });
                    // 等待复制完成，最多500ms
                    if (!latch.await(500, TimeUnit.MILLISECONDS)) {
                        Log.w(TAG, "PixelCopy timeout");
                        return null;
                    }
                    if (copyResult[0] == PixelCopy.SUCCESS) {
                        return bitmap;
                    } else {
                        Log.w(TAG, "PixelCopy failed with error: " + copyResult[0]);
                        bitmap.recycle();
                        return null;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "PixelCopy exception", e);
                    return null;
                }
            } else {
                // 低版本Android：使用View.getDrawingCache（已弃用）或创建一个空位图作为占位
                // 这里返回一个纯色位图用于测试
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(Color.argb(255, 100, 100, 255)); // 蓝色半透明
                return bitmap;
            }
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