package com.tianma.xsmscode.xp.hook.permission;

import static com.tianma.xsmscode.common.constant.PermConst.PACKAGE_PERMISSIONS;

import android.os.Build;
import android.os.UserHandle;

import androidx.annotation.RequiresApi;

import com.tianma.xsmscode.common.utils.XLog;
import com.tianma.xsmscode.xp.helper.MethodHookWrapper;
import com.tianma.xsmscode.xp.hook.BaseSubHook;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Since Android 14(API 34+)<br/>
 * Hook com.android.server.pm.permission.PermissionManagerServiceImpl
 */
public class PermissionManagerServiceHook34 extends BaseSubHook {
    // IMPORTANT: There are two types of permissions: install and runtime.

    // Android 14, API 34
    private static final String CLASS_PERMISSION_MANAGER_SERVICE = "com.android.server.pm.permission.PermissionManagerServiceImpl";

    private static final String CLASS_ANDROID_PACKAGE = "com.android.server.pm.pkg.AndroidPackage";

    // 2026-09-14 修复：嵌套接口类名必须用 '$' 而不是 '.'（Class.forName 语义），
    // 否则 findClassIfExists 恒为 null → findMethodExact 收到 null 参数类型 →
    // getDeclaredMethod 抛 NPE → hook 静默失败 → INJECT_EVENTS 无法补授 →
    // 自动输入三层降级全部失效（A15 ColorOS 实测复现）。
    private static final String CLASS_PERMISSION_CALLBACK = CLASS_PERMISSION_MANAGER_SERVICE + "$PermissionCallback";


    public PermissionManagerServiceHook34(ClassLoader classLoader) {
        super(classLoader);
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Override
    public void startHook() {
        try {
            hookGrantPermissions();
        } catch (Throwable e) {
            XLog.e("Failed to hook PermissionManagerService", e);
        }
    }

    private void hookGrantPermissions() {
        XLog.d("Hooking grantPermissions() for Android 34+");
        Method method = findTargetMethod();
        if (method == null) {
            XLog.e("Cannot find the method to grant relevant permission");
            return;
        }
        XposedBridge.hookMethod(method, new MethodHookWrapper() {
            @Override
            protected void after(MethodHookParam param) throws Throwable {
                afterRestorePermissionStateSinceAndroid14(param);
            }
        });
        XLog.i("Hook34: restorePermissionState hooked OK: %s", method);
    }

    private Method findTargetMethod() {
        Class<?> pmsClass = XposedHelpers.findClass(CLASS_PERMISSION_MANAGER_SERVICE, mClassLoader);
        Class<?> androidPackageClass = XposedHelpers.findClass(CLASS_ANDROID_PACKAGE, mClassLoader);
        Class<?> callbackClass = XposedHelpers.findClassIfExists(CLASS_PERMISSION_CALLBACK, mClassLoader);

        // 首选：按方法形状匹配（不依赖 PermissionCallback 的精确类型，跨 ROM/小版本稳定）
        // 签名: restorePermissionState(AndroidPackage, boolean, String, PermissionCallback, int)
        for (Method m : pmsClass.getDeclaredMethods()) {
            if (!"restorePermissionState".equals(m.getName())) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 5
                    && p[0] == androidPackageClass
                    && p[1] == boolean.class
                    && p[2] == String.class
                    && p[4] == int.class) {
                m.setAccessible(true);
                XLog.i("Hook34: matched restorePermissionState by shape (callback=%s)", p[3].getName());
                return m;
            }
        }

        // 后备：精确匹配（callback 类型已知时）
        if (callbackClass != null) {
            Method method = XposedHelpers.findMethodExactIfExists(pmsClass, "restorePermissionState",
                    /* AndroidPackage pkg          */ androidPackageClass,
                    /* boolean replace             */ boolean.class,
                    /* String packageOfInterest    */ String.class,
                    /* PermissionCallback callback */ callbackClass,
                    /* int filterUserId            */ int.class);

            if (method == null) { // method restorePermissionState() not found
                // 参数类型精确匹配
                Method[] _methods = XposedHelpers.findMethodsByExactParameters(pmsClass, Void.TYPE,
                        /* AndroidPackage pkg          */ androidPackageClass,
                        /* boolean replace             */ boolean.class,
                        /* String packageOfInterest    */ String.class,
                        /* PermissionCallback callback */ callbackClass,
                        /* int filterUserId            */ int.class);
                if (_methods != null && _methods.length > 0) {
                    method = _methods[0];
                }
            }
            if (method != null) {
                return method;
            }
        }

        // 最后兜底：任何名为 restorePermissionState、参数个数>=4 且首参为 AndroidPackage 的方法
        for (Method m : pmsClass.getDeclaredMethods()) {
            if (!"restorePermissionState".equals(m.getName())) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (p.length >= 4 && p[0] == androidPackageClass) {
                m.setAccessible(true);
                XLog.i("Hook34: fallback matched restorePermissionState (%d params)", p.length);
                return m;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void afterRestorePermissionStateSinceAndroid14(XC_MethodHook.MethodHookParam param) {
        // com.android.server.pm.pkg.AndroidPackage 对象
        Object pkg = param.args[0];

        final String _packageName;
        try {
            _packageName = (String) XposedHelpers.callMethod(pkg, "getPackageName");
        } catch (Throwable t) {
            XLog.e("Hook34: getPackageName failed: %s", t);
            return;
        }
        // v3: d 级记录每个包，持久日志可判断 restorePermissionState 在本 ROM 是否按包触发
        XLog.d("Hook34: restorePermissionState fired for %s", _packageName);

        Set<String> packageSet = PACKAGE_PERMISSIONS.keySet();
        for (String packageName : packageSet) {
            if (!packageName.equals(_packageName)) {
                continue;
            }
            XLog.i("Hook34: processing %s", packageName);

            Object pmsImpl = param.thisObject;

            // 2026-09-15: ColorOS A15 重构了 permission 服务内部结构，
            // 每一步都可能失败，逐段防御并记录，便于从持久日志定位差异。
            final Object mPackageManagerInt;
            try {
                mPackageManagerInt = XposedHelpers.getObjectField(pmsImpl, "mPackageManagerInt");
            } catch (Throwable t) {
                XLog.e("Hook34: field mPackageManagerInt missing: %s", t);
                return;
            }

            final int filterUserId = (int) param.args[4];
            final int USER_ALL = XposedHelpers.getStaticIntField(UserHandle.class, "USER_ALL");
            final int[] userIds;
            try {
                userIds = filterUserId == USER_ALL
                        ? (int[]) XposedHelpers.callMethod(pmsImpl, "getAllUserIds")
                        : new int[]{filterUserId};
            } catch (Throwable t) {
                XLog.e("Hook34: getAllUserIds failed: %s", t);
                return;
            }

            List<String> permissionsToGrant = PACKAGE_PERMISSIONS.get(packageName);

            if (userIds == null) {
                XLog.w("Hook34: userIds is null, skip");
                return;
            }

            final Object ps;
            try {
                ps = XposedHelpers.callMethod(mPackageManagerInt, "getPackageStateInternal", packageName);
            } catch (Throwable t) {
                XLog.e("Hook34: getPackageStateInternal failed: %s", t);
                return;
            }
            if (ps == null) {
                XLog.w("Hook34: package state is null for %s", packageName);
                return;
            }

            final List<String> requestedPermissions;
            try {
                requestedPermissions = (List<String>) XposedHelpers.callMethod(pkg, "getRequestedPermissions");
            } catch (Throwable t) {
                XLog.e("Hook34: getRequestedPermissions failed: %s", t);
                return;
            }

            final Object mState;
            try {
                mState = XposedHelpers.getObjectField(pmsImpl, "mState");
            } catch (Throwable t) {
                XLog.e("Hook34: field mState missing: %s", t);
                return;
            }

            final Object mRegistry;
            try {
                mRegistry = XposedHelpers.getObjectField(pmsImpl, "mRegistry");
            } catch (Throwable t) {
                XLog.e("Hook34: field mRegistry missing: %s", t);
                return;
            }

            final int appId;
            try {
                appId = (int) XposedHelpers.callMethod(ps, "getAppId");
            } catch (Throwable t) {
                XLog.e("Hook34: getAppId failed: %s", t);
                return;
            }

            for (final int userId : userIds) {
                Object uidState;
                try {
                    Object userState = XposedHelpers.callMethod(mState, "getOrCreateUserState", userId);
                    uidState = XposedHelpers.callMethod(userState, "getOrCreateUidState", appId);
                } catch (Throwable t) {
                    XLog.e("Hook34: getOrCreateUidState(user=%d) failed: %s", userId, t);
                    continue;
                }

                for (String permissionToGrant : permissionsToGrant) {
                    if (requestedPermissions.contains(permissionToGrant)) {
                        XLog.d("Hook34: %s already requested", permissionToGrant);
                        continue;
                    }
                    try {
                        boolean granted = (boolean) XposedHelpers.callMethod(uidState, "isPermissionGranted", permissionToGrant);
                        if (granted) {
                            XLog.d("Hook34: already have %s", permissionToGrant);
                            continue;
                        }
                        final Object bpToGrant = XposedHelpers.callMethod(mRegistry, "getPermission", permissionToGrant);
                        if (bpToGrant == null) {
                            XLog.e("Hook34: registry has no permission %s", permissionToGrant);
                            continue;
                        }
                        boolean result = (boolean) XposedHelpers.callMethod(uidState, "grantPermission", bpToGrant);
                        XLog.i("Hook34: grant %s -> %b", permissionToGrant, result);
                    } catch (Throwable t) {
                        XLog.e("Hook34: grant %s failed: %s", permissionToGrant, t);
                    }
                }
            }
        }
    }


}
