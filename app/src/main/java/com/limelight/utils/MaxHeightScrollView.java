package com.limelight.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/** A ScrollView that wraps its content but never grows taller than a given limit. */
public class MaxHeightScrollView extends ScrollView {
    private int maxHeightPx = Integer.MAX_VALUE;

    public MaxHeightScrollView(Context context) {
        super(context);
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setMaxHeightPx(int maxHeightPx) {
        this.maxHeightPx = maxHeightPx > 0 ? maxHeightPx : Integer.MAX_VALUE;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        int size = MeasureSpec.getSize(heightMeasureSpec);
        if (mode == MeasureSpec.UNSPECIFIED) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST);
        }
        else if (size > maxHeightPx) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeightPx,
                    mode == MeasureSpec.EXACTLY ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
