package com.tianma.xsmscode.ui.app;

import android.content.res.Resources;

import com.jaredrummler.cyanea.Cyanea;
import com.jaredrummler.cyanea.CyaneaResources;
import com.tianma.xsmscode.data.eventbus.MyEventBusIndex;
import com.tianma.xsmscode.feature.migrate.TransitionTask;

import org.greenrobot.eventbus.EventBus;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import dagger.android.AndroidInjector;
import dagger.android.DaggerApplication;

public class SmsCodeApplication extends DaggerApplication {

    private CyaneaResources mResources = null;

    @Override
    protected AndroidInjector<? extends DaggerApplication> applicationInjector() {
        return DaggerApplicationComponent.factory().create(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // 2026-09-19 根治"模块读不到用户配置"：必须在 Application 启动时
        // 以 MODE_WORLD_READABLE 打开配置——LSPosed 会 hook ContextImpl
        // 的 getPreferencesDir()，把整个应用的配置目录切到世界可读位置
        // (/data/misc/.../prefs/<pkg>/)，电话进程(radio uid)才能读到。
        // 官方文档：https://github.com/LSPosed/LSPosed/wiki/New-XSharedPreferences
        // 只用 MODE_PRIVATE 的话，配置留在 /data/data/ 下，模块永远读不到，
        // 表现为"拦截/复制/通知自动清除"三个开关失效。
        try {
            getSharedPreferences(com.tianma.xsmscode.common.constant.PrefConst.PREF_NAME,
                    android.content.Context.MODE_WORLD_READABLE);
        } catch (SecurityException ignored) {
            // 模块未被 LSPosed 激活时 checkMode 不被 hook，退回私有模式
            getSharedPreferences(com.tianma.xsmscode.common.constant.PrefConst.PREF_NAME,
                    android.content.Context.MODE_PRIVATE);
        }
        Cyanea.init(this, super.getResources());
        // 启动转发保活前台服务（息屏时保证转发通道可达；服务内部自适应）
        try {
            com.tianma.xsmscode.feature.forward.ForwardKeepAliveService.start(this);
        } catch (Throwable ignored) {
        }
        if (!Cyanea.getInstance().isThemeModified()) {
            Cyanea.getInstance().edit()
                    .baseTheme(Cyanea.BaseTheme.LIGHT)
                    .apply();
        }

        installDefaultEventBus();
        performTransitionTask();
        // 2026-09-19：启动时也导出一次配置（保证模块在任何时刻都能读到最新值）
        try {
            com.tianma.xsmscode.common.utils.PrefsExporter.export(this);
        } catch (Throwable ignored) {
        }
        // 激活检测（2026-09-20）：注册 libxposed 官方 service 监听，
        // 框架绑定成功后 service 非空即表示模块已激活（详见 ActivationService 注释）
        com.tianma.xsmscode.xp.hook.ActivationService.init(this);
    }

    @Override
    public Resources getResources() {
        if (Cyanea.isInitialized()) {
            if (mResources == null) {
                mResources = new CyaneaResources(super.getResources(), Cyanea.getInstance());
            }
            return mResources;
        }
        return super.getResources();
    }

    private void installDefaultEventBus() {
        EventBus.builder().addIndex(new MyEventBusIndex()).installDefaultEventBus();
    }

    // data transition task
    private void performTransitionTask() {
        Executor singlePool = Executors.newSingleThreadExecutor();
        singlePool.execute(new TransitionTask(this));
    }

}
