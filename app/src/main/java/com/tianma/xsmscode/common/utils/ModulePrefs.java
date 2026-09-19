package com.tianma.xsmscode.common.utils;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置读取（2026-09-18 重写，根治"模块读不到用户配置"）。
 *
 * 背景：原实现用 XSharedPreferences 读 /data/data/<pkg>/shared_prefs/<name>.xml，
 * 电话进程(radio uid)受 SELinux 隔离读不到 → 所有开关退回代码默认值，
 * 表现为"拦截/复制/通知自动清除"（用户开了、默认关）全部失效。
 *
 * 本实现按优先级取配置，任一失败即降级，绝不向宿主抛异常：
 *   1) LSPosed 代理目录 /data/misc/apexdata/<id>/prefs/<pkg>/<name>.xml（世界可读）
 *   2) 应用数据目录 /data/data/<pkg>/shared_prefs/<name>.xml（部分 ROM 可读）
 *   3) 框架官方远程偏好 getRemotePreferences()（Binder 直连 lspd，无需文件权限）
 *
 * 注意：不做任何 Context 反射、不调用 ContentProvider——
 * system_server 等敏感进程也会执行本类，必须保持零副作用（2026-09-18 事故教训）。
 */
public class ModulePrefs {

    private static final String TAG = "XSmsCode-Prefs";

    private static final String PROXY_ROOT = "/data/misc/apexdata";
    private static final String LEGACY_TEMPLATE = "/data/data/%s/shared_prefs/%s.xml";

    /** 应用主动导出的配置文件名（PrefsExporter 写入） */
    private static final String EXPORT_NAME = "prefs_export.xml";
    /** 外部存储可能挂载点（phone 进程可读，media_rw_data_file 标签） */
    private static final String[] EXTERNAL_ROOTS = {
            "/sdcard", "/storage/emulated/0", "/storage/self/primary",
    };

    private static String sResolvedPath;
    private static long sResolvedModified;
    private static Map<String, Object> sCache;

    /** 远程偏好（框架 API）缓存 */
    private static android.content.SharedPreferences sRemote;
    private static boolean sRemoteTried;
    private static boolean sLoggedFailure;

    private ModulePrefs() {
    }

    private static Map<String, Object> load(String packageName, String prefFileName) {
        // 1/2) 文件通道
        Map<String, Object> viaFile = loadViaFile(packageName, prefFileName);
        if (viaFile != null) {
            applyLogLevel(viaFile);
            return viaFile;
        }
        // 3) 远程偏好通道
        Map<String, Object> viaRemote = loadViaRemote(packageName, prefFileName);
        if (viaRemote != null) {
            applyLogLevel(viaRemote);
            return viaRemote;
        }
        // 4) 全部失败：尝试唤醒 app 进程（让 Application.onCreate 导出配置），
        //    下次读取即可成功。开机早期 app 未启动时尤其重要。
        wakeAppIfNeeded();
        if (!sLoggedFailure) {
            sLoggedFailure = true;
            XLog.e("%s: all channels failed for %s/%s, using defaults",
                    TAG, packageName, prefFileName);
        }
        return null;
    }

    /** 是否已尝试过唤醒（避免每次读配置都触发） */
    private static volatile boolean sWakeAttempted = false;

    /** 电话进程 Context（由 SmsHandlerHook 在 hook 内注入，仅电话进程有值） */
    private static volatile android.content.Context sPhoneContext;

    public static void setPhoneContext(android.content.Context ctx) {
        sPhoneContext = ctx;
    }

    /**
     * 唤醒应用进程（2026-09-19）。
     *
     * 开机早期应用进程往往尚未启动，导出文件不存在 → 模块读不到配置。
     * 这里通过 ContentProvider 调用触发 Android 拉起应用进程；应用启动后
     * Application.onCreate() 会导出配置，模块后续读取即可成功。
     *
     * ⚠️ 安全约束：只用 hook 注入的真实 Context，**绝不反射 ActivityThread**
     * （反射 systemMain 会在 system_server 内执行并崩溃，曾导致 LSPosed 安全模式）。
     * 仅电话进程会注入 Context，其他进程自然跳过。全程静默，失败只记日志。
     */
    private static void wakeAppIfNeeded() {
        if (sWakeAttempted) {
            return;
        }
        final android.content.Context ctx = sPhoneContext;
        if (ctx == null) {
            return;   // 非电话进程：不做任何跨进程动作
        }
        sWakeAttempted = true;
        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 3; i++) {
                        try {
                            ctx.getContentResolver().call(
                                    android.net.Uri.parse("content://" + PROVIDER_AUTHORITY),
                                    "module_ping", null, null);
                            XLog.e("%s: app wake-up attempted (provider ping ok)", TAG);
                            return;
                        } catch (Throwable t) {
                            XLog.e("%s: app wake-up attempt %d failed: %s", TAG, i + 1, t);
                        }
                        try {
                            Thread.sleep(1500);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }, "smscodf-wake-app").start();
        } catch (Throwable t) {
            XLog.e("%s: wake app thread failed: %s", TAG, t);
        }
    }

    /**
     * 每次成功加载配置后同步日志级别（2026-09-19 修复）。
     *
     * 原实现只在「模块加载」与「短信处理入口」两处设置日志级别：
     * 开机时 app 进程往往尚未启动、导出文件还没生成 → 读到默认 false →
     * 级别停留在 INFO，即使随后用户已开启详细日志，DEBUG 也永远不输出。
     * 现在改为：任何一次成功读到配置都校正一次级别，开关切换后下一条短信即生效。
     */
    private static void applyLogLevel(Map<String, Object> prefs) {
        try {
            Object v = prefs.get(com.tianma.xsmscode.common.constant.PrefConst.KEY_VERBOSE_LOG_MODE);
            boolean verbose = v instanceof Boolean ? (Boolean) v : false;
            if (verbose == sLastVerbose) {
                return;
            }
            sLastVerbose = verbose;
            XLog.setLogLevel(verbose ? android.util.Log.VERBOSE : com.smscodf.zhuxf.BuildConfig.LOG_LEVEL);
            XLog.i("%s: log level applied (verbose=%s)", TAG, verbose);
        } catch (Throwable ignored) {
        }
    }

    /** 上次应用的详细日志开关状态（避免重复设置） */
    private static Boolean sLastVerbose = null;

    private static Map<String, Object> loadViaFile(String packageName, String prefFileName) {
        String path = resolvePath(packageName, prefFileName);
        if (path == null) {
            XLog.e("%s: file channel: no readable path (tried export/apex/legacy)", TAG);
            return null;
        }
        File f = new File(path);
        long modified = f.lastModified();
        if (sCache != null && path.equals(sResolvedPath) && modified == sResolvedModified) {
            return sCache;
        }
        Map<String, Object> map = parse(f);
        if (map == null || map.isEmpty()) {
            XLog.e("%s: file channel: parse failed/empty for %s", TAG, path);
            return null;
        }
        sResolvedPath = path;
        sResolvedModified = modified;
        sCache = map;
        XLog.i("%s: loaded %d key(s) from file %s", TAG, map.size(), path);
        return map;
    }

    private static String resolvePath(String packageName, String prefFileName) {
        // 0) 【首选】应用主动导出的世界可读配置（2026-09-19 根治方案）：
        //    /sdcard/Android/data/<pkg>/files/prefs_export.xml
        //    SELinux 标签 media_rw_data_file，phone 进程可直接读（实证：同目录
        //    下 prev_code_record 属主为 radio）。路径因挂载点不同，逐个探测。
        for (String root : EXTERNAL_ROOTS) {
            File f = new File(root + "/Android/data/" + packageName + "/files/" + EXPORT_NAME);
            try {
                if (f.isFile() && f.canRead()) {
                    return f.getAbsolutePath();
                }
            } catch (Throwable ignored) {
            }
        }
        // 1) LSPosed 代理目录（仅当模块自身进程被注入时才存在）
        File apex = new File(PROXY_ROOT);
        File[] ids = apex.listFiles();
        if (ids != null) {
            for (File id : ids) {
                File candidate = new File(id, "prefs/" + packageName + "/" + prefFileName + ".xml");
                try {
                    if (candidate.isFile() && candidate.canRead()) {
                        return candidate.getAbsolutePath();
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        // 2) 应用数据目录（多数情况下 phone 进程不可读）
        File legacy = new File(String.format(LEGACY_TEMPLATE, packageName, prefFileName));
        try {
            if (legacy.isFile() && legacy.canRead()) {
                return legacy.getAbsolutePath();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Map<String, Object> loadViaRemote(String packageName, String prefFileName) {
        if (sRemoteTried && sRemote == null) {
            return null;
        }
        try {
            if (sRemote == null) {
                sRemoteTried = true;
                io.github.libxposed.api.XposedInterface xi =
                        de.robv.android.xposed.XposedBridge.getXposedInterface();
                if (xi == null) {
                    // 用 ERROR 级别：确保在 INFO 级别下也能看到（排查配置读取问题必需）
                    XLog.e("%s: remote channel unavailable (framework interface is null)", TAG);
                    return null;
                }
                sRemote = xi.getRemotePreferences(prefFileName);
            }
            if (sRemote == null) {
                XLog.e("%s: remote prefs returned null", TAG);
                return null;
            }
            Map<String, ?> all = sRemote.getAll();
            if (all == null || all.isEmpty()) {
                XLog.e("%s: remote prefs empty", TAG);
                return null;
            }
            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<String, ?> e : all.entrySet()) {
                map.put(e.getKey(), e.getValue());
            }
            sCache = map;
            sResolvedPath = "remote://" + prefFileName;
            sResolvedModified = 0;
            XLog.i("%s: loaded %d key(s) from remote prefs", TAG, map.size());
            return map;
        } catch (Throwable t) {
            XLog.e("%s: remote channel failed: %s", TAG, t);
            return null;
        }
    }

    private static Map<String, Object> parse(File f) {
        Map<String, Object> map = new HashMap<>();
        try (FileInputStream is = new FileInputStream(f)) {
            org.xmlpull.v1.XmlPullParser parser = android.util.Xml.newPullParser();
            parser.setInput(is, "utf-8");
            int event;
            while ((event = parser.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("map".equals(name)) {
                        continue;
                    }
                    String key = parser.getAttributeValue(null, "name");
                    if (key == null) {
                        continue;
                    }
                    // Android SharedPreferences XML 格式：
                    //   <boolean name="x" value="true" />   ← 值在 value 属性（自闭合）
                    //   <string name="y">文本</string>      ← 值在文本节点
                    // 2026-09-19 修正：原实现对所有类型都用 nextText()，导致
                    // 自闭合标签取到空串 → boolean 全部解析为 false（所有开关失效）
                    String attrValue = parser.getAttributeValue(null, "value");
                    if (attrValue != null) {
                        map.put(key, coerce(name, attrValue));
                    } else if ("string".equals(name)) {
                        map.put(key, parser.nextText());
                    }
                }
            }
        } catch (Throwable t) {
            XLog.w("%s: parse failed %s: %s", TAG, f, t);
            return null;
        }
        return map;
    }

    private static Object coerce(String type, String value) {
        try {
            switch (type) {
                case "boolean":
                    return Boolean.parseBoolean(value);
                case "int":
                    return Integer.parseInt(value.trim());
                case "long":
                    return Long.parseLong(value.trim());
                case "float":
                    return Float.parseFloat(value.trim());
                default:
                    return value;
            }
        } catch (Throwable t) {
            return value;
        }
    }

    public static boolean getBoolean(String packageName, String prefFileName, String key, boolean defValue) {
        Map<String, Object> map = load(packageName, prefFileName);
        if (map == null) {
            return defValue;
        }
        Object v = map.get(key);
        return v instanceof Boolean ? (Boolean) v : defValue;
    }

    public static String getString(String packageName, String prefFileName, String key, String defValue) {
        Map<String, Object> map = load(packageName, prefFileName);
        if (map == null) {
            return defValue;
        }
        Object v = map.get(key);
        return v == null ? defValue : String.valueOf(v);
    }

    public static int getInt(String packageName, String prefFileName, String key, int defValue) {
        Map<String, Object> map = load(packageName, prefFileName);
        if (map == null) {
            return defValue;
        }
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).intValue() : defValue;
    }

    public static long getLong(String packageName, String prefFileName, String key, long defValue) {
        Map<String, Object> map = load(packageName, prefFileName);
        if (map == null) {
            return defValue;
        }
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).longValue() : defValue;
    }

    /** 供诊断：当前配置来源 */
    public static String resolvedPath() {
        return sResolvedPath;
    }
}
