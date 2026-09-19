package com.tianma.xsmscode.ui.forward;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 限高 FrameLayout（2026-09-19）：
 * 用于承载弹窗内的 PreferenceFragment。PreferenceFragmentCompat 内部的 RecyclerView
 * 是 MATCH_PARENT，在 wrap_content 的弹窗里若不限高，条目多 / 键盘弹出时会顶出屏幕。
 * 这里在 onMeasure 阶段按比例封顶，超出部分交给内部 RecyclerView 自己滚动。
 */
public class MaxHeightFrameLayout extends FrameLayout {

    /** 最大高度占屏幕可用高度的比例 */
    private static final float MAX_HEIGHT_RATIO = 0.62f;

    public MaxHeightFrameLayout(@NonNull Context context) {
        super(context);
    }

    public MaxHeightFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MaxHeightFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels * MAX_HEIGHT_RATIO);
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        int size = MeasureSpec.getSize(heightMeasureSpec);
        // 取「父级约束」与「比例上限」中的较小值
        int capped = (mode == MeasureSpec.UNSPECIFIED) ? maxHeight : Math.min(size, maxHeight);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(capped, MeasureSpec.AT_MOST));
    }
}
