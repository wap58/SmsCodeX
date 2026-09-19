package com.tianma.xsmscode.ui.forward;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

import androidx.annotation.Nullable;

/**
 * 可限高的 ScrollView。
 *
 * <p>弹窗里的内容区高度不能随字段数量无限增长，否则小屏上会把窗口撑出屏幕。
 * 这里在测量阶段把可用高度压到 {@code maxHeight} 以内，超出的部分交给自身滚动。
 */
public class MaxHeightScrollView extends ScrollView {

    private int mMaxHeight;

    public MaxHeightScrollView(Context context) {
        super(context);
    }

    public MaxHeightScrollView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MaxHeightScrollView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setMaxHeight(int maxHeight) {
        mMaxHeight = maxHeight;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int spec = heightMeasureSpec;
        if (mMaxHeight > 0 && MeasureSpec.getSize(spec) > mMaxHeight) {
            spec = MeasureSpec.makeMeasureSpec(mMaxHeight, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, spec);
    }
}
