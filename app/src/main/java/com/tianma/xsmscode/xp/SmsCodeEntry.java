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
            // 2026-09-19：级别设置已收敛到 ModulePrefs.applyLogLevel()
            // （任何一次成功读配置都会校正），此处保留作为早期兜底。
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

        // 激活自检（2026-09-20）：模块被注入到自身进程时，装一个自检 hook，
        // 由其在 Application 创建后写标记文件到自己的 filesDir，供 UI 判定激活态。
        // 这是社区标准做法（Xposed 作者 rovo89 在 issue #64 中确认为 best practice），
        // 也是现代 API 下唯一可行的方案——官方文档明确"module apps are no longer
        // hooked by themselves"，故必须在 scope.list 声明自身包名。
        if (BuildConfig.APPLICATION_ID.equals(param.getPackageName())) {
            com.tianma.xsmscode.xp.hook.ActivationMarker.install(this);
            return;
        }

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
