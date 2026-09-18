package com.tianma.xsmscode.common.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.tianma.xsmscode.common.constant.PrefConst;


/**
 * Common Shared preferences utils.
 */
public class PreferencesUtils {

    private PreferencesUtils() {

    }

    @SuppressLint("WorldReadableFiles")
    private static SharedPreferences getPreferences(Context context) {
        // 2026-09-19 根治：必须用 MODE_WORLD_READABLE，LSPosed 才会 hook
        // ContextImpl.getPreferencesDir() 把配置放到世界可读目录，模块进程
        // （电话进程 radio uid）才能读到。官方文档：
        // https://github.com/LSPosed/LSPosed/wiki/New-XSharedPreferences
        // 用 MODE_PRIVATE 会导致模块永远读到默认值（拦截/复制/通知自动清除失效）。
        try {
            return context.getSharedPreferences(PrefConst.PREF_NAME, Context.MODE_WORLD_READABLE);
        } catch (SecurityException e) {
            // 模块未被激活时框架不 hook checkMode，退回私有模式（应用自身仍可用）
            return context.getSharedPreferences(PrefConst.PREF_NAME, Context.MODE_PRIVATE);
        }
    }

    public static boolean contains(Context context, String key) {
        return getPreferences(context).contains(key);
    }

    public static String getString(Context context, String key, String defValue) {
        return getPreferences(context).getString(key, defValue);
    }

    public static void putString(Context context, String key, String value) {
        getPreferences(context).edit().putString(key, value).apply();
    }

    public static boolean getBoolean(Context context, String key, boolean defValue) {
        return getPreferences(context).getBoolean(key, defValue);
    }

    public static void putBoolean(Context context, String key, boolean value) {
        getPreferences(context).edit().putBoolean(key, value).apply();
    }

    public static int getInt(Context context, String key, int defValue) {
        return getPreferences(context).getInt(key, defValue);
    }

    public static void putInt(Context context, String key, int value) {
        getPreferences(context).edit().putInt(key, value).apply();
    }

    public static float getFloat(Context context, String key, float defValue) {
        return getPreferences(context).getFloat(key, defValue);
    }

    public static void putFloat(Context context, String key, float value) {
        getPreferences(context).edit().putFloat(key, value).apply();
    }

    public static long getLong(Context context, String key, long defValue) {
        return getPreferences(context).getLong(key, defValue);
    }

    public static void putLong(Context context, String key, long value) {
        getPreferences(context).edit().putLong(key, value).apply();
    }

}
