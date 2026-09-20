package com.tianma.xsmscode.xp.hook;

import android.app.Application;

import com.tianma.xsmscode.common.utils.XLog;

import java.util.List;

import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * 激活状态检测（2026-09-20，libxposed 官方 service 机制）。
 *
 * <h3>为什么必须用 service 机制</h3>
 * 现代 API 下 <b>模块自身不会被注入</b>，证据有两处：
 * <ol>
 *   <li>LSPosed 官方文档：<i>"As a result, module apps are no longer hooked
 *       by themselves."</i></li>
 *   <li>LSPosed 源码 {@code ScopeAdapter.refresh()} 显式排除：
 *       <pre>if (packageName.equals(module.packageName) ||
 *       packageName.equals(BuildConfig.APPLICATION_ID)) return;</pre>
 *       模块自身既不出现在作用域列表、无法勾选、也不会被注入。</li>
 * </ol>
 * 故 self-hook / 写标记文件 / 跨进程上报等方案均不可行（此前已逐一实测失败）。
 *
 * <h3>官方方案</h3>
 * 官方文档：
 * <blockquote>register an Xposed service listener in your module, and once your
 * module app is launched, the <b>Xposed framework will send you a service</b>
 * to communicate with the framework.</blockquote>
 * 框架主动下发 service，app 侧 service 非空即表示模块已激活。
 * 该通道不依赖模块自身注入，也不受 SELinux/权限限制。
 *
 * <h3>依赖说明</h3>
 * 使用本地 jar（app/libs/libxposed-service-102.0.0.jar）而非 Maven 依赖，
 * 因 Maven 版要求 compileSdk 37（本项目 34）。其 XposedProvider 已在
 * AndroidManifest 手动声明（authority 为 {@code <applicationId>.XposedService}）。
 */
public final class ActivationService {

    private static final String TAG = "ActivationService";

    /** 框架服务实例；非空即表示模块已激活 */
    private static volatile XposedService sService;

    private ActivationService() {
    }

    /** app 启动时调用（SmsCodeApplication.onCreate） */
    public static void init(Application app) {
        try {
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override
                public void onServiceBind(XposedService service) {
                    sService = service;
                    try {
                        XLog.i("%s: service bound, framework=%s %s, api=%d, scope=%s",
                                TAG, service.getFrameworkName(), service.getFrameworkVersion(),
                                service.getApiVersion(), service.getScope());
                        List<HookedTarget> targets = service.getRunningTargets();
                        if (targets != null) {
                            for (HookedTarget t : targets) {
                                XLog.i("%s: running target: %s (pid=%d, state=%s)",
                                        TAG, t.getProcessName(), t.getPid(), t.getState());
                            }
                        }
                    } catch (Throwable t) {
                        XLog.e("%s: log service info failed: %s", TAG, t);
                    }
                }

                @Override
                public void onServiceDied(XposedService service) {
                    XLog.w("%s: service died", TAG);
                    sService = null;
                }
            });
            XLog.i("%s: listener registered", TAG);
        } catch (Throwable t) {
            // 模块未激活时注册可能失败，属正常情况
            XLog.w("%s: register listener failed (module not active?): %s", TAG, t);
        }
    }

    /** 模块是否已激活（框架服务已绑定） */
    public static boolean isActivated() {
        return sService != null;
    }

    /** 框架名（如 LSPosed），未激活返回 null */
    public static String getFrameworkName() {
        XposedService s = sService;
        if (s == null) {
            return null;
        }
        try {
            return s.getFrameworkName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 框架版本，未激活返回 null */
    public static String getFrameworkVersion() {
        XposedService s = sService;
        if (s == null) {
            return null;
        }
        try {
            return s.getFrameworkVersion();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 当前作用域列表，未激活返回 null */
    public static List<String> getScope() {
        XposedService s = sService;
        if (s == null) {
            return null;
        }
        try {
            return s.getScope();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
