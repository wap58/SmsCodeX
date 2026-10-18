package com.tianma.xsmscode.feature.forward;

import android.text.TextUtils;

import com.tianma.xsmscode.common.utils.XLog;

import java.util.ArrayList;
import java.util.List;

/**
 * 多通道发送器（2026-10-17 新增）。
 *
 * <p>把「配置读取」与「通道发送」解耦：
 * {@link #send} 只依赖一个极简的配置读取接口 {@link Cfg}，
 * app 进程传 SharedPreferences 实现，电话进程传 ModulePrefs 实现，
 * 两边共用同一套分发逻辑，避免像原来那样在两个文件里各写一遍 switch
 * （新增通道时容易只改一边）。
 *
 * <p>★ 发送策略（与传信一致）：
 * <ul>
 *   <li>逐通道<b>串行</b>发送 —— 手机上一个短信可能同时推 3~4 个通道，
 *       并发握手会让弱网下大量超时；串行总耗时仍在 1~3 秒量级</li>
 *   <li>单个通道失败<b>不影响</b>其他通道，全部结果都记录</li>
 *   <li>至少一个成功即视为整体成功</li>
 * </ul>
 */
public final class ForwardDispatcher {

    /** 配置读取抽象：与 SharedPreferences / ModulePrefs 都能对接 */
    public interface Cfg {
        String getStr(String key, String def);

        boolean getBool(String key, boolean def);

        long getLong(String key, long def);
    }

    private ForwardDispatcher() {
    }

    /**
     * 按配置逐通道发送。
     *
     * @param cfg      配置来源
     * @param content  已排版好的文案
     * @param sender   号码（企业微信应用通道需要原始字段）
     * @param body     短信正文
     * @param code     验证码
     * @param time     短信时间
     * @return 逐通道结果；空表示没有可用通道
     */
    public static List<ForwardResultStore.ChannelResult> send(
            Cfg cfg, String content,
            String sender, String body, String code, long time) {

        List<ForwardResultStore.ChannelResult> results = new ArrayList<>();

        String multi = cfg.getStr(PrefKeys.CHANNELS, "");
        String single = cfg.getStr(PrefKeys.CHANNEL_TYPE, "wecom_agent");
        List<String> channels = ForwardChannels.effective(multi, single);
        if (channels.isEmpty()) {
            return results;
        }

        for (String id : channels) {
            ForwardResultStore.ChannelResult r = sendOne(cfg, id, content,
                    sender, body, code, time);
            if (r != null) {
                results.add(r);
                if (!r.ok) {
                    XLog.w("ForwardDispatcher: %s failed (%s)", id, r.reason);
                }
            }
        }
        return results;
    }

    /** 单个通道；未配置时返回 null（不记入结果，避免误报失败） */
    private static ForwardResultStore.ChannelResult sendOne(
            Cfg cfg, String id, String content,
            String sender, String body, String code, long time) {
        try {
            switch (id == null ? "" : id) {
                case "wecom_robot": {
                    String webhook = cfg.getStr(PrefKeys.WECOM_ROBOT_WEBHOOK, "");
                    if (TextUtils.isEmpty(webhook)) {
                        return null;
                    }
                    return new ForwardResultStore.ChannelResult(id,
                            ChannelSender.sendWecomRobot(webhook, content), "send_failed");
                }
                case "dingtalk": {
                    String webhook = cfg.getStr(PrefKeys.DINGTALK_WEBHOOK, "");
                    if (TextUtils.isEmpty(webhook)) {
                        return null;
                    }
                    String secret = cfg.getStr(PrefKeys.DINGTALK_SECRET, "");
                    return new ForwardResultStore.ChannelResult(id,
                            ChannelSender.sendDingtalk(webhook, secret, content), "send_failed");
                }
                case "feishu": {
                    String webhook = cfg.getStr(PrefKeys.FEISHU_WEBHOOK, "");
                    if (TextUtils.isEmpty(webhook)) {
                        return null;
                    }
                    return new ForwardResultStore.ChannelResult(id,
                            ChannelSender.sendFeishu(webhook, content), "send_failed");
                }
                case "xizhi": {
                    String key = cfg.getStr(PrefKeys.XIZHI_KEY, "");
                    if (TextUtils.isEmpty(key)) {
                        return null;
                    }
                    return new ForwardResultStore.ChannelResult(id,
                            ChannelSender.sendXizhi(key, content), "send_failed");
                }
                case "pushplus": {
                    String token = cfg.getStr(PrefKeys.PUSHPLUS_TOKEN, "");
                    if (TextUtils.isEmpty(token)) {
                        return null;
                    }
                    return new ForwardResultStore.ChannelResult(id,
                            ChannelSender.sendPushplus(token, content), "send_failed");
                }
                case "qqbot": {
                    String appId = cfg.getStr(PrefKeys.QQBOT_APPID, "");
                    String secret = cfg.getStr(PrefKeys.QQBOT_SECRET, "");
                    String openId = cfg.getStr(PrefKeys.QQBOT_OPENID, "");
                    if (TextUtils.isEmpty(appId) || TextUtils.isEmpty(secret)
                            || TextUtils.isEmpty(openId)) {
                        return null;
                    }
                    boolean group = cfg.getBool(PrefKeys.QQBOT_IS_GROUP, false);
                    String cached = cfg.getStr(PrefKeys.QQBOT_TOKEN, "");
                    long expire = cfg.getLong(PrefKeys.QQBOT_TOKEN_EXPIRE, 0L);
                    if (expire <= 0 || System.currentTimeMillis() >= expire) {
                        cached = "";
                    }
                    com.tianma.xsmscode.feature.forward.qqbot.QQBotApi.SendResult sr =
                            com.tianma.xsmscode.feature.forward.qqbot.QQBotApi.sendWithRetry(
                                    appId, secret, cached, openId, group, content);
                    if (!sr.ok) {
                        // 记进日志便于诊断（HTTP 0 时 sr.reason() 会带出底层异常）
                        XLog.w("QQBot send failed: %s", sr.reason());
                    }
                    return new ForwardResultStore.ChannelResult(id, sr.ok,
                            sr.ok ? null : sr.reason());
                }
                case "cmcc": {
                    String key = cfg.getStr(PrefKeys.CMCC_API_KEY, "");
                    if (TextUtils.isEmpty(key)) {
                        return null;
                    }
                    String err = CmccSender.send(key, content);
                    return new ForwardResultStore.ChannelResult(id, err == null, err);
                }
                case "wecom_agent":
                default: {
                    String corpId = cfg.getStr(PrefKeys.WECOM_CORPID, "");
                    String agentId = cfg.getStr(PrefKeys.WECOM_AGENTID, "");
                    String secret = cfg.getStr(PrefKeys.WECOM_SECRET, "");
                    if (TextUtils.isEmpty(corpId) || TextUtils.isEmpty(agentId)
                            || TextUtils.isEmpty(secret)) {
                        return null;
                    }
                    String toUser = cfg.getStr(PrefKeys.WECOM_TOUSER, "");
                    boolean ok = WeComForwarder.send(corpId, agentId, secret, toUser,
                            sender, body, code, time);
                    return new ForwardResultStore.ChannelResult("wecom_agent", ok, "send_failed");
                }
            }
        } catch (Throwable t) {
            XLog.e("ForwardDispatcher: %s threw %s", id, t);
            return new ForwardResultStore.ChannelResult(id, false,
                    t.getClass().getSimpleName() + "：" + t.getMessage());
        }
    }

    /** 偏好键名（避免两个实现各自拼字符串写错） */
    public static final class PrefKeys {
        public static final String CHANNELS = "pref_forward_channels";
        public static final String CHANNEL_TYPE = "pref_forward_channel_type";
        public static final String WECOM_CORPID = "pref_forward_wecom_corpid";
        public static final String WECOM_AGENTID = "pref_forward_wecom_agentid";
        public static final String WECOM_SECRET = "pref_forward_wecom_secret";
        public static final String WECOM_TOUSER = "pref_forward_wecom_touser";
        public static final String WECOM_ROBOT_WEBHOOK = "pref_forward_wecom_robot_webhook";
        public static final String DINGTALK_WEBHOOK = "pref_forward_dingtalk_webhook";
        public static final String DINGTALK_SECRET = "pref_forward_dingtalk_secret";
        public static final String FEISHU_WEBHOOK = "pref_forward_feishu_webhook";
        public static final String XIZHI_KEY = "pref_forward_xizhi_key";
        public static final String PUSHPLUS_TOKEN = "pref_forward_pushplus_token";
        public static final String QQBOT_APPID = "pref_forward_qqbot_appid";
        public static final String QQBOT_SECRET = "pref_forward_qqbot_secret";
        public static final String QQBOT_OPENID = "pref_forward_qqbot_openid";
        public static final String QQBOT_IS_GROUP = "pref_forward_qqbot_is_group";
        public static final String QQBOT_TOKEN = "pref_forward_qqbot_token";
        public static final String QQBOT_TOKEN_EXPIRE = "pref_forward_qqbot_token_expire";
        public static final String CMCC_API_KEY = "pref_forward_cmcc_api_key";

        private PrefKeys() {
        }
    }
}
