package com.tianma.xsmscode.feature.forward.qqbot;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.tianma.xsmscode.common.constant.PrefConst;
import com.tianma.xsmscode.common.utils.PrefsExporter;

/**
 * QQ 机器人凭据存储（2026-10-17 自「传信」移植）。
 *
 * <p>★ 存进 {@link PrefConst#PREF_NAME} 指定的 SharedPreferences，而不是自建文件：
 * 这样 {@link PrefsExporter} 会把它们一并导出成世界可读 XML，
 * 电话进程（息屏直发路径）才能读到。
 *
 * <p>每次写入后立即触发一次导出（导出失败只记日志，不影响保存）。
 */
public final class QQBotStore {

    private static final String KEY_APPID = "pref_forward_qqbot_appid";
    private static final String KEY_SECRET = "pref_forward_qqbot_secret";
    private static final String KEY_OPENID = "pref_forward_qqbot_openid";
    private static final String KEY_IS_GROUP = "pref_forward_qqbot_is_group";
    private static final String KEY_TOKEN = "pref_forward_qqbot_token";
    private static final String KEY_TOKEN_EXPIRE = "pref_forward_qqbot_token_expire";

    private QQBotStore() {
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(PrefConst.PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String appId(Context ctx) {
        return sp(ctx).getString(KEY_APPID, "");
    }

    public static String appSecret(Context ctx) {
        return sp(ctx).getString(KEY_SECRET, "");
    }

    public static String openId(Context ctx) {
        return sp(ctx).getString(KEY_OPENID, "");
    }

    public static boolean isGroup(Context ctx) {
        return sp(ctx).getBoolean(KEY_IS_GROUP, false);
    }

    /** 被绑定目标：AppID + Secret 有了、且拿到 openid 才算配置完成 */
    public static boolean isConfigured(Context ctx) {
        return !TextUtils.isEmpty(appId(ctx))
                && !TextUtils.isEmpty(appSecret(ctx))
                && !TextUtils.isEmpty(openId(ctx));
    }

    public static void save(Context ctx, String appId, String secret, String openId, boolean group) {
        sp(ctx).edit()
                .putString(KEY_APPID, appId == null ? "" : appId.trim())
                .putString(KEY_SECRET, secret == null ? "" : secret.trim())
                .putString(KEY_OPENID, openId == null ? "" : openId.trim())
                .putBoolean(KEY_IS_GROUP, group)
                .apply();
        PrefsExporter.export(ctx);
    }

    public static void clearSession(Context ctx) {
        sp(ctx).edit()
                .putString(KEY_OPENID, "")
                .putString(KEY_TOKEN, "")
                .putLong(KEY_TOKEN_EXPIRE, 0L)
                .apply();
        PrefsExporter.export(ctx);
    }

    /** 未过期的 access_token；过期或不存在返回空串 */
    public static String cachedToken(Context ctx) {
        SharedPreferences p = sp(ctx);
        String token = p.getString(KEY_TOKEN, "");
        long expireAt = p.getLong(KEY_TOKEN_EXPIRE, 0L);
        if (TextUtils.isEmpty(token) || System.currentTimeMillis() >= expireAt) {
            return "";
        }
        return token;
    }

    /** 缓存 token，提前 5 分钟视为过期 */
    public static void saveToken(Context ctx, String token, int expiresInSeconds) {
        long at = System.currentTimeMillis()
                + Math.max(60, expiresInSeconds - 300) * 1000L;
        sp(ctx).edit()
                .putString(KEY_TOKEN, token == null ? "" : token)
                .putLong(KEY_TOKEN_EXPIRE, at)
                .apply();
        PrefsExporter.export(ctx);
    }

    /** 通道列表摘要 */
    public static String summary(Context ctx) {
        if (!isConfigured(ctx)) {
            return "未配置（需 AppID + AppSecret，并接收一次机器人消息）";
        }
        String oid = openId(ctx);
        String tail = oid.length() > 12 ? "…" + oid.substring(oid.length() - 8) : oid;
        return (isGroup(ctx) ? "群聊 " : "私聊 ") + tail;
    }
}
