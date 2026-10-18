package com.tianma.xsmscode.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.InputType;
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

import com.smscodf.zhuxf.R;
import com.tianma.xsmscode.ui.forward.ChannelDialogTheme;
import com.tianma.xsmscode.ui.forward.MaxHeightScrollView;

/**
 * 通用弹窗（2026-10-18 新增）。
 *
 * <p>替代项目里所有 {@code MaterialDialog} 的原生样式——后者是 Alert 风格
 * （小圆角 9-patch、系统强调色按钮），与现有卡片风格不一致。
 *
 * <p>一个类覆盖四种形态：
 * <ul>
 *   <li>标题 + 正文 + 双按钮（用户须知、隐私政策、版本更新）</li>
 *   <li>标题 + 输入框 + 双按钮（验证码测试、备份文件名）</li>
 *   <li>标题 + 选项列表（选择备份文件、正则快速选择）</li>
 *   <li>仅进度（{@link #showProgress}）</li>
 * </ul>
 *
 * <p>配色统一走 {@link ChannelDialogTheme}（按 Cyanea 背景亮度判断深浅）。
 */
public class CommonDialog extends AppCompatDialogFragment {

    private static final String TAG = "CommonDialog";

    private static final String ARG_TITLE = "title";
    private static final String ARG_CONTENT = "content";
    private static final String ARG_INPUT_HINT = "input_hint";
    private static final String ARG_INPUT_TEXT = "input_text";
    private static final String ARG_INPUT_MULTILINE = "input_multiline";
    private static final String ARG_POSITIVE = "positive";
    private static final String ARG_NEGATIVE = "negative";
    private static final String ARG_ITEMS = "items";
    private static final String ARG_LEFT_ALIGNED = "left_aligned";
    private static final String ARG_CUSTOM_LAYOUT = "custom_layout";
    private static final String ARG_AUTO_DISMISS = "auto_dismiss";
    private static final String ARG_PROGRESS = "progress";

    /** 主按钮点击 */
    public interface OnPositive {
        /** @param input 输入框内容（无输入框时为空串） */
        void onPositive(String input);
    }

    /** 选项点击 */
    public interface OnItemPicked {
        void onItemPicked(int position, String text);
    }

    /** 负按钮点击 */
    public interface OnNegative {
        void onNegative();
    }

    /** 自定义视图填充回调（此时视图已 inflate，可在此绑定监听） */
    public interface OnCustomInflate {
        void onCustomInflate(View container);
    }

    private OnPositive mOnPositive;
    /** 自定义视图容器（customView 模式时由外部填充） */
    private android.widget.FrameLayout mCustomBox;
    private OnNegative mOnNegative;
    private OnItemPicked mOnItemPicked;
    private OnCustomInflate mOnCustomInflate;
    private EditText mInput;

    // ------------------------------------------------------------ 构造入口

    public static class Builder {
        private final FragmentManager mFm;
        private final android.content.Context mCtx;
        private String mTitle;
        private String mContent;
        private String mInputHint;
        private String mInputText;
        private boolean mMultiline;
        private String mPositive;
        private String mNegative;
        private String[] mItems;
        private boolean mLeftAligned;
        private int mCustomLayout;
        private boolean mAutoDismiss = true;
        private boolean mProgress;
        private OnPositive mOnPositive;
    /** 自定义视图容器（customView 模式时由外部填充） */
    private android.widget.FrameLayout mCustomBox;
        private OnNegative mOnNegative;
        private OnItemPicked mOnItemPicked;
        private OnCustomInflate mOnCustomInflate;

        public Builder(@Nullable FragmentManager fm, android.content.Context ctx) {
            mFm = fm;
            mCtx = ctx == null ? null : ctx.getApplicationContext();
        }

        private String str(int resId) {
            return mCtx == null ? "" : mCtx.getString(resId);
        }

        public Builder title(CharSequence title) {
            mTitle = title == null ? null : title.toString();
            return this;
        }

        public Builder title(int resId) {
            mTitle = str(resId);
            return this;
        }

        public Builder content(CharSequence content) {
            mContent = content == null ? null : content.toString();
            return this;
        }

        public Builder content(int resId) {
            mContent = str(resId);
            return this;
        }

        public Builder input(String hint, String text, boolean multiline) {
            mInputHint = hint;
            mInputText = text;
            mMultiline = multiline;
            return this;
        }

        public Builder positive(int resId, OnPositive l) {
            mPositive = str(resId);
            mOnPositive = l;
            return this;
        }

        public Builder positive(String text, OnPositive l) {
            mPositive = text;
            mOnPositive = l;
            return this;
        }

        public Builder negative(int resId) {
            mNegative = str(resId);
            return this;
        }

        public Builder negative(int resId, OnNegative l) {
            mNegative = str(resId);
            mOnNegative = l;
            return this;
        }

        /** 无负按钮（只显示一个"知道了"） */
        public Builder dismissOnly(int resId) {
            mPositive = str(resId);
            return this;
        }

        public Builder items(String[] items, OnItemPicked l) {
            mItems = items;
            mOnItemPicked = l;
            return this;
        }

        /** 自定义视图作为内容（与 input/items 互斥） */
        public Builder customView(int layoutRes, OnCustomInflate onInflate) {
            mCustomLayout = layoutRes;
            mOnCustomInflate = onInflate;
            return this;
        }

        /** 主按钮点击后不自动关闭（便于校验失败时留在弹窗） */
        public Builder autoDismiss(boolean auto) {
            mAutoDismiss = auto;
            return this;
        }

        /** 显示不确定进度条（转圈），配合 content 用作加载提示 */
        public Builder progress(boolean show) {
            mProgress = show;
            return this;
        }

        /** 正文左对齐（默认居中，长文本建议左对齐） */
        public Builder leftAligned() {
            mLeftAligned = true;
            return this;
        }

        /** 构造但不显示（需要设置 setCancelable 等时用） */
        public CommonDialog build() {
            CommonDialog d = new CommonDialog();
            Bundle args = new Bundle();
            args.putString(ARG_TITLE, mTitle);
            args.putString(ARG_CONTENT, mContent);
            args.putString(ARG_INPUT_HINT, mInputHint);
            args.putString(ARG_INPUT_TEXT, mInputText);
            args.putBoolean(ARG_INPUT_MULTILINE, mMultiline);
            args.putString(ARG_POSITIVE, mPositive);
            args.putString(ARG_NEGATIVE, mNegative);
            args.putStringArray(ARG_ITEMS, mItems);
            args.putBoolean(ARG_LEFT_ALIGNED, mLeftAligned);
            args.putInt(ARG_CUSTOM_LAYOUT, mCustomLayout);
            args.putBoolean(ARG_AUTO_DISMISS, mAutoDismiss);
            args.putBoolean(ARG_PROGRESS, mProgress);
            d.setArguments(args);
            d.mOnPositive = mOnPositive;
            d.mOnNegative = mOnNegative;
            d.mOnItemPicked = mOnItemPicked;
            d.mOnCustomInflate = mOnCustomInflate;
            return d;
        }

        private CommonDialog mShown;

        public void show() {
            if (mFm == null) {
                return;
            }
            // 同名 dialog 只保留一个，避免重复弹出
            if (mFm.findFragmentByTag(TAG) != null) {
                return;
            }
            mShown = build();
            mShown.show(mFm, TAG);
        }

        /** 供 autoDismiss(false) 时手动关闭（在 positive 回调里调用） */
        public void dismiss() {
            if (mShown != null && mShown.isAdded()) {
                mShown.dismissAllowingStateLoss();
            }
        }
    }

    // ------------------------------------------------------------ 生命周期

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
        return inflater.inflate(R.layout.dialog_common, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int bg = ChannelDialogTheme.dialogBackground();
        int textPrimary = ChannelDialogTheme.textColor();
        int textHint = ChannelDialogTheme.hintColor();
        int divider = ChannelDialogTheme.dividerColor();
        int accent = ChannelDialogTheme.accentColor();
        boolean dark = ChannelDialogTheme.isDark();

        Bundle a = getArguments();
        String title = a == null ? null : a.getString(ARG_TITLE);
        String content = a == null ? null : a.getString(ARG_CONTENT);
        String hint = a == null ? null : a.getString(ARG_INPUT_HINT);
        String inputText = a == null ? null : a.getString(ARG_INPUT_TEXT);
        boolean multiline = a != null && a.getBoolean(ARG_INPUT_MULTILINE, false);
        String positive = a == null ? null : a.getString(ARG_POSITIVE);
        String negative = a == null ? null : a.getString(ARG_NEGATIVE);
        String[] items = a == null ? null : a.getStringArray(ARG_ITEMS);

        com.google.android.material.card.MaterialCardView card =
                view.findViewById(R.id.common_dialog_card);
        card.setCardBackgroundColor(bg);
        view.findViewById(R.id.common_dialog_divider_top).setBackgroundColor(divider);
        view.findViewById(R.id.common_dialog_divider_bottom).setBackgroundColor(divider);

        TextView titleView = view.findViewById(R.id.common_dialog_title);
        if (TextUtils.isEmpty(title)) {
            titleView.setVisibility(View.GONE);
            view.findViewById(R.id.common_dialog_divider_top).setVisibility(View.GONE);
        } else {
            titleView.setText(title);
            titleView.setTextColor(textPrimary);
        }

        MaxHeightScrollView scroll = view.findViewById(R.id.common_dialog_scroll);

        // ---- 正文 ----
        TextView contentView = view.findViewById(R.id.common_dialog_content);
        if (!TextUtils.isEmpty(content)) {
            contentView.setText(content);
            contentView.setTextColor(textPrimary);
            contentView.setVisibility(View.VISIBLE);
            boolean leftAligned = a != null && a.getBoolean(ARG_LEFT_ALIGNED, false);
            contentView.setGravity(leftAligned
                    ? android.view.Gravity.START : android.view.Gravity.CENTER);
        }

        // ---- 输入框 ----
        mInput = view.findViewById(R.id.common_dialog_input);
        if (hint != null) {
            mInput.setVisibility(View.VISIBLE);
            mInput.setHint(hint);
            mInput.setText(inputText == null ? "" : inputText);
            mInput.setTextColor(textPrimary);
            mInput.setHintTextColor(textHint);
            mInput.setInputType(multiline
                    ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE)
                    : InputType.TYPE_CLASS_TEXT);
            android.graphics.drawable.GradientDrawable box =
                    new android.graphics.drawable.GradientDrawable();
            box.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            box.setCornerRadius(dp(10));
            box.setColor(dark ? 0x14FFFFFF : 0x0F000000);
            box.setStroke(Math.max(1, (int) dp(1)), dark ? 0x22FFFFFF : 0x14000000);
            mInput.setBackground(box);
            if (inputText != null) {
                mInput.setSelection(mInput.getText().length());
            }
        }

        // ---- 进度条 ----
        if (a != null && a.getBoolean(ARG_PROGRESS, false)) {
            View pb = view.findViewById(R.id.common_dialog_progress);
            if (pb != null) {
                pb.setVisibility(View.VISIBLE);
                if (pb instanceof android.widget.ProgressBar) {
                    ((android.widget.ProgressBar) pb).getIndeterminateDrawable()
                            .setColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN);
                }
            }
        }

        // ---- 自定义视图 ----
        int customLayout = a == null ? 0 : a.getInt(ARG_CUSTOM_LAYOUT, 0);
        mCustomBox = view.findViewById(R.id.common_dialog_custom);
        if (customLayout != 0) {
            mCustomBox.setVisibility(View.VISIBLE);
            LayoutInflater.from(requireContext()).inflate(customLayout, mCustomBox, true);
            if (mOnCustomInflate != null) {
                mOnCustomInflate.onCustomInflate(mCustomBox);
            }
        }

        // ---- 选项列表 ----
        LinearLayout itemsBox = view.findViewById(R.id.common_dialog_items);
        if (items != null && items.length > 0) {
            itemsBox.setVisibility(View.VISIBLE);
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            for (int i = 0; i < items.length; i++) {
                final int pos = i;
                final String text = items[i];
                View row = inflater.inflate(R.layout.item_channel_option, itemsBox, false);
                TextView label = row.findViewById(R.id.channel_option_text);
                label.setText(text);
                label.setTextColor(textPrimary);
                View cfg = row.findViewById(R.id.channel_option_config);
                if (cfg != null) {
                    cfg.setVisibility(View.GONE);
                }
                View check = row.findViewById(R.id.channel_option_check);
                if (check != null) {
                    check.setVisibility(View.GONE);
                }
                row.setOnClickListener(v -> {
                    if (mOnItemPicked != null) {
                        mOnItemPicked.onItemPicked(pos, text);
                    }
                    dismissAllowingStateLoss();
                });
                itemsBox.addView(row);
            }
        }

        // ---- 按钮 ----
        TextView pos = view.findViewById(R.id.common_dialog_positive);
        if (TextUtils.isEmpty(positive)) {
            pos.setVisibility(View.GONE);
        } else {
            pos.setText(positive);
            pos.setTextColor(accent);
            pos.setOnClickListener(v -> {
                String input = mInput == null || mInput.getVisibility() != View.VISIBLE
                        ? "" : mInput.getText().toString();
                if (mOnPositive != null) {
                    mOnPositive.onPositive(input);
                }
                boolean auto = getArguments() == null
                        || getArguments().getBoolean(ARG_AUTO_DISMISS, true);
                if (auto) {
                    dismissAllowingStateLoss();
                }
            });
        }

        TextView neg = view.findViewById(R.id.common_dialog_negative);
        if (TextUtils.isEmpty(negative)) {
            neg.setVisibility(View.GONE);
        } else {
            neg.setText(negative);
            neg.setTextColor(textHint);
            neg.setOnClickListener(v -> {
                if (mOnNegative != null) {
                    mOnNegative.onNegative();
                }
                dismissAllowingStateLoss();
            });
        }

        // 内容区限高，避免超长文本把弹窗顶出屏幕
        if (scroll != null) {
            int h = getResources().getDisplayMetrics().heightPixels;
            scroll.setMaxHeight((int) (h * 0.55f));
        }
    }

    /** 取自定义视图容器（customView 模式下由调用方填充内容） */
    @Nullable
    public View getCustomContainer() {
        return mCustomBox;
    }

    @Nullable
    public EditText getInputView() {
        return mInput;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null) {
            return;
        }
        Window w = dialog.getWindow();
        if (w == null) {
            return;
        }
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int maxWidth = (int) (560 * getResources().getDisplayMetrics().density);
        int width = Math.min((int) (screenWidth * 0.92f), maxWidth);
        w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
}
