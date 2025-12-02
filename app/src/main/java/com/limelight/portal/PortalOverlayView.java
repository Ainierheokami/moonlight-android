package com.limelight.portal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.limelight.Game;
import com.limelight.nvstream.input.MouseButtonPacket;

/**
 * 单个传送门的叠加视图
 * 负责：
 * 1. 绘制源区域选框（可拖拽调整）
 * 2. 绘制目标区域（显示复制的画面）
 * 3. 处理目标区域的触摸输入映射
 */
public class PortalOverlayView extends View {
    private static final String TAG = "PortalOverlayView";

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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (config == null || !config.enabled) {
            return;
        }

        // 绘制目标区域背景（半透明黑色）
        canvas.drawRect(config.dstRect, new Paint(Color.argb(128, 0, 0, 0)));

        // 如果有关联的位图，绘制到目标区域
        if (portalBitmap != null && !portalBitmap.isRecycled()) {
            canvas.drawBitmap(portalBitmap, null, config.dstRect, bitmapPaint);
        }

        // 绘制目标区域边框
        borderPaint.setColor(config.borderColor);
        borderPaint.setStrokeWidth(config.borderWidth);
        canvas.drawRect(config.dstRect, borderPaint);

        // 如果当前是编辑模式，绘制拖拽手柄
        if (isEditing()) {
            drawHandles(canvas, getEditRectScreen());
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
            // 编辑源区域：将归一化的 srcRect 转换为屏幕坐标
            View streamView = game.getStreamView();
            if (streamView == null) return config.dstRect; // 回退
            int streamWidth = streamView.getWidth();
            int streamHeight = streamView.getHeight();
            // 获取 StreamView 在屏幕上的位置（相对于本 View 的坐标？）
            // 由于 PortalOverlayView 位于叠加层，其坐标与屏幕一致，可以直接使用 StreamView 的全局位置
            int[] location = new int[2];
            streamView.getLocationOnScreen(location);
            float left = config.srcRect.left * streamWidth + location[0];
            float top = config.srcRect.top * streamHeight + location[1];
            float right = config.srcRect.right * streamWidth + location[0];
            float bottom = config.srcRect.bottom * streamHeight + location[1];
            return new RectF(left, top, right, bottom);
        } else {
            // editMode == 2 或默认：目标区域（已经是屏幕坐标）
            return config.dstRect;
        }
    }

    private void drawHandles(Canvas canvas, RectF rect) {
        float left = rect.left;
        float top = rect.top;
        float right = rect.right;
        float bottom = rect.bottom;
        float halfHandle = HANDLE_SIZE / 2;

        // 四个角的手柄
        canvas.drawCircle(left, top, halfHandle, handlePaint);
        canvas.drawCircle(right, top, halfHandle, handlePaint);
        canvas.drawCircle(left, bottom, halfHandle, handlePaint);
        canvas.drawCircle(right, bottom, halfHandle, handlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (config == null || !config.enabled) {
            return super.onTouchEvent(event);
        }

        float x = event.getX();
        float y = event.getY();
        int action = event.getActionMasked();

        // 编辑模式：处理拖拽和调整大小
        if (isEditing()) {
            RectF editRectScreen = getEditRectScreen();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    // 检查是否点击在手柄上
                    dragHandle = getTouchedHandle(x, y, editRectScreen);
                    if (dragHandle >= 0) {
                        isResizing = true;
                    } else if (editRectScreen.contains(x, y)) {
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
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    if (config.dstRect.contains(x, y)) {
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

    private void updateRect(float dx, float dy, RectF editRectScreen) {
        if (config.editMode == 1) {
            // 编辑源区域：更新 srcRect（归一化坐标）
            View streamView = game.getStreamView();
            if (streamView == null) return;
            int streamWidth = streamView.getWidth();
            int streamHeight = streamView.getHeight();
            int[] location = new int[2];
            streamView.getLocationOnScreen(location);
            float streamLeft = location[0];
            float streamTop = location[1];

            // 计算屏幕坐标中的新矩形
            RectF newScreenRect = new RectF(editRectScreen);
            if (isDragging) {
                newScreenRect.offset(dx, dy);
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
            }

            // 将屏幕坐标转换回归一化坐标
            float left = (newScreenRect.left - streamLeft) / streamWidth;
            float top = (newScreenRect.top - streamTop) / streamHeight;
            float right = (newScreenRect.right - streamLeft) / streamWidth;
            float bottom = (newScreenRect.bottom - streamTop) / streamHeight;
            // 限制在 0-1 范围内
            left = Math.max(0, Math.min(1, left));
            top = Math.max(0, Math.min(1, top));
            right = Math.max(0, Math.min(1, right));
            bottom = Math.max(0, Math.min(1, bottom));
            if (right < left) right = left + 0.01f;
            if (bottom < top) bottom = top + 0.01f;
            config.srcRect.set(left, top, right, bottom);
        } else {
            // 编辑目标区域：更新 dstRect（屏幕坐标）
            if (isDragging) {
                config.dstRect.offset(dx, dy);
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
            }
        }
    }

    private void saveConfig() {
        // 通知PortalManagerView保存配置
        if (getParent() instanceof PortalManagerView) {
            ((PortalManagerView) getParent()).saveConfigs();
        }
    }

    /**
     * 将目标区域的触摸坐标映射回源区域，并发送输入事件
     */
    public boolean handlePortalTouch(MotionEvent event) {
        if (game == null || game.getConnection() == null) {
            return false;
        }
        // 坐标映射
        float srcX = mapCoordinate(event.getX(), config.dstRect, config.srcRect, true);
        float srcY = mapCoordinate(event.getY(), config.dstRect, config.srcRect, false);

        // 转换为流画面相对坐标（归一化）
        float[] normalized = game.getStreamViewRelativeNormalizedXY(srcX, srcY);

        // 发送输入事件（简化版，仅处理鼠标左键）
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                game.getConnection().sendMousePosition(
                    (short)(normalized[0] * 65535),
                    (short)(normalized[1] * 65535),
                    (short)game.getStreamView().getWidth(),
                    (short)game.getStreamView().getHeight());
                game.getConnection().sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
                break;
            case MotionEvent.ACTION_UP:
                game.getConnection().sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                break;
            case MotionEvent.ACTION_MOVE:
                game.getConnection().sendMousePosition(
                    (short)(normalized[0] * 65535),
                    (short)(normalized[1] * 65535),
                    (short)game.getStreamView().getWidth(),
                    (short)game.getStreamView().getHeight());
                break;
        }
        return true;
    }

    private float mapCoordinate(float coord, RectF from, RectF to, boolean isX) {
        if (isX) {
            return (coord - from.left) / from.width() * to.width() + to.left;
        } else {
            return (coord - from.top) / from.height() * to.height() + to.top;
        }
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