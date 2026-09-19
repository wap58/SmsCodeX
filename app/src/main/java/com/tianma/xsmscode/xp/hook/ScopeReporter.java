package com.tianma.xsmscode.xp.hook;

import com.tianma.xsmscode.common.constant.PrefConst;
import com.tianma.xsmscode.common.utils.XLog;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 作用域报到（2026-09-20）。
 *
 * <p>目的：UI 左上角显示"已激活 / 未激活"。判据是<b>模块是否真的被注入到
 * 两个必需的作用域</b>——系统框架(android) 与 电话服务(com.android.phone)。
 *
 * <p>为什么不用 LSPosed 的勾选状态：
 * 勾选配置在 {@code /data/adb/lspd/config/modules_config.db}，而 {@code /data/adb}
 * 权限为 0700 root:root，普通应用进程无法读取。
 *
 * <p>为什么不用 SharedPreferences 或 ContentResolver：
 * system_server 进程（android 作用域）<b>没有可用的 Context</b>，
 * 反射 ActivityThread.systemMain() 取 Context 会在 system_server 里 NPE 崩溃，
 * 导致 LSPosed 进入安全模式（项目已踩过此坑，见禁止事项 1）。
 * 因此改为<b>直接写文件</b>到应用可读目录。
 *
 * <p>文件位置与应用侧 {@code ModulePrefs.CACHE_ROOTS} 保持一致，
 * 该目录由模块自身创建，无 SELinux MCS 分类问题。
 */
public final class ScopeReporter {

    private static final String TAG = "ScopeReporter";

    /** 与应用侧 ModulePrefs.CACHE_ROOTS 一致 */
    private static final String[] DIRS = {
            "/sdcard/Android/data/com.smscodf.zhuxf/files/",
            "/storage/emulated/0/Android/data/com.smscodf.zhuxf/files/",
            "/storage/self/primary/Android/data/com.smscodf.zhuxf/files/",
    };

    private ScopeReporter() {
    }

    /** 系统框架作用域报到 */
    public static void reportSystem() {
        report(PrefConst.ACTIVE_FILE_SYSTEM, "system");
    }

    /** 电话服务作用域报到 */
    public static void reportPhone() {
        report(PrefConst.ACTIVE_FILE_PHONE, "phone");
    }

    /**
     * 写报到文件。内容为写入时刻的<b>墙钟时间</b>（System.currentTimeMillis），
     * 应用侧据此判断报到是否发生在本次开机内（见 isScopeReported）。
     * 不用 elapsedRealtime：它开机归零，跨重启无法直接比较。
     */
    private static void report(String fileName, String label) {
        final long now = System.currentTimeMillis();
        final String content = String.valueOf(now);
        // 异步执行：报到绝不能拖慢被 hook 进程的启动
        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (String root : DIRS) {
                        try {
                            File dir = new File(root);
                            if (!dir.isDirectory() && !dir.mkdirs()) {
                                continue;
                            }
                            File out = new File(dir, fileName);
                            try (FileOutputStream fos = new FileOutputStream(out)) {
                                fos.write(content.getBytes(StandardCharsets.UTF_8));
                                fos.flush();
                            }
                            out.setReadable(true, false);
                            XLog.i("%s: reported %s scope (wall=%d)", TAG, label, now);
                            return;
                        } catch (Throwable ignored) {
                            // 尝试下一个候选目录
                        }
                    }
                    XLog.w("%s: report %s scope failed (no writable dir)", TAG, label);
                }
            }, "smscodf-scope-" + label).start();
        } catch (Throwable t) {
            XLog.e("%s: spawn reporter thread failed: %s", TAG, t);
        }
    }

    /** 读取某作用域的报到时间戳；未报到返回 -1 */
    public static long readReportedElapsed(String fileName) {
        for (String root : DIRS) {
            try {
                File f = new File(root, fileName);
                if (!f.isFile()) {
                    continue;
                }
                byte[] buf = new byte[32];
                try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                    int n = fis.read(buf);
                    if (n <= 0) {
                        continue;
                    }
                    String s = new String(buf, 0, n, StandardCharsets.UTF_8).trim();
                    return Long.parseLong(s);
                }
            } catch (Throwable ignored) {
            }
        }
        return -1L;
    }

    /**
     * 应用侧判断：某作用域是否在<b>本次开机内</b>报到过。
     *
     * <p>报到文件存的是<b>写入时刻的墙钟时间</b>（System.currentTimeMillis）。
     * 应用侧用「文件时间 vs 本次开机时刻」比较：
     * 开机时刻 = 当前墙钟 - elapsedRealtime。文件时间早于开机时刻，
     * 说明是上一次开机留下的，视为未报到——重启后能正确显示"未激活"。
     *
     * <p>不用 elapsedRealtime 存储：它开机归零，跨重启无法直接比较。
     */
    public static boolean isScopeReported(String fileName) {
        long reported = readReportedElapsed(fileName);
        if (reported <= 0) {
            return false;
        }
        long bootWallClock = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
        // 允许 3 秒误差（写入与开机时刻的时序差）
        return reported >= bootWallClock - 3000L;
    }
}
