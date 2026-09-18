package com.tianma.xsmscode.xp.hook.forward;

import android.text.TextUtils;

import com.tianma.xsmscode.common.constant.PrefConst;
import com.tianma.xsmscode.common.utils.XLog;

import org.json.JSONObject;
import de.robv.android.xposed.XSharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
            String channel = com.tianma.xsmscode.common.utils.ModulePrefs.getString(PKG, PF, PrefConst.KEY_FORWARD_CHANNEL_TYPE, "wecom_agent");
            String content = buildContent(sender, body, code, time);
            switch (channel == null ? "wecom_agent" : channel) {
                case "wecom_robot": {
                    String webhook = com.tianma.xsmscode.common.utils.ModulePrefs.getString(PKG, PF, PrefConst.KEY_FORWARD_WECOM_ROBOT_WEBHOOK, "");
                    if (TextUtils.isEmpty(webhook)) return RESULT_SKIP;
                    return sendWecomRobot(webhook, content) ? RESULT_SENT : RESULT_FAILED;
                }
                case "dingtalk": {
                    String webhook = com.tianma.xsmscode.common.utils.ModulePrefs.getString(PKG, PF, PrefConst.KEY_FORWARD_DINGTALK_WEBHOOK, "");
                    if (TextUtils.isEmpty(webhook)) return RESULT_SKIP;
                    String secret = com.tianma.xsmscode.common.utils.ModulePrefs.getString(PKG, PF, PrefConst.KEY_FORWARD_DINGTALK_SECRET, "");
                    return sendDingtalk(webhook, secret, content) ? RESULT_SENT : RESULT_FAILED;
                }
                case "feishu": {
                    String webhook = com.tianma.xsmscode.common.utils.ModulePrefs.getString(PKG, PF, PrefConst.KEY_FORWARD_FEISHU_WEBHOOK, "");
                    if (TextUtils.isEmpty(webhook)) return RESULT_SKIP;
                    return sendFeishu(webhook, content) ? RESULT_SENT : RESULT_FAILED;
                }
                case "xizhi": {
                    String key = com.tianma.xsmscode.common.utils.ModulePrefs.getString(PKG, PF, PrefConst.KEY_FORWARD_XIZHI_KEY, "");
                    if (TextUtils.isEmpty(key)) return RESULT_SKIP;
                    return sendXizhi(key, content) ? RESULT_SENT : RESULT_FAILED;
                }
                case "wecom_agent":
                default: {
                    String corpId = com.tianma.xsmscode.common.utils.ModulePrefs.getString(PKG, PF, PrefConst.KEY_FORWARD_WECOM_CORPID, "");
                    String agentId = com.tianma.xsmscode.common.utils.ModulePrefs.getString(PKG, PF, PrefConst.KEY_FORWARD_WECOM_AGENTID, "");
                    String secret = com.tianma.xsmscode.common.utils.ModulePrefs.getString(PKG, PF, PrefConst.KEY_FORWARD_WECOM_SECRET, "");
                    if (TextUtils.isEmpty(corpId) || TextUtils.isEmpty(agentId) || TextUtils.isEmpty(secret)) {
                        return RESULT_SKIP;
                    }
                    String toUser = com.tianma.xsmscode.common.utils.ModulePrefs.getString(PKG, PF, PrefConst.KEY_FORWARD_WECOM_TOUSER, "");
                    return sendWecomAgent(corpId, agentId, secret, toUser, content)
                            ? RESULT_SENT : RESULT_FAILED;
                }
            }
        } catch (Throwable t) {
            XLog.e("DirectForwarder error: %s", t);
            return RESULT_FAILED;
        }
    }

    // ==================== 各通道发送（与 app 侧 ChannelSender/WeComForwarder 同协议） ====================

    private static boolean sendWecomRobot(String webhook, String content) throws Exception {
        JSONObject text = new JSONObject();
        text.put("content", content);
        JSONObject body = new JSONObject();
        body.put("msgtype", "text");
        body.put("text", text);
        JSONObject r = new JSONObject(post(webhook.trim(), body.toString()));
        if (!r.has("errcode")) {
            return true;
        }
        int errcode = r.optInt("errcode", -1);
        if (errcode != 0) {
            XLog.w("WecomRobot(direct): errcode=%d errmsg=%s", errcode, r.optString("errmsg"));
            return false;
        }
        return true;
    }

    private static boolean sendDingtalk(String webhook, String secret, String content) throws Exception {
        String url = webhook.trim();
        if (!TextUtils.isEmpty(secret)) {
            long timestamp = System.currentTimeMillis();
            String sign = dingSign(timestamp, secret);
            if (TextUtils.isEmpty(sign)) {
                return false;
            }
            url = url + (url.contains("?") ? "&" : "?") + "timestamp=" + timestamp + "&sign=" + sign;
        }
        JSONObject text = new JSONObject();
        text.put("content", content);
        JSONObject body = new JSONObject();
        body.put("msgtype", "text");
        body.put("text", text);
        JSONObject r = new JSONObject(post(url, body.toString()));
        int errcode = r.optInt("errcode", -1);
        if (errcode != 0) {
            XLog.w("Dingtalk(direct): errcode=%d errmsg=%s", errcode, r.optString("errmsg"));
            return false;
        }
        return true;
    }

    private static boolean sendFeishu(String webhook, String content) throws Exception {
        JSONObject text = new JSONObject();
        text.put("text", content);
        JSONObject body = new JSONObject();
        body.put("msg_type", "text");
        body.put("content", text);
        JSONObject r = new JSONObject(post(webhook.trim(), body.toString()));
        int code = r.optInt("code", r.optInt("StatusCode", -1));
        if (code != 0) {
            XLog.w("Feishu(direct): code=%d msg=%s", code, r.optString("msg", r.optString("StatusMessage")));
            return false;
        }
        return true;
    }

    private static boolean sendXizhi(String key, String content) throws Exception {
        String title = URLEncoder.encode("验证码提醒", "UTF-8");
        String enc = URLEncoder.encode(content, "UTF-8");
        String url = "https://xizhi.qqoq.net/" + key.trim() + ".send?title=" + title + "&content=" + enc;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(5000);
        int status = conn.getResponseCode();
        String resp = readAll(status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();
        if (status != 200) {
            XLog.w("Xizhi(direct): http=%d", status);
            return false;
        }
        try {
            JSONObject j = new JSONObject(resp);
            if (j.has("code") && j.optInt("code", -1) != 200) {
                XLog.w("Xizhi(direct): rejected: %s", resp.length() > 200 ? resp.substring(0, 200) : resp);
                return false;
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    private static boolean sendWecomAgent(String corpId, String agentId, String secret, String toUser, String content)
            throws Exception {
        String touser = TextUtils.isEmpty(toUser) ? "@all" : toUser.trim();
        int agentid;
        try {
            agentid = Integer.parseInt(agentId.trim());
        } catch (NumberFormatException e) {
            XLog.e("WecomAgent(direct): agentid must be a number");
            return false;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            String token = getWecomToken(corpId, secret);
            if (TextUtils.isEmpty(token)) {
                return false;
            }
            JSONObject text = new JSONObject();
            text.put("content", content);
            JSONObject msg = new JSONObject();
            msg.put("touser", touser);
            msg.put("msgtype", "text");
            msg.put("agentid", agentid);
            msg.put("text", text);
            JSONObject r = new JSONObject(
                    post("https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=" + token,
                            msg.toString()));
            int errcode = r.optInt("errcode", -1);
            if (errcode == 0) {
                return true;
            }
            XLog.w("WecomAgent(direct): errcode=%d errmsg=%s", errcode, r.optString("errmsg"));
            if (errcode == 40014 || errcode == 42001) {
                continue;
            }
            return false;
        }
        return false;
    }

    private static String getWecomToken(String corpId, String secret) {
        try {
            String resp = get("https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid="
                    + corpId.trim() + "&corpsecret=" + secret.trim());
            JSONObject r = new JSONObject(resp);
            if (r.optInt("errcode", -1) != 0) {
                XLog.w("WecomAgent(direct): gettoken errcode=%d errmsg=%s",
                        r.optInt("errcode", -1), r.optString("errmsg"));
                return null;
            }
            return r.getString("access_token");
        } catch (Throwable t) {
            XLog.e("WecomAgent(direct): gettoken failed %s", t);
            return null;
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

    private static String dingSign(long timestamp, String secret) {
        try {
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return URLEncoder.encode(Base64.getEncoder().encodeToString(raw), "UTF-8");
        } catch (Throwable t) {
            XLog.e("Dingtalk(direct) sign failed %s", t);
            return null;
        }
    }

    private static String post(String urlStr, String jsonBody) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
        int status = conn.getResponseCode();
        String resp = readAll(status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();
        return resp;
    }

    private static String get(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(5000);
        int status = conn.getResponseCode();
        String resp = readAll(status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();
        return resp;
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) {
            return "{}";
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = is.read(chunk)) > 0) {
            buf.write(chunk, 0, n);
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }
}
