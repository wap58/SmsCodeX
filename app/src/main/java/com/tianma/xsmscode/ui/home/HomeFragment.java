package com.tianma.xsmscode.ui.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.smscodf.zhuxf.R;
import com.tianma.xsmscode.common.constant.PrefConst;
import com.tianma.xsmscode.common.utils.ThemeColors;
import com.tianma.xsmscode.data.db.DBManager;
import com.tianma.xsmscode.data.db.entity.SmsMsg;
import com.tianma.xsmscode.feature.forward.ForwardKeepAliveService;
import com.tianma.xsmscode.feature.forward.ForwardResultStore;
import com.tianma.xsmscode.ui.record.SmsDetailDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页（2026-10-18 新增）。
 *
 * <p>三段式：状态卡（转发开关）/ 统计卡（今日·累计·失败）/ 最近转发列表。
 * 与「记录」页（{@link com.tianma.xsmscode.ui.record.CodeRecordFragment}）是并列的两个 tab，
 * 记录页保持原样不动。
 *
 * <p>数据来源：
 * <ul>
 *   <li>开关状态：{@link PrefConst#KEY_ENABLE_FORWARD}</li>
 *   <li>统计与最近列表：{@link ForwardResultStore}（转发结果历史）</li>
 *   <li>验证码与正文：{@link DBManager}（按时间戳与转发记录对应）</li>
 * </ul>
 */
public class HomeFragment extends Fragment {

    private TextView mActivationTitle;
    private TextView mActivationVer;
    private TextView mActivationApi;
    private View mActivationCard;
    private TextView mStatusTitle;
    private TextView mStatusSub;
    private SwitchMaterial mStatusSwitch;
    private TextView mStatToday;
    private TextView mStatTotal;
    private TextView mStatFailed;
    private TextView mStatTodayLabel;
    private TextView mStatTotalLabel;
    private TextView mStatFailedLabel;
    private TextView mEmptyHint;
    private HomeAdapter mAdapter;
    /** 本页是否可见（onResume 时才刷新，避免重复查询数据库） */
    private boolean mResumed = false;

    public static HomeFragment newInstance() {
        return new HomeFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mActivationTitle = view.findViewById(R.id.activation_title);
        mActivationVer = view.findViewById(R.id.activation_ver);
        mActivationApi = view.findViewById(R.id.activation_api);
        mActivationCard = view.findViewById(R.id.card_activation);
        mStatusTitle = view.findViewById(R.id.status_title);
        mStatusSub = view.findViewById(R.id.status_sub);
        mStatusSwitch = view.findViewById(R.id.status_switch);
        mStatToday = view.findViewById(R.id.stat_today);
        mStatTotal = view.findViewById(R.id.stat_total);
        mStatFailed = view.findViewById(R.id.stat_failed);
        mStatTodayLabel = view.findViewById(R.id.stat_today_label);
        mStatTotalLabel = view.findViewById(R.id.stat_total_label);
        mStatFailedLabel = view.findViewById(R.id.stat_failed_label);
        mEmptyHint = view.findViewById(R.id.empty_hint);

        mStatusSwitch.setOnCheckedChangeListener((btn, checked) -> {
            getPrefs().edit().putBoolean(PrefConst.KEY_ENABLE_FORWARD, checked).apply();
            try {
                com.tianma.xsmscode.common.utils.PrefsExporter.export(requireContext());
            } catch (Throwable ignored) {
            }
            if (checked) {
                try {
                    ForwardKeepAliveService.start(requireContext());
                } catch (Throwable ignored) {
                }
            }
            refresh();
        });

        RecyclerView rv = view.findViewById(R.id.list_recent);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setNestedScrollingEnabled(false);
        mAdapter = new HomeAdapter();
        mAdapter.setOnDetailClick(this::openDetailByTime);
        rv.setAdapter(mAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        mResumed = true;
        refresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        mResumed = false;
    }

    private SharedPreferences getPrefs() {
        return requireContext().getSharedPreferences(PrefConst.PREF_NAME, Context.MODE_PRIVATE);
    }

    /** 刷新全部区块 */
    private void refresh() {
        if (!mResumed || mStatusSwitch == null) {
            return;
        }
        boolean dark = ThemeColors.isDark();
        int textPrimary = dark ? 0xFFFFFFFF : 0xFF1A1A1A;
        int textHint = dark ? 0x99FFFFFF : 0x99000000;
        int accent = ThemeColors.accent();
        int cardBg = dark ? 0xFF2A2D34 : 0xFFF4F5F8;

        View root = getView();
        if (root == null) {
            return;
        }
        MaterialCardView c1 = root.findViewById(R.id.card_status);
        MaterialCardView c2 = root.findViewById(R.id.card_stats);
        MaterialCardView c3 = root.findViewById(R.id.card_recent);
        c1.setCardBackgroundColor(cardBg);
        c2.setCardBackgroundColor(cardBg);
        c3.setCardBackgroundColor(cardBg);

        // ---- 激活卡（2026-10-18：从标题栏副标题移过来） ----
        bindActivation(root, dark);

        // ---- 状态卡 ----
        boolean enabled = getPrefs().getBoolean(PrefConst.KEY_ENABLE_FORWARD, false);
        mStatusTitle.setText(enabled
                ? R.string.home_service_running : R.string.home_service_stopped);
        mStatusTitle.setTextColor(textPrimary);
        mStatusSub.setText(enabled
                ? R.string.home_service_running_sub : R.string.home_service_stopped_sub);
        mStatusSub.setTextColor(textHint);

        // 开关用强调色（避免原生品红）
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        int thumb = dark ? 0xFFFFFFFF : 0xFFFFFFFF;
        mStatusSwitch.setThumbTintList(new ColorStateList(states,
                new int[]{thumb, dark ? 0xFF8A8F98 : 0xFFBDBDBD}));
        mStatusSwitch.setTrackTintList(new ColorStateList(states,
                new int[]{accent, dark ? 0x33FFFFFF : 0x1F000000}));
        if (mStatusSwitch.isChecked() != enabled) {
            mStatusSwitch.setOnCheckedChangeListener(null);
            mStatusSwitch.setChecked(enabled);
            mStatusSwitch.setOnCheckedChangeListener((btn, checked) -> onSwitchToggled(checked));
        }

        ImageView icon = root.findViewById(R.id.status_icon);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(enabled ? accent : (dark ? 0xFF4A4E57 : 0xFFBDBDBD));
        icon.setBackground(circle);
        Drawable d = icon.getDrawable();
        if (d != null) {
            d = DrawableCompat.wrap(d.mutate());
            DrawableCompat.setTint(d, 0xFFFFFFFF);
            icon.setImageDrawable(d);
        }

        // ---- 统计卡 ----
        ForwardResultStore.Stats st = ForwardResultStore.stats();
        mStatToday.setText(String.valueOf(st.today));
        mStatTotal.setText(String.valueOf(st.total));
        mStatFailed.setText(String.valueOf(st.failed));
        mStatToday.setTextColor(accent);
        mStatTotal.setTextColor(accent);
        mStatFailed.setTextColor(st.failed > 0 ? 0xFFE05252 : accent);
        mStatTodayLabel.setTextColor(textHint);
        mStatTotalLabel.setTextColor(textHint);
        mStatFailedLabel.setTextColor(textHint);

        TextView section = root.findViewById(R.id.section_recent);
        section.setTextColor(textHint);
        mEmptyHint.setTextColor(textHint);

        // ---- 最近转发 ----
        List<ForwardResultStore.Record> recent = ForwardResultStore.readRecent(20);
        List<String> codes = new ArrayList<>();
        for (ForwardResultStore.Record r : recent) {
            codes.add(lookupCode(r.time));
        }
        mAdapter.setColors(textPrimary, textHint, accent, dark);
        mAdapter.setData(recent, codes);
        mEmptyHint.setVisibility(recent.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * 激活卡（2026-10-18）。
     *
     * <p>原来这段信息是标题栏的副标题（{@code actionBar.setSubtitle}），
     * 现在挪到首页卡片上，信息量更大且不占标题栏。
     *
     * <p>版式：深绿渐变底 + 白字；右侧白色勾号圆环（右下角溢出裁切）。
     * 未激活时改为中性灰底，文案提示去启用。
     */
    /**
     * 只刷新激活卡（2026-10-18）。
     *
     * <p>LSPosed 的服务是异步绑定的，{@code HomeActivity#checkModuleActivationStatus}
     * 会在 2s/5s/10s 复查，这里提供入口让那几次复查能单独刷新这张卡，
     * 而不必重跑整页（避免反复查数据库）。
     */
    public void refreshActivationOnly() {
        View root = getView();
        if (root == null || mActivationTitle == null) {
            return;
        }
        bindActivation(root, ThemeColors.isDark());
    }

    private void bindActivation(View root, boolean dark) {
        boolean activated = com.tianma.xsmscode.xp.hook.ActivationService.isActivated();
        String fw = com.tianma.xsmscode.xp.hook.ActivationService.getFrameworkName();
        String ver = com.tianma.xsmscode.xp.hook.ActivationService.getFrameworkVersion();
        int api = com.tianma.xsmscode.xp.hook.ActivationService.getApiVersion();

        mActivationTitle.setText(activated
                ? R.string.module_status_active : R.string.module_status_inactive);
        mActivationTitle.setTextColor(0xFFFFFFFF);
        mActivationVer.setTextColor(0xCCFFFFFF);

        StringBuilder verText = new StringBuilder();
        if (ver != null) {
            verText.append(ver);
        } else if (fw != null) {
            verText.append(fw);
        }
        if (api > 0) {
            if (verText.length() > 0) {
                verText.append(" (").append(api).append(')');
            } else {
                verText.append("API ").append(api);
            }
        }
        mActivationVer.setText(verText.length() == 0 ? "—" : verText.toString());
        mActivationApi.setText(api > 0
                ? getString(R.string.activation_api, api)
                : getString(R.string.activation_api_unknown));

        MaterialCardView card = (MaterialCardView) mActivationCard;
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                activated
                        ? new int[]{0xFF1E5C3A, 0xFF17492E}
                        : new int[]{dark ? 0xFF33363D : 0xFFE0E0E0,
                        dark ? 0xFF2A2D34 : 0xFFD6D6D6});
        bg.setCornerRadius(dp(18));
        card.setCardBackgroundColor(0);
        mActivationCard.setBackground(bg);

        View badge = root.findViewById(R.id.activation_badge);
        if (badge != null) {
            badge.setVisibility(activated ? View.VISIBLE : View.GONE);
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private void onSwitchToggled(boolean checked) {
        getPrefs().edit().putBoolean(PrefConst.KEY_ENABLE_FORWARD, checked).apply();
        try {
            com.tianma.xsmscode.common.utils.PrefsExporter.export(requireContext());
        } catch (Throwable ignored) {
        }
        if (checked) {
            try {
                ForwardKeepAliveService.start(requireContext());
            } catch (Throwable ignored) {
            }
        }
        refresh();
    }

    /** 按时间戳取验证码 */
    private String lookupCode(long time) {
        try {
            List<SmsMsg> all = DBManager.get(requireContext()).queryAllSmsMsg();
            for (SmsMsg m : all) {
                if (m.getDate() == time) {
                    return m.getSmsCode() == null ? "" : m.getSmsCode();
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** 点「短信详情」：按时间戳找到短信并弹详情 */
    private void openDetailByTime(long time) {
        try {
            List<SmsMsg> all = DBManager.get(requireContext()).queryAllSmsMsg();
            for (final SmsMsg m : all) {
                if (m.getDate() != time) {
                    continue;
                }
                SmsDetailDialog.show(getChildFragmentManager(),
                        m.getSender(), m.getBody(), m.getSmsCode(), m.getDate(),
                        () -> {
                            try {
                                Bundle args = new Bundle();
                                args.putString("sender", m.getSender());
                                args.putString("body", m.getBody());
                                args.putString("code", m.getSmsCode());
                                args.putLong("time", m.getDate());
                                requireContext().getContentResolver().call(
                                        android.net.Uri.parse("content://"
                                                + com.tianma.xsmscode.data.db.DBProvider.AUTHORITY),
                                        "forward", null, args);
                                Toast.makeText(requireContext(), R.string.forward_succeed,
                                        Toast.LENGTH_SHORT).show();
                            } catch (Throwable t) {
                                Toast.makeText(requireContext(), R.string.forward_failed,
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                return;
            }
        } catch (Throwable ignored) {
        }
    }
}
