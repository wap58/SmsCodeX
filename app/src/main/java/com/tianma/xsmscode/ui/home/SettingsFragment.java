package com.tianma.xsmscode.ui.home;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.afollestad.materialdialogs.MaterialDialog;
import com.smscodf.zhuxf.BuildConfig;
import com.smscodf.zhuxf.R;
import com.jaredrummler.cyanea.prefs.CyaneaSettingsActivity;
import com.tianma.xsmscode.common.constant.PrefConst;
import com.tianma.xsmscode.common.preference.ResetEditPreference;
import com.tianma.xsmscode.common.preference.ResetEditPreferenceDialogFragCompat;
import com.tianma.xsmscode.common.utils.ModuleUtils;
import com.tianma.xsmscode.common.utils.PackageUtils;
import com.tianma.xsmscode.common.utils.SPUtils;
import com.tianma.xsmscode.common.utils.SnackbarHelper;
import com.tianma.xsmscode.common.utils.XLog;
import com.tianma.xsmscode.data.db.entity.ApkVersion;
import com.tianma.xsmscode.ui.app.base.BasePreferenceFragment;
import com.tianma.xsmscode.ui.block.AppBlockActivity;
import com.tianma.xsmscode.ui.record.CodeRecordActivity;
import com.tianma.xsmscode.ui.rule.CodeRulesActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasAndroidInjector;
import dagger.android.support.AndroidSupportInjection;

/**
 * 首选项Fragment
 */
public class SettingsFragment extends BasePreferenceFragment implements
        Preference.OnPreferenceClickListener,
        Preference.OnPreferenceChangeListener,
        HasAndroidInjector,
        SettingsContract.View {

    static final String EXTRA_ACTION = "extra_action";
    static final String ACTION_DONATE_BY_ALIPAY = "donate_by_alipay";

    private HomeActivity mActivity;

    @Inject
    DispatchingAndroidInjector<Object> androidInjector;

    @Inject
    SettingsContract.Presenter mPresenter;

    public SettingsFragment() {
    }

    public static SettingsFragment newInstance() {
        return newInstance(null);
    }

    public static SettingsFragment newInstance(String extraAction) {
        SettingsFragment fragment = new SettingsFragment();
        Bundle args = new Bundle();
        args.putString(EXTRA_ACTION, extraAction);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public AndroidInjector<Object> androidInjector() {
        return androidInjector;
    }

    @Override
    public void onAttach(Context context) {
        AndroidSupportInjection.inject(this);
        super.onAttach(context);
    }

    @NonNull
    @Override
    public <T extends Preference> T findPreference(@NonNull CharSequence key) {
        return Objects.requireNonNull(super.findPreference(key));
    }

    @Override
    protected void doOnCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings);

        // general group（激活态提示已按用户决定移除）

        findPreference(PrefConst.KEY_HIDE_LAUNCHER_ICON).setOnPreferenceChangeListener(this);
        findPreference(PrefConst.KEY_CHOOSE_THEME).setOnPreferenceClickListener(this);
        // general group end

        // SMS code group
        findPreference(PrefConst.KEY_APP_BLOCK_ENTRY).setOnPreferenceClickListener(this);
        // SMS code group end

        // experimental group
        // experimental group end

        // code rule group
        findPreference(PrefConst.KEY_CODE_RULES).setOnPreferenceClickListener(this);
        findPreference(PrefConst.KEY_SMSCODE_TEST).setOnPreferenceClickListener(this);
        // code rule group end

        // code records group：已迁移至首页"记录" tab，设置页不再展示（2026-09-15 UI 改版）
        // 记录功能开关沿用原 pref_enable_code_records 配置值（默认开启）


        // about group（可折叠）
        // version info preference
        Preference versionPref = findPreference(PrefConst.KEY_VERSION);
        versionPref.setOnPreferenceClickListener(this);
        showVersionInfo(versionPref);
        findPreference(PrefConst.KEY_SOURCE_CODE).setOnPreferenceClickListener(this);
        findPreference(PrefConst.KEY_PRIVACY_POLICY).setOnPreferenceClickListener(this);

        // 全部分类可折叠：默认折叠，点击分类头切换子项显隐
        makeCollapsible(PrefConst.KEY_GENERAL_HEADER,
                PrefConst.KEY_ENABLE, PrefConst.KEY_HIDE_LAUNCHER_ICON, PrefConst.KEY_CHOOSE_THEME);
        makeCollapsible(PrefConst.KEY_SMS_CODE_HEADER,
                PrefConst.KEY_SHOW_TOAST, PrefConst.KEY_COPY_TO_CLIPBOARD,
                PrefConst.KEY_BLOCK_SMS, PrefConst.KEY_DEDUPLICATE_SMS);
        makeCollapsible(PrefConst.KEY_AUTO_INPUT_HEADER,
                PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, PrefConst.KEY_APP_BLOCK_ENTRY);
        makeCollapsible(PrefConst.KEY_NOTIFICATION_HEADER,
                PrefConst.KEY_SHOW_CODE_NOTIFICATION, PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
                PrefConst.KEY_NOTIFICATION_RETENTION_TIME);
        makeCollapsible(PrefConst.KEY_FORWARD_HEADER,
                PrefConst.KEY_ENABLE_FORWARD, PrefConst.KEY_FORWARD_WECOM_CORPID,
                PrefConst.KEY_FORWARD_WECOM_AGENTID, PrefConst.KEY_FORWARD_WECOM_SECRET,
                PrefConst.KEY_FORWARD_WECOM_TOUSER);
        // 验证码历史记录入口已迁移至首页"记录" tab；设置里保留记录开关（2026-09-15）
        makeCollapsible(PrefConst.KEY_CODE_RECORDS_HEADER,
                PrefConst.KEY_ENABLE_CODE_RECORDS);
        makeCollapsible(PrefConst.KEY_MATCH_RULES_HEADER,
                PrefConst.KEY_SMSCODE_KEYWORDS, PrefConst.KEY_CODE_RULES, PrefConst.KEY_SMSCODE_TEST);
        makeCollapsible(PrefConst.KEY_EXPERIMENTAL,
                PrefConst.KEY_MARK_AS_READ, PrefConst.KEY_DELETE_SMS, PrefConst.KEY_KILL_ME,
                PrefConst.KEY_VERBOSE_LOG_MODE);
        makeCollapsible(PrefConst.KEY_ABOUT,
                PrefConst.KEY_VERSION, PrefConst.KEY_SOURCE_CODE, PrefConst.KEY_PRIVACY_POLICY);
        // about group end

        // 全页图标按主题强调色着色：原生图标为纯黑填充，深色主题下不可见（2026-09-15）
        tintPreferenceIcons();
    }

    private void tintPreferenceIcons() {
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }
        int accent = resolveThemeAccent();
        tintIconsRecursive(screen, accent);
    }

    private void tintIconsRecursive(androidx.preference.PreferenceGroup group, int accent) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference preference = group.getPreference(i);
            android.graphics.drawable.Drawable icon = preference.getIcon();
            if (icon != null) {
                android.graphics.drawable.Drawable mutated = icon.mutate();
                androidx.core.graphics.drawable.DrawableCompat.setTint(mutated, accent);
                preference.setIcon(mutated);
            }
            if (preference instanceof androidx.preference.PreferenceGroup) {
                tintIconsRecursive((androidx.preference.PreferenceGroup) preference, accent);
            }
        }
    }

    private int resolveThemeAccent() {
        // 直接读取 Cyanea 配置的强调色（主题切换/深色模式下均稳定）；
        // 主题属性解析在部分 Cyanea 主题下会回退失败导致图标隐身（2026-09-15 实测）
        try {
            return com.jaredrummler.cyanea.Cyanea.getInstance().getAccent();
        } catch (Throwable ignored) {
        }
        android.util.TypedValue value = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(androidx.appcompat.R.attr.colorAccent, value, true);
        return value.data;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mActivity = (HomeActivity) requireActivity();

        mPresenter.handleArguments(getArguments());
    }

    @Override
    public void onPause() {
        super.onPause();
        String preferencesName = getPreferenceManager().getSharedPreferencesName();
        mPresenter.setPreferenceWorldWritable(preferencesName);
        mPresenter.setInternalFilesWritable();
    }

    @Override
    public void showAppAlreadyNewest() {
        SnackbarHelper.makeLong(getListView(), R.string.app_already_newest).show();
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if (PrefConst.KEY_CHOOSE_THEME.equals(key)) {
            Intent intent = new Intent(mActivity, CyaneaSettingsActivity.class);
            startActivity(intent);
        } else if (PrefConst.KEY_CODE_RULES.equals(key)) {
            CodeRulesActivity.startToMe(mActivity);
        } else if (PrefConst.KEY_SMSCODE_TEST.equals(key)) {
            showSmsCodeTestDialog();
        } else if (PrefConst.KEY_SOURCE_CODE.equals(key)) {
            mPresenter.showSourceProject();
        } else if (PrefConst.KEY_ENTRY_CODE_RECORDS.equals(key)) {
            CodeRecordActivity.startToMe(mActivity);
        } else if (PrefConst.KEY_APP_BLOCK_ENTRY.equals(key)) {
            AppBlockActivity.startMe(mActivity);
        } else if (PrefConst.KEY_VERSION.equals(key)) {
            mPresenter.checkUpdate();
        } else if(PrefConst.KEY_PRIVACY_POLICY.equals(key)) {
            showPrivacyPolicy();
        } else {
            return false;
        }
        return true;
    }

    /**
     * 将分类改为可折叠组：头部默认折叠（子项隐藏），点击头部切换子项显隐
     */
    private void makeCollapsible(String headerKey, String... childKeys) {
        final Preference header = findPreference(headerKey);
        final List<Preference> children = new ArrayList<>(childKeys.length);
        for (String childKey : childKeys) {
            children.add(findPreference(childKey));
        }
        for (Preference child : children) {
            child.setVisible(false);
        }
        header.setOnPreferenceClickListener(preference -> {
            boolean visible = !children.get(0).isVisible();
            for (Preference child : children) {
                child.setVisible(visible);
            }
            return true;
        });
    }

    private void showVersionInfo(Preference preference) {
        String summary = getString(R.string.pref_version_summary, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
        preference.setSummary(summary);
    }


    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (PrefConst.KEY_HIDE_LAUNCHER_ICON.equals(key)) {
            mPresenter.hideOrShowLauncherIcon((Boolean) newValue);
        } else {
            return false;
        }
        return true;
    }

    private void showSmsCodeTestDialog() {
        new MaterialDialog.Builder(mActivity)
                .title(R.string.pref_smscode_test_title)
                .input(R.string.sms_content_hint, 0, true,
                        (dialog, input) -> mPresenter.performSmsCodeTest(input.toString()))
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE)
                .negativeText(R.string.cancel)
                .show();
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        boolean handled = false;
        if (preference instanceof ResetEditPreference) {
            DialogFragment dialogFragment =
                    ResetEditPreferenceDialogFragCompat.newInstance(preference.getKey());

            FragmentManager fm = getFragmentManager();
            if (fm != null) {
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(fm, "android.support.v7.preference.PreferenceFragment.DIALOG");
                handled = true;
            }
        }
        if (!handled) {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    private void initRecordEntryPreference(Preference preference) {
        String summary = getString(R.string.pref_entry_code_records_summary, PrefConst.MAX_SMS_RECORDS_COUNT_DEFAULT);
        preference.setSummary(summary);
    }

    @Override
    public void showSmsCodeTestResult(String code) {
        String text = TextUtils.isEmpty(code) ? getString(R.string.cannot_parse_smscode)
                : getString(R.string.current_sms_code, code);
        SnackbarHelper.makeLong(getListView(), text).show();
    }

    @Override
    public void showCheckError(Throwable t) {
        SnackbarHelper.makeShort(getListView(), R.string.check_update_failed).show();
    }

    @Override
    public void showUpdateDialog(ApkVersion latestVersion) {
        new MaterialDialog.Builder(mActivity)
                .title(R.string.new_version_found)
                .content(latestVersion.getVersionInfo())
                .positiveText(R.string.update_from_coolapk)
                .onPositive((dialog, which) -> mPresenter.updateFromCoolApk())
                .negativeText(R.string.update_from_github)
                .onNegative((dialog, which) -> mPresenter.updateFromGithub())
                .show();
    }

    @Override
    public void showPrivacyPolicy() {
        // 隐私政策
        new MaterialDialog.Builder(mActivity)
                .title(R.string.privacy_dialog_title)
                .content(R.string.privacy_dialog_content)
                .positiveText(R.string.privacy_dialog_confirm)
                .onPositive((dialog, which) -> {
                    SPUtils.setPrivacyPolicyAccepted(mActivity, true);
                })
                .cancelable(false)
                .canceledOnTouchOutside(false)
                .negativeText(R.string.privacy_dialog_cancel)
                .onNegative((dialog, which) -> {
                    SPUtils.setPrivacyPolicyAccepted(mActivity, false);
                    mActivity.finish();
                })
                .show();
    }
}
