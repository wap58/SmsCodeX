package com.tianma.xsmscode.common.utils;

import android.content.Context;
import android.os.SystemClock;

import com.tianma.xsmscode.common.constant.PrefConst;

/**
 * 当前Xposed模块相关工具类
 */
public class ModuleUtils {

    private ModuleUtils() {
    }

    /**
     * 返回模块版本 <br/>
     * 注意：该方法被本模块Hook住，返回的值是 BuildConfig.MODULE_VERSION，如果没被Hook则返回-1
     */
    public static int getModuleVersion() {
        XLog.d("getModuleVersion()");
        return -1;
    }

    /**
     * 当前模块是否在XposedInstaller中被启用（仅自进程 hook 检测）
     */
    public static boolean isModuleEnabled() {
        return getModuleVersion() > 0;
    }

    /**
     * 当前模块是否启用/激活。
     * LSPosed 对静态作用域模块不注入自身进程，自 hook 检测恒为 -1；
     * 故补充依据：电话进程加载模块时经 DBProvider 写入的"报到"时间戳
     * （值为 elapsedRealtime，重启归零），报到发生在本次开机内即视为激活。
     */
    public static boolean isModuleEnabled(Context context) {
        if (getModuleVersion() > 0) {
            return true;
        }
        if (context == null) {
            return false;
        }
        try {
            long activeElapsed = context.getSharedPreferences(PrefConst.PREF_NAME, Context.MODE_PRIVATE)
                    .getLong(PrefConst.KEY_MODULE_ACTIVE_ELAPSED, -1L);
            return activeElapsed >= 0 && activeElapsed <= SystemClock.elapsedRealtime();
        } catch (Throwable t) {
            return false;
        }
    }
}
