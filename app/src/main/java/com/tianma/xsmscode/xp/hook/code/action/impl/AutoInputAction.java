package com.tianma.xsmscode.xp.hook.code.action.impl;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.tianma.xsmscode.common.utils.XLog;
import com.tianma.xsmscode.data.db.DBProvider;
import com.tianma.xsmscode.data.db.entity.AppInfo;
import com.tianma.xsmscode.data.db.entity.AppInfoDao;
import com.tianma.xsmscode.data.db.entity.SmsMsg;
import com.tianma.xsmscode.feature.store.EntityStoreManager;
import com.tianma.xsmscode.feature.store.EntityType;
import com.tianma.xsmscode.xp.hook.code.action.CallableAction;
import com.tianma.xsmscode.xp.hook.code.helper.InputHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.XSharedPreferences;

/**
 * 自动输入验证码
 */
public class AutoInputAction extends CallableAction {

    public AutoInputAction(Context pluginContext, Context phoneContext, SmsMsg smsMsg, XSharedPreferences xsp) {
        super(pluginContext, phoneContext, smsMsg, xsp);
    }

    @Override
    public Bundle action() {
        prepareAutoInputCode(mSmsMsg.getSmsCode());
        return null;
    }

    private void prepareAutoInputCode(String code) {
        if (!autoInputBlockedHere()) {
            autoInputCode(code);
        }
    }

    // auto-input：三层降级——①直接注入按键 ②Ctrl+V粘贴剪贴板 ③root执行input命令
    private void autoInputCode(String code) {
        try {
            InputHelper.sendText(code);
            XLog.i("Auto input succeed (direct inject)");
            return;
        } catch (Throwable t) {
            XLog.e("Auto input direct inject failed, trying ctrl+v paste", t);
        }
        try {
            // 等待剪贴板写入完成（CopyToClipboardAction 在主线程先行执行）
            Thread.sleep(150);
            InputHelper.sendPasteViaCtrlV();
            XLog.i("Auto input succeed (ctrl+v paste)");
            return;
        } catch (Throwable t) {
            XLog.e("Auto input paste failed, trying root", t);
        }
        if (InputHelper.sendTextViaRoot(code)) {
            XLog.i("Auto input succeed (root)");
        } else {
            XLog.e("Auto input failed: inject/paste/root all unavailable");
        }
    }

    // 是否屏蔽自动输入
    private boolean autoInputBlockedHere() {
        boolean result = false;
        try {
            List<String> blockedAppList = new ArrayList<>();
            try {
                Uri appInfoUri = DBProvider.APP_INFO_URI;
                ContentResolver resolver = mPluginContext.getContentResolver();

                final String packageColumn = AppInfoDao.Properties.PackageName.columnName;
                final String blockedColumn = AppInfoDao.Properties.Blocked.columnName;

                String[] projection = {packageColumn,};
                String selection = blockedColumn + " = ?";
                String[] selectionArgs = {String.valueOf(1)};
                Cursor cursor = resolver.query(appInfoUri, projection, selection, selectionArgs, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        blockedAppList.add(cursor.getString(cursor.getColumnIndexOrThrow(packageColumn)));
                    }
                    cursor.close();
                }
                XLog.d("Get blocked apps by content provider");
            } catch (Exception e) {
                List<AppInfo> appInfoList = EntityStoreManager
                        .loadEntitiesFromFile(EntityType.BLOCKED_APP, AppInfo.class);
                for (AppInfo appInfo : appInfoList) {
                    blockedAppList.add(appInfo.getPackageName());
                }
                XLog.d("Get blocked apps from file");
            }

            if (blockedAppList.isEmpty()) {
                return false;
            }

            List<ActivityManager.RunningTaskInfo> runningTasks = getRunningTasks(mPhoneContext);
            String topPkgPrimary = null;
            if (runningTasks != null && runningTasks.size() > 0) {
                topPkgPrimary = runningTasks.get(0).topActivity.getPackageName();
                XLog.d("topPackagePrimary: %s", topPkgPrimary);
            }

            if (topPkgPrimary != null && blockedAppList.contains(topPkgPrimary)) {
                return true;
            }

            // RunningAppProcess 判断当前的进程不是很准确，所以用作次要参考
            List<ActivityManager.RunningAppProcessInfo> appProcesses = getRunningAppProcesses(mPhoneContext);
            if (appProcesses == null) {
                return false;
            }

            String[] topPkgSecondary = appProcesses.get(0).pkgList;
            String topProcessSecondary = appProcesses.get(0).processName;
            XLog.d("topProcessSecondary: %s, topPackages: %s", topProcessSecondary, Arrays.toString(topPkgSecondary));

            if (blockedAppList.contains(topProcessSecondary)) {
                result = true;
            } else {
                for (String topPackage : topPkgSecondary) {
                    if (blockedAppList.contains(topPackage)) {
                        result = true;
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            XLog.e("", t);
        }
        return result;
    }

    private List<ActivityManager.RunningAppProcessInfo> getRunningAppProcesses(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return am == null ? null : am.getRunningAppProcesses();
    }

    @SuppressWarnings("deprecation")
    private List<ActivityManager.RunningTaskInfo> getRunningTasks(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return am == null ? null : am.getRunningTasks(10);
    }
}
