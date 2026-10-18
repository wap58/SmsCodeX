package com.tianma.xsmscode.common.utils;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.tianma.xsmscode.common.constant.PrefConst;

/**
 * 应用前后台跟踪 + 隐藏多任务（2026-10-18）。
 *
 * <p>为什么要单独做这个：{@code android:excludeFromRecents} 是清单静态属性，
 * 没有运行期开关 API；等效手段只有在"应用真正退到后台"时调用
 * {@code finishAndRemoveTask()}，时机判断错了就会把用户正在看的页面杀掉。
 *
 * <p>★ 之前两版都错在时机：
 * <ul>
 *   <li>v1 在 {@code HomeActivity.onPause} 里摘 → 点「主题」跳 CyaneaSettingsActivity
 *       也会 onPause，主题页被连带杀掉，表现为"点主题闪退"</li>
 *   <li>v2 用 {@code onUserLeaveHint} → Android 规定"启动另一个 Activity"时同样回调，
 *       问题依旧</li>
 * </ul>
 *
 * <p>本版用 {@link Application.ActivityLifecycleCallbacks} 统计处于
 * started 状态的 Activity 数量：数量归零 = 应用真的到后台了。
 * 这是 ProcessLifecycleOwner 内部的同一套判据，稳定可靠。
 */
public final class AppForegroundTracker implements Application.ActivityLifecycleCallbacks {

    private static int sStarted = 0;
    private static volatile AppForegroundTracker sInstance;

    private static final java.util.List<java.lang.ref.WeakReference<Activity>> sActivities =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final Context mApp;

    private AppForegroundTracker(Context context) {
        mApp = context.getApplicationContext();
    }

    public static void install(Application app) {
        if (sInstance != null) {
            return;
        }
        AppForegroundTracker t = new AppForegroundTracker(app);
        app.registerActivityLifecycleCallbacks(t);
        sInstance = t;
    }

    /** 应用是否有界面可见 */
    public static boolean isForeground() {
        return sStarted > 0;
    }

    @Override
    public void onActivityStarted(Activity activity) {
        sStarted++;
        sActivities.add(new java.lang.ref.WeakReference<>(activity));
    }

    @Override
    public void onActivityStopped(Activity activity) {
        sStarted--;
        if (sStarted <= 0) {
            sStarted = 0;
            // 应用刚退到后台：稍等再摘，避免刚被系统重建时误摘
            mMain.postDelayed(this::maybeRemoveFromRecents, 800L);
        }
    }

    /** 隐藏多任务开关打开时，把自身任务从最近任务栈摘掉 */
    private void maybeRemoveFromRecents() {
        // 期间又回到前台了 → 不摘
        if (sStarted > 0) {
            return;
        }
        SharedPreferences sp = mApp.getSharedPreferences(
                PrefConst.PREF_NAME, Context.MODE_PRIVATE);
        if (!sp.getBoolean(PrefConst.KEY_HIDE_RECENTS, false)) {
            return;
        }
        // 直接结束所有 Activity 并从最近任务移除。
        // 注意：必须用 Activity 上的 finishAndRemoveTask()，它只作用于本任务，
        // 不需要 root，也不会误伤别的应用。
        try {
            for (java.lang.ref.WeakReference<Activity> ref : sActivities) {
                Activity a = ref.get();
                if (a != null && !a.isFinishing()) {
                    a.finishAndRemoveTask();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ---- 未使用但必须实现 ----

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        for (java.util.Iterator<java.lang.ref.WeakReference<Activity>> it =
                sActivities.iterator(); it.hasNext(); ) {
            Activity a = it.next().get();
            if (a == null || a == activity) {
                it.remove();
            }
        }
    }
}
