package com.tianma.xsmscode.ui.record;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentManager;

import com.smscodf.zhuxf.R;
import com.tianma.xsmscode.common.utils.ThemeColors;
import com.tianma.xsmscode.feature.forward.ForwardChannels;
import com.tianma.xsmscode.feature.forward.ForwardResultStore;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 短信详情弹窗（2026-10-17 重做）。
 *
 * <p>为什么不用 MaterialDialog：原实现是原生 Alert 样式，与整体卡片风格不一致。
 * 这里自绘卡片（20dp 圆角），结构与「传信」的 dialog_sms_detail 对齐：
 * <pre>
 *   标题行：短信详情 · 关闭
 *   卡片①：发件人 / 时间 / ——— / 转发状态（逐通道）
 *   卡片②：短信正文
 *   底部：复制短信 · 转发信息 · 复制验证码
 * </pre>
 *
 * <p>★ 转发状态单独成块（用户要求），位置在短信内容<b>上方</b>的元信息卡片内。
 * 数据来自 {@link ForwardResultStore}（电话进程转发后写入的共享文件）。
 *
 * <p>配色运行时按 Cyanea 赋值：本项目通过主题属性取色会回退失败（2026-09-15 实测）。
 */
public class SmsDetailDialog extends AppCompatDialogFragment {

    private static final String TAG = "SmsDetailDialog";
    private static final String ARG_SENDER = "sender";
    private static final String ARG_BODY = "body";
    private static final String ARG_CODE = "code";
    private static final String ARG_TIME = "time";

    public interface Actions {
        void onCopySms();

        void onForward();

        void onCopyCode();
    }

    private Actions mActions;

    public void setActions(Actions actions) {
        mActions = actions;
    }

    public static void show(@Nullable FragmentManager fm, String sender, String body,
                            String code, long time, Actions actions) {
        if (fm == null) {
            return;
        }
        if (fm.findFragmentByTag(TAG) != null) {
            return;
        }
        SmsDetailDialog dialog = new SmsDetailDialog();
        Bundle args = new Bundle();
        args.putString(ARG_SENDER, sender);
        args.putString(ARG_BODY, body);
        args.putString(ARG_CODE, code);
        args.putLong(ARG_TIME, time);
        dialog.setArguments(args);
        dialog.mActions = actions;
        dialog.show(fm, TAG);
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
        return inflater.inflate(R.layout.dialog_sms_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        String sender = args == null ? "" : args.getString(ARG_SENDER, "");
        String body = args == null ? "" : args.getString(ARG_BODY, "");
        String code = args == null ? "" : args.getString(ARG_CODE, "");
        long time = args == null ? 0L : args.getLong(ARG_TIME, 0L);

        // ---- 配色 ----
        int cardBg = ThemeColors.isDark() ? 0xFF2A2D34 : 0xFFF4F5F8;
        int dialogBg = ThemeColors.isDark() ? 0xFF24262B : 0xFFFFFFFF;
        int textPrimary = ThemeColors.isDark() ? Color.WHITE : 0xFF212121;
        int textHint = ThemeColors.isDark() ? 0x99FFFFFF : 0x99000000;
        int divider = ThemeColors.isDark() ? 0x1FFFFFFF : 0x14000000;
        int accent = ThemeColors.accent();

        com.google.android.material.card.MaterialCardView root =
                view.findViewById(R.id.sms_detail_card);
        root.setCardBackgroundColor(dialogBg);

        // 标题 / 关闭
        TextView title = view.findViewById(R.id.sms_detail_title);
        title.setTextColor(textPrimary);
        TextView close = view.findViewById(R.id.sms_detail_close);
        close.setTextColor(accent);
        close.setOnClickListener(v -> dismissAllowingStateLoss());

        // 元信息卡片
        com.google.android.material.card.MaterialCardView metaCard =
                view.findViewById(R.id.sms_detail_meta_card);
        metaCard.setCardBackgroundColor(cardBg);

        TextView labelSender = view.findViewById(R.id.sms_detail_label_sender);
        labelSender.setTextColor(textHint);
        TextView senderView = view.findViewById(R.id.sms_detail_sender);
        senderView.setTextColor(textPrimary);
        senderView.setText(TextUtils.isEmpty(sender) ? "-" : sender);

        TextView labelTime = view.findViewById(R.id.sms_detail_label_time);
        labelTime.setTextColor(textHint);
        TextView timeView = view.findViewById(R.id.sms_detail_time);
        timeView.setTextColor(textPrimary);
        timeView.setText(time <= 0 ? "-" : new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(time)));

        view.findViewById(R.id.sms_detail_divider).setBackgroundColor(divider);

        TextView labelForward = view.findViewById(R.id.sms_detail_label_forward);
        labelForward.setTextColor(textHint);

        LinearLayout forwardBox = view.findViewById(R.id.sms_detail_forward_box);
        bindForwardStatus(forwardBox, time, textPrimary, textHint, accent);

        // 正文卡片
        com.google.android.material.card.MaterialCardView bodyCard =
                view.findViewById(R.id.sms_detail_body_card);
        bodyCard.setCardBackgroundColor(cardBg);
        TextView bodyView = view.findViewById(R.id.sms_detail_body);
        bodyView.setTextColor(textPrimary);
        bodyView.setText(body == null ? "" : body);

        // 底部操作
        view.findViewById(R.id.sms_detail_action_divider).setBackgroundColor(divider);
        TextView copySms = view.findViewById(R.id.sms_detail_action_copy_sms);
        TextView forward = view.findViewById(R.id.sms_detail_action_forward);
        TextView copyCode = view.findViewById(R.id.sms_detail_action_copy_code);
        copySms.setTextColor(accent);
        forward.setTextColor(accent);
        copyCode.setTextColor(accent);

        copySms.setOnClickListener(v -> {
            if (mActions != null) {
                mActions.onCopySms();
            }
            dismissAllowingStateLoss();
        });
        forward.setOnClickListener(v -> {
            if (mActions != null) {
                mActions.onForward();
            }
            dismissAllowingStateLoss();
        });
        copyCode.setOnClickListener(v -> {
            if (mActions != null) {
                mActions.onCopyCode();
            }
            dismissAllowingStateLoss();
        });
    }

    /**
     * 渲染转发状态块（2026-10-17）。
     *
     * <p>三种情况：
     * <ol>
     *   <li>有匹配记录 → 「通道名 … 成功/失败」，失败项下方追加红色原因</li>
     *   <li>记录存在但时间戳不匹配（说明是更早的短信）→ 提示暂无记录</li>
     *   <li>完全没有记录文件 → 提示未转发</li>
     * </ol>
     */
    private void bindForwardStatus(LinearLayout box, long time,
                                   int textPrimary, int textHint, int accent) {
        box.removeAllViews();

        if (!ForwardResultStore.hasRecord()) {
            box.addView(makeLine(getString(R.string.sms_detail_forward_not_sent),
                    textHint, 13f, false));
            return;
        }

        java.util.List<ForwardResultStore.ChannelResult> list = ForwardResultStore.readFor(time);
        if (list.isEmpty()) {
            box.addView(makeLine(getString(R.string.sms_detail_forward_unknown),
                    textHint, 13f, false));
            return;
        }

        // 逐通道一行（多通道转发，2026-10-17）
        for (ForwardResultStore.ChannelResult r : list) {
            box.addView(buildChannelRow(ForwardChannels.name(requireContext(), r.channel),
                    r.ok ? getString(R.string.sms_detail_forward_success)
                            : getString(R.string.sms_detail_forward_fail),
                    r.ok, textPrimary, accent));

            if (!r.ok && !TextUtils.isEmpty(r.reason)) {
                TextView detail = makeLine(translateReason(r.reason), 0xFFE05252, 12.5f, false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(3);
                lp.bottomMargin = dp(2);
                detail.setLayoutParams(lp);
                box.addView(detail);
            }
        }
    }

    /** 「左通道名 · 右成功/失败」一行 */
    private View buildChannelRow(String name, String status, boolean ok,
                                 int textPrimary, int accent) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView left = new TextView(requireContext());
        left.setText(name);
        left.setTextSize(13f);
        left.setTextColor(textPrimary);
        left.setSingleLine(true);
        left.setEllipsize(android.text.TextUtils.TruncateAt.END);
        left.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(left);

        TextView right = new TextView(requireContext());
        right.setText(status);
        right.setTextSize(13f);
        right.setTextColor(ok ? accent : 0xFFE05252);
        row.addView(right);

        return row;
    }

    private TextView makeLine(String text, int color, float sizeSp, boolean bold) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        tv.setLineSpacing(dp(2), 1f);
        if (bold) {
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        return tv;
    }


    /** 失败原因翻译成可读文案 */
    private String translateReason(String reason) {
        if (TextUtils.isEmpty(reason)) {
            return "";
        }
        switch (reason) {
            case "send_failed":
                return "接口调用失败，请检查网络与通道参数";
            case "disabled":
                return "转发未启用";
            case "channel_unconfigured":
                return "通道参数未填写";
            case "forbidden":
                return "调用被拒绝（进程权限不足）";
            default:
                return reason;
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
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
        window.setGravity(Gravity.CENTER);
    }
}
