package com.tianma.xsmscode.ui.home;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.afollestad.materialdialogs.MaterialDialog;
import com.smscodf.zhuxf.R;
import com.tianma.xsmscode.common.constant.Const;
import com.tianma.xsmscode.common.constant.PrefConst;
import com.tianma.xsmscode.common.utils.PackageUtils;
import com.tianma.xsmscode.common.utils.ThemeColors;
import com.tianma.xsmscode.common.utils.Utils;
import com.tianma.xsmscode.xp.hook.ActivationService;
import com.tianma.xsmscode.ui.app.base.BaseActivity;
import com.tianma.xsmscode.ui.faq.FaqFragment;
import com.tianma.xsmscode.ui.record.CodeRecordFragment;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * 主界面（2026-09-15 改版：底栏双 tab —— 记录 | 设置）
 */
public class HomeActivity extends BaseActivity {
    @BindView(R.id.toolbar)
    Toolbar mToolbar;

    private static final String TAG_FAQ = "tag_faq";

    private Fragment mCurrentFragment;
    private FragmentManager mFragmentManager;

    /** 底栏（2026-10-17 改为传信同款悬浮胶囊） */
    private View mNavRecords;
    private View mNavSettings;
    private ImageView mNavRecordsIcon;
    private ImageView mNavSettingsIcon;
    private TextView mNavRecordsText;
    private TextView mNavSettingsText;
    private View mBottomBar;
    /** 当前选中 tab：true = 记录，false = 设置 */
    private boolean mRecordsSelected = true;

    // 首页三段式面板（2026-10-18）
    private TextView mStatusTitle;
    private TextView mStatusSub;
    private com.google.android.material.switchmaterial.SwitchMaterial mStatusSwitch;
    private TextView mStatToday;
    private TextView mStatTotal;
    private TextView mStatFailed;
    private TextView mStatTodayLabel;
    private TextView mStatTotalLabel;
    private TextView mStatFailedLabel;
    private HomeAdapter mHomeAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_home);
        ButterKnife.bind(this);

        getExternalFilesDir("");

        shareXposedPreferences();

        // setup toolbar
        setupToolbar();

        // 首页面板（2026-10-18）
        setupHomePanel();

        // bottom tabs: 记录 | 设置（默认进入记录）
        setupBottomNav();
        handleIntent(getIntent());

        // check module activation status
        checkModuleActivationStatus();
    }

    private void setupToolbar() {
        setSupportActionBar(mToolbar);
    }

    /** 首页面板初始化（2026-10-18） */
    private void setupHomePanel() {
        mStatusTitle = findViewById(R.id.status_title);
        mStatusSub = findViewById(R.id.status_sub);
        mStatusSwitch = findViewById(R.id.status_switch);
        mStatToday = findViewById(R.id.stat_today);
        mStatTotal = findViewById(R.id.stat_total);
        mStatFailed = findViewById(R.id.stat_failed);
        mStatTodayLabel = findViewById(R.id.stat_today_label);
        mStatTotalLabel = findViewById(R.id.stat_total_label);
        mStatFailedLabel = findViewById(R.id.stat_failed_label);

        if (mStatusSwitch != null) {
            mStatusSwitch.setOnCheckedChangeListener((btn, checked) -> {
                getSharedPreferences(PrefConst.PREF_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(PrefConst.KEY_ENABLE_FORWARD, checked).apply();
                try {
                    com.tianma.xsmscode.common.utils.PrefsExporter.export(this);
                } catch (Throwable ignored) {
                }
                if (checked) {
                    try {
                        com.tianma.xsmscode.feature.forward.ForwardKeepAliveService.start(this);
                    } catch (Throwable ignored) {
                    }
                }
                refreshHome();
            });
        }

        androidx.recyclerview.widget.RecyclerView rv = findViewById(R.id.list_recent);
        if (rv != null) {
            rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
            rv.setNestedScrollingEnabled(false);
            mHomeAdapter = new HomeAdapter();
            mHomeAdapter.setOnDetailClick(this::openDetailByTime);
            rv.setAdapter(mHomeAdapter);
        }
    }

    /** 从「最近转发」进入短信详情（2026-10-18） */
    private void openDetailByTime(long time) {
        try {
            java.util.List<com.tianma.xsmscode.data.db.entity.SmsMsg> all =
                    com.tianma.xsmscode.data.db.DBManager.get(this).queryAllSmsMsg();
            for (com.tianma.xsmscode.data.db.entity.SmsMsg m : all) {
                if (m.getDate() == time) {
                    com.tianma.xsmscode.ui.record.SmsDetailDialog.show(
                            getSupportFragmentManager(),
                            m.getSender(), m.getBody(), m.getSmsCode(), m.getDate(),
                            new com.tianma.xsmscode.ui.record.SmsDetailDialog.Actions() {
                                @Override
                                public void onForward() {
                                    // 「转发信息」：把这条短信再发一遍
                                    try {
                                        android.os.Bundle args = new android.os.Bundle();
                                        args.putString("sender", m.getSender());
                                        args.putString("body", m.getBody());
                                        args.putString("code", m.getSmsCode());
                                        args.putLong("time", m.getDate());
                                        getContentResolver().call(android.net.Uri.parse(
                                                        "content://" + com.tianma.xsmscode.data.db.DBProvider.AUTHORITY),
                                                "forward", null, args);
                                        android.widget.Toast.makeText(HomeActivity.this, R.string.forward_succeed,
                                                android.widget.Toast.LENGTH_SHORT).show();
                                    } catch (Throwable t) {
                                        android.widget.Toast.makeText(HomeActivity.this,
                                                R.string.forward_failed,
                                                android.widget.Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void setupBottomNav() {
        mNavRecords = findViewById(R.id.nav_records);
        mNavSettings = findViewById(R.id.nav_settings);
        mNavRecordsIcon = findViewById(R.id.nav_records_icon);
        mNavSettingsIcon = findViewById(R.id.nav_settings_icon);
        mNavRecordsText = findViewById(R.id.nav_records_text);
        mNavSettingsText = findViewById(R.id.nav_settings_text);
        mBottomBar = findViewById(R.id.bottom_bar);

        mNavRecords.setOnClickListener(v -> selectTab(true));
        mNavSettings.setOnClickListener(v -> selectTab(false));

        applyNavTheme();
    }

    /**
     * 选中/切换 tab（2026-10-18 改版）。
     *
     * <p>首页不再是「验证码记录 Fragment」，而是三段式面板
     * （状态卡 / 统计卡 / 最近转发），直接写在 activity_home 里：
     * 切到「设置」时把首页面板整体隐藏、把 Fragment 容器铺满即可，
     * 切回时反向操作。
     */
    private void selectTab(boolean records) {
        mRecordsSelected = records;
        View homePanel = findViewById(R.id.home_panel);
        View container = findViewById(R.id.home_content);

        if (records) {
            // 清掉设置页
            while (mFragmentManager.getBackStackEntryCount() > 0) {
                mFragmentManager.popBackStackImmediate();
            }
            mFragmentManager.beginTransaction()
                    .replace(R.id.home_content, new Fragment())
                    .commitNowAllowingStateLoss();
            if (homePanel != null) {
                homePanel.setVisibility(View.VISIBLE);
            }
            if (container != null) {
                container.setVisibility(View.GONE);
            }
            refreshHome();
        } else {
            if (homePanel != null) {
                homePanel.setVisibility(View.GONE);
            }
            if (container != null) {
                container.setVisibility(View.VISIBLE);
            }
            mFragmentManager.beginTransaction()
                    .replace(R.id.home_content, SettingsFragment.newInstance())
                    .commitAllowingStateLoss();
        }
        applyNavTheme();
    }

    /** 刷新首页状态卡、统计与最近列表（2026-10-18） */
    private void refreshHome() {
        if (mStatusSwitch == null) {
            return;
        }
        SharedPreferences sp = getSharedPreferences(PrefConst.PREF_NAME, Context.MODE_PRIVATE);
        boolean enabled = sp.getBoolean(PrefConst.KEY_ENABLE_FORWARD, false);

        boolean dark = ThemeColors.isDark();
        int textPrimary = dark ? 0xFFFFFFFF : 0xFF1A1A1A;
        int textHint = dark ? 0x99FFFFFF : 0x99000000;
        int accent = ThemeColors.accent();
        int cardBg = dark ? 0xFF2A2D34 : 0xFFF4F5F8;

        com.google.android.material.card.MaterialCardView c1 = findViewById(R.id.card_status);
        com.google.android.material.card.MaterialCardView c2 = findViewById(R.id.card_stats);
        com.google.android.material.card.MaterialCardView c3 = findViewById(R.id.card_recent);
        if (c1 != null) {
            c1.setCardBackgroundColor(cardBg);
        }
        if (c2 != null) {
            c2.setCardBackgroundColor(cardBg);
        }
        if (c3 != null) {
            c3.setCardBackgroundColor(cardBg);
        }

        // 状态卡
        mStatusTitle.setText(enabled
                ? R.string.home_service_running : R.string.home_service_stopped);
        mStatusTitle.setTextColor(textPrimary);
        mStatusSub.setText(enabled
                ? R.string.home_service_running_sub : R.string.home_service_stopped_sub);
        mStatusSub.setTextColor(textHint);
        mStatusSwitch.setChecked(enabled);

        ImageView icon = findViewById(R.id.status_icon);
        if (icon != null) {
            android.graphics.drawable.GradientDrawable circle =
                    new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(enabled ? accent : (dark ? 0xFF4A4E57 : 0xFFBDBDBD));
            icon.setBackground(circle);
            // 图标本身是纯黑填充，在绿底上几乎不可见 —— 统一 tint 成白色
            Drawable d = icon.getDrawable();
            if (d != null) {
                d = DrawableCompat.wrap(d.mutate());
                DrawableCompat.setTint(d, 0xFFFFFFFF);
                icon.setImageDrawable(d);
            }
        }

        // 统计
        com.tianma.xsmscode.feature.forward.ForwardResultStore.Stats st =
                com.tianma.xsmscode.feature.forward.ForwardResultStore.stats();
        mStatToday.setText(String.valueOf(st.today));
        mStatTotal.setText(String.valueOf(st.total));
        mStatFailed.setText(String.valueOf(st.failed));
        mStatToday.setTextColor(accent);
        mStatTotal.setTextColor(accent);
        mStatFailed.setTextColor(st.failed > 0 ? 0xFFE05252 : accent);
        if (mStatTodayLabel != null) {
            mStatTodayLabel.setTextColor(textHint);
        }
        if (mStatTotalLabel != null) {
            mStatTotalLabel.setTextColor(textHint);
        }
        if (mStatFailedLabel != null) {
            mStatFailedLabel.setTextColor(textHint);
        }
        View section = findViewById(R.id.section_recent);
        if (section instanceof TextView) {
            ((TextView) section).setTextColor(textHint);
        }

        // 最近列表
        java.util.List<com.tianma.xsmscode.feature.forward.ForwardResultStore.Record> recent =
                com.tianma.xsmscode.feature.forward.ForwardResultStore.readRecent(20);
        java.util.List<String> codes = new java.util.ArrayList<>();
        for (com.tianma.xsmscode.feature.forward.ForwardResultStore.Record r : recent) {
            codes.add(lookupCode(r.time));
        }
        if (mHomeAdapter != null) {
            mHomeAdapter.setColors(textPrimary, textHint, accent, dark);
            mHomeAdapter.setData(recent, codes);
        }
        View empty = findViewById(R.id.empty_hint);
        if (empty != null) {
            empty.setVisibility(recent.isEmpty() ? View.VISIBLE : View.GONE);
            if (empty instanceof TextView) {
                ((TextView) empty).setTextColor(textHint);
            }
        }
    }

    /** 按时间戳取短信验证码（列表展示用，查不到返回空串） */
    private String lookupCode(long time) {
        try {
            java.util.List<com.tianma.xsmscode.data.db.entity.SmsMsg> all =
                    com.tianma.xsmscode.data.db.DBManager.get(this).queryAllSmsMsg();
            for (com.tianma.xsmscode.data.db.entity.SmsMsg m : all) {
                if (m.getDate() == time) {
                    return m.getSmsCode() == null ? "" : m.getSmsCode();
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /**
     * 底栏视觉（2026-10-17）：完全照搬传信的色值与结构。
     *
     * <p>★ 明暗不由 {@code values-night} 决定：智码通的主题由 Cyanea 管理，
     * 可能与系统 uiMode 不一致（实测：系统深色 + 应用白底，走 night 目录会把
     * 深色玻璃叠在白色页面上，看起来就是一块灰）。改为按 Cyanea 背景亮度显式选资源。
     *
     * <p>颜色见 drawable/bg_nav_bar_glass[_dark].xml 与
     * drawable/bg_nav_item_selected[_dark].xml，数值即传信原值。
     */
    private void applyNavTheme() {
        if (mBottomBar == null) {
            return;
        }
        boolean dark = ThemeColors.isDark();

        mBottomBar.setBackgroundResource(dark
                ? R.drawable.bg_nav_bar_glass_dark
                : R.drawable.bg_nav_bar_glass);

        mNavRecords.setBackgroundResource(mRecordsSelected
                ? (dark ? R.drawable.bg_nav_item_selected_dark : R.drawable.bg_nav_item_selected)
                : 0);
        mNavSettings.setBackgroundResource(!mRecordsSelected
                ? (dark ? R.drawable.bg_nav_item_selected_dark : R.drawable.bg_nav_item_selected)
                : 0);

        int sel = dark ? 0xFF4CC97B : 0xFF2F9D56;
        int normal = 0xFF8D97A6;
        if (mNavRecordsText != null) {
            mNavRecordsText.setTextColor(mRecordsSelected ? sel : normal);
        }
        if (mNavSettingsText != null) {
            mNavSettingsText.setTextColor(!mRecordsSelected ? sel : normal);
        }
        tint(mNavRecordsIcon, mRecordsSelected, sel, normal);
        tint(mNavSettingsIcon, !mRecordsSelected, sel, normal);
    }

    private void tint(ImageView iv, boolean selected, int selectedColor, int normalColor) {
        if (iv == null) {
            return;
        }
        Drawable d = iv.getDrawable();
        if (d == null) {
            return;
        }
        d = DrawableCompat.wrap(d.mutate());
        DrawableCompat.setTint(d, selected ? selectedColor : normalColor);
        iv.setImageDrawable(d);
    }

    private void handleIntent(Intent intent) {
        mFragmentManager = getSupportFragmentManager();
        // 默认进入记录 tab
        selectTab(true);
    }

    private void switchTab(Fragment fragment, String title) {
        // 清掉 FAQ 等压栈页面，避免跨 tab 残留
        while (mFragmentManager.getBackStackEntryCount() > 0) {
            mFragmentManager.popBackStackImmediate();
        }
        mFragmentManager.beginTransaction()
                .replace(R.id.home_content, fragment)
                .commit();
        mCurrentFragment = fragment;
        refreshActionBar(title);
        invalidateOptionsMenu();
    }

    private String titleFor(Fragment fragment) {
        // 两个 tab 的标题统一为应用名"智码通"（2026-09-15 用户要求）
        return getString(R.string.app_name);
    }

    private void refreshActionBar(String title) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
            boolean hasStack = mFragmentManager != null && mFragmentManager.getBackStackEntryCount() > 0;
            actionBar.setHomeButtonEnabled(hasStack);
            actionBar.setDisplayHomeAsUpEnabled(hasStack);
        }
        refreshActivationStatus();
    }

    /**
     * 刷新标题栏的激活状态（2026-09-20）。
     *
     * <p>判据：libxposed 官方 service 是否已绑定。官方文档说明——
     * 模块 app 启动时框架会主动下发 service；service 非空即表示模块已激活。
     *
     * <p>为什么不用 self-hook：LSPosed 源码 ScopeAdapter.refresh() 显式排除
     * {@code packageName.equals(module.packageName)}，模块自身既不能勾选、
     * 也不会被注入（官方文档亦确认 "module apps are no longer hooked by
     * themselves"），故 self-hook 方案在该框架下不可行。
     */
    private void refreshActivationStatus() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        boolean activated = ActivationService.isActivated();
        String text;
        if (activated) {
            String fw = ActivationService.getFrameworkName();
            String ver = ActivationService.getFrameworkVersion();
            text = (fw == null)
                    ? getString(R.string.module_status_active)
                    : getString(R.string.module_status_active) + " · " + fw
                      + (ver == null ? "" : " " + ver);
        } else {
            text = getString(R.string.module_status_inactive);
        }
        actionBar.setSubtitle(text);
    }

    @Override
    public void onBackPressed() {
        if (mFragmentManager == null || mFragmentManager.getBackStackEntryCount() == 0) {
            super.onBackPressed();
        } else {
            mFragmentManager.popBackStackImmediate();
            mCurrentFragment = mFragmentManager.findFragmentById(R.id.home_content);
            refreshActionBar(titleFor(mCurrentFragment));
        }
        invalidateOptionsMenu();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_home_faq:
                onFAQSelected();
                return true;
            case R.id.action_taichi_users_notice:
                onTaichiUsersNoticeSelected();
                return true;
            case R.id.action_edxposed_users_notice:
                onEdxposedUsersNoticeSelected();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 恢复右上角三点菜单（2026-09-19 用户要求）。
        // 仅在"设置"tab 显示 menu_home；记录 tab 交给 CodeRecordFragment
        // 贡献自己的菜单（编辑模式的删除/全选），避免两组菜单混排。
        if (mCurrentFragment instanceof SettingsFragment) {
            getMenuInflater().inflate(R.menu.menu_home, menu);
        }
        return true;
    }

    private void onFAQSelected() {
        openFaq();
    }

    /** 打开常见问题页（2026-09-19：三点菜单隐藏后，由 设置 → 关于 → 常见问题 调用） */
    void openFaq() {
        FaqFragment faqFragment = FaqFragment.newInstance();
        mFragmentManager
                .beginTransaction()
                .replace(R.id.home_content, faqFragment, TAG_FAQ)
                .addToBackStack(TAG_FAQ)
                .commit();
        mCurrentFragment = faqFragment;
        refreshActionBar(getString(R.string.action_home_faq_title));
        invalidateOptionsMenu();
    }

    void onTaichiUsersNoticeSelected() {
        new MaterialDialog.Builder(this)
                .title(R.string.taichi_users_notice)
                .content(R.string.taichi_users_notice_content)
                .negativeText(R.string.add_apps_in_taichi)
                .onNegative((dialog, which) -> PackageUtils.startAddAppsInTaiChi(HomeActivity.this))
                .positiveText(R.string.check_module_in_taichi)
                .onPositive((dialog, which) -> PackageUtils.startCheckModuleInTaiChi(HomeActivity.this))
                .show();
    }

    /**
     * 关于软件（2026-09-20）：
     * 原"EdXposed用户须知"改为本软件介绍，并提供仓库入口。
     * 菜单项 id 沿用 action_edxposed_users_notice 以保持兼容。
     */
    void onEdxposedUsersNoticeSelected() {
        new MaterialDialog.Builder(this)
                .title(R.string.about_software_title)
                .content(R.string.about_software_content)
                .positiveText(R.string.about_software_repo)
                .onPositive((dialog, which) ->
                        Utils.showWebPage(HomeActivity.this, Const.PROJECT_SOURCE_CODE_URL))
                .negativeText(R.string.i_know)
                .show();
    }

    private void checkModuleActivationStatus() {
        Handler handler = new Handler(Looper.getMainLooper());
        // 报到由被 hook 的进程异步写入，app 启动时可能尚未落盘。
        // 分几次复查：2s / 5s / 10s，避免用户看到"未激活"就一直不变。
        long[] delays = {2000L, 5000L, 10000L};
        for (long delay : delays) {
            handler.postDelayed(() -> {
                if (!isFinishing()) {
                    refreshActivationStatus();
                }
            }, delay);
        }
    }

    @SuppressLint("WorldReadableFiles")
    private void shareXposedPreferences() {
        try {
            // EdXposed or LSPosed new XSharedPreferences:  https://github.com/LSPosed/LSPosed/wiki/New-XSharedPreferences
            getSharedPreferences(PrefConst.PREF_NAME, Context.MODE_WORLD_READABLE);
        } catch (SecurityException exception) {
            // 如果模块没有被 EdXposed 或者 LSPosed 激活，就会走到这里来
            // ignore
        }
    }
}
