package com.tianma.xsmscode.xp;

import android.util.Log;

import com.smscodf.zhuxf.BuildConfig;
import com.tianma.xsmscode.common.constant.PrefConst;
import com.tianma.xsmscode.common.utils.XLog;
import com.tianma.xsmscode.common.utils.XSPUtils;
import com.tianma.xsmscode.xp.hook.BaseHook;
import com.tianma.xsmscode.xp.hook.code.SmsHandlerHook;
import com.tianma.xsmscode.xp.hook.me.ModuleUtilsHook;
import com.tianma.xsmscode.xp.hook.permission.PermissionGranterHook;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

/**
 * libxposed 入口。分发逻辑与原 HookEntry 完全一致，由各 hook 自行过滤目标进程。
 */
public class SmsCodeEntry extends XposedModule {

    private static final String TAG = "SmsCodeX";

    private final List<BaseHook> mHookList;
    private boolean mSystemHooked = false;

    public SmsCodeEntry() {
        mHookList = new ArrayList<>();
        mHookList.add(new SmsHandlerHook());        // InboundSmsHandler Hook (com.android.phone)
        mHookList.add(new PermissionGranterHook()); // PermissionManagerService Hook (system server)
        mHookList.add(new ModuleUtilsHook());       // 模块激活检测 Hook (模块自身进程)
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        XposedBridge.attach(this);
        this.log(Log.INFO, TAG, "=== onModuleLoaded, process=" + param.getProcessName()
                + ", libxposed API " + getApiVersion()
                + ", framework " + getFrameworkName() + " " + getFrameworkVersion() + " ===");

        try {
            XSharedPreferences xsp = new XSharedPreferences(
                    BuildConfig.APPLICATION_ID, PrefConst.PREF_NAME);
            if (XSPUtils.isVerboseLogMode(xsp)) {
                XLog.setLogLevel(Log.VERBOSE);
            } else {
                XLog.setLogLevel(BuildConfig.LOG_LEVEL);
            }
        } catch (Throwable t) {
            XLog.e("", t);
        }
        this.log(Log.INFO, TAG, "onModuleLoaded done, hooks=" + mHookList.size());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        super.onPackageReady(param);
        this.log(Log.INFO, TAG, "onPackageReady: " + param.getPackageName());
        dispatch(param.getPackageName(), param.getPackageName(), param.getClassLoader());
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        super.onSystemServerStarting(param);
        if (mSystemHooked) return;
        mSystemHooked = true;
        this.log(Log.INFO, TAG, "onSystemServerStarting");
        dispatch("android", "android", param.getClassLoader());
    }

    private void dispatch(String packageName, String processName, ClassLoader classLoader) {
        XC_LoadPackage.LoadPackageParam lpp = new XC_LoadPackage.LoadPackageParam();
        lpp.packageName = packageName;
        lpp.processName = processName;
        lpp.classLoader = classLoader;
        lpp.isFirstApplication = true;
        for (BaseHook hook : mHookList) {
            if (hook.hookOnLoadPackage()) {
                try {
                    hook.onLoadPackage(lpp);
                } catch (Throwable t) {
                    this.log(Log.ERROR, TAG, "dispatch failed for " + hook.getClass().getName(), t);
                }
            }
        }
    }
}
