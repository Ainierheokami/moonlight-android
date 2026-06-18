package com.limelight.portal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import com.limelight.Game;
import com.limelight.nvstream.input.MouseButtonPacket;

import java.util.Locale;

/**
 * 单个传送门的叠加视图
 * 负责：
 * 1. 绘制源区域选框（可拖拽调整）
 * 2. 绘制目标区域（显示复制的画面）
 * 3. 处理目标区域的触摸输入映射
 */
public class PortalOverlayView extends View {
    private static final String TAG = "PortalOverlayView";
    private static final boolean DEBUG_DRAW = false;

    private PortalConfig config;
    private Game game;
    private Bitmap portalBitmap; // 用于显示复制的画面（临时）
    private Paint borderPaint;
    private Paint handlePaint;
    private Paint bitmapPaint;

    // 拖拽状态
    private boolean isDragging = false;
    private boolean isResizing = false;
    private int dragHandle = -1; // 0: 左上, 1: 右上, 2: 左下, 3: 右下, -1: 移动整个选框
    private float lastTouchX, lastTouchY;

    // 双击检测
    private long lastTouchTime = 0;
    private static final int DOUBLE_TAP_THRESHOLD = 300; // 毫秒
    private float lastTapX, lastTapY;
    private static final float TOUCH_SLOP = 20f; // 像素容差

    // 输入映射状态
    private boolean isPortalTouchActive = false;

    // 选框手柄大小
    private static final float HANDLE_SIZE = 40f;

    public PortalOverlayView(Context context) {
        super(context);
        init();
    }

    public PortalOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PortalOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.GREEN);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);

        handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setColor(Color.RED);
        handlePaint.setStyle(Paint.Style.FILL);

        bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bitmapPaint.setFilterBitmap(true);
    }

    public void setConfig(PortalConfig config) {
        this.config = config;
        invalidate();
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public void updatePortalBitmap(Bitmap bitmap) {
        if (portalBitmap != null && !portalBitmap.isRecycled()) {
            portalBitmap.recycle();
        }
        portalBitmap = bitmap;
        invalidate();
    }

    public void clearPortalBitmap() {
        if (portalBitmap != null && !portalBitmap.isRecycled()) {
            portalBitmap.recycle();
        }
        portalBitmap = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (config == null || !config.enabled) {
            return;
        }

        // 将目标矩形从屏幕坐标转换为视图坐标
        RectF dstRectView = screenToViewRect(config.dstRect);

        RectF displayRectView = getDisplayRectView(dstRectView);

        // 绘制目标区域背景（半透明黑色）
        canvas.drawRect(dstRectView, new Paint(Color.argb(128, 0, 0, 0)));

        // 如果有关联的位图，绘制到目标区域（1:1拉伸填充）
        if (portalBitmap != null && !portalBitmap.isRecycled()) {
            float bitmapWidth = portalBitmap.getWidth();
            float bitmapHeight = portalBitmap.getHeight();
            float dstWidth = displayRectView.width();
            float dstHeight = displayRectView.height();
            logDraw(String.format("onDraw: bitmap=%dx%d dstView=%dx%d stretch to dstRectView",
                    (int)bitmapWidth, (int)bitmapHeight, (int)dstWidth, (int)dstHeight));
            canvas.drawBitmap(portalBitmap, null, displayRectView, bitmapPaint);
        }

        // 绘制目标区域边框（如果边框宽度>0）
        if (config.borderWidth > 0) {
            borderPaint.setColor(config.borderColor);
            borderPaint.setStrokeWidth(config.borderWidth);
            canvas.drawRect(displayRectView, borderPaint);
        }

        // 如果当前是编辑模式，绘制编辑矩形边框和拖拽手柄
        if (isEditing()) {
            RectF editRectView = screenToViewRect(getEditRectScreen());
            // 绘制编辑矩形边框（蓝色虚线）
            Paint editRectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            editRectPaint.setColor(Color.BLUE);
            editRectPaint.setStyle(Paint.Style.STROKE);
            editRectPaint.setStrokeWidth(3f);
            editRectPaint.setPathEffect(new DashPathEffect(new float[]{10, 5}, 0));
            canvas.drawRect(editRectView, editRectPaint);
            // 绘制手柄
            drawHandles(canvas, editRectView);
        }
    }

    private boolean isEditing() {
        return config != null && config.editing;
    }

    /**
     * 获取当前编辑模式对应的矩形（屏幕坐标）
     */
    private RectF getEditRectScreen() {
        if (config.editMode == 1) {
            RectF videoRectScreen = getVideoContentRectScreen();
            if (videoRectScreen == null) return config.dstRect;
            float left = videoRectScreen.left + config.srcRect.left * videoRectScreen.width();
            float top = videoRectScreen.top + config.srcRect.top * videoRectScreen.height();
            float right = videoRectScreen.left + config.srcRect.right * videoRectScreen.width();
            float bottom = videoRectScreen.top + config.srcRect.bottom * videoRectScreen.height();
            logDraw(String.format("getEditRectScreen src: videoRect=(%.1f,%.1f,%.1f,%.1f) srcRect=(%.3f,%.3f,%.3f,%.3f) screenRect=(%.1f,%.1f,%.1f,%.1f)",
                    videoRectScreen.left, videoRectScreen.top, videoRectScreen.right, videoRectScreen.bottom,
                    config.srcRect.left, config.srcRect.top, config.srcRect.right, config.srcRect.bottom,
                    left, top, right, bottom));
            return new RectF(left, top, right, bottom);
        } else {
            // editMode == 2 或默认：目标区域（已经是屏幕坐标）
            logDraw(String.format("getEditRectScreen dst: dstRect=(%.1f,%.1f,%.1f,%.1f)",
                    config.dstRect.left, config.dstRect.top, config.dstRect.right, config.dstRect.bottom));
            return config.dstRect;
        }
    }

    /**
     * 将屏幕坐标矩形转换为当前视图的坐标矩形
     */
    private RectF screenToViewRect(RectF screenRect) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        float viewLeft = screenRect.left - location[0];
        float viewTop = screenRect.top - location[1];
        float viewRight = screenRect.right - location[0];
        float viewBottom = screenRect.bottom - location[1];
        logDraw(String.format("screenToViewRect: screen=(%.1f,%.1f,%.1f,%.1f) viewLoc=(%d,%d) viewRect=(%.1f,%.1f,%.1f,%.1f)",
                screenRect.left, screenRect.top, screenRect.right, screenRect.bottom,
                location[0], location[1], viewLeft, viewTop, viewRight, viewBottom));
        return new RectF(viewLeft, viewTop, viewRight, viewBottom);
    }

    private void drawHandles(Canvas canvas, RectF rect) {
        float left = rect.left;
        float top = rect.top;
        float right = rect.right;
        float bottom = rect.bottom;
        float halfHandle = HANDLE_SIZE / 2;

        logDraw(String.format("drawHandles: rect=(%.1f,%.1f,%.1f,%.1f) view size=%dx%d",
                left, top, right, bottom, getWidth(), getHeight()));

        // 四个角的手柄
        canvas.drawCircle(left, top, halfHandle, handlePaint);
        canvas.drawCircle(right, top, halfHandle, handlePaint);
        canvas.drawCircle(left, bottom, halfHandle, handlePaint);
        canvas.drawCircle(right, bottom, halfHandle, handlePaint);
    }

    private RectF getDisplayRectView(RectF dstRectView) {
        if (config == null) {
            return dstRectView;
        }

        float scale = Math.max(0.25f, Math.min(2.0f, config.scale));
        float width = dstRectView.width() * scale;
        float height = dstRectView.height() * scale;

        if (config.aspectRatioMode == 1 && portalBitmap != null && !portalBitmap.isRecycled() && portalBitmap.getHeight() > 0) {
            float bitmapAspect = (float) portalBitmap.getWidth() / portalBitmap.getHeight();
            float targetAspect = width / height;
            if (targetAspect > bitmapAspect) {
                width = height * bitmapAspect;
            } else {
                height = width / bitmapAspect;
            }
        } else if (config.aspectRatioMode == 2) {
            float side = Math.min(width, height);
            width = side;
            height = side;
        }

        float centerX = dstRectView.centerX();
        float centerY = dstRectView.centerY();
        return new RectF(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f);
    }

    private void logDraw(String message) {
        if (DEBUG_DRAW) {
            Log.d(TAG, message);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (config == null || !config.enabled) {
            return super.onTouchEvent(event);
        }

        float x = event.getX();
        float y = event.getY();
        int action = event.getActionMasked();

        // 双击检测（仅在编辑模式下有效）
        if (action == MotionEvent.ACTION_DOWN) {
            long currentTime = System.currentTimeMillis();
            if (isEditing() && currentTime - lastTouchTime < DOUBLE_TAP_THRESHOLD
                    && Math.abs(x - lastTapX) < TOUCH_SLOP
                    && Math.abs(y - lastTapY) < TOUCH_SLOP) {
                // 双击事件
                handleDoubleTap(x, y);
                lastTouchTime = 0; // 重置以避免重复检测
                return true;
            } else {
                lastTouchTime = currentTime;
                lastTapX = x;
                lastTapY = y;
            }
        }

        // 编辑模式：处理拖拽和调整大小
        if (isEditing()) {
            RectF editRectScreen = getEditRectScreen();
            RectF editRectView = screenToViewRect(editRectScreen);
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    // 检查是否点击在手柄上（使用视图坐标）
                    dragHandle = getTouchedHandle(x, y, editRectView);
                    if (dragHandle >= 0) {
                        isResizing = true;
                    } else if (editRectView.contains(x, y)) {
                        isDragging = true;
                    } else {
                        return false; // 点击外部，不处理
                    }
                    lastTouchX = x;
                    lastTouchY = y;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isDragging || isResizing) {
                        float dx = x - lastTouchX;
                        float dy = y - lastTouchY;
                        updateRect(dx, dy, editRectScreen);
                        lastTouchX = x;
                        lastTouchY = y;
                        invalidate();
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging || isResizing) {
                        isDragging = false;
                        isResizing = false;
                        dragHandle = -1;
                        // 保存配置
                        saveConfig();
                        return true;
                    }
                    break;
            }
            return super.onTouchEvent(event);
        } else {
            // 非编辑模式：输入映射
            float screenX = viewToScreenX(x);
            float screenY = viewToScreenY(y);
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    if (config.dstRect.contains(screenX, screenY)) {
                        isPortalTouchActive = true;
                        return handlePortalTouch(event);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (isPortalTouchActive) {
                        return handlePortalTouch(event);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isPortalTouchActive) {
                        isPortalTouchActive = false;
                        return handlePortalTouch(event);
                    }
                    break;
            }
            return false;
        }
    }

    private int getTouchedHandle(float x, float y, RectF rect) {
        float left = rect.left;
        float top = rect.top;
        float right = rect.right;
        float bottom = rect.bottom;
        float tolerance = HANDLE_SIZE;

        if (Math.abs(x - left) < tolerance && Math.abs(y - top) < tolerance) return 0;
        if (Math.abs(x - right) < tolerance && Math.abs(y - top) < tolerance) return 1;
        if (Math.abs(x - left) < tolerance && Math.abs(y - bottom) < tolerance) return 2;
        if (Math.abs(x - right) < tolerance && Math.abs(y - bottom) < tolerance) return 3;
        return -1;
    }

    private float viewToScreenX(float x) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return x + location[0];
    }

    private float viewToScreenY(float y) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return y + location[1];
    }

    private void updateRect(float dx, float dy, RectF editRectScreen) {
        if (config.editMode == 1) {
            RectF videoBounds = getVideoContentRectScreen();
            if (videoBounds == null || videoBounds.width() <= 0 || videoBounds.height() <= 0) return;

            // 计算屏幕坐标中的新矩形
            RectF newScreenRect = new RectF(editRectScreen);
            if (isDragging) {
                newScreenRect.offset(dx, dy);
                clampRectInside(newScreenRect, videoBounds);
            } else if (isResizing) {
                switch (dragHandle) {
                    case 0: // 左上
                        newScreenRect.left += dx;
                        newScreenRect.top += dy;
                        break;
                    case 1: // 右上
                        newScreenRect.right += dx;
                        newScreenRect.top += dy;
                        break;
                    case 2: // 左下
                        newScreenRect.left += dx;
                        newScreenRect.bottom += dy;
                        break;
                    case 3: // 右下
                        newScreenRect.right += dx;
                        newScreenRect.bottom += dy;
                        break;
                }
                // 确保矩形有效
                if (newScreenRect.width() < HANDLE_SIZE * 2) {
                    if (dragHandle == 0 || dragHandle == 2) {
                        newScreenRect.left = newScreenRect.right - HANDLE_SIZE * 2;
                    } else {
                        newScreenRect.right = newScreenRect.left + HANDLE_SIZE * 2;
                    }
                }
                if (newScreenRect.height() < HANDLE_SIZE * 2) {
                    if (dragHandle == 0 || dragHandle == 1) {
                        newScreenRect.top = newScreenRect.bottom - HANDLE_SIZE * 2;
                    } else {
                        newScreenRect.bottom = newScreenRect.top + HANDLE_SIZE * 2;
                    }
                }
                clampRectInside(newScreenRect, videoBounds);
            }

            // 将屏幕坐标转换回归一化坐标
            float left = (newScreenRect.left - videoBounds.left) / videoBounds.width();
            float top = (newScreenRect.top - videoBounds.top) / videoBounds.height();
            float right = (newScreenRect.right - videoBounds.left) / videoBounds.width();
            float bottom = (newScreenRect.bottom - videoBounds.top) / videoBounds.height();
            // 限制在 0-1 范围内
            left = Math.max(0, Math.min(1, left));
            top = Math.max(0, Math.min(1, top));
            right = Math.max(0, Math.min(1, right));
            bottom = Math.max(0, Math.min(1, bottom));
            if (right <= left) right = Math.min(1f, left + 0.01f);
            if (bottom <= top) bottom = Math.min(1f, top + 0.01f);
            config.srcRect.set(left, top, right, bottom);
        } else {
            RectF viewBounds = getScreenBounds();
            // 编辑目标区域：更新 dstRect（屏幕坐标）
            if (isDragging) {
                config.dstRect.offset(dx, dy);
                clampRectInside(config.dstRect, viewBounds);
            } else if (isResizing) {
                switch (dragHandle) {
                    case 0: // 左上
                        config.dstRect.left += dx;
                        config.dstRect.top += dy;
                        break;
                    case 1: // 右上
                        config.dstRect.right += dx;
                        config.dstRect.top += dy;
                        break;
                    case 2: // 左下
                        config.dstRect.left += dx;
                        config.dstRect.bottom += dy;
                        break;
                    case 3: // 右下
                        config.dstRect.right += dx;
                        config.dstRect.bottom += dy;
                        break;
                }
                // 确保矩形有效
                if (config.dstRect.width() < HANDLE_SIZE * 2) {
                    if (dragHandle == 0 || dragHandle == 2) {
                        config.dstRect.left = config.dstRect.right - HANDLE_SIZE * 2;
                    } else {
                        config.dstRect.right = config.dstRect.left + HANDLE_SIZE * 2;
                    }
                }
                if (config.dstRect.height() < HANDLE_SIZE * 2) {
                    if (dragHandle == 0 || dragHandle == 1) {
                        config.dstRect.top = config.dstRect.bottom - HANDLE_SIZE * 2;
                    } else {
                        config.dstRect.bottom = config.dstRect.top + HANDLE_SIZE * 2;
                    }
                }
                clampRectInside(config.dstRect, viewBounds);
            }
        }
    }

    private RectF getScreenBounds() {
        int[] location = new int[2];
        getLocationOnScreen(location);
        return new RectF(location[0], location[1], location[0] + getWidth(), location[1] + getHeight());
    }

    private void clampRectInside(RectF rect, RectF bounds) {
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }

        float minWidth = Math.min(HANDLE_SIZE * 2, bounds.width());
        float minHeight = Math.min(HANDLE_SIZE * 2, bounds.height());
        if (rect.width() < minWidth) {
            rect.right = rect.left + minWidth;
        }
        if (rect.height() < minHeight) {
            rect.bottom = rect.top + minHeight;
        }

        if (rect.width() >= bounds.width()) {
            rect.left = bounds.left;
            rect.right = bounds.right;
        } else {
            if (rect.left < bounds.left) rect.offset(bounds.left - rect.left, 0);
            if (rect.right > bounds.right) rect.offset(bounds.right - rect.right, 0);
        }

        if (rect.height() >= bounds.height()) {
            rect.top = bounds.top;
            rect.bottom = bounds.bottom;
        } else {
            if (rect.top < bounds.top) rect.offset(0, bounds.top - rect.top);
            if (rect.bottom > bounds.bottom) rect.offset(0, bounds.bottom - rect.bottom);
        }
    }

    private void saveConfig() {
        // 通知PortalManagerView保存配置
        if (getParent() instanceof PortalManagerView) {
            ((PortalManagerView) getParent()).saveConfigs();
        }
    }

    /**
     * 获取源区域在屏幕上的矩形（像素坐标）
     */
    private RectF getSrcRectScreen() {
        RectF videoRectScreen = getVideoContentRectScreen();
        if (videoRectScreen == null) return new RectF();
        float left = videoRectScreen.left + config.srcRect.left * videoRectScreen.width();
        float top = videoRectScreen.top + config.srcRect.top * videoRectScreen.height();
        float right = videoRectScreen.left + config.srcRect.right * videoRectScreen.width();
        float bottom = videoRectScreen.top + config.srcRect.bottom * videoRectScreen.height();
        return new RectF(left, top, right, bottom);
    }

    /**
     * 处理双击事件
     * 仅在编辑模式下弹出设置对话框；非编辑模式下无操作。
     */
    private void handleDoubleTap(float x, float y) {
        // 将视图坐标转换为屏幕坐标
        int[] viewLocation = new int[2];
        getLocationOnScreen(viewLocation);
        float screenX = x + viewLocation[0];
        float screenY = y + viewLocation[1];

        RectF srcRectScreen = getSrcRectScreen();
        RectF dstRectScreen = config.dstRect;

        // 判断双击位置
        boolean inSrc = srcRectScreen.contains(screenX, screenY);
        boolean inDst = dstRectScreen.contains(screenX, screenY);

        // 记录双击位置
        Log.d(TAG, String.format("handleDoubleTap: (%.1f,%.1f) inSrc=%b inDst=%b editing=%b",
                screenX, screenY, inSrc, inDst, config.editing));

        if (config.editing) {
            // 编辑模式下弹出设置对话框
            showPortalSettingsDialog();
        } else {
            // 非编辑模式下，双击无操作（不进入编辑模式，不弹出对话框）
            Log.d(TAG, "非编辑模式下双击，无操作");
        }
    }

    /**
     * 显示传送门设置对话框
     */
    private void showPortalSettingsDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("画面映射设置 - " + config.name);
        String[] items = {
                "删除画面映射",
                "设置帧率限制",
                "设置缩放比例",
                "设置宽高比",
                "切换编辑模式",
                "取消"
        };
        builder.setItems(items, (dialog, which) -> {
            switch (which) {
                case 0: // 删除传送门
                    if (getParent() instanceof PortalManagerView) {
                        ((PortalManagerView) getParent()).removePortal(config.id);
                    }
                    break;
                case 1: // 设置帧率限制
                    showFrameRateLimitDialog();
                    break;
                case 2: // 设置缩放比例
                    showScaleDialog();
                    break;
                case 3: // 设置宽高比
                    showAspectRatioDialog();
                    break;
                case 4: // 切换编辑模式
                    toggleEditingMode();
                    break;
                case 5: // 取消
                    // 什么都不做
                    break;
            }
        });
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    private void toggleEditingMode() {
        if (config.editing) {
            config.editMode = (config.editMode == 1) ? 2 : 1;
        } else {
            config.editing = true;
            config.editMode = 1;
        }
        saveConfig();
        invalidate();
        Log.d(TAG, "切换编辑模式: editing=" + config.editing + ", editMode=" + config.editMode);
    }

    private void showFrameRateLimitDialog() {
        final int[] fpsValues = new int[]{5, 10, 15, 20, 30, 60};
        String[] labels = new String[fpsValues.length];
        int checked = 4;
        int current = config.frameRateLimit <= 0 ? 30 : config.frameRateLimit;
        for (int i = 0; i < fpsValues.length; i++) {
            labels[i] = fpsValues[i] + " FPS";
            if (fpsValues[i] == current) {
                checked = i;
            }
        }

        new android.app.AlertDialog.Builder(getContext())
                .setTitle("设置帧率限制")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    config.frameRateLimit = fpsValues[which];
                    saveConfig();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showScaleDialog() {
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(24);
        content.setPadding(padding, dp(18), padding, dp(6));

        TextView valueText = new TextView(getContext());
        valueText.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        valueText.setTextSize(20);
        content.addView(valueText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        SeekBar seekBar = new SeekBar(getContext());
        seekBar.setMax(175);
        seekBar.setProgress(Math.round((Math.max(0.25f, Math.min(2.0f, config.scale)) - 0.25f) * 100f));
        content.addView(seekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Runnable updateText = () -> valueText.setText(String.format(Locale.getDefault(), "%.0f%%", (0.25f + seekBar.getProgress() / 100f) * 100f));
        updateText.run();
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateText.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        new android.app.AlertDialog.Builder(getContext())
                .setTitle("设置缩放比例")
                .setView(content)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    config.scale = 0.25f + seekBar.getProgress() / 100f;
                    saveConfig();
                    invalidate();
                })
                .setNeutralButton("重置", (dialog, which) -> {
                    config.scale = 1.0f;
                    saveConfig();
                    invalidate();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAspectRatioDialog() {
        String[] labels = new String[]{"拉伸填充", "保持源比例", "正方形"};
        int checked = Math.max(0, Math.min(labels.length - 1, config.aspectRatioMode));
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("设置宽高比")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    config.aspectRatioMode = which;
                    saveConfig();
                    invalidate();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public boolean handlePortalTouch(MotionEvent event) {
        if (game == null || game.getConnection() == null) {
            return false;
        }

        if (config.dstRect.width() <= 0 || config.dstRect.height() <= 0) {
            return false;
        }

        float touchRatioX = (viewToScreenX(event.getX()) - config.dstRect.left) / config.dstRect.width();
        float touchRatioY = (viewToScreenY(event.getY()) - config.dstRect.top) / config.dstRect.height();
        touchRatioX = clamp01(touchRatioX);
        touchRatioY = clamp01(touchRatioY);

        float sourceX = config.srcRect.left + touchRatioX * config.srcRect.width();
        float sourceY = config.srcRect.top + touchRatioY * config.srcRect.height();
        sourceX = clamp01(sourceX);
        sourceY = clamp01(sourceY);

        int referenceWidth = getHostReferenceWidth();
        int referenceHeight = getHostReferenceHeight();
        if (referenceWidth <= 1 || referenceHeight <= 1) {
            return false;
        }

        short eventX = (short) Math.round(sourceX * (referenceWidth - 1));
        short eventY = (short) Math.round(sourceY * (referenceHeight - 1));
        short refWidth = (short) referenceWidth;
        short refHeight = (short) referenceHeight;

        // 发送输入事件（简化版，仅处理鼠标左键）
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                game.getConnection().sendMousePosition(eventX, eventY, refWidth, refHeight);
                game.getConnection().sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
                break;
            case MotionEvent.ACTION_UP:
                game.getConnection().sendMousePosition(eventX, eventY, refWidth, refHeight);
                game.getConnection().sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                break;
            case MotionEvent.ACTION_CANCEL:
                game.getConnection().sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                break;
            case MotionEvent.ACTION_MOVE:
                game.getConnection().sendMousePosition(eventX, eventY, refWidth, refHeight);
                break;
        }
        return true;
    }

    private RectF getVideoContentRectScreen() {
        View streamView = game != null ? game.getStreamView() : null;
        RectF rectInView = getVideoContentRectInView();
        if (streamView == null || rectInView == null) {
            return null;
        }
        int[] location = new int[2];
        streamView.getLocationOnScreen(location);
        return new RectF(
                location[0] + rectInView.left,
                location[1] + rectInView.top,
                location[0] + rectInView.right,
                location[1] + rectInView.bottom);
    }

    private RectF getVideoContentRectInView() {
        View streamView = game != null ? game.getStreamView() : null;
        if (streamView == null || streamView.getWidth() <= 0 || streamView.getHeight() <= 0) {
            return null;
        }

        float viewWidth = streamView.getWidth();
        float viewHeight = streamView.getHeight();
        com.limelight.preferences.PreferenceConfiguration prefConfig = game.getPrefConfig();
        if (prefConfig == null || prefConfig.stretchVideo || prefConfig.width <= 0 || prefConfig.height <= 0) {
            return new RectF(0, 0, viewWidth, viewHeight);
        }

        float viewAspect = viewWidth / viewHeight;
        float videoAspect = (float) prefConfig.width / prefConfig.height;
        float contentWidth;
        float contentHeight;
        float contentLeft = 0;
        float contentTop = 0;
        if (viewAspect > videoAspect) {
            contentHeight = viewHeight;
            contentWidth = contentHeight * videoAspect;
            contentLeft = (viewWidth - contentWidth) / 2f;
        } else {
            contentWidth = viewWidth;
            contentHeight = contentWidth / videoAspect;
            contentTop = (viewHeight - contentHeight) / 2f;
        }
        return new RectF(contentLeft, contentTop, contentLeft + contentWidth, contentTop + contentHeight);
    }

    private int getHostReferenceWidth() {
        com.limelight.preferences.PreferenceConfiguration prefConfig = game.getPrefConfig();
        if (prefConfig != null && prefConfig.width > 1) {
            return Math.min(prefConfig.width, Short.MAX_VALUE);
        }
        RectF videoRect = getVideoContentRectInView();
        return videoRect != null ? Math.min(Math.max(2, Math.round(videoRect.width())), Short.MAX_VALUE) : 0;
    }

    private int getHostReferenceHeight() {
        com.limelight.preferences.PreferenceConfiguration prefConfig = game.getPrefConfig();
        if (prefConfig != null && prefConfig.height > 1) {
            return Math.min(prefConfig.height, Short.MAX_VALUE);
        }
        RectF videoRect = getVideoContentRectInView();
        return videoRect != null ? Math.min(Math.max(2, Math.round(videoRect.height())), Short.MAX_VALUE) : 0;
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (portalBitmap != null && !portalBitmap.isRecycled()) {
            portalBitmap.recycle();
            portalBitmap = null;
        }
    }
}
