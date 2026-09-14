package com.tianma.xsmscode.xp.hook.permission;

import android.content.pm.PackageManager;
import android.os.Process;

import com.tianma.xsmscode.common.utils.XLog;
import com.tianma.xsmscode.xp.hook.BaseSubHook;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 2026-09-15 新增：权限查询旁路。
 *
 * ColorOS A15 重构了 PermissionManagerServiceImpl 内部结构（mState/mRegistry 等字段
 * 及 restorePermissionState 的补授路径在部分 ROM 上不可用），导致 Hook34 的
 * 权限补授无法落地，com.android.phone 始终没有 INJECT_EVENTS。
 *
 * 本 hook 直接在权限查询入口（checkUidPermission / checkPermission）上做定点放行：
 * 仅当查询目标是 INJECT_EVENTS 且查询者 uid 属于 phone（uid%100000==1001，
 * 或 pkgName 为 com.android.phone）时返回 GRANTED，其余查询原样放行，
 * 开销为每次调用一次字符串比较，不影响其他权限判定。
 */
public class PermissionCheckBypassHook extends BaseSubHook {

    private static final String INJECT_EVENTS = "android.permission.INJECT_EVENTS";
    private static final String PHONE_PACKAGE = "com.android.phone";
    private static final int PHONE_UID_SUFFIX = 1001;
    private static final int GRANTED = PackageManager.PERMISSION_GRANTED;

    private static final String[] TARGET_CLASSES = {
            "com.android.server.pm.permission.PermissionManagerServiceImpl",
            "com.android.server.pm.permission.PermissionManagerService",
    };

    private static final String[] TARGET_METHODS = {
            "checkUidPermission", "checkPermission",
    };

    public PermissionCheckBypassHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public void startHook() {
        for (String className : TARGET_CLASSES) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, mClassLoader);
            if (clazz == null) {
                XLog.i("PermBypass: class %s not found", className);
                continue;
            }
            int hooked = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                if (!matchesName(method.getName())) {
                    continue;
                }
                if (!hasStringArg(method)) {
                    continue;
                }
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!isPhoneInjectQuery(param.args)) {
                                return;
                            }
                            XLog.i("PermBypass: granting INJECT_EVENTS query for phone, method=%s",
                                    param.method.getName());
                            param.setResult(GRANTED);
                        }
                    });
                    hooked++;
                    XLog.i("PermBypass: hooked %s#%s(%s)", className, method.getName(), signature(method));
                } catch (Throwable t) {
                    XLog.e("PermBypass: hook %s#%s failed: %s", className, method.getName(), t);
                }
            }
            if (hooked > 0) {
                XLog.i("PermBypass: %d method(s) hooked on %s", hooked, className);
                return;
            }
            XLog.w("PermBypass: no matching method on %s", className);
        }
        XLog.e("PermBypass: no target method found on any candidate class");
    }

    private static boolean matchesName(String name) {
        for (String candidate : TARGET_METHODS) {
            if (candidate.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasStringArg(Method method) {
        for (Class<?> type : method.getParameterTypes()) {
            if (type == String.class) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPhoneInjectQuery(Object[] args) {
        boolean injectQuery = false;
        boolean phoneQuery = false;
        for (Object arg : args) {
            if (arg instanceof String) {
                String s = (String) arg;
                if (INJECT_EVENTS.equals(s)) {
                    injectQuery = true;
                } else if (PHONE_PACKAGE.equals(s)) {
                    phoneQuery = true;
                }
            } else if (arg instanceof Integer) {
                int uid = (Integer) arg;
                if (uid >= 0 && uid % 100000 == PHONE_UID_SUFFIX) {
                    phoneQuery = true;
                }
            }
        }
        return injectQuery && phoneQuery;
    }

    private static String signature(Method method) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> type : method.getParameterTypes()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(type.getSimpleName());
        }
        return sb.toString();
    }
}
