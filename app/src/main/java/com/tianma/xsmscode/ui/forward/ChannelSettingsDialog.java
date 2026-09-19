package com.tianma.xsmscode.ui.forward;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.smscodf.zhuxf.R;
import com.tianma.xsmscode.common.constant.PrefConst;

import java.util.ArrayList;
import java.util.List;

/**
 * 通道参数配置弹窗。
 *
 * <p>单层弹窗：所有字段内联在同一个弹窗里直接编辑，底部「完成」统一保存。
 * 不使用 {@code PreferenceFragmentCompat}，因此不会出现"点一项又弹一层"的嵌套输入框。
 *
 * <p>字段直接读写默认 SharedPreferences（{@link PrefConst#PREF_NAME}），
 * 与原有 preference 页面共用同一份存储，键名保持一致。
 */
public class ChannelSettingsDialog extends AppCompatDialogFragment {

    private static final String ARG_CHANNEL = "channel";
    private static final String TAG = "ChannelSettingsDialog";

    /** 一个待编辑字段：标题、键名、是否密码型 */
    private static final class Field {
        final int titleRes;
        final String key;
        final boolean secret;

        Field(int titleRes, String key, boolean secret) {
            this.titleRes = titleRes;
            this.key = key;
            this.secret = secret;
        }
    }

    public static void showDialog(@Nullable FragmentManager fm, String channel) {
        if (fm == null) {
            return;
        }
        ChannelSettingsDialog old = (ChannelSettingsDialog) fm.findFragmentByTag(TAG);
        if (old != null) {
            return;
        }
        ChannelSettingsDialog dialog = new ChannelSettingsDialog();
        Bundle args = new Bundle();
        args.putString(ARG_CHANNEL, channel);
        dialog.setArguments(args);
        dialog.show(fm, TAG);
    }

    /** 通道显示名，与 {@link ChannelSettingsFragment#titleFor} 保持一致 */
    private static int titleFor(String channel) {
        switch (channel == null ? "wecom_agent" : channel) {
            case "wecom_robot":
                return R.string.forward_channel_page_wecom_robot;
            case "dingtalk":
                return R.string.forward_channel_page_dingtalk;
            case "feishu":
                return R.string.forward_channel_page_feishu;
            case "xizhi":
                return R.string.forward_channel_page_xizhi;
            case "pushplus":
                return R.string.forward_channel_page_pushplus;
            case "wecom_agent":
            default:
                return R.string.forward_channel_page_wecom_agent;
        }
    }

    private static List<Field> fieldsFor(String channel) {
        List<Field> list = new ArrayList<>();
        switch (channel == null ? "wecom_agent" : channel) {
            case "wecom_robot":
                list.add(new Field(R.string.forward_wecom_robot_title,
                        PrefConst.KEY_FORWARD_WECOM_ROBOT_WEBHOOK, false));
                break;
            case "dingtalk":
                list.add(new Field(R.string.forward_dingtalk_title,
                        PrefConst.KEY_FORWARD_DINGTALK_WEBHOOK, false));
                list.add(new Field(R.string.forward_dingtalk_secret_title,
                        PrefConst.KEY_FORWARD_DINGTALK_SECRET, true));
                break;
            case "feishu":
                list.add(new Field(R.string.forward_feishu_title,
                        PrefConst.KEY_FORWARD_FEISHU_WEBHOOK, false));
                break;
            case "xizhi":
                list.add(new Field(R.string.forward_xizhi_title,
                        PrefConst.KEY_FORWARD_XIZHI_KEY, false));
                break;
            case "pushplus":
                list.add(new Field(R.string.forward_pushplus_title,
                        PrefConst.KEY_FORWARD_PUSHPLUS_TOKEN, false));
                break;
            case "wecom_agent":
            default:
                list.add(new Field(R.string.pref_forward_wecom_corpid_title,
                        PrefConst.KEY_FORWARD_WECOM_CORPID, false));
                list.add(new Field(R.string.pref_forward_wecom_agentid_title,
                        PrefConst.KEY_FORWARD_WECOM_AGENTID, false));
                list.add(new Field(R.string.pref_forward_wecom_secret_title,
                        PrefConst.KEY_FORWARD_WECOM_SECRET, true));
                list.add(new Field(R.string.pref_forward_wecom_touser_title,
                        PrefConst.KEY_FORWARD_WECOM_TOUSER, false));
                break;
        }
        return list;
    }

    private String mChannel;
    private List<Field> mFields = new ArrayList<>();
    private final List<EditText> mInputs = new ArrayList<>();
    private MaxHeightScrollView mScroll;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mChannel = getArguments() == null ? null : getArguments().getString(ARG_CHANNEL);
        if (mChannel == null) {
            mChannel = "wecom_agent";
        }
        mFields = fieldsFor(mChannel);
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
        return inflater.inflate(R.layout.dialog_channel_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 配色统一走 ChannelDialogTheme（内部按背景亮度判断明暗，并显式从 Cyanea 取值，
        // 因为本项目通过主题属性取色会回退失败）
        int bgColor = ChannelDialogTheme.dialogBackground();
        int textColor = ChannelDialogTheme.textColor();
        int hintColor = ChannelDialogTheme.hintColor();
        int dividerColor = ChannelDialogTheme.dividerColor();
        int accentColor = ChannelDialogTheme.accentColor();

        TextView title = view.findViewById(R.id.channel_settings_dialog_title);
        title.setText(titleFor(mChannel));
        title.setTextColor(textColor);

        com.google.android.material.card.MaterialCardView card =
                view.findViewById(R.id.channel_settings_dialog_card);
        card.setCardBackgroundColor(bgColor);
        view.findViewById(R.id.channel_settings_dialog_divider_top).setBackgroundColor(dividerColor);
        view.findViewById(R.id.channel_settings_dialog_divider_bottom).setBackgroundColor(dividerColor);

        TextView done = view.findViewById(R.id.channel_settings_dialog_done);
        done.setTextColor(accentColor);

        mScroll = view.findViewById(R.id.channel_settings_dialog_scroll);
        LinearLayout fieldsBox = view.findViewById(R.id.channel_settings_dialog_fields);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(requireContext());
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (Field field : mFields) {
            View row = inflater.inflate(R.layout.item_channel_field, fieldsBox, false);
            TextView fieldTitle = row.findViewById(R.id.channel_field_title);
            EditText input = row.findViewById(R.id.channel_field_input);
            View divider = row.findViewById(R.id.channel_field_divider);

            fieldTitle.setText(field.titleRes);
            fieldTitle.setTextColor(textColor);

            input.setText(sp.getString(field.key, ""));
            input.setHint(field.titleRes);
            input.setTextColor(textColor);
            input.setHintTextColor(hintColor);
            input.setTag(field.key);

            divider.setBackgroundColor(dividerColor);

            if (field.secret) {
                // 密钥类字段：保持可见，方便核对是否填过
                input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            }
            input.setSingleLine(true);

            mInputs.add(input);
            fieldsBox.addView(row);
        }

        done.setOnClickListener(v -> {
            saveValues();
            dismissAllowingStateLoss();
        });
    }

    /** 把各输入框的当前值写回 SharedPreferences（仅写有变化的项） */
    private void saveValues() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(requireContext());
        SharedPreferences.Editor editor = sp.edit();
        boolean changed = false;
        for (EditText input : mInputs) {
            String key = (String) input.getTag();
            if (TextUtils.isEmpty(key)) {
                continue;
            }
            String newValue = input.getText() == null ? "" : input.getText().toString().trim();
            String oldValue = sp.getString(key, "");
            if (!TextUtils.equals(newValue, oldValue)) {
                editor.putString(key, newValue);
                changed = true;
            }
        }
        if (changed) {
            editor.apply();
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
        // 宽度取屏宽的 92%，上限 560dp，避免大屏上被拉得过分宽
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int maxWidth = (int) (560 * getResources().getDisplayMetrics().density);
        int width = Math.min((int) (screenWidth * 0.92f), maxWidth);
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);

        // 键盘弹出时压缩弹窗可用高度（而非把弹窗顶出屏幕），配合内容区限高使用
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // 内容区限高，保证键盘弹出时输入框不会被顶出屏幕
        if (mScroll != null) {
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            mScroll.setMaxHeight((int) (screenHeight * 0.5f));
        }
    }
}
