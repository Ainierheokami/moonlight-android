package com.limelight;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import java.util.ArrayList;
import java.util.Locale;

final class EdgeMenuTrailOverlay extends View {
    private static final long EDGE_MENU_TRAIL_HIDE_DELAY_MS = 650;
    private static final long EDGE_MENU_TRAIL_FADE_MS = 900;
    private static final int EDGE_MENU_TRAIL_MAX_POINTS = 32;
    private final ArrayList<TrailPoint> trailPoints = new ArrayList<>();
    private final Paint zoneFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zoneStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint currentPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            trailPoints.clear();
            setVisibility(View.GONE);
            invalidate();
        }
    };
    private int viewWidth;
    private int viewHeight;
    private int edgeZonePx;
    private int thresholdPx;
    private boolean candidate;
    private boolean consuming;
    private boolean startedLeft;

    EdgeMenuTrailOverlay(Context context) {
        super(context);
        setWillNotDraw(false);

        zoneFillPaint.setStyle(Paint.Style.FILL);
        zoneFillPaint.setColor(0x2244AAFF);

        zoneStrokePaint.setStyle(Paint.Style.STROKE);
        zoneStrokePaint.setStrokeWidth(Math.max(1f, dp(context, 1)));
        zoneStrokePaint.setColor(0x8844AAFF);

        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setStrokeWidth(Math.max(2f, dp(context, 2)));
        trailPaint.setColor(0xFF66FFCC);

        trailPointPaint.setStyle(Paint.Style.FILL);
        trailPointPaint.setColor(0xCC66FFCC);

        currentPointPaint.setStyle(Paint.Style.FILL);
        currentPointPaint.setColor(0xFFFFFFFF);

        textPaint.setColor(0xDDFFFFFF);
        textPaint.setTextSize(dp(context, 10));
        textPaint.setShadowLayer(2f, 0f, 0f, 0xAA000000);
    }

    void beginGesture(float x, float y, int width, int height, int edgeZone, int threshold, boolean candidate) {
        removeCallbacks(hideRunnable);
        trailPoints.clear();
        this.viewWidth = width;
        this.viewHeight = height;
        this.edgeZonePx = edgeZone;
        this.thresholdPx = threshold;
        this.candidate = candidate;
        this.consuming = false;
        this.startedLeft = x <= edgeZone;
        addPointInternal(x, y);
        setVisibility(View.VISIBLE);
        invalidate();
    }

    void addPoint(float x, float y, boolean candidate, boolean consuming) {
        if (getVisibility() != View.VISIBLE && trailPoints.isEmpty()) {
            return;
        }

        this.candidate = candidate;
        this.consuming = consuming;
        addPointInternal(x, y);
        postInvalidateOnAnimation();
    }

    void setGestureState(boolean candidate, boolean consuming, boolean startedLeft, int edgeZone, int threshold) {
        this.candidate = candidate;
        this.consuming = consuming;
        this.startedLeft = startedLeft;
        this.edgeZonePx = edgeZone;
        this.thresholdPx = threshold;
    }

    void finishGesture(float x, float y, boolean consuming) {
        this.consuming = consuming;
        addPointInternal(x, y);
        removeCallbacks(hideRunnable);
        postDelayed(hideRunnable, EDGE_MENU_TRAIL_HIDE_DELAY_MS);
        postInvalidateOnAnimation();
    }

    void hideSoon() {
        removeCallbacks(hideRunnable);
        postDelayed(hideRunnable, EDGE_MENU_TRAIL_HIDE_DELAY_MS);
    }

    void hideNow() {
        removeCallbacks(hideRunnable);
        trailPoints.clear();
        setVisibility(View.GONE);
        invalidate();
    }

    private void addPointInternal(float x, float y) {
        trailPoints.add(new TrailPoint(x, y, SystemClock.uptimeMillis()));
        while (trailPoints.size() > EDGE_MENU_TRAIL_MAX_POINTS) {
            trailPoints.remove(0);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (trailPoints.isEmpty()) {
            return;
        }

        int width = viewWidth > 0 ? viewWidth : getWidth();
        int height = viewHeight > 0 ? viewHeight : getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        int edgeWidth = Math.min(edgeZonePx, Math.max(1, width / 3));
        canvas.drawRect(0, 0, edgeWidth, height, zoneFillPaint);
        canvas.drawRect(width - edgeWidth, 0, width, height, zoneFillPaint);
        canvas.drawLine(edgeWidth, 0, edgeWidth, height, zoneStrokePaint);
        canvas.drawLine(width - edgeWidth, 0, width - edgeWidth, height, zoneStrokePaint);

        long now = SystemClock.uptimeMillis();
        TrailPoint previous = null;
        for (int i = 0; i < trailPoints.size(); i++) {
            TrailPoint point = trailPoints.get(i);
            float age = Math.min(1f, (now - point.timeMs) / (float) EDGE_MENU_TRAIL_FADE_MS);
            int alpha = (int) (255f * (1f - age));
            trailPaint.setAlpha(Math.max(40, alpha));
            trailPointPaint.setAlpha(Math.max(50, alpha));

            if (previous != null) {
                canvas.drawLine(previous.x, previous.y, point.x, point.y, trailPaint);
            }

            float radius = i == trailPoints.size() - 1 ? dp(getContext(), 5) : dp(getContext(), 3);
            canvas.drawCircle(point.x, point.y, radius, i == trailPoints.size() - 1 ? currentPointPaint : trailPointPaint);
            previous = point;
        }

        TrailPoint head = trailPoints.get(trailPoints.size() - 1);
        String side = startedLeft ? "LEFT" : "RIGHT";
        String state = (candidate ? "cand" : "free") + "/" + (consuming ? "cons" : "pass");
        canvas.drawText("side=" + side + " " + state + " edge=" + edgeZonePx + " thr=" + thresholdPx,
                dp(getContext(), 10), dp(getContext(), 18), textPaint);
        canvas.drawText(String.format(Locale.US, "x=%.1f y=%.1f", head.x, head.y),
                dp(getContext(), 10), dp(getContext(), 34), textPaint);
    }

    private static float dp(Context context, float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }

    private static class TrailPoint {
        final float x;
        final float y;
        final long timeMs;

        TrailPoint(float x, float y, long timeMs) {
            this.x = x;
            this.y = y;
            this.timeMs = timeMs;
        }
    }
}
