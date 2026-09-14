package de.robv.android.xposed;

import java.lang.reflect.Member;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

public abstract class XC_MethodHook {

    public static final int PRIORITY_DEFAULT = 50;
    public static final int PRIORITY_LOWEST = Integer.MIN_VALUE;
    public static final int PRIORITY_HIGHEST = Integer.MAX_VALUE;

    protected XC_MethodHook() {
    }

    protected XC_MethodHook(int priority) {
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public static class Unhook {
        private final XposedInterface.HookHandle mHandle;
        private final XC_MethodHook mCallback;

        Unhook(XposedInterface.HookHandle handle, XC_MethodHook callback) {
            mHandle = handle;
            mCallback = callback;
        }

        public XC_MethodHook getCallback() {
            return mCallback;
        }

        public void unhook() {
            try {
                mHandle.unhook();
            } catch (Throwable ignored) {
            }
        }
    }

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        /* package */ Object result = null;
        /* package */ Throwable throwable = null;
        /* package */ boolean returnEarly = false;

        public MethodHookParam() {
        }

        MethodHookParam(XposedInterface.Chain chain) {
            this.method = chain.getExecutable();
            this.thisObject = chain.getThisObject();
            List<Object> argList = chain.getArgs();
            this.args = argList != null ? argList.toArray() : new Object[0];
        }

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
            this.returnEarly = true;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
            this.returnEarly = true;
        }

        public boolean hasThrowable() {
            return throwable != null;
        }

        public Object getResultOrThrowable() throws Throwable {
            if (throwable != null) throw throwable;
            return result;
        }
    }
}
