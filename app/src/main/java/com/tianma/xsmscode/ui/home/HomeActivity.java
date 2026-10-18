package com.tianma.xsmscode.ui.home;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
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
    private View mNavHome;
    private View mNavSettings;
    private ImageView mNavRecordsIcon;
    private ImageView mNavHomeIcon;
    private ImageView mNavSettingsIcon;
    private TextView mNavRecordsText;
    private TextView mNavHomeText;
    private TextView mNavSettingsText;
    private View mBottomBar;
    /**
     * 当前选中 tab（2026-10-18 起为三个）：
     * {@link #TAB_RECORDS} 记录（左）/ {@link #TAB_HOME} 首页（中）/ {@link #TAB_SETTINGS} 设置（右）
     */
    private static final int TAB_RECORDS = 0;
    private static final int TAB_HOME = 1;
    private static final int TAB_SETTINGS = 2;
    private int mCurrentTab = TAB_HOME;

    /** 上次停留的 tab（2026-10-18，进程回收后恢复用） */
    private static final String KEY_LAST_TAB = "pref_home_last_tab";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_home);
        ButterKnife.bind(this);

        getExternalFilesDir("");

        shareXposedPreferences();

        // setup toolbar
        setupToolbar();

        // bottom tabs: 记录 | 设置（默认进入记录）
        setupBottomNav();
        handleIntent(getIntent());

        // check module activation status
        checkModuleActivationStatus();
    }

    private void setupToolbar() {
        setSupportActionBar(mToolbar);
    }

    private void setupBottomNav() {
        mNavRecords = findViewById(R.id.nav_records);
        mNavHome = findViewById(R.id.nav_home);
        mNavSettings = findViewById(R.id.nav_settings);
        mNavRecordsIcon = findViewById(R.id.nav_records_icon);
        mNavHomeIcon = findViewById(R.id.nav_home_icon);
        mNavSettingsIcon = findViewById(R.id.nav_settings_icon);
        mNavRecordsText = findViewById(R.id.nav_records_text);
        mNavHomeText = findViewById(R.id.nav_home_text);
        mNavSettingsText = findViewById(R.id.nav_settings_text);
        mBottomBar = findViewById(R.id.bottom_bar);

        mNavRecords.setOnClickListener(v -> selectTab(TAB_RECORDS));
        mNavHome.setOnClickListener(v -> selectTab(TAB_HOME));
        mNavSettings.setOnClickListener(v -> selectTab(TAB_SETTINGS));

        applyNavTheme();
    }

    /**
     * 切换 tab（2026-10-18 三个 tab：记录 / 首页 / 设置）。
     *
     * <p>记录页与首页都是 Fragment，设置页也是 Fragment，统一走
     * {@link #switchTab}。首页默认选中。
     */
    private void selectTab(int tab) {
        mCurrentTab = tab;
        try {
            getSharedPreferences(PrefConst.PREF_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_LAST_TAB, tab).apply();
        } catch (Throwable ignored) {
        }
        switch (tab) {
            case TAB_RECORDS:
                switchTab(CodeRecordFragment.newInstance(), getString(R.string.app_name));
                break;
            case TAB_SETTINGS:
                switchTab(SettingsFragment.newInstance(), getString(R.string.app_name));
                break;
            case TAB_HOME:
            default:
                switchTab(HomeFragment.newInstance(), getString(R.string.app_name));
                break;
        }
        applyNavTheme();
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

        int pill = dark ? R.drawable.bg_nav_item_selected_dark : R.drawable.bg_nav_item_selected;
        mNavRecords.setBackgroundResource(mCurrentTab == TAB_RECORDS ? pill : 0);
        mNavHome.setBackgroundResource(mCurrentTab == TAB_HOME ? pill : 0);
        mNavSettings.setBackgroundResource(mCurrentTab == TAB_SETTINGS ? pill : 0);

        int sel = dark ? 0xFF4CC97B : 0xFF2F9D56;
        int normal = 0xFF8D97A6;
        boolean r = mCurrentTab == TAB_RECORDS;
        boolean h = mCurrentTab == TAB_HOME;
        boolean st = mCurrentTab == TAB_SETTINGS;
        if (mNavRecordsText != null) {
            mNavRecordsText.setTextColor(r ? sel : normal);
        }
        if (mNavHomeText != null) {
            mNavHomeText.setTextColor(h ? sel : normal);
        }
        if (mNavSettingsText != null) {
            mNavSettingsText.setTextColor(st ? sel : normal);
        }
        tint(mNavRecordsIcon, r, sel, normal);
        tint(mNavHomeIcon, h, sel, normal);
        tint(mNavSettingsIcon, st, sel, normal);
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
        // 2026-10-18：记住上次停的 tab（进程被系统回收后重建时不至于跳回默认页）。
        // 首次安装没有记录 → 默认首页。
        int saved = getSharedPreferences(PrefConst.PREF_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_LAST_TAB, TAB_HOME);
        if (saved != TAB_RECORDS && saved != TAB_HOME && saved != TAB_SETTINGS) {
            saved = TAB_HOME;
        }
        selectTab(saved);
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
    /**
     * 标题栏副标题（2026-10-18）。
     *
     * <p>激活信息只在<b>首页</b>以卡片形式展示（{@code HomeFragment#bindActivation}），
     * 标题栏所有页面一律不显示副标题。保留这个方法是为了让
     * {@code checkModuleActivationStatus()} 的多次延时刷新仍然有落脚点
     * （它同时触发首页卡片刷新）。
     */
    private void refreshActivationStatus() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle(null);
        }
        // 首页在前台时同步刷新激活卡
        if (mCurrentTab == TAB_HOME) {
            Fragment f = mFragmentManager == null ? null
                    : mFragmentManager.findFragmentById(R.id.home_content);
            if (f instanceof HomeFragment) {
                ((HomeFragment) f).refreshActivationOnly();
            }
        }
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
        // 2026-10-18：改用自绘弹窗，与整体卡片风格一致
        new com.tianma.xsmscode.ui.dialog.CommonDialog.Builder(
                getSupportFragmentManager(), this)
                .title(R.string.taichi_users_notice)
                .content(R.string.taichi_users_notice_content)
                .negative(R.string.add_apps_in_taichi,
                        () -> PackageUtils.startAddAppsInTaiChi(this))
                .positive(R.string.check_module_in_taichi,
                        input -> PackageUtils.startCheckModuleInTaiChi(this))
                .show();
    }

    /**
     * 关于软件（2026-09-20）：
     * 原"EdXposed用户须知"改为本软件介绍，并提供仓库入口。
     * 菜单项 id 沿用 action_edxposed_users_notice 以保持兼容。
     */
    void onEdxposedUsersNoticeSelected() {
        new com.tianma.xsmscode.ui.dialog.CommonDialog.Builder(
                getSupportFragmentManager(), this)
                .title(R.string.about_software_title)
                .content(R.string.about_software_content)
                .negative(R.string.i_know)
                .positive(R.string.about_software_repo,
                        input -> Utils.showWebPage(this, Const.PROJECT_SOURCE_CODE_URL))
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
