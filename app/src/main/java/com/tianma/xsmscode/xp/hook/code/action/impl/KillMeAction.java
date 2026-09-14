package com.tianma.xsmscode.xp.hook.code.action.impl;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;

import com.smscodf.zhuxf.BuildConfig;
import com.tianma.xsmscode.common.utils.XLog;
import com.tianma.xsmscode.common.utils.XSPUtils;
import com.tianma.xsmscode.data.db.entity.SmsMsg;
import com.tianma.xsmscode.xp.hook.code.action.CallableAction;

import de.robv.android.xposed.XSharedPreferences;

public class KillMeAction extends CallableAction {

    public KillMeAction(Context pluginContext, Context phoneContext, SmsMsg smsMsg, XSharedPreferences xsp) {
        super(pluginContext, phoneContext, smsMsg, xsp);
    }

    @Override
    public Bundle action() {
        killMe();
        return null;
    }

    private void killMe() {
        if (!XSPUtils.killMeEnabled(xsp)) {
            return;
        }
        // 主路径：通过自家 ContentProvider 指令 App 进程自杀（provider 侧校验 callingUid，
        // 自杀无需任何权限，绕开 ROM 裁剪 KILL_BACKGROUND_PROCESSES 的问题）
        try {
            Bundle result = mPluginContext.getContentResolver().call(
                    android.net.Uri.parse("content://" + com.tianma.xsmscode.data.db.DBProvider.AUTHORITY),
                    "kill_me", null, null);
            if (result != null && result.getBoolean("ok", false)) {
                XLog.i("Kill me: suicide order accepted by app process");
                return;
            }
            XLog.w("Kill me: provider rejected the order, falling back");
        } catch (Throwable t) {
            XLog.e("Kill me via provider failed, falling back", t);
        }
        // 兜底路径：传统 killBackgroundProcesses（仅权限完好的 ROM 有效）
        killBackgroundProcess(BuildConfig.APPLICATION_ID);
    }

    /**
     * android.app.ActivityManager#killBackgroundProcess()
     */
    @SuppressLint("MissingPermission")
    private void killBackgroundProcess(String packageName) {
        try {
            ActivityManager activityManager = (ActivityManager) mPluginContext.getSystemService(Context.ACTIVITY_SERVICE);

            if (activityManager != null) {
                activityManager.killBackgroundProcesses(packageName);
                XLog.d("Kill %s background process succeed", packageName);
            }
        } catch (Throwable e) {
            XLog.e("Error occurs when kill background process %s", packageName, e);
        }
    }
}
