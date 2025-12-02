package com.limelight.portal;

import android.graphics.RectF;

/**
 * 传送门配置数据类
 */
public class PortalConfig {
    public int id;
    public RectF srcRect; // 源区域（相对于StreamView的归一化坐标，0-1）
    public RectF dstRect; // 目标区域（相对于屏幕的绝对像素坐标）
    public boolean enabled;
    public String name;
    // 其他配置：缩放模式、透明度、边框颜色等
    public float scale = 1.0f;
    public int borderColor = 0xFF00FF00; // 绿色边框（默认，但宽度为0故不显示）
    public int borderWidth = 0; // 默认无边框
    public boolean editing = false; // 是否处于编辑模式（显示手柄，可拖拽调整）
    public int editMode = 0; // 0=无编辑，1=编辑源区域，2=编辑目标区域

    public PortalConfig() {
    }

    public PortalConfig(int id, RectF srcRect, RectF dstRect, boolean enabled, String name) {
        this.id = id;
        this.srcRect = srcRect;
        this.dstRect = dstRect;
        this.enabled = enabled;
        this.name = name;
    }
}