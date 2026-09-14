package de.robv.android.xposed;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;

/**
 * de.robv XposedBridge 兼容层（仅 libxposed 路径使用；legacy 路径下被框架实现遮蔽）。
 */
public final class XposedBridge {

    public static final int XPOSED_BRIDGE_VERSION = 93;

    private static volatile XposedInterface sXposed = null;

    private XposedBridge() {
    }

    public static void attach(XposedInterface xposed) {
        sXposed = xposed;
    }

    public static XposedInterface getXposedInterface() {
        return sXposed;
    }

    public static int getXposedVersion() {
        return XPOSED_BRIDGE_VERSION;
    }

    public static void log(String text) {
        XposedInterface xi = sXposed;
        if (xi != null) {
            xi.log(Log.INFO, "XposedBridge", text);
        } else {
            Log.i("XposedBridge", text);
        }
    }

    public static void log(Throwable t) {
        XposedInterface xi = sXposed;
        if (xi != null) {
            xi.log(Log.ERROR, "XposedBridge", Log.getStackTraceString(t));
        } else {
            Log.e("XposedBridge", "", t);
        }
    }

    public static Set<XC_MethodHook.Unhook> hookMethod(Member hookMethod, XC_MethodHook callback) {
        Set<XC_MethodHook.Unhook> unhooks = new HashSet<>();
        XposedInterface xi = requireAttached();
        if (!(hookMethod instanceof Executable)) {
            throw new IllegalArgumentException("Not a hookable method: " + hookMethod);
        }
        XposedInterface.HookHandle handle = xi.hook((Executable) hookMethod).intercept(wrap(callback));
        unhooks.add(new XC_MethodHook.Unhook(handle, callback));
        return unhooks;
    }

    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {
        Set<XC_MethodHook.Unhook> unhooks = new HashSet<>();
        XposedInterface xi = requireAttached();
        for (Method method : hookClass.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                try {
                    XposedInterface.HookHandle handle = xi.hook(method).intercept(wrap(callback));
                    unhooks.add(new XC_MethodHook.Unhook(handle, callback));
                } catch (Throwable t) {
                    log(t);
                }
            }
        }
        return unhooks;
    }

    public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> hookClass, XC_MethodHook callback) {
        Set<XC_MethodHook.Unhook> unhooks = new HashSet<>();
        XposedInterface xi = requireAttached();
        for (Constructor<?> constructor : hookClass.getDeclaredConstructors()) {
            try {
                XposedInterface.HookHandle handle = xi.hook(constructor).intercept(wrap(callback));
                unhooks.add(new XC_MethodHook.Unhook(handle, callback));
            } catch (Throwable t) {
                log(t);
            }
        }
        return unhooks;
    }

    static XposedInterface.Hooker wrap(final XC_MethodHook callback) {
        return new XposedInterface.Hooker() {
            @Override
            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam(chain);
                try {
                    callback.beforeHookedMethod(param);
                } catch (Throwable t) {
                    log(t);
                }
                if (!param.returnEarly) {
                    try {
                        param.result = chain.proceed(param.args);
                    } catch (Throwable t) {
                        param.throwable = t;
                    }
                }
                try {
                    callback.afterHookedMethod(param);
                } catch (Throwable t) {
                    log(t);
                }
                if (param.hasThrowable()) {
                    throw param.throwable;
                }
                return param.result;
            }
        };
    }

    private static XposedInterface requireAttached() {
        XposedInterface xi = sXposed;
        if (xi == null) {
            throw new IllegalStateException("Xposed framework not attached");
        }
        return xi;
    }
}
