package com.tianma.xsmscode.ui.forward;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

/**
 * 转发通道下拉框（2026-09-16）：选中后确定性回调。
 * <p>
 * 背景：普通 ListPreference 选中后，"通道参数"行摘要刷新依赖 onPreferenceChange /
 * SharedPreferences 监听，均不可靠（onPreferenceChange 对本应用不触发、
 * 监听器实例可能与偏好框架持有的实例不一致）。
 * 改为子类在 onDialogClosed 完成（值已持久化）后直接回调宿主，即时刷新相关行。
 */
public class ForwardChannelListPreference extends ListPreference {

    public interface OnChannelChangedListener {
        void onChannelChanged(ListPreference preference);
    }

    private OnChannelChangedListener mListener;

    public ForwardChannelListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setOnChannelChangedListener(OnChannelChangedListener listener) {
        this.mListener = listener;
    }

    /**
     * 下拉选中后的确定性钩子（androidx 1.2.1）：
     * 对话框选中项时，框架经 ListPreferenceDialogFragment 调 getPreference().setValueIndex(...)
     * 持久化新值——这里在 super（持久化完成）后回调宿主，即时刷新"通道参数"行。
     * 幂等：重复/未变更触发也无副作用。
     */
    @Override
    public void setValueIndex(int index) {
        super.setValueIndex(index);
        if (mListener != null) {
            mListener.onChannelChanged(this);
        }
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        if (mListener != null) {
            mListener.onChannelChanged(this);
        }
    }

    /**
     * 供自绘弹窗（ForwardChannelDialog）在选中后手动通知宿主（2026-09-19）。
     * 自绘弹窗不走 ListPreference 内部对话框，不会触发 setValueIndex，故需显式调用。
     */
    public void notifyChannelChanged() {
        if (mListener != null) {
            mListener.onChannelChanged(this);
        }
    }
}
