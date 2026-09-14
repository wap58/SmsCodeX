package de.robv.android.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;

/**
 * de.robv XposedHelpers 兼容层：签名与 rovo89 原版一致。
 */
public final class XposedHelpers {

    private XposedHelpers() {
    }

    public static class ClassNotFoundError extends Error {
        public ClassNotFoundError(Throwable cause) {
            super(cause);
        }
    }

    public static class InvocationTargetError extends Error {
        public InvocationTargetError(Throwable cause) {
            super(cause);
        }
    }

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new ClassNotFoundError(e);
        }
    }

    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        try {
            return findClass(className, classLoader);
        } catch (ClassNotFoundError e) {
            return null;
        }
    }

    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader,
                                                         String methodName, Object... parameterTypesAndCallback) {
        return findAndHookMethod(findClass(className, classLoader), methodName, parameterTypesAndCallback);
    }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName,
                                                         Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback.length == 0 ||
                !(parameterTypesAndCallback[parameterTypesAndCallback.length - 1] instanceof XC_MethodHook)) {
            throw new IllegalArgumentException("last argument must be an XC_MethodHook");
        }
        Method method = findMethodExact(clazz, methodName,
                getParameterTypes(clazz, parameterTypesAndCallback, true));
        Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookMethod(method,
                (XC_MethodHook) parameterTypesAndCallback[parameterTypesAndCallback.length - 1]);
        return unhooks.isEmpty() ? null : unhooks.iterator().next();
    }

    public static XC_MethodHook.Unhook findAndHookConstructor(String className, ClassLoader classLoader,
                                                              Object... parameterTypesAndCallback) {
        return findAndHookConstructor(findClass(className, classLoader), parameterTypesAndCallback);
    }

    public static XC_MethodHook.Unhook findAndHookConstructor(Class<?> clazz, Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback.length == 0 ||
                !(parameterTypesAndCallback[parameterTypesAndCallback.length - 1] instanceof XC_MethodHook)) {
            throw new IllegalArgumentException("last argument must be an XC_MethodHook");
        }
        Constructor<?> constructor = findConstructorExact(clazz,
                getParameterTypes(clazz, parameterTypesAndCallback, true));
        Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookMethod(constructor,
                (XC_MethodHook) parameterTypesAndCallback[parameterTypesAndCallback.length - 1]);
        return unhooks.isEmpty() ? null : unhooks.iterator().next();
    }

    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {
        return XposedBridge.hookAllMethods(hookClass, methodName, callback);
    }

    public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> hookClass, XC_MethodHook callback) {
        return XposedBridge.hookAllConstructors(hookClass, callback);
    }

    public static Method findMethodExact(Class<?> clazz, String methodName, Object... parameterTypes) {
        return findMethodExact(clazz, methodName, getParameterTypes(clazz, parameterTypes, false));
    }

    public static Method findMethodExact(String className, ClassLoader classLoader, String methodName,
                                         Object... parameterTypes) {
        return findMethodExact(findClass(className, classLoader), methodName, parameterTypes);
    }

    public static Method findMethodExact(Class<?> clazz, String methodName, Class<?>[] parameterTypes) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(methodName, parameterTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodError(methodName + " in " + clazz.getName());
    }

    public static Method findMethodExactIfExists(Class<?> clazz, String methodName, Object... parameterTypes) {
        try {
            return findMethodExact(clazz, methodName, parameterTypes);
        } catch (Error e) {
            return null;
        }
    }

    public static Method findMethodBestMatch(Class<?> clazz, String methodName, Object... args) {
        Class<?>[] argTypes = getTypesForArgs(args);
        Method best = null;
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (!method.getName().equals(methodName)) continue;
                if (matchParamTypes(method.getParameterTypes(), argTypes)) {
                    if (best == null || isMoreSpecific(method, best)) best = method;
                }
            }
            if (best != null) break;
        }
        if (best == null) {
            throw new NoSuchMethodError(methodName + " in " + clazz.getName());
        }
        best.setAccessible(true);
        return best;
    }

    public static Method[] findMethodsByExactParameters(Class<?> clazz, Class<?> returnType, Class<?>... parameterTypes) {
        List<Method> result = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (returnType != null && method.getReturnType() != returnType) continue;
            if (parameterTypes != null) {
                if (method.getParameterTypes().length != parameterTypes.length) continue;
                boolean mismatch = false;
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (parameterTypes[i] != null && method.getParameterTypes()[i] != parameterTypes[i]) {
                        mismatch = true;
                        break;
                    }
                }
                if (mismatch) continue;
            }
            method.setAccessible(true);
            result.add(method);
        }
        return result.toArray(new Method[0]);
    }

    private static Constructor<?> findConstructorExact(Class<?> clazz, Class<?>[] parameterTypes) {
        try {
            Constructor<?> c = clazz.getDeclaredConstructor(parameterTypes);
            c.setAccessible(true);
            return c;
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError("constructor in " + clazz.getName());
        }
    }

    public static Object callMethod(Object obj, String methodName, Object... args) {
        if (obj == null) throw new NullPointerException("target object is null");
        try {
            return findMethodBestMatch(obj.getClass(), methodName, args).invoke(obj, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new InvocationTargetError(cause);
        }
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        try {
            Method method = findMethodBestMatch(clazz, methodName, args);
            if (!Modifier.isStatic(method.getModifiers())) {
                throw new IllegalArgumentException(methodName + " is not static");
            }
            return method.invoke(null, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new InvocationTargetError(cause);
        }
    }

    public static Object callMethod(Object obj, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (obj == null) throw new NullPointerException("target object is null");
        try {
            return findMethodByTypes(obj.getClass(), methodName, parameterTypes).invoke(obj, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new InvocationTargetError(cause);
        }
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = findMethodByTypes(clazz, methodName, parameterTypes);
            if (!Modifier.isStatic(method.getModifiers())) {
                throw new IllegalArgumentException(methodName + " is not static");
            }
            return method.invoke(null, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new InvocationTargetError(cause);
        }
    }

    /** 精确匹配优先；失败按 参数个数+可赋值性 匹配（KeyEvent→InputEvent 等子类场景）。 */
    private static Method findMethodByTypes(Class<?> clazz, String methodName, Class<?>[] parameterTypes) {
        try {
            Method m = clazz.getDeclaredMethod(methodName, parameterTypes);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException ignored) {
        }
        Class<?> c = clazz;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(methodName)) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != parameterTypes.length) continue;
                boolean ok = true;
                for (int i = 0; i < p.length; i++) {
                    Class<?> want = parameterTypes[i];
                    if (want == null) continue;
                    if (p[i].isPrimitive()) {
                        if (!primitiveMatches(p[i], want)) { ok = false; break; }
                    } else if (!p[i].isAssignableFrom(want)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    m.setAccessible(true);
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        throw new NoSuchMethodError(clazz.getName() + "#" + methodName + " (no compatible "
                + parameterTypes.length + "-param overload)");
    }

    private static boolean primitiveMatches(Class<?> prim, Class<?> want) {
        if (prim == int.class) return want == Integer.class || want == int.class;
        if (prim == long.class) return want == Long.class || want == long.class;
        if (prim == boolean.class) return want == Boolean.class || want == boolean.class;
        if (prim == float.class) return want == Float.class || want == float.class;
        if (prim == double.class) return want == Double.class || want == double.class;
        if (prim == short.class) return want == Short.class || want == short.class;
        if (prim == byte.class) return want == Byte.class || want == byte.class;
        if (prim == char.class) return want == Character.class || want == char.class;
        return prim == want;
    }

    public static Object getObjectField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).get(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        try {
            findField(obj.getClass(), fieldName).set(obj, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static int getIntField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).getInt(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static int getStaticIntField(Class<?> clazz, String fieldName) {
        try {
            return findField(clazz, fieldName).getInt(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        try {
            return findField(clazz, fieldName).get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field findField(Class<?> clazz, String fieldName) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldError(fieldName + " in " + clazz.getName());
    }

    private static Class<?>[] getParameterTypes(Class<?> clazz, Object[] parameterTypes, boolean excludeLastCallback) {
        int len = excludeLastCallback ? parameterTypes.length - 1 : parameterTypes.length;
        Class<?>[] types = new Class<?>[len];
        for (int i = 0; i < len; i++) {
            Object t = parameterTypes[i];
            if (t == null) {
                types[i] = null;
            } else if (t instanceof Class) {
                types[i] = (Class<?>) t;
            } else if (t instanceof String) {
                types[i] = findClass((String) t, clazz.getClassLoader());
            } else {
                throw new IllegalStateException("parameter type must be Class or String: " + t);
            }
        }
        return types;
    }

    private static Class<?>[] getTypesForArgs(Object[] args) {
        if (args == null || args.length == 0) return new Class<?>[0];
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] != null ? args[i].getClass() : null;
        }
        return types;
    }

    private static boolean matchParamTypes(Class<?>[] declared, Class<?>[] given) {
        if (declared.length != given.length) return false;
        for (int i = 0; i < declared.length; i++) {
            if (given[i] == null) {
                if (declared[i].isPrimitive()) return false;
            } else if (!match(declared[i], given[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean match(Class<?> declared, Class<?> given) {
        if (declared.isPrimitive()) {
            return wrapper(declared) == given;
        }
        return declared.isAssignableFrom(given);
    }

    private static boolean isMoreSpecific(Method a, Method b) {
        Class<?>[] pa = a.getParameterTypes();
        Class<?>[] pb = b.getParameterTypes();
        for (int i = 0; i < pa.length; i++) {
            if (pa[i] != pb[i] && pb[i].isAssignableFrom(pa[i])) return true;
        }
        return false;
    }

    private static Class<?> wrapper(Class<?> primitive) {
        if (primitive == int.class) return Integer.class;
        if (primitive == long.class) return Long.class;
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == byte.class) return Byte.class;
        if (primitive == short.class) return Short.class;
        if (primitive == char.class) return Character.class;
        if (primitive == float.class) return Float.class;
        if (primitive == double.class) return Double.class;
        return primitive;
    }
}
