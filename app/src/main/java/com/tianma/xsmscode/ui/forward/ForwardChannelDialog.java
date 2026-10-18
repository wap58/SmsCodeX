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
import com.tianma.xsmscode.feature.forward.ForwardChannels;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 转发通道多选弹窗（2026-10-17 重做，从单选改多选）。
 *
 * <p>原实现继承 {@code ListPreference} 的单选语义，一次只能推一条通道。
 * 现改为可勾选多路，配置存逗号分隔串（见 {@link ForwardChannels}），
 * 转发时逐通道串行发送，任一成功即整体成功。
 *
 * <p>列表用 {@code bottom_sheet} 风格的圆角卡片，配色按 Cyanea 运行时赋值。
 */
public class ForwardChannelDialog extends AppCompatDialogFragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_CURRENT = "current";
    private static final String TAG = "ForwardChannelDialog";

    /** 选完后回调宿主，参数为逗号分隔串 */
    public interface OnChannelsPickedListener {
        void onChannelsPicked(String csv);
    }

    private OnChannelsPickedListener mListener;
    private final Set<String> mSelected = new LinkedHashSet<>();
    private MaxHeightScrollView mScroll;
    private LinearLayout mOptions;
    private int mTextColor;
    private int mAccentColor;

    public void setOnChannelsPickedListener(OnChannelsPickedListener listener) {
        mListener = listener;
    }

    public static ForwardChannelDialog newInstance(String title, String currentCsv) {
        ForwardChannelDialog d = new ForwardChannelDialog();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_CURRENT, currentCsv);
        d.setArguments(args);
        return d;
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
        return inflater.inflate(R.layout.dialog_channel_multi, container, false);
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
        String current = args == null ? null : args.getString(ARG_CURRENT);

        // 只保留仍然配置齐全的通道（配置可能被清空过）
        List<String> pre = ForwardChannels.parse(current);
        mSelected.clear();
        for (String id : pre) {
            if (ForwardChannels.isConfigured(requireContext(), id)) {
                mSelected.add(id);
            }
        }

        com.google.android.material.card.MaterialCardView card =
                view.findViewById(R.id.channel_multi_card);
        card.setCardBackgroundColor(bgColor);
        view.findViewById(R.id.channel_multi_divider_top).setBackgroundColor(dividerColor);
        view.findViewById(R.id.channel_multi_divider_bottom).setBackgroundColor(dividerColor);

        TextView titleView = view.findViewById(R.id.channel_multi_title);
        titleView.setText(title == null ? getString(R.string.pref_forward_channel_title) : title);
        titleView.setTextColor(mTextColor);

        mScroll = view.findViewById(R.id.channel_multi_scroll);
        mOptions = view.findViewById(R.id.channel_multi_options);
        rebuildOptions();

        TextView cancel = view.findViewById(R.id.channel_multi_cancel);
        cancel.setTextColor(mAccentColor);
        cancel.setOnClickListener(v -> dismissAllowingStateLoss());

        TextView done = view.findViewById(R.id.channel_multi_done);
        done.setTextColor(mAccentColor);
        done.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onChannelsPicked(ForwardChannels.serialize(mSelected));
            }
            dismissAllowingStateLoss();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从「通道配置页」返回后，配置可能已补齐 → 重建列表刷新灰态
        if (mOptions != null) {
            rebuildOptions();
        }
    }

    /** 重建选项列表（勾选状态实时反映在图标上） */
    private void rebuildOptions() {
        mOptions.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (String id : ForwardChannels.ALL) {
            View row = inflater.inflate(R.layout.item_channel_option, mOptions, false);
            TextView label = row.findViewById(R.id.channel_option_text);
            ImageView check = row.findViewById(R.id.channel_option_check);

            // 标题 = 通道名 [+ 备注]，备注用于区分多个账号（2026-10-18）
            String displayName = ForwardChannels.name(requireContext(), id);
            String note = requireContext().getSharedPreferences(
                    com.tianma.xsmscode.common.constant.PrefConst.PREF_NAME,
                    android.content.Context.MODE_PRIVATE)
                    .getString(ChannelSettingsDialog.noteKey(id), "");
            if (note != null && !note.trim().isEmpty()) {
                displayName = displayName + " · " + note.trim();
            }
            label.setText(displayName);
            label.setTextColor(mTextColor);

            boolean configured = ForwardChannels.isConfigured(requireContext(), id);
            boolean on = mSelected.contains(id);
            syncCheck(check, on);

            // ★ 未配置的通道禁止勾选（2026-10-17）：灰色 + 点击提示去配置，
            //   否则转发时会被 Dispatcher 当"未配置"跳过，用户以为推了其实没推。
            if (!configured) {
                row.setAlpha(0.4f);
                label.setText(label.getText() + "（未配置）");
            }

            row.setOnClickListener(v -> {
                if (!configured) {
                    android.widget.Toast.makeText(requireContext(),
                            getString(R.string.channel_not_configured_hint),
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mSelected.contains(id)) {
                    mSelected.remove(id);
                } else {
                    mSelected.add(id);
                }
                // 只刷新本行，避免整体重建导致闪烁
                syncCheck(check, mSelected.contains(id));
            });

            // ★ 齿轮：直接进该通道的参数配置页（2026-10-17）
            //   解决"多通道后必须先单选再配置"的繁琐：勾选与配置互不干扰。
            ImageView cfg = row.findViewById(R.id.channel_option_config);
            if (cfg != null) {
                cfg.setVisibility(View.VISIBLE);
                tintIcon(cfg, mTextColor);
                cfg.setOnClickListener(v -> {
                    // 2026-10-18：改为弹窗配置，不再跳整页（浅层参数一个弹窗足够）
                    ChannelSettingsDialog.showDialog(getChildFragmentManager(), id);
                });
            }
            mOptions.addView(row);
        }
    }

    /** 图标按主题文字色着色（原图标是纯黑填充，深色主题下不可见） */
    private void tintIcon(ImageView iv, int color) {
        Drawable d = iv.getDrawable();
        if (d == null) {
            return;
        }
        d = DrawableCompat.wrap(d.mutate());
        DrawableCompat.setTint(d, color);
        iv.setImageDrawable(d);
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
