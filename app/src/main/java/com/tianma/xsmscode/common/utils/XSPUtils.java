package com.tianma.xsmscode.common.utils;

import com.smscodf.zhuxf.BuildConfig;
import com.tianma.xsmscode.common.constant.PrefConst;

import de.robv.android.xposed.XSharedPreferences;

/**
 * 配置读取工具（2026-09-18 重写：不再依赖 XSharedPreferences 的固定路径）。
 *
 * 原实现经 XSharedPreferences 读 /data/data/<pkg>/shared_prefs/...，
 * 电话进程(radio uid)受 SELinux 隔离读不到 → 所有开关退回默认值，
 * 造成"拦截/复制/通知自动清除失效"（用户开了但默认关）。
 *
 * 现统一走 ModulePrefs（多路径探测 + 世界可读的 LSPosed 代理目录优先）。
 * 公开方法签名保持不变，调用方无需改动；preferences 参数保留仅为兼容。
 */
public class XSPUtils {

    private static final String PKG = BuildConfig.APPLICATION_ID;
    private static final String PREFS = PrefConst.PREF_NAME;

    private XSPUtils() {
    }

    private static boolean getBoolean(XSharedPreferences ignored, String key, boolean def) {
        return ModulePrefs.getBoolean(PKG, PREFS, key, def);
    }

    private static String getString(XSharedPreferences ignored, String key, String def) {
        return ModulePrefs.getString(PKG, PREFS, key, def);
    }

    /**
     * 总开关是否打开
     */
    public static boolean isEnabled(XSharedPreferences preferences) {
        return getBoolean(preferences, PrefConst.KEY_ENABLE, true);
    }

    /**
     * 日志模式是否是verbose log模式
     */
    public static boolean isVerboseLogMode(XSharedPreferences preferences) {
        return getBoolean(preferences, PrefConst.KEY_VERBOSE_LOG_MODE, false);
    }

    /**
     * 自动输入总开关是否打开
     */
    public static boolean autoInputCodeEnabled(XSharedPreferences preferences) {
        return getBoolean(preferences, PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, true);
    }

    /**
     * 自动输入延迟(单位s)
     */
    public static long getAutoInputCodeDelay(XSharedPreferences preferences) {
        String value = getString(preferences, PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
                PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT);
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return Long.parseLong(PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT);
        }
    }

    /**
     * 是否应该在复制验证码到系统剪切板之后显示Toast
     */
    public static boolean shouldShowToast(XSharedPreferences preferences) {
        return getBoolean(preferences, PrefConst.KEY_SHOW_TOAST, true);
    }

    /**
     * 获取短信验证码关键字
     */
    public static String getSMSCodeKeywords(XSharedPreferences preferences) {
        return getString(preferences, PrefConst.KEY_SMSCODE_KEYWORDS,
                PrefConst.SMSCODE_KEYWORDS_DEFAULT);
    }

    /**
     * 标记为已读是否打开
     */
    public static boolean markAsReadEnabled(XSharedPreferences preferences) {
        return getBoolean(preferences, PrefConst.KEY_MARK_AS_READ, false);
    }

    /**
     * 是否删除验证码短信
     */
    public static boolean deleteSmsEnabled(XSharedPreferences preferences) {
        return getBoolean(preferences, PrefConst.KEY_DELETE_SMS, false);
    }

    /**
     * 是否复制到剪切板
     */
    public static boolean copyToClipboardEnabled(XSharedPreferences preferences) {
        return getBoolean(preferences, PrefConst.KEY_COPY_TO_CLIPBOARD, false);
    }

    /**
     * 是否记录短信验证码
     */
    public static boolean recordSmsCodeEnabled(XSharedPreferences preferences) {
        return getBoolean(preferences, PrefConst.KEY_ENABLE_CODE_RECORDS, true);
    }

    /**
     * 是否拦截短信通知
     */
    public static boolean blockSmsEnabled(XSharedPreferences preferences) {
        return getBoolean(preferences, PrefConst.KEY_BLOCK_SMS, false);
    }

    /**
     * 验证码提取成功后是否杀掉模块进程
     */
    public static boolean killMeEnabled(XSharedPreferences preferences) {
        return getBoolean(preferences, PrefConst.KEY_KILL_ME, false);
    }

    /**
     * 是否显示验证码通知
     */
    public static boolean showCodeNotification(XSharedPreferences preferences) {
        return getBoolean(preferences, PrefConst.KEY_SHOW_CODE_NOTIFICATION, true);
    }

    /**
     * 是否自动清除验证码通知
     */
    public static boolean autoCancelCodeNotification(XSharedPreferences preferences) {
        return getBoolean(preferences, PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION, false);
    }

    /**
     * 获取验证码通知保留时间
     */
    public static int getNotificationRetentionTime(XSharedPreferences preferences) {
        String value = getString(preferences, PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
                PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT);
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 是否过滤掉重复短信
     */
    public static boolean deduplicateSms(XSharedPreferences preferences) {
        return getBoolean(preferences, PrefConst.KEY_DEDUPLICATE_SMS, false);
    }

    /**
     * 转发范围是否为"全部短信"（2026-09-19）。
     * true：任何短信都转发；false：仅转发验证码短信（默认，保持原行为）。
     */
    public static boolean forwardAllSmsEnabled(XSharedPreferences preferences) {
        return PrefConst.FORWARD_SCOPE_ALL.equals(
                getString(preferences, PrefConst.KEY_FORWARD_SCOPE, PrefConst.FORWARD_SCOPE_CODE));
    }
}
