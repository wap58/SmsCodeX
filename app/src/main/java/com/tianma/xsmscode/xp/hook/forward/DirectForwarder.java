package com.tianma.xsmscode.xp.hook.forward;

import android.text.TextUtils;

import com.tianma.xsmscode.common.constant.PrefConst;
import com.tianma.xsmscode.common.utils.XLog;

import de.robv.android.xposed.XSharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


/**
 * 电话进程直发器（2026-09-17 新增，彻底修复息屏转发）：
 * <p>
 * 背景：自动转发原走"电话进程 → ContentProvider → app 进程 → HTTP"。
 * 息屏时 app 进程被系统冻结/杀掉，Provider 通道不可达 → 转发必挂；
 * 保活前台服务又经常被 ColorOS 杀掉，不可靠。
 * <p>
 * 方案：com.android.phone 是系统常驻进程（radio uid，有网权限），
 * 模块 hook 直接在本进程内完成 HTTP 发送——不依赖 app 进程、
 * 不依赖前台服务、不怕息屏冻结。XSharedPreferences 直接读 app 的
 * 偏好文件（app 侧 onPause 已 setPreferenceWorldWritable）。
 * <p>
 * 通道与 app 侧 ChannelSender/DBProvider 保持同语义。
 * 失败时由 CodeWorker 回退 Provider 通道（开屏场景兜底）。
 */
public final class DirectForwarder {

    public static final int RESULT_SENT = 1;   // 发送成功
    public static final int RESULT_FAILED = 0;  // 失败（调用方回退 Provider）
    public static final int RESULT_SKIP = -1;   // 未启用/未配置，无需重试

    private DirectForwarder() {
    }

    public static int forward(XSharedPreferences xsp, String sender, String body, String code, long time) {
        // 2026-09-19：改用 ModulePrefs（读应用导出的世界可读配置），
        // 框架 XSharedPreferences 在电话进程读不到配置（LSPosed 机制限制）
        final String PKG = com.smscodf.zhuxf.BuildConfig.APPLICATION_ID;
        final String PF = com.tianma.xsmscode.common.constant.PrefConst.PREF_NAME;
        try {
            if (!com.tianma.xsmscode.common.utils.ModulePrefs.getBoolean(PKG, PF, PrefConst.KEY_ENABLE_FORWARD, false)) {
                return RESULT_SKIP;
            }
            String content = buildContent(sender, body, code, time);

            // 2026-10-17：改为多通道分发，与 app 侧共用 ForwardDispatcher，
            // 不再在这里单独维护一份 switch（新增通道只需改一处）。
            java.util.List<com.tianma.xsmscode.feature.forward.ForwardResultStore.ChannelResult> results =
                    com.tianma.xsmscode.feature.forward.ForwardDispatcher.send(
                            new com.tianma.xsmscode.feature.forward.ForwardDispatcher.Cfg() {
                                @Override
                                public String getStr(String key, String def) {
                                    return com.tianma.xsmscode.common.utils.ModulePrefs
                                            .getString(PKG, PF, key, def);
                                }

                                @Override
                                public boolean getBool(String key, boolean def) {
                                    return com.tianma.xsmscode.common.utils.ModulePrefs
                                            .getBoolean(PKG, PF, key, def);
                                }

                                @Override
                                public long getLong(String key, long def) {
                                    return com.tianma.xsmscode.common.utils.ModulePrefs
                                            .getLong(PKG, PF, key, def);
                                }
                            },
                            content, sender, body, code, time);

            // 记结果（短信详情弹窗用）
            com.tianma.xsmscode.feature.forward.ForwardResultStore.write(time, results);

            if (results.isEmpty()) {
                return RESULT_SKIP;
            }
            boolean anyOk = false;
            boolean anyMade = false;
            for (com.tianma.xsmscode.feature.forward.ForwardResultStore.ChannelResult r : results) {
                anyMade = true;
                if (r.ok) {
                    anyOk = true;
                    break;
                }
            }
            if (!anyMade) {
                return RESULT_SKIP;
            }
            return anyOk ? RESULT_SENT : RESULT_FAILED;
        } catch (Throwable t) {
            XLog.e("DirectForwarder error: %s", t);
            return RESULT_FAILED;
        }
    }

    // ==================== 工具 ====================

    /** 文案排版与 app 侧 WeComForwarder.buildContent 保持一致 */
    private static String buildContent(String sender, String body, String code, long time) {
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(body)) {
            sb.append(body.length() > 500 ? body.substring(0, 500) + "…" : body);
        } else if (!TextUtils.isEmpty(code)) {
            sb.append("验证码：").append(code);
        }
        sb.append("\n\n");
        sb.append("来源号码：").append(TextUtils.isEmpty(sender) ? "未知" : sender).append('\n');
        sb.append("发送时间：").append(
                new SimpleDateFormat("yyyy-M-d HH.mm", Locale.getDefault()).format(new Date(time)));
        return sb.toString();
    }


}
