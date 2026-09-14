package com.tianma.xsmscode.xp.hook.permission;

import android.os.Binder;

import com.tianma.xsmscode.common.utils.XLog;
import com.tianma.xsmscode.xp.hook.BaseSubHook;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 2026-09-15 v3：注入权限检查的最终绕过。
 *
 * ColorOS A15 上：
 *  - checkInjectEventsPermission 已改名/内联（方法名中不再含 "InjectEvents"）；
 *  - 权限检查内联在 injectInputEvent* 方法体内，无法通过 hook 独立检查方法放行；
 *  - PMS 补授路径（restorePermissionState）在本 ROM 未按 AOSP 时机调用，Hook34 无从触发；
 *  - checkUidPermission 旁路 hook 也未被该检查路径经过（内部走 AccessCheckDelegate 等）。
 *
 * 因此改用身份切换方案：hook InputManagerService 的所有 injectInputEvent* 入口，
 * 当且仅当调用者是 phone 进程（uid%100000==1001）时，先 Binder.clearCallingIdentity()
 * ——方法体内联的权限检查随后调用 Binder.getCallingUid() 时读到的是 system uid，
 * 检查必然通过——注入完成后在 afterHookedMethod 恢复原身份。
 *
 * 安全性：只影响 phone uid 的注入调用；其他应用（uid 不匹配）走原检查逻辑，行为不变。
 */
public class InputManagerServiceHook extends BaseSubHook {

    private static final String CLASS_INPUT_MANAGER_SERVICE = "com.android.server.input.InputManagerService";

    private static final int PHONE_UID_SUFFIX = 1001;

    /** 每个 binder 线程的身份令牌（before 存，after 还原） */
    private static final ThreadLocal<Long> IDENTITY = new ThreadLocal<>();

    public InputManagerServiceHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public void startHook() {
        try {
            Class<?> imsClass = XposedHelpers.findClassIfExists(CLASS_INPUT_MANAGER_SERVICE, mClassLoader);
            if (imsClass == null) {
                XLog.e("InputManagerServiceHook: %s not found", CLASS_INPUT_MANAGER_SERVICE);
                return;
            }

            int hooked = 0;
            for (Method method : imsClass.getDeclaredMethods()) {
                String name = method.getName();
                String lower = name.toLowerCase();
                if (!lower.contains("inject")) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                XLog.i("InputManagerServiceHook: discovered %s(%s)", name, signature(params));

                // 只挂接注入入口方法（首参为 InputEvent）；检查方法已内联，无法单独挂接
                boolean injectEntry = params.length >= 1
                        && android.view.InputEvent.class.isAssignableFrom(params[0]);
                if (!injectEntry) {
                    continue;
                }
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                int uid = Binder.getCallingUid();
                                if (uid >= 0 && uid % 100000 == PHONE_UID_SUFFIX) {
                                    IDENTITY.set(Binder.clearCallingIdentity());
                                    XLog.d("InputManagerServiceHook: identity cleared for phone injection");
                                }
                            } catch (Throwable t) {
                                XLog.e("InputManagerServiceHook: clear identity failed: %s", t);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Long token = IDENTITY.get();
                            if (token != null) {
                                IDENTITY.remove();
                                try {
                                    Binder.restoreCallingIdentity(token);
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                    });
                    hooked++;
                    XLog.i("InputManagerServiceHook: identity-bypass installed on %s(%s)",
                            name, signature(params));
                } catch (Throwable t) {
                    XLog.e("InputManagerServiceHook: hook %s failed: %s", name, t);
                }
            }
            XLog.i("InputManagerServiceHook: %d injection entr(y|ies) hooked", hooked);
        } catch (Throwable t) {
            XLog.e("Failed to hook InputManagerService", t);
        }
    }

    private static String signature(Class<?>[] params) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> p : params) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(p.getSimpleName());
        }
        return sb.toString();
    }
}
