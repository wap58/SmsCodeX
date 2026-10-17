package com.tianma.xsmscode.ui.home;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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

    /** 选中/切换 tab（记录 ↔ 设置） */
    private void selectTab(boolean records) {
        mRecordsSelected = records;
        if (records) {
            switchTab(CodeRecordFragment.newInstance(), getString(R.string.app_name));
        } else {
            switchTab(SettingsFragment.newInstance(), getString(R.string.app_name));
        }
        applyNavTheme();
    }

    /**
     * 底栏配色（2026-10-17）：对齐传信 —— 悬浮胶囊玻璃 + 选中项圆角高亮。
     *
     * <p>颜色全部现算，不用 {@code ?attr/}：本项目 Cyanea 主题下主题属性取色会回退失败
     * （见 ChannelDialogTheme 的实测记录）。深色判定用背景亮度，而非 Cyanea.isDark()，
     * 因为用户可能选"浅色基底 + 深色背景"。
     */
    private void applyNavTheme() {
        if (mBottomBar == null) {
            return;
        }
        boolean dark = isDarkBackground();
        int accent = ThemeColors.accent();

        // 导航条：半透明底 + 细描边 + 27dp 圆角
        GradientDrawable bar = new GradientDrawable();
        bar.setShape(GradientDrawable.RECTANGLE);
        bar.setCornerRadius(dp(27));
        bar.setColor(dark ? 0xF2272A31 : 0xF2FFFFFF);
        bar.setStroke(Math.max(1, (int) dp(0.8f)), dark ? 0x1AFFFFFF : 0x14000000);
        mBottomBar.setBackground(bar);

        // 选中项：accent 20% 透明度的胶囊
        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        pill.setCornerRadius(dp(26));
        pill.setColor((accent & 0x00FFFFFF) | 0x1A000000);

        mNavRecords.setBackground(mRecordsSelected ? pill : null);
        mNavSettings.setBackground(mRecordsSelected ? null : pill);

        tint(mNavRecordsIcon, mRecordsSelected, accent, dark);
        tint(mNavSettingsIcon, !mRecordsSelected, accent, dark);
        mNavRecordsText.setTextColor(mRecordsSelected ? accent : (dark ? 0xFF8D97A6 : 0xFF8D97A6));
        mNavSettingsText.setTextColor(mRecordsSelected ? 0xFF8D97A6 : accent);
    }

    private void tint(ImageView iv, boolean selected, int accent, boolean dark) {
        if (iv == null) {
            return;
        }
        Drawable d = iv.getDrawable();
        if (d == null) {
            return;
        }
        d = DrawableCompat.wrap(d.mutate());
        DrawableCompat.setTint(d, selected ? accent : 0xFF8D97A6);
        iv.setImageDrawable(d);
    }

    private boolean isDarkBackground() {
        try {
            int bg = com.jaredrummler.cyanea.Cyanea.getInstance().getBackgroundColor();
            float lum = (0.299f * Color.red(bg) + 0.587f * Color.green(bg)
                    + 0.114f * Color.blue(bg)) / 255f;
            return lum < 0.5f;
        } catch (Throwable ignored) {
            int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return mode == Configuration.UI_MODE_NIGHT_YES;
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
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
