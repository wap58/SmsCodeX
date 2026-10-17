package com.tianma.xsmscode.ui.forward;

import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.smscodf.zhuxf.R;

/**
 * 通用单选弹窗（2026-10-17 新增）。
 *
 * <p>替代 androidx 原生 {@code ListPreference} 的对话框——后者背景取自
 * {@code abc_dialog_material_background}（9-patch，圆角极小），
 * 单选圈还是粉色 Material 样式，与本项目的卡片风格完全不搭。
 *
 * <p>复用「转发通道多选」那套视觉：20dp 圆角卡片 + 自绘勾选标记 + 主题强调色。
 * 用于「转发范围」「通知保留时长」这类二选一/多选一场景。
 */
public class SingleChoiceDialog extends AppCompatDialogFragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_ENTRIES = "entries";
    private static final String ARG_VALUES = "values";
    private static final String ARG_CURRENT = "current";
    private static final String TAG = "SingleChoiceDialog";

    public interface OnPickedListener {
        void onPicked(String value);
    }

    private OnPickedListener mListener;
    private String mCurrent;
    private MaxHeightScrollView mScroll;
    private int mTextColor;
    private int mAccentColor;

    public void setOnPickedListener(OnPickedListener listener) {
        mListener = listener;
    }

    public static void show(@Nullable androidx.fragment.app.FragmentManager fm,
                            String title, String[] entries, String[] values,
                            String current, OnPickedListener listener) {
        if (fm == null) {
            return;
        }
        if (fm.findFragmentByTag(TAG) != null) {
            return;
        }
        SingleChoiceDialog d = new SingleChoiceDialog();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putStringArray(ARG_ENTRIES, entries);
        args.putStringArray(ARG_VALUES, values);
        args.putString(ARG_CURRENT, current);
        d.setArguments(args);
        d.mListener = listener;
        d.show(fm, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new AppCompatDialog(requireContext(), R.style.Theme_XsmsCode_ChannelDialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_single_choice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int bgColor = ChannelDialogTheme.dialogBackground();
        mTextColor = ChannelDialogTheme.textColor();
        mAccentColor = ChannelDialogTheme.accentColor();
        int dividerColor = ChannelDialogTheme.dividerColor();

        Bundle args = getArguments();
        String title = args == null ? null : args.getString(ARG_TITLE);
        String[] entries = args == null ? new String[0] : args.getStringArray(ARG_ENTRIES);
        String[] values = args == null ? new String[0] : args.getStringArray(ARG_VALUES);
        mCurrent = args == null ? null : args.getString(ARG_CURRENT);
        if (entries == null) {
            entries = new String[0];
        }
        if (values == null) {
            values = new String[0];
        }

        com.google.android.material.card.MaterialCardView card =
                view.findViewById(R.id.single_choice_card);
        card.setCardBackgroundColor(bgColor);
        view.findViewById(R.id.single_choice_divider_top).setBackgroundColor(dividerColor);
        view.findViewById(R.id.single_choice_divider_bottom).setBackgroundColor(dividerColor);

        TextView titleView = view.findViewById(R.id.single_choice_title);
        titleView.setText(title == null ? "" : title);
        titleView.setTextColor(mTextColor);

        mScroll = view.findViewById(R.id.single_choice_scroll);
        LinearLayout options = view.findViewById(R.id.single_choice_options);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < entries.length && i < values.length; i++) {
            final String value = values[i];
            View row = inflater.inflate(R.layout.item_channel_option, options, false);
            TextView label = row.findViewById(R.id.channel_option_text);
            ImageView check = row.findViewById(R.id.channel_option_check);

            label.setText(entries[i]);
            label.setTextColor(mTextColor);
            syncCheck(check, value != null && value.equals(mCurrent));

            // 单选场景没有"每项配置"，隐藏齿轮
            View cfg = row.findViewById(R.id.channel_option_config);
            if (cfg != null) {
                cfg.setVisibility(View.GONE);
            }

            row.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onPicked(value);
                }
                dismissAllowingStateLoss();
            });
            options.addView(row);
        }

        TextView cancel = view.findViewById(R.id.single_choice_cancel);
        cancel.setTextColor(mAccentColor);
        cancel.setOnClickListener(v -> dismissAllowingStateLoss());
    }

    private void syncCheck(ImageView check, boolean on) {
        check.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        if (on) {
            Drawable d = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_plain);
            if (d != null) {
                d = DrawableCompat.wrap(d.mutate());
                DrawableCompat.setTint(d, mAccentColor);
                check.setImageDrawable(d);
            }
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
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int maxWidth = (int) (560 * getResources().getDisplayMetrics().density);
        int width = Math.min((int) (screenWidth * 0.92f), maxWidth);
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);

        if (mScroll != null) {
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            mScroll.setMaxHeight((int) (screenHeight * 0.5f));
        }
    }
}
