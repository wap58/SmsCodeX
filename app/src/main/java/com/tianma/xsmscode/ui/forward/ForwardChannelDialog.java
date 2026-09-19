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
 * 转发通道选择弹窗（2026-09-19）。
 *
 * <p>替代 androidx.preference 默认的 {@code ListPreference} 弹窗：后者背景取自
 * {@code abc_dialog_material_background}（9-patch，圆角极小），无法做成
 * 与"通道参数"弹窗一致的 20dp 圆角。这里自绘卡片，两者观感统一。
 *
 * <p>行为与原弹窗一致：点选项即选中并关闭；有「取消」按钮。
 */
public class ForwardChannelDialog extends AppCompatDialogFragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_ENTRIES = "entries";
    private static final String ARG_VALUES = "values";
    private static final String ARG_CURRENT = "current";

    /** 回调由宿主 Fragment 实现，避免依赖 ListPreference 内部机制 */
    public interface OnChannelPickedListener {
        void onChannelPicked(String value);
    }

    private OnChannelPickedListener mListener;

    public void setOnChannelPickedListener(OnChannelPickedListener listener) {
        mListener = listener;
    }

    public static ForwardChannelDialog newInstance(String title, String[] entries,
                                                   String[] values, String current) {
        ForwardChannelDialog dialog = new ForwardChannelDialog();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putStringArray(ARG_ENTRIES, entries);
        args.putStringArray(ARG_VALUES, values);
        args.putString(ARG_CURRENT, current);
        dialog.setArguments(args);
        return dialog;
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
        return inflater.inflate(R.layout.dialog_channel_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int bgColor = ChannelDialogTheme.dialogBackground();
        int textColor = ChannelDialogTheme.textColor();
        int dividerColor = ChannelDialogTheme.dividerColor();
        int accentColor = ChannelDialogTheme.accentColor();

        Bundle args = getArguments();
        String title = args == null ? null : args.getString(ARG_TITLE);
        String[] entries = args == null ? new String[0] : args.getStringArray(ARG_ENTRIES);
        String[] values = args == null ? new String[0] : args.getStringArray(ARG_VALUES);
        String current = args == null ? null : args.getString(ARG_CURRENT);
        if (entries == null) {
            entries = new String[0];
        }
        if (values == null) {
            values = new String[0];
        }

        com.google.android.material.card.MaterialCardView card =
                view.findViewById(R.id.channel_list_card);
        card.setCardBackgroundColor(bgColor);
        view.findViewById(R.id.channel_list_divider_top).setBackgroundColor(dividerColor);
        view.findViewById(R.id.channel_list_divider_bottom).setBackgroundColor(dividerColor);

        TextView titleView = view.findViewById(R.id.channel_list_title);
        titleView.setText(title);
        titleView.setTextColor(textColor);

        mScroll = view.findViewById(R.id.channel_list_scroll);

        TextView cancel = view.findViewById(R.id.channel_list_cancel);
        cancel.setText(android.R.string.cancel);
        cancel.setTextColor(accentColor);
        cancel.setOnClickListener(v -> dismiss());

        LinearLayout options = view.findViewById(R.id.channel_list_options);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < entries.length && i < values.length; i++) {
            final String value = values[i];
            View row = inflater.inflate(R.layout.item_channel_option, options, false);
            TextView label = row.findViewById(R.id.channel_option_text);
            ImageView check = row.findViewById(R.id.channel_option_check);

            label.setText(entries[i]);
            label.setTextColor(textColor);

            boolean selected = value != null && value.equals(current);
            if (selected) {
                check.setVisibility(View.VISIBLE);
                Drawable d = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_plain);
                if (d != null) {
                    d = DrawableCompat.wrap(d.mutate());
                    DrawableCompat.setTint(d, accentColor);
                    check.setImageDrawable(d);
                }
            } else {
                check.setVisibility(View.INVISIBLE);
            }

            row.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onChannelPicked(value);
                }
                dismissAllowingStateLoss();
            });
            options.addView(row);
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

    private MaxHeightScrollView mScroll;
}
