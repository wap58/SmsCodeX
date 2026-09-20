package com.tianma.xsmscode.xp.hook;

import android.app.Application;
import android.util.Log;

import com.smscodf.zhuxf.BuildConfig;
import com.tianma.xsmscode.common.utils.XLog;

import java.io.File;

import io.github.libxposed.api.XposedInterface;

/**
 * 激活状态自检（2026-09-20，参照社区标准做法重写）。
 *
 * <h3>机制</h3>
 * LSPosed 现代 API 下"模块自身不再被注入"（官方文档原文：
 * <i>As a result, module apps are no longer hooked by themselves.</i>），
 * 但若在 scope.list 中声明了模块自身包名，模块仍会被注入自身进程。
 * 此时在自身进程内 hook Android 公开 API
 * {@code Instrumentation.callApplicationOnCreate}，于 Application
 * 创建完成后<b>往自己的 filesDir 写标记文件</b>。
 *
 * <p>UI 侧读该文件即可判定"模块已被注入自身进程"，即激活。
 *
 * <h3>为什么这样最可靠</h3>
 * <ul>
 *   <li>写的是 app 进程自己的 filesDir —— 不存在跨进程/SELinux 权限问题
 *       （此前尝试的 /sdcard、/data/local/tmp、其他进程目录全部被拒）</li>
 *   <li>不依赖日志、不依赖 LSPosed 配置库（/data/adb 为 0700 读不到）</li>
 *   <li>hook 公开 API，不依赖被混淆的本应用类名</li>
 *   <li>写入内容含框架名/版本/API，便于排查</li>
 * </ul>
 *
 * <p>做法参照 zjz-Beiming/DoNotTryAccessibility-Reborn（同为 libxposed
 * Modern API 102 的 LSPosed 模块）。
 */
public final class ActivationMarker {

    private static final String TAG = "ActivationMarker";

    /** 标记文件名（app 自身 filesDir 下） */
    public static final String MARKER_FILE = "module_activated.marker";

    private ActivationMarker() {
    }

    /**
     * 在模块自身进程中安装自检：hook Instrumentation.callApplicationOnCreate，
     * Application 创建完成后写标记文件。
     *
     * @param xi 框架接口（XposedModule 实例自身）
     */
    public static void install(XposedInterface xi) {
        if (xi == null) {
            XLog.w("%s: install skipped (framework interface null)", TAG);
            return;
        }
        try {
            java.lang.reflect.Method onCreate = Class.forName("android.app.Instrumentation")
                    .getMethod("callApplicationOnCreate", Application.class);

            xi.hook(onCreate).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Object arg0 = chain.getArg(0);
                    if (arg0 instanceof Application) {
                        Application app = (Application) arg0;
                        if (BuildConfig.APPLICATION_ID.equals(app.getPackageName())) {
                            writeMarker(app);
                        }
                    }
                } catch (Throwable t) {
                    XLog.w("%s: write marker failed: %s", TAG, t);
                }
                return result;
            });
            XLog.i("%s: activation check installed", TAG);
        } catch (Throwable t) {
            XLog.e("%s: install activation check failed: %s", TAG, t);
        }
    }

    private static void writeMarker(Application app) {
        String info = System.currentTimeMillis()
                + "|" + BuildConfig.VERSION_NAME
                + "|" + BuildConfig.VERSION_CODE;
        File f = new File(app.getFilesDir(), MARKER_FILE);
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
            fos.write(info.getBytes("UTF-8"));
            fos.flush();
        } catch (Throwable t) {
            XLog.e("%s: write marker failed: %s", TAG, t);
            return;
        }
        XLog.i("%s: marker written: %s", TAG, info);
    }

    /**
     * UI 侧读取：返回标记内容，未激活返回 null。
     *
     * <p>标记含写入时刻的时间戳（墙钟），UI 可据此判断是否为本次开机所写。
     */
    public static String readMarker(android.content.Context context) {
        if (context == null) {
            return null;
        }
        try {
            File f = new File(context.getFilesDir(), MARKER_FILE);
            if (!f.isFile()) {
                return null;
            }
            byte[] buf = new byte[(int) Math.min(f.length(), 256)];
            try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                int n = fis.read(buf);
                return n <= 0 ? null : new String(buf, 0, n, "UTF-8");
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /** 标记是否在本次开机内写入 */
    public static boolean isActivatedThisBoot(android.content.Context context) {
        String marker = readMarker(context);
        if (marker == null || marker.isEmpty()) {
            return false;
        }
        try {
            long ts = Long.parseLong(marker.split("\\|")[0]);
            long bootWall = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
            return ts >= bootWall - 3000L;
        } catch (Throwable t) {
            return false;
        }
    }
}
