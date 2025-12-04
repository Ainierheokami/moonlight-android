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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (config == null || !config.enabled) {
            return;
        }

        // 将目标矩形从屏幕坐标转换为视图坐标
        RectF dstRectView = screenToViewRect(config.dstRect);

        // 绘制目标区域背景（半透明黑色）
        canvas.drawRect(dstRectView, new Paint(Color.argb(128, 0, 0, 0)));

        // 如果有关联的位图，绘制到目标区域（1:1拉伸填充）
        if (portalBitmap != null && !portalBitmap.isRecycled()) {
            float bitmapWidth = portalBitmap.getWidth();
            float bitmapHeight = portalBitmap.getHeight();
            float dstWidth = dstRectView.width();
            float dstHeight = dstRectView.height();
            Log.d(TAG, String.format("onDraw: bitmap=%dx%d dstView=%dx%d stretch to dstRectView",
                    (int)bitmapWidth, (int)bitmapHeight, (int)dstWidth, (int)dstHeight));
            // 直接使用目标矩形进行绘制（拉伸填充）
            canvas.drawBitmap(portalBitmap, null, dstRectView, bitmapPaint);
        }

        // 绘制目标区域边框（如果边框宽度>0）
        if (config.borderWidth > 0) {
            borderPaint.setColor(config.borderColor);
            borderPaint.setStrokeWidth(config.borderWidth);
            canvas.drawRect(dstRectView, borderPaint);
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
            Log.d(TAG, String.format("getEditRectScreen src: stream=%dx%d loc=(%d,%d) srcRect=(%.3f,%.3f,%.3f,%.3f) screenRect=(%.1f,%.1f,%.1f,%.1f)",
                    streamWidth, streamHeight, location[0], location[1],
                    config.srcRect.left, config.srcRect.top, config.srcRect.right, config.srcRect.bottom,
                    left, top, right, bottom));
            return new RectF(left, top, right, bottom);
        } else {
            // editMode == 2 或默认：目标区域（已经是屏幕坐标）
            Log.d(TAG, String.format("getEditRectScreen dst: dstRect=(%.1f,%.1f,%.1f,%.1f)",
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
        Log.d(TAG, String.format("screenToViewRect: screen=(%.1f,%.1f,%.1f,%.1f) viewLoc=(%d,%d) viewRect=(%.1f,%.1f,%.1f,%.1f)",
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

        Log.d(TAG, String.format("drawHandles: rect=(%.1f,%.1f,%.1f,%.1f) view size=%dx%d",
                left, top, right, bottom, getWidth(), getHeight()));

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
     * 获取源区域在屏幕上的矩形（像素坐标）
     */
    private RectF getSrcRectScreen() {
        View streamView = game.getStreamView();
        if (streamView == null) return new RectF();
        int streamWidth = streamView.getWidth();
        int streamHeight = streamView.getHeight();
        int[] location = new int[2];
        streamView.getLocationOnScreen(location);
        float left = config.srcRect.left * streamWidth + location[0];
        float top = config.srcRect.top * streamHeight + location[1];
        float right = config.srcRect.right * streamWidth + location[0];
        float bottom = config.srcRect.bottom * streamHeight + location[1];
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
        builder.setTitle("传送门设置 - " + config.name);
        String[] items = {
                "删除传送门",
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
                    // 暂未实现
                    showNotImplementedToast();
                    break;
                case 2: // 设置缩放比例
                    showNotImplementedToast();
                    break;
                case 3: // 设置宽高比
                    showNotImplementedToast();
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

    private void showNotImplementedToast() {
        android.widget.Toast.makeText(getContext(), "功能尚未实现", android.widget.Toast.LENGTH_SHORT).show();
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