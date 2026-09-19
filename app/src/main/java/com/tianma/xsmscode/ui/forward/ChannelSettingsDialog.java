package com.tianma.xsmscode.ui.forward;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.smscodf.zhuxf.R;
import com.tianma.xsmscode.common.utils.XLog;

/**
 * 通道参数配置弹窗（2026-09-19，用户指定：由整页 Activity 改为弹窗）。
 *
 * 实现要点：
 * 1. 复用现有 {@link ChannelSettingsFragment}（PreferenceFragmentCompat），
 *    6 个通道的 preference xml 一行未改；
 * 2. 主题必须带 preferenceTheme，见 styles.xml 的 Theme.XsmsCode.ChannelDialog；
 * 3. 宽度 / 键盘行为在 onStart() 里手动接管（默认 dialog 宽约 280dp 太窄）。
 */
public class ChannelSettingsDialog extends DialogFragment {

    private static final String TAG = "ChannelSettingsDialog";
    private static final String ARG_CHANNEL = "channel";

    /** 弹窗宽度占屏幕比例 */
    private static final float WIDTH_RATIO = 0.92f;
    /** 弹窗宽度上限（平板 / 折叠屏） */
    private static final int MAX_WIDTH_DP = 560;

    public static ChannelSettingsDialog newInstance(String channel) {
        ChannelSettingsDialog dialog = new ChannelSettingsDialog();
        Bundle args = new Bundle();
        args.putString(ARG_CHANNEL, channel == null ? "wecom_agent" : channel);
        dialog.setArguments(args);
        return dialog;
    }

    /** 统一的弹出入口，内部做重复弹出保护（名字避开父类 DialogFragment.show） */
    public static void showDialog(@Nullable FragmentManager fm, String channel) {
        if (fm == null) {
            return;
        }
        if (fm.findFragmentByTag(TAG) != null) {
            return;
        }
        newInstance(channel).show(fm, TAG);
    }

    private String channel() {
        Bundle args = getArguments();
        return args == null ? "wecom_agent" : args.getString(ARG_CHANNEL, "wecom_agent");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.Theme_XsmsCode_ChannelDialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_channel_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String channel = channel();
        XLog.i("SmsCodeX: ChannelSettingsDialog onViewCreated channel=%s", channel);

        TextView title = view.findViewById(R.id.channel_settings_dialog_title);
        title.setText(ChannelSettingsFragment.titleFor(requireContext(), channel));

        view.findViewById(R.id.channel_settings_dialog_done)
                .setOnClickListener(v -> dismiss());

        // 重建（如旋转屏幕）时 FragmentManager 会自动恢复子 Fragment，避免重复添加
        if (savedInstanceState == null) {
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.channel_settings_dialog_container,
                            ChannelSettingsFragment.newInstance(channel))
                    .commit();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null) {
            return;
        }
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }

        // 默认 dialog 宽度约 280dp，对参数表单太窄，这里撑到屏宽 92%
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        float density = getResources().getDisplayMetrics().density;
        int width = Math.min((int) (screenWidth * WIDTH_RATIO), (int) (MAX_WIDTH_DP * density));
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);

        // 键盘弹出时收缩窗口，避免输入框被遮挡
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // 卡片圆角由布局提供，窗口背景必须透明，否则会出现方形白底
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        dialog.setCanceledOnTouchOutside(true);
    }
}
