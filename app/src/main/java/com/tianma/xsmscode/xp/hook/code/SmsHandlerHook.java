package com.tianma.xsmscode.xp.hook.code;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Telephony;

import com.smscodf.zhuxf.BuildConfig;
import com.smscodf.zhuxf.R;
import com.tianma.xsmscode.common.constant.NotificationConst;
import com.tianma.xsmscode.common.constant.PrefConst;
import com.tianma.xsmscode.common.utils.NotificationUtils;
import com.tianma.xsmscode.common.utils.XLog;
import com.tianma.xsmscode.common.utils.XSPUtils;
import com.tianma.xsmscode.xp.helper.XposedWrapper;
import com.tianma.xsmscode.xp.hook.BaseHook;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook class com.android.internal.telephony.InBoundSmsHandler
 */
public class SmsHandlerHook extends BaseHook {

    public static final String ANDROID_PHONE_PACKAGE = "com.android.phone";

    private static final String TELEPHONY_PACKAGE = "com.android.internal.telephony";
    private static final String SMS_HANDLER_CLASS = TELEPHONY_PACKAGE + ".InboundSmsHandler";
    private static final String SMSCODE_PACKAGE = BuildConfig.APPLICATION_ID;

    // Host App Context
    private Context mPhoneContext;
    // Plugin App Context
    private Context mPluginContext;

    @Override
    public void onLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (ANDROID_PHONE_PACKAGE.equals(lpparam.packageName)) {
            XLog.i("SmsCode initializing");
            printDeviceInfo();
            try {
                hookSmsHandler(lpparam.classLoader);
            } catch (Throwable e) {
                XLog.e("Failed to hook SmsHandler", e);
            }
            XLog.i("SmsCode initialize completely");
        }
    }

    @SuppressWarnings("deprecation")
    private static void printDeviceInfo() {
        XLog.i("Phone manufacturer: %s", Build.MANUFACTURER);
        XLog.i("Phone model: %s", Build.MODEL);
        XLog.i("Android version: %s", Build.VERSION.RELEASE);
        int xposedVersion;
        try {
            xposedVersion = XposedBridge.getXposedVersion();
        } catch (Throwable e) {
            xposedVersion = XposedBridge.XPOSED_BRIDGE_VERSION;
        }
        XLog.i("Xposed bridge version: %d", xposedVersion);
        XLog.i("SmsCode version: %s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
    }

    private void hookSmsHandler(ClassLoader classloader) {
        hookConstructor(classloader);
        hookDispatchIntent(classloader);
    }

    private void hookConstructor(ClassLoader classloader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+
            hookConstructor34(classloader);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+
            hookConstructor30(classloader);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android 7.0 ~ 10 (api 24 - 29)
            hookConstructor24(classloader);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Android 4.4 ~ 6.1 (api 19 - 23)
            hookConstructor19(classloader);
        }
    }

    // Android 14+
    private void hookConstructor34(ClassLoader classLoader) {
        XLog.i("Hooking InboundSmsHandler constructor for android v34+");
//        XposedHelpers.findAndHookConstructor(SMS_HANDLER_CLASS, classLoader,
//                /* name                 */ String.class,
//                /* context              */ Context.class,
//                /* storageMonitor       */ TELEPHONY_PACKAGE + ".SmsStorageMonitor",
//                /* phone                */ TELEPHONY_PACKAGE + ".Phone",
//                /* cellBroadcastHandler */ TELEPHONY_PACKAGE + ".CellBroadcastHandler",
//                /* smsDispatchersController */ TELEPHONY_PACKAGE + ".SmsDispatchersController",
//                new ConstructorHook());
        Class<?> smsHandlerClazz = XposedWrapper.findClass(SMS_HANDLER_CLASS, classLoader);
        if (smsHandlerClazz != null) {
            XposedBridge.hookAllConstructors(smsHandlerClazz, new ConstructorHook());
        }
    }

    // Android 11+
    private void hookConstructor30(ClassLoader classloader) {
        XLog.i("Hooking InboundSmsHandler constructor for android v30+");
        XposedHelpers.findAndHookConstructor(SMS_HANDLER_CLASS, classloader,
                /* name                 */ String.class,
                /* context              */ Context.class,
                /* storageMonitor       */ TELEPHONY_PACKAGE + ".SmsStorageMonitor",
                /* phone                */ TELEPHONY_PACKAGE + ".Phone",
                new ConstructorHook());
    }

    // Android N+
    private void hookConstructor24(ClassLoader classloader) {
        XLog.i("Hooking InboundSmsHandler constructor for android v24+");
        XposedHelpers.findAndHookConstructor(SMS_HANDLER_CLASS, classloader,
                /* name                 */ String.class,
                /* context              */ Context.class,
                /* storageMonitor       */ TELEPHONY_PACKAGE + ".SmsStorageMonitor",
                /* phone                */ TELEPHONY_PACKAGE + ".Phone",
                /* cellBroadcastHandler */ TELEPHONY_PACKAGE + ".CellBroadcastHandler",
                new ConstructorHook());
    }

    // Android KitKat+
    private void hookConstructor19(ClassLoader classloader) {
        XLog.i("Hooking InboundSmsHandler constructor for Android v19+");
        XposedHelpers.findAndHookConstructor(SMS_HANDLER_CLASS, classloader,
                /*                 name */ String.class,
                /*              context */ Context.class,
                /*       storageMonitor */ TELEPHONY_PACKAGE + ".SmsStorageMonitor",
                /*                phone */ TELEPHONY_PACKAGE + ".PhoneBase",
                /* cellBroadcastHandler */ TELEPHONY_PACKAGE + ".CellBroadcastHandler",
                new ConstructorHook());
    }

    private void hookDispatchIntent(ClassLoader classloader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hookDispatchIntent29(classloader);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hookDispatchIntent23(classloader);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            hookDispatchIntent21(classloader);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            hookDispatchIntent19(classloader);
        }
    }

    // Android K
    private void hookDispatchIntent19(ClassLoader classloader) {
        XLog.d("Hooking dispatchIntent() for Android v19+");
        XposedHelpers.findAndHookMethod(SMS_HANDLER_CLASS, classloader, "dispatchIntent",
                /*         intent */ Intent.class,
                /*     permission */ String.class,
                /*          appOp */ int.class,
                /* resultReceiver */ BroadcastReceiver.class,
                new DispatchIntentHook(3));
    }

    // Android L+
    private void hookDispatchIntent21(ClassLoader classloader) {
        XLog.d("Hooking dispatchIntent() for Android v21+");
        XposedHelpers.findAndHookMethod(SMS_HANDLER_CLASS, classloader, "dispatchIntent",
                /*         intent */ Intent.class,
                /*     permission */ String.class,
                /*          appOp */ int.class,
                /* resultReceiver */ BroadcastReceiver.class,
                /*           user */ UserHandle.class,
                new DispatchIntentHook(3));
    }

    // Android M+
    private void hookDispatchIntent23(ClassLoader classloader) {
        XLog.d("Hooking dispatchIntent() for Android v23+");
        XposedHelpers.findAndHookMethod(SMS_HANDLER_CLASS, classloader, "dispatchIntent",
                /*         intent */ Intent.class,
                /*     permission */ String.class,
                /*          appOp */ int.class,
                /*           opts */ Bundle.class,
                /* resultReceiver */ BroadcastReceiver.class,
                /*           user */ UserHandle.class,
                new DispatchIntentHook(4));
    }

    // Android 10+
    private void hookDispatchIntent29(ClassLoader classLoader) {
        XLog.d("Hooking dispatchIntent() for Android v29+");
        // 实际上这是一个通用的方式，不再使用精确匹配来找到对应的 Method，而使用模糊搜索的方式
        // 但是之前分 API 匹配的逻辑在以往 Android 版本的系统之中已经验证通过，故而保留原有逻辑

        Class<?> inboundSmsHandlerClass = XposedWrapper.findClass(SMS_HANDLER_CLASS, classLoader);
        if (inboundSmsHandlerClass == null) {
            XLog.e("Class: %s cannot found", SMS_HANDLER_CLASS);
            return;
        }

        Method[] methods = inboundSmsHandlerClass.getDeclaredMethods();
        Method exactMethod = null;
        final String DISPATCH_INTENT = "dispatchIntent";
        int receiverIndex = 0;
        for (Method method : methods) {
            String methodName = method.getName();
            if (DISPATCH_INTENT.equals(methodName)) {
                exactMethod = method;

                Class<?>[] parameterTypes = method.getParameterTypes();
                for (int i = 0; i < parameterTypes.length; i++) {
                    Class<?> parameterType = parameterTypes[i];
                    if (BroadcastReceiver.class.isAssignableFrom(parameterType)) {
                        // 是否是 BroadcastReceiver 或者其 子类
                        receiverIndex = i;
                    }
                }

                break;
            }
        }

        if (exactMethod == null) {
            XLog.e("Method %s for Class %s cannot found", DISPATCH_INTENT, SMS_HANDLER_CLASS);
            return;
        }

        XposedWrapper.hookMethod(exactMethod, new DispatchIntentHook(receiverIndex));
    }

    private class ConstructorHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            try {
                afterConstructorHandler(param);
            } catch (Throwable e) {
                // 模块异常绝不能抛回宿主系统进程！2026-09-17 教训：
                // 一次 SecurityException 曾炸断 PhoneGlobals.onCreate，
                // 导致短信分发/拦截/通知/转发在电话进程内全线失效。
                XLog.e("Error occurred in constructor hook (swallowed, host intact)", e);
            }
        }
    }

    private void afterConstructorHandler(XC_MethodHook.MethodHookParam param) {
        Context context = (Context) param.args[1];
        if (mPhoneContext == null) {
            mPhoneContext = context;
            // 2026-09-19：把电话进程真实 Context 交给配置读取器，
            // 供「读取失败时唤醒 app 进程」使用（不反射、仅电话进程有效）
            try {
                com.tianma.xsmscode.common.utils.ModulePrefs.setPhoneContext(context);
            } catch (Throwable ignored) {
            }
            try {
                mPluginContext = mPhoneContext.createPackageContext(SMSCODE_PACKAGE,
                        Context.CONTEXT_IGNORE_SECURITY);
                initNotificationChannel();
            } catch (Exception e) {
                XLog.e("Create plugin context failed: %s", e);
            }
            try {
                registerCopyCodeReceiver();
            } catch (Throwable e) {
                XLog.e("Register copy-code receiver failed (non-fatal): %s", e);
            }
            pingModuleActive();
            // 电话服务作用域报到（2026-09-20）：此处已拿到电话进程 Context，
            // 走 Provider 由 app 进程代写 SharedPreferences（模块进程无写文件权限）
            com.tianma.xsmscode.xp.hook.ScopeReporter.reportPhone(mPhoneContext, null);
        }
    }

    /**
     * 向自家 App 进程报到：模块已在电话进程加载（供 UI 激活态显示，
     * 不依赖 LSPosed 对自身进程的注入——静态作用域模块会被排除自身）。
     * 后台线程重试数次等待 provider 就绪。
     */
    private void pingModuleActive() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 5; i++) {
                    try {
                        Context pluginCtx = getPluginContext();
                        if (pluginCtx != null) {
                            pluginCtx.getContentResolver().call(
                                    android.net.Uri.parse("content://" + com.tianma.xsmscode.data.db.DBProvider.AUTHORITY),
                                    "module_ping", null, null);
                            XLog.i("Module active ping sent to app process");
                            return;
                        }
                    } catch (Throwable t) {
                        XLog.e("Module active ping failed", t);
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }, "smscodf-module-ping").start();
    }

    private void initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION;
            String channelName = getPluginContext().getString(R.string.channel_name_smscode_notification);
            NotificationUtils.createNotificationChannel(mPhoneContext,
                    channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
            XLog.d("Init notification channel succeed");
        }
    }

    private void registerCopyCodeReceiver() {
        XSharedPreferences xsp = new XSharedPreferences(BuildConfig.APPLICATION_ID, PrefConst.PREF_NAME);
        if (XSPUtils.showCodeNotification(xsp)) {
            CopyCodeReceiver.registerMe(mPhoneContext);
            XLog.d("Register copy code receiver");
        }
    }

    private class DispatchIntentHook extends XC_MethodHook {
        private final int mReceiverIndex;

        DispatchIntentHook(int receiverIndex) {
            mReceiverIndex = receiverIndex;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            try {
                beforeDispatchIntentHandler(param, mReceiverIndex);
            } catch (Throwable e) {
                XLog.e("Error occurred in dispatchIntent() hook, ", e);
                throw e;
            }
        }
    }

    private void beforeDispatchIntentHandler(XC_MethodHook.MethodHookParam param, int receiverIndex) {
        Intent intent = (Intent) param.args[0];
        String action = intent.getAction();

        // We only care about the initial SMS_DELIVER intent,
        // the rest are irrelevant
        if (!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(action)) {
            return;
        }

        ParseResult parseResult = new CodeWorker(getPluginContext(), mPhoneContext, intent).parse();
        if (parseResult != null) {// parse succeed
            if (parseResult.isBlockSms()) {
                XLog.d("Blocking code SMS...");
                deleteRawTableAndSendMessage(param.thisObject, param.args[receiverIndex]);
                param.setResult(null);
            }
        }
    }

    private static final int EVENT_BROADCAST_COMPLETE = 3;

    private void deleteRawTableAndSendMessage(Object inboundSmsHandler, Object smsReceiver) {
        long token = Binder.clearCallingIdentity();
        try {
            deleteFromRawTable(inboundSmsHandler, smsReceiver);
        } catch (Throwable e) {
            XLog.e("Error occurs when delete SMS data from raw table", e);
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        sendEventBroadcastComplete(inboundSmsHandler);
    }

    private void sendEventBroadcastComplete(Object inboundSmsHandler) {
        XLog.d("Send event(EVENT_BROADCAST_COMPLETE)");
        XposedHelpers.callMethod(inboundSmsHandler, "sendMessage", EVENT_BROADCAST_COMPLETE);
    }

    private void deleteFromRawTable(Object inboundSmsHandler, Object smsReceiver) throws ReflectiveOperationException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            deleteFromRawTable24(inboundSmsHandler, smsReceiver);
        } else {
            deleteFromRawTable19(inboundSmsHandler, smsReceiver);
        }
    }

    private void deleteFromRawTable19(Object inboundSmsHandler, Object smsReceiver) throws ReflectiveOperationException {
        XLog.d("Delete raw SMS data from database on Android 19+");
        Object deleteWhere = XposedHelpers.getObjectField(smsReceiver, "mDeleteWhere");
        Object deleteWhereArgs = XposedHelpers.getObjectField(smsReceiver, "mDeleteWhereArgs");

        callDeclaredMethod(SMS_HANDLER_CLASS, inboundSmsHandler, "deleteFromRawTable",
                /* String deleteWhere       */ deleteWhere,
                /* String[] deleteWhereArgs */ deleteWhereArgs);
    }

    private void deleteFromRawTable24(Object inboundSmsHandler, Object smsReceiver) throws ReflectiveOperationException {
        XLog.d("Delete raw SMS data from database on Android 24+");
        Object deleteWhere = XposedHelpers.getObjectField(smsReceiver, "mDeleteWhere");
        Object deleteWhereArgs = XposedHelpers.getObjectField(smsReceiver, "mDeleteWhereArgs");
        final int MARK_DELETED = 2;

        callDeclaredMethod(SMS_HANDLER_CLASS, inboundSmsHandler, "deleteFromRawTable",
                /* String deleteWhere       */ deleteWhere,
                /* String[] deleteWhereArgs */ deleteWhereArgs,
                /* int deleteType           */ MARK_DELETED);
    }

    private static Object callDeclaredMethod(String className, Object obj, String methodName, Object... args) throws InvocationTargetException, IllegalAccessException {
        // XposedHelpers#callMethod() 方法，不能反射调用 private 的方法
        // 而本方法可以反射调用指定类的 private 方法
        Class<?> clz = XposedHelpers.findClass(className, obj.getClass().getClassLoader());
        Method method = XposedHelpers.findMethodBestMatch(clz, methodName, args);
        return method.invoke(obj, args);
    }

    private Context getPluginContext() {
        if (mPluginContext == null) {
            try {
                mPluginContext = mPhoneContext.createPackageContext(SMSCODE_PACKAGE,
                        Context.CONTEXT_IGNORE_SECURITY);
            } catch (Exception e) {
                XLog.e("Create plugin context failed: %s", e);
            }
        }
        return mPluginContext;
    }

}
