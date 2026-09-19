package com.tianma.xsmscode.ui.home;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.smscodf.zhuxf.R;
import com.tianma.xsmscode.common.constant.Const;
import com.tianma.xsmscode.common.constant.PrefConst;
import com.tianma.xsmscode.common.utils.PackageUtils;
import com.tianma.xsmscode.common.utils.Utils;
import com.tianma.xsmscode.xp.hook.ScopeReporter;
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

    @BindView(R.id.bottom_nav)
    BottomNavigationView mBottomNav;

    private static final String TAG_FAQ = "tag_faq";

    private Fragment mCurrentFragment;
    private FragmentManager mFragmentManager;

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
        mBottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.bottom_nav_records) {
                switchTab(CodeRecordFragment.newInstance(), getString(R.string.app_name));
                return true;
            } else if (itemId == R.id.bottom_nav_settings) {
                switchTab(SettingsFragment.newInstance(), getString(R.string.app_name));
                return true;
            }
            return false;
        });
    }

    private void handleIntent(Intent intent) {
        mFragmentManager = getSupportFragmentManager();
        // 默认进入记录 tab
        mBottomNav.setSelectedItemId(R.id.bottom_nav_records);
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
     * <p>判据：模块是否被注入到两个必需作用域——系统框架(android) 与
     * 电话服务(com.android.phone)。由被注入的进程通过 DBProvider 回传，
     * 应用进程代写 SharedPreferences（模块进程无写文件权限，
     * 且 app 侧拿不到 XposedInterface 读 remote prefs）。
     *
     * <p>不用 LSPosed 勾选状态：/data/adb 为 0700 root:root，应用进程读不到；
     * 且"勾选"不等于"注入成功"。
     */
    private void refreshActivationStatus() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        android.content.SharedPreferences sp = getSharedPreferences(
                PrefConst.PREF_NAME, android.content.Context.MODE_PRIVATE);
        boolean systemOk = ScopeReporter.isWithinCurrentBoot(
                sp.getLong(PrefConst.KEY_ACTIVE_SYSTEM_ELAPSED, -1L));
        boolean phoneOk = ScopeReporter.isWithinCurrentBoot(
                sp.getLong(PrefConst.KEY_ACTIVE_PHONE_ELAPSED, -1L));

        String text;
        if (systemOk && phoneOk) {
            text = getString(R.string.module_status_active);
        } else if (systemOk) {
            text = getString(R.string.module_status_phone_missing);
        } else if (phoneOk) {
            text = getString(R.string.module_status_system_missing);
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
