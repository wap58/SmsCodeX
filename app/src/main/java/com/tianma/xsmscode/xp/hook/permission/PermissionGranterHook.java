package com.tianma.xsmscode.xp.hook.permission;

import android.os.Build;

import com.tianma.xsmscode.common.utils.XLog;
import com.tianma.xsmscode.xp.hook.BaseHook;
import com.tianma.xsmscode.xp.hook.ScopeReporter;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * system server 内的权限相关 hook。
 *
 * 2026-09-12 实测定案（OnePlus ColorOS / Android 16）：
 * - 原版 2.5.1 的 restorePermissionState 补授路径（Hook34）在 Android 16 上长期运行安全，
 *   且是自动输入真正生效的通路（原版 MT 共存版实测可用）。
 * - 此前“A16 禁用 Hook34、仅靠 InputManagerServiceHook bypass”的策略实测无效
 *   （12:54 开机 system 注入成功、IMS bypass 已装，自动输入依然失败）。
 * - 故恢复与原版一致的行为：立即安装 Hook34；IMS bypass 保留为兜底层
 *   （覆盖 Hook34 目标类缺失的 ROM）。
 *
 * 时机：与原版一致，system server 加载时立即 hook，不做延迟——
 * restorePermissionState 在开机早期即被高频调用，延迟会错过电话进程的授权窗口。
 */
public class PermissionGranterHook extends BaseHook {

    public static final String ANDROID_PACKAGE = "android";

    @Override
    public void onLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!ANDROID_PACKAGE.equals(lpparam.packageName) || !ANDROID_PACKAGE.equals(lpparam.processName)) {
            return;
        }
        ClassLoader classLoader = lpparam.classLoader;
        final int sdkInt = Build.VERSION.SDK_INT;
        XLog.i("PermissionGranter: installing hooks now (Android %d)", sdkInt);

        try {
            // 兜底层：注入权限检查 bypass（动态发现检查方法，适配改名后的 ROM）
            new InputManagerServiceHook(classLoader).startHook();

            // 兜底层 2：权限查询旁路（checkUidPermission 定点放行，见类注释）
            new PermissionCheckBypassHook(classLoader).startHook();

            // 主路径：与原版一致，restorePermissionState 补授 INJECT_EVENTS 等权限
            if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
                new PermissionManagerServiceHook34(classLoader).startHook();
            } else if (sdkInt >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
                new PermissionManagerServiceHook33(classLoader).startHook();
            } else if (sdkInt >= Build.VERSION_CODES.S) { // Android 12~12L
                new PermissionManagerServiceHook31(classLoader).startHook();
            } else if (sdkInt >= Build.VERSION_CODES.R) { // Android 11
                new PermissionManagerServiceHook30(classLoader).startHook();
            } else if (sdkInt >= Build.VERSION_CODES.P) { // Android 9.0~10
                new PermissionManagerServiceHook(classLoader).startHook();
            } else { // Android 5.0 ~ 8.1
                new PackageManagerServiceHook(classLoader).startHook();
            }
            XLog.i("PermissionGranter: hooks installed");
            // 系统框架作用域报到（2026-09-20）：供 UI 显示激活态。
            // 此处无 Context，故写文件而非走 Provider（见 ScopeReporter 注释）。
            ScopeReporter.reportSystem();
        } catch (Throwable t) {
            XLog.e("PermissionGranter failed", t);
        }
    }
}
