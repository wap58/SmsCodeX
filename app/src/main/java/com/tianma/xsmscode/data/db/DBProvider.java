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
        //
        // 2026-09-20：改为按调用方 uid 区分作用域——
        //   uid 1000 (system)         → 系统框架(android)作用域已注入
        //   uid 1001 (radio/phone)    → 电话服务(com.android.phone)作用域已注入
        // 由 app 进程代写 SharedPreferences（模块进程无写文件权限，
        // 实测 radio 写 /sdcard 与 /data/data 均 Permission denied）。
        if ("module_ping".equals(method)) {
            Context ctx = getContext();
            android.os.Bundle result = new android.os.Bundle();
            if (ctx != null) {
                int uid = android.os.Binder.getCallingUid();
                android.content.SharedPreferences.Editor ed = ctx
                        .getSharedPreferences(com.tianma.xsmscode.common.constant.PrefConst.PREF_NAME,
                                Context.MODE_PRIVATE)
                        .edit();
                // 保留旧键，兼容既有逻辑
                ed.putLong(com.tianma.xsmscode.common.constant.PrefConst.KEY_MODULE_ACTIVE_ELAPSED,
                        android.os.SystemClock.elapsedRealtime());
                String scope;
                if (uid == android.os.Process.SYSTEM_UID) {
                    scope = "system";
                    ed.putLong(com.tianma.xsmscode.common.constant.PrefConst.KEY_ACTIVE_SYSTEM_ELAPSED,
                            android.os.SystemClock.elapsedRealtime());
                } else if (uid == 1001) {
                    scope = "phone";
                    ed.putLong(com.tianma.xsmscode.common.constant.PrefConst.KEY_ACTIVE_PHONE_ELAPSED,
                            android.os.SystemClock.elapsedRealtime());
                } else {
                    scope = "other";
                }
                ed.apply();
                result.putString("scope", scope);
                android.util.Log.i("XSmsCode", "DBProvider: module_ping from uid=" + uid + " -> scope=" + scope);
            }
            result.putBoolean("ok", true);
            return result;
        }
        // 作用域报到（2026-09-20）：由被注入的进程回传，app 进程代写 SharedPreferences。
        // 模块进程无写文件权限（实测 radio/system 写 /sdcard、/data/local/tmp、
        // /data/data 全部 Permission denied），故必须借 app 进程落盘。
        if ("scope_report".equals(method)) {
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
            if (ctx == null) {
                result.putBoolean("ok", false);
                result.putString("reason", "no_context");
                return result;
            }
            long phoneWall = extras == null ? -1L : extras.getLong("phone_wall", -1L);
            long systemWall = extras == null ? -1L : extras.getLong("system_wall", -1L);
            android.content.SharedPreferences.Editor ed = ctx
                    .getSharedPreferences(com.tianma.xsmscode.common.constant.PrefConst.PREF_NAME,
                            Context.MODE_PRIVATE)
                    .edit();
            if (phoneWall > 0) {
                ed.putLong(com.tianma.xsmscode.common.constant.PrefConst.KEY_ACTIVE_PHONE_ELAPSED,
                        phoneWall);
            }
            if (systemWall > 0) {
                ed.putLong(com.tianma.xsmscode.common.constant.PrefConst.KEY_ACTIVE_SYSTEM_ELAPSED,
                        systemWall);
            }
            ed.apply();
            android.util.Log.i("XSmsCode", "DBProvider: scope_report uid=" + callingUid
                    + " phone=" + phoneWall + " system=" + systemWall);
            result.putBoolean("ok", true);
            return result;
        }
        // 电话进程读取配置（2026-09-19 方案A）：
        // 应用进程读自己的 SharedPreferences 必然成功（不受 SELinux MCS 影响），
        // 打包回传给模块。模块侧异步调用 + 超时，避免 app 冻结时卡住短信处理。
        // 仅接受 system(1000)/radio(1001)/本应用 uid 的调用。
        if ("get_prefs".equals(method)) {
            int callingUid = android.os.Binder.getCallingUid();
            boolean allowed = callingUid == android.os.Process.SYSTEM_UID
                    || callingUid == 1001
                    || callingUid == android.os.Process.myUid();
            android.os.Bundle result = new android.os.Bundle();
            if (!allowed) {
                result.putBoolean("ok", false);
                return result;
            }
            Context ctx = getContext();
            if (ctx == null) {
                result.putBoolean("ok", false);
                return result;
            }
            android.content.SharedPreferences sp = ctx.getSharedPreferences(
                    com.tianma.xsmscode.common.constant.PrefConst.PREF_NAME, Context.MODE_PRIVATE);
            android.os.Bundle prefs = new android.os.Bundle();
            for (java.util.Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                Object v = e.getValue();
                String k = e.getKey();
                if (v instanceof Boolean) {
                    prefs.putBoolean(k, (Boolean) v);
                } else if (v instanceof Integer) {
                    prefs.putInt(k, (Integer) v);
                } else if (v instanceof Long) {
                    prefs.putLong(k, (Long) v);
                } else if (v instanceof Float) {
                    prefs.putFloat(k, (Float) v);
                } else if (v instanceof String) {
                    prefs.putString(k, (String) v);
                }
            }
            result.putBoolean("ok", true);
            result.putBundle("prefs", prefs);
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
            String sender = extras == null ? "" : extras.getString("sender", "");
            String body = extras == null ? "" : extras.getString("body", "");
            String code = extras == null ? "" : extras.getString("code", "");
            long time = extras == null ? System.currentTimeMillis()
                    : extras.getLong("time", System.currentTimeMillis());
            if (!enabled) {
                result.putBoolean("ok", false);
                result.putString("reason", "disabled");
                return result;
            }
            // 2026-10-17：改为多通道分发（逐通道串行、单通道失败不影响其他）
            //
            // ★ 2026-10-18：放到独立线程执行并限时等待。
            //   call() 跑在 binder 线程上，各通道超时加起来（QQ 机器人 15s×2 重试）
            //   可能阻塞 30 秒以上，会拖住调用方（电话进程）的转发链路。
            //   这里给它 25 秒上限，超时按失败返回，不把 binder 线程长期占住。
            final android.content.SharedPreferences spRef = sp;
            final java.util.List<com.tianma.xsmscode.feature.forward.ForwardResultStore.ChannelResult>[] box =
                    new java.util.List[1];
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            new Thread(() -> {
                try {
                    box[0] = com.tianma.xsmscode.feature.forward.ForwardDispatcher.send(
                            new com.tianma.xsmscode.feature.forward.ForwardDispatcher.Cfg() {
                                @Override
                                public String getStr(String key, String def) {
                                    return spRef.getString(key, def);
                                }

                                @Override
                                public boolean getBool(String key, boolean def) {
                                    return spRef.getBoolean(key, def);
                                }

                                @Override
                                public long getLong(String key, long def) {
                                    return spRef.getLong(key, def);
                                }
                            },
                            com.tianma.xsmscode.feature.forward.WeComForwarder.buildContent(
                                    sender, body, code, time),
                            sender, body, code, time);
                } catch (Throwable t) {
                    com.tianma.xsmscode.common.utils.XLog.e("DBProvider forward: %s", t);
                } finally {
                    latch.countDown();
                }
            }, "smscodf-forward").start();

            try {
                if (!latch.await(25, java.util.concurrent.TimeUnit.SECONDS)) {
                    result.putBoolean("ok", false);
                    result.putString("reason", "send_failed");
                    com.tianma.xsmscode.common.utils.XLog.w(
                            "DBProvider forward: timeout after 25s");
                    return result;
                }
            } catch (InterruptedException e) {
                result.putBoolean("ok", false);
                result.putString("reason", "send_failed");
                return result;
            }

            java.util.List<com.tianma.xsmscode.feature.forward.ForwardResultStore.ChannelResult> results =
                    box[0] == null
                            ? new java.util.ArrayList<>()
                            : box[0];

            // 写结果文件供「短信详情」展示（电话进程也会写，这里覆盖为最新一条）
            com.tianma.xsmscode.feature.forward.ForwardResultStore.write(time, results);

            if (results.isEmpty()) {
                result.putBoolean("ok", false);
                result.putString("reason", "channel_unconfigured");
                return result;
            }
            boolean anyOk = false;
            for (com.tianma.xsmscode.feature.forward.ForwardResultStore.ChannelResult r : results) {
                if (r.ok) {
                    anyOk = true;
                    break;
                }
            }
            result.putBoolean("ok", anyOk);
            result.putString("reason", anyOk ? "sent" : "send_failed");
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
