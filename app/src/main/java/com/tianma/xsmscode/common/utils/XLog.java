package com.tianma.xsmscode.common.utils;

import android.util.Log;

import com.smscodf.zhuxf.BuildConfig;

import de.robv.android.xposed.XposedBridge;

public class XLog {

    private static final String LOG_TAG = BuildConfig.LOG_TAG;
    private static int sLogLevel = BuildConfig.LOG_LEVEL;
    private static final boolean LOG_TO_XPOSED = BuildConfig.LOG_TO_XPOSED;

    private XLog() {
    }

    private static void log(int priority, String message, Object... args) {
        if (priority < sLogLevel)
            return;

        message = String.format(message, args);

        if (args.length > 0 && args[args.length - 1] instanceof Throwable) {
            Throwable throwable = (Throwable) args[args.length - 1];
            String stacktraceStr = Log.getStackTraceString(throwable);
            message += '\n' + stacktraceStr;
        }

        // Write to the default log tag
        Log.println(priority, LOG_TAG, message);

        if (LOG_TO_XPOSED) {
            // logcat side channel (visible in logcat, rolls quickly)
            Log.println(priority, "LSPosed-Bridge", LOG_TAG + ": " + message);
            // Persist to LSPosed modules log (survives reboot) — 2026-09-15:
            // boot-time hook diagnostics were previously lost to logcat rotation.
            try {
                XposedBridge.log(LOG_TAG + ": " + message);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void v(String message, Object... args) {
        log(Log.VERBOSE, message, args);
    }

    public static void d(String message, Object... args) {
        log(Log.DEBUG, message, args);
    }

    public static void i(String message, Object... args) {
        log(Log.INFO, message, args);
    }

    public static void w(String message, Object... args) {
        log(Log.WARN, message, args);
    }

    public static void e(String message, Object... args) {
        log(Log.ERROR, message, args);
    }

    public static void setLogLevel(int logLevel) {
        sLogLevel = logLevel;
    }
}
