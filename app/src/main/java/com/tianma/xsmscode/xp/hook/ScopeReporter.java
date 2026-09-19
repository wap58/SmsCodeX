package com.tianma.xsmscode.xp.hook;

import android.content.Context;
import android.net.Uri;

import com.smscodf.zhuxf.BuildConfig;
import com.tianma.xsmscode.common.utils.XLog;

import io.github.libxposed.api.XposedInterface;

/**
 * 作用域报到（2026-09-20）。
 *
 * <p>目的：UI 显示模块是否已注入两个必需作用域——系统框架(android) 与
 * 电话服务(com.android.phone)。
 *
 * <h3>两条通道并存</h3>
 * 两个进程的能力不同，各用可行的那条：
 * <ul>
 *   <li><b>电话进程</b>：有 Context，可调 ContentResolver →
 *       走 Provider 报到（DBProvider 内由 app 进程代写 SharedPreferences）。
 *       实测 radio(1001) 写 /sdcard、/data/local/tmp、/data/data 全部
 *       Permission denied，写文件不可行。</li>
 *   <li><b>system_server</b>：<b>无 Context</b>（反射 ActivityThread.systemMain()
 *       会 NPE 崩溃并触发 LSPosed 安全模式，项目禁止事项 1），
 *       但有 libxposed 的 {@link XposedInterface} →
 *       走 {@code getRemotePreferences()}（LSPosed 官方文档推荐，
 *       数据存 LSPosed 数据库，不受 SELinux 限制）。</li>
 * </ul>
 *
 * <p>UI 侧最终读的是 app 自己的 SharedPreferences：两条通道都通过
 * DBProvider 落到同一处（system 侧的 remote 值由电话进程在下次报到时一并回传）。
 */
public final class ScopeReporter {

    private static final String TAG = "ScopeReporter";

    /** libxposed 官方跨进程存储名 */
    public static final String REMOTE_NAME = "smscodex_scope";

    private ScopeReporter() {
    }

    /**
     * 电话进程启动时补报一次（2026-09-20）。
     *
     * <p>时序问题：电话进程可能早于 system_server 完成报到，
     * 此时回传的 system 值为 -1，导致 UI 显示"系统框架未激活"。
     * 故在电话进程内延迟重试若干次，直到拿到 system 值或超时。
     */
    public static void reportPhoneWithRetry(final Context context) {
        if (context == null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 6; i++) {
                    try {
                        io.github.libxposed.api.XposedInterface xi =
                                de.robv.android.xposed.XposedBridge.getXposedInterface();
                        long systemWall = readSystemReport(xi);
                        reportPhone(context, xi);
                        if (systemWall > 0) {
                            XLog.i("%s: retry %d got systemWall=%d, done", TAG, i + 1, systemWall);
                            return;
                        }
                    } catch (Throwable t) {
                        XLog.e("%s: retry %d failed: %s", TAG, i + 1, t);
                    }
                    try {
                        Thread.sleep(3000L);
                    } catch (InterruptedException ignored) {
                        return;
                    }
                }
                XLog.w("%s: retries exhausted, system scope value still missing", TAG);
            }
        }, "smscodf-scope-retry").start();
    }

    /**
     * 报到电话服务作用域（有 Context，走 Provider）。
     *
     * @param xi 可传 null；若传入框架接口，会顺带把 system 侧的报到时间一并回传，
     *           使 app 侧一次拿到两项状态。
     */
    public static void reportPhone(Context context, XposedInterface xi) {
        if (context == null) {
            XLog.w("%s: phone report skipped (context null)", TAG);
            return;
        }
        try {
            long systemWall = readSystemReport(xi);
            android.os.Bundle in = new android.os.Bundle();
            in.putString("scope", "phone");
            in.putLong("phone_wall", System.currentTimeMillis());
            in.putLong("system_wall", systemWall);
            android.os.Bundle r = context.getContentResolver().call(
                    Uri.parse("content://" + com.tianma.xsmscode.data.db.DBProvider.AUTHORITY),
                    "scope_report", null, in);
            boolean ok = r != null && r.getBoolean("ok", false);
            XLog.i("%s: phone report %s (systemWall=%d)", TAG, ok ? "ok" : "failed", systemWall);
        } catch (Throwable t) {
            XLog.e("%s: phone report failed: %s", TAG, t);
        }
    }

    /**
     * 报到系统框架作用域。
     *
     * <p>system_server 无 Context，故写入 libxposed remote prefs；
     * 下次电话进程报到时会把它一并带回 app 侧（见 reportPhone 的实现）。
     */
    public static void reportSystem(XposedInterface xi) {
        if (xi == null) {
            XLog.w("%s: system report skipped (framework interface null)", TAG);
            return;
        }
        try {
            android.content.SharedPreferences sp = xi.getRemotePreferences(REMOTE_NAME);
            if (sp == null) {
                XLog.w("%s: system report failed (remote prefs null)", TAG);
                return;
            }
            sp.edit()
                    .putLong("system_wall", System.currentTimeMillis())
                    .putString("system_ver", BuildConfig.VERSION_NAME)
                    .commit();
            XLog.i("%s: system report ok", TAG);
        } catch (Throwable t) {
            XLog.e("%s: system report failed: %s", TAG, t);
        }
    }

    /** 读取 system 侧报到时间（供电话进程回传时使用） */
    public static long readSystemReport(XposedInterface xi) {
        if (xi == null) {
            return -1L;
        }
        try {
            android.content.SharedPreferences sp = xi.getRemotePreferences(REMOTE_NAME);
            return sp == null ? -1L : sp.getLong("system_wall", -1L);
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    /**
     * 模块加载时报到（2026-09-20）。
     *
     * <p>用官方 {@code ModuleLoadedParam} 精确判定当前进程：
     * <ul>
     *   <li>system_server → 写 libxposed remote prefs（无 Context 可用）</li>
     *   <li>com.android.phone → 待其 Context 就绪后由 SmsHandlerHook 走 Provider 报到</li>
     * </ul>
     */
    public static void reportOnLoad(XposedInterface xi, String processName, boolean isSystemServer) {
        if (isSystemServer || "android".equals(processName)) {
            reportSystem(xi);
            return;
        }
        if ("com.android.phone".equals(processName)) {
            // 此处尚无 Context；实际报到由 SmsHandlerHook 在拿到电话进程 Context 后触发
            XLog.i("%s: phone process loaded, report deferred until context ready", TAG);
            return;
        }
        // 其他进程（模块自身、com.oplus.subsys 等）不参与激活态判定
    }

    /**
     * 判断报到时间是否落在<b>本次开机内</b>。
     * 报到值用墙钟时间；重启后旧值早于开机时刻，自然失效。
     */
    public static boolean isWithinCurrentBoot(long reportedWallClock) {
        if (reportedWallClock <= 0) {
            return false;
        }
        long bootWallClock = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
        return reportedWallClock >= bootWallClock - 3000L;
    }
}
