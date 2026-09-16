package com.tianma.xsmscode.data.db;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.smscodf.zhuxf.BuildConfig;
import com.tianma.xsmscode.data.db.entity.AppInfoDao;
import com.tianma.xsmscode.data.db.entity.SmsCodeRuleDao;
import com.tianma.xsmscode.data.db.entity.SmsMsgDao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class DBProvider extends ContentProvider {

    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".db.provider";

    private static final String PATH_SMS_MSG = "sms_msg";
    private static final String PATH_SMS_CODE_RULE = "sms_code_rule";
    private static final String PATH_APP_INFO = "app_info";

    public static final Uri SMS_MSG_CONTENT_URI =
            Uri.parse("content://" + AUTHORITY + "/" + PATH_SMS_MSG);
    public static final Uri SMS_CODE_RULE_URI =
            Uri.parse("content://" + AUTHORITY + "/" + PATH_SMS_CODE_RULE);
    public static final Uri APP_INFO_URI =
            Uri.parse("content://" + AUTHORITY + "/" + PATH_APP_INFO);

    private static final int SMS_MSG_DIR = 0;
    private static final int SMS_MSG_ID = 1;
    private static final int SMS_CODE_RULE_DIR = 2;
    private static final int SMS_CODE_RULE_ID = 3;
    private static final int APP_INFO_DIR = 4;
    private static final int APP_INFO_ID = 5;


    private static final String TABLE_SMS_MSG = SmsMsgDao.TABLENAME;
    private static final String TABLE_SMS_CODE_RULE = SmsCodeRuleDao.TABLENAME;
    private static final String TABLE_APP_INFO = AppInfoDao.TABLENAME;

    private static final UriMatcher sUriMatcher;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, PATH_SMS_MSG, SMS_MSG_DIR);
        sUriMatcher.addURI(AUTHORITY, PATH_SMS_MSG + "/#", SMS_MSG_ID);

        sUriMatcher.addURI(AUTHORITY, PATH_SMS_CODE_RULE, SMS_CODE_RULE_DIR);
        sUriMatcher.addURI(AUTHORITY, PATH_SMS_CODE_RULE + "/#", SMS_CODE_RULE_ID);

        sUriMatcher.addURI(AUTHORITY, PATH_APP_INFO, APP_INFO_DIR);
        sUriMatcher.addURI(AUTHORITY, PATH_APP_INFO + "/#", APP_INFO_ID);
    }

    private SQLiteDatabase mDatabase;
    private Context mContext;

    @Override
    public boolean onCreate() {
        mContext = getContext();
        mDatabase = DBManager.get(mContext).getSQLiteDatabase();
        return true;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        int uriType = sUriMatcher.match(uri);
        long id;
        String path;
        switch (uriType) {
            case SMS_MSG_DIR:
                id = mDatabase.insert(TABLE_SMS_MSG, null, values);
                path = PATH_SMS_MSG + "/" + id;
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        if (mContext != null) {
            mContext.getContentResolver().notifyChange(uri, null);
        }
        return Uri.parse(path);
    }

    @Nullable
    @Override
    public android.os.Bundle call(@NonNull String method, @Nullable String arg, @Nullable android.os.Bundle extras) {
        // 电话/系统进程报到：模块在本次开机已加载（UI 激活态判定依据）
        if ("module_ping".equals(method)) {
            Context ctx = getContext();
            if (ctx != null) {
                ctx.getSharedPreferences(com.tianma.xsmscode.common.constant.PrefConst.PREF_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putLong(com.tianma.xsmscode.common.constant.PrefConst.KEY_MODULE_ACTIVE_ELAPSED,
                                android.os.SystemClock.elapsedRealtime())
                        .apply();
            }
            android.os.Bundle result = new android.os.Bundle();
            result.putBoolean("ok", true);
            return result;
        }
        // 电话进程的自杀指令：App 进程自我了断（无需任何权限）；
        // 仅接受 system(1000)/radio(1001)/本应用 uid 的调用，防滥用
        if ("kill_me".equals(method)) {
            int callingUid = android.os.Binder.getCallingUid();
            boolean allowed = callingUid == android.os.Process.SYSTEM_UID
                    || callingUid == 1001
                    || callingUid == android.os.Process.myUid();
            android.os.Bundle result = new android.os.Bundle();
            result.putBoolean("ok", allowed);
            if (allowed) {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                }, 300);
            }
            return result;
        }
        // 电话进程 → App 进程：验证码短信转发到企业微信（2026-09-15 新增）。
        // App 进程持有 INTERNET 权限，电话进程没有；同步处理（3s 超时），
        // 保证在 KillMeAction 自杀（延至 8s）之前完成。
        if ("forward".equals(method)) {
            int callingUid = android.os.Binder.getCallingUid();
            boolean allowed = callingUid == android.os.Process.SYSTEM_UID
                    || callingUid == 1001
                    || callingUid == android.os.Process.myUid();
            android.os.Bundle result = new android.os.Bundle();
            if (!allowed) {
                result.putBoolean("ok", false);
                result.putString("reason", "forbidden");
                return result;
            }
            Context ctx = getContext();
            android.content.SharedPreferences sp = ctx == null ? null
                    : ctx.getSharedPreferences(com.tianma.xsmscode.common.constant.PrefConst.PREF_NAME,
                            Context.MODE_PRIVATE);
            boolean enabled = sp != null && sp.getBoolean(
                    com.tianma.xsmscode.common.constant.PrefConst.KEY_ENABLE_FORWARD, false);
            String corpId = sp == null ? "" : sp.getString(
                    com.tianma.xsmscode.common.constant.PrefConst.KEY_FORWARD_WECOM_CORPID, "");
            String agentId = sp == null ? "" : sp.getString(
                    com.tianma.xsmscode.common.constant.PrefConst.KEY_FORWARD_WECOM_AGENTID, "");
            String secret = sp == null ? "" : sp.getString(
                    com.tianma.xsmscode.common.constant.PrefConst.KEY_FORWARD_WECOM_SECRET, "");
            String toUser = sp == null ? "" : sp.getString(
                    com.tianma.xsmscode.common.constant.PrefConst.KEY_FORWARD_WECOM_TOUSER, "");
            String channel = sp == null ? "wecom_agent" : sp.getString(
                    com.tianma.xsmscode.common.constant.PrefConst.KEY_FORWARD_CHANNEL_TYPE, "wecom_agent");
            String robotWebhook = sp == null ? "" : sp.getString(
                    com.tianma.xsmscode.common.constant.PrefConst.KEY_FORWARD_WECOM_ROBOT_WEBHOOK, "");
            String dingWebhook = sp == null ? "" : sp.getString(
                    com.tianma.xsmscode.common.constant.PrefConst.KEY_FORWARD_DINGTALK_WEBHOOK, "");
            String dingSecret = sp == null ? "" : sp.getString(
                    com.tianma.xsmscode.common.constant.PrefConst.KEY_FORWARD_DINGTALK_SECRET, "");
            String feishuWebhook = sp == null ? "" : sp.getString(
                    com.tianma.xsmscode.common.constant.PrefConst.KEY_FORWARD_FEISHU_WEBHOOK, "");
            String pushplusToken = sp == null ? "" : sp.getString(
                    com.tianma.xsmscode.common.constant.PrefConst.KEY_FORWARD_PUSHPLUS_TOKEN, "");
            String sender = extras == null ? "" : extras.getString("sender", "");
            String body = extras == null ? "" : extras.getString("body", "");
            String code = extras == null ? "" : extras.getString("code", "");
            long time = extras == null ? System.currentTimeMillis()
                    : extras.getLong("time", System.currentTimeMillis());
            boolean ok;
            if (!enabled) {
                result.putBoolean("ok", false);
                result.putString("reason", "disabled");
                return result;
            }
            String content = com.tianma.xsmscode.feature.forward.WeComForwarder.buildContent(
                    sender, body, code, time);
            switch (channel) {
                case "wecom_robot":
                    ok = com.tianma.xsmscode.feature.forward.ChannelSender.sendWecomRobot(robotWebhook, content);
                    break;
                case "dingtalk":
                    ok = com.tianma.xsmscode.feature.forward.ChannelSender.sendDingtalk(dingWebhook, dingSecret, content);
                    break;
                case "feishu":
                    ok = com.tianma.xsmscode.feature.forward.ChannelSender.sendFeishu(feishuWebhook, content);
                    break;
                case "pushplus":
                    ok = com.tianma.xsmscode.feature.forward.ChannelSender.sendPushplus(pushplusToken, content);
                    break;
                case "wecom_agent":
                default:
                    if (corpId.trim().isEmpty() || agentId.trim().isEmpty() || secret.trim().isEmpty()) {
                        result.putBoolean("ok", false);
                        result.putString("reason", "disabled");
                        return result;
                    }
                    ok = com.tianma.xsmscode.feature.forward.WeComForwarder.send(
                            corpId, agentId, secret, toUser, sender, body, code, time);
                    break;
            }
            result.putBoolean("ok", ok);
            result.putString("reason", ok ? "sent" : "send_failed");
            return result;
        }
        return super.call(method, arg, extras);
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        int uriType = sUriMatcher.match(uri);
        String tableName;
        switch (uriType) {
            case SMS_CODE_RULE_DIR:
                tableName = TABLE_SMS_CODE_RULE;
                break;
            case SMS_MSG_DIR:
                tableName = TABLE_SMS_MSG;
                break;
            case APP_INFO_DIR:
                tableName = TABLE_APP_INFO;
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        return mDatabase.query(tableName, projection, selection, selectionArgs, null, null, sortOrder);
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        int uriType = sUriMatcher.match(uri);
        int rowsDeleted;
        switch (uriType) {
            case SMS_MSG_DIR:
                rowsDeleted = mDatabase.delete(TABLE_SMS_MSG, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        if (rowsDeleted > 0) {
            mContext.getContentResolver().notifyChange(uri, null);
        }
        return rowsDeleted;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
