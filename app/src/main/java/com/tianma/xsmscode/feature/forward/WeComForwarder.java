package com.tianma.xsmscode.feature.forward;

import android.text.TextUtils;

import com.tianma.xsmscode.common.utils.XLog;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 企业微信「自建应用」通道转发器（2026-09-15）。
 * <p>
 * 配置字段与信驿 Relay 的 WeworkAgent 通道对齐：corpID / agentID / secret / toUser。
 * 流程：gettoken 取 access_token（进程内缓存，提前 5 分钟过期）→ message/send 发文本消息；
 * errcode 40014/42001（token 失效）自动刷新重试一次。
 * 注意：secret/token 不写入日志，日志仅含 errcode/errmsg。
 */
public final class WeComForwarder {

    private static final String DEFAULT_API = "https://qyapi.weixin.qq.com";

    private static final Object TOKEN_LOCK = new Object();
    private static String sToken;
    private static long sTokenExpireAt;

    private WeComForwarder() {
    }

    /**
     * @param corpId  企业ID
     * @param agentId 应用 AgentId（纯数字）
     * @param secret  应用 Secret
     * @param toUser  接收人，多个用 | 分隔；空 = @all
     * @return true = 企微接口返回 errcode 0
     */
    public static boolean send(String corpId, String agentId, String secret, String toUser,
                               String sender, String body, String code, long time) {
        if (TextUtils.isEmpty(corpId) || TextUtils.isEmpty(agentId) || TextUtils.isEmpty(secret)) {
            return false;
        }
        String content = buildContent(sender, body, code, time);
        String touser = TextUtils.isEmpty(toUser) ? "@all" : toUser.trim();
        int agentid;
        try {
            agentid = Integer.parseInt(agentId.trim());
        } catch (NumberFormatException e) {
            XLog.e("WeComForwarder: agentid must be a number");
            return false;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String token = getToken(corpId, secret, attempt > 0);
                if (TextUtils.isEmpty(token)) {
                    return false;
                }
                JSONObject msg = new JSONObject();
                msg.put("touser", touser);
                msg.put("msgtype", "text");
                msg.put("agentid", agentid);
                JSONObject text = new JSONObject();
                text.put("content", content);
                msg.put("text", text);
                JSONObject r = new JSONObject(request(
                        DEFAULT_API + "/cgi-bin/message/send?access_token=" + token, msg.toString()));
                int errcode = r.optInt("errcode", -1);
                if (errcode == 0) {
                    return true;
                }
                XLog.e("WeComForwarder: send errcode=%d errmsg=%s", errcode, r.optString("errmsg"));
                if (errcode == 40014 || errcode == 42001) {
                    synchronized (TOKEN_LOCK) {
                        sToken = null;
                        sTokenExpireAt = 0;
                    }
                    continue;
                }
                return false;
            } catch (Throwable t) {
                XLog.e("WeComForwarder: send failed: %s", t);
                return false;
            }
        }
        return false;
    }

    private static String getToken(String corpId, String secret, boolean forceRefresh) {
        synchronized (TOKEN_LOCK) {
            if (!forceRefresh && sToken != null && System.currentTimeMillis() < sTokenExpireAt) {
                return sToken;
            }
        }
        try {
            JSONObject r = new JSONObject(request(
                    DEFAULT_API + "/cgi-bin/gettoken?corpid=" + corpId.trim()
                            + "&corpsecret=" + secret.trim(), null));
            int errcode = r.optInt("errcode", -1);
            if (errcode != 0) {
                XLog.e("WeComForwarder: gettoken errcode=%d errmsg=%s", errcode, r.optString("errmsg"));
                return null;
            }
            String token = r.getString("access_token");
            long expires = r.optLong("expires_in", 7200);
            synchronized (TOKEN_LOCK) {
                sToken = token;
                sTokenExpireAt = System.currentTimeMillis() + (expires - 300) * 1000L;
            }
            return token;
        } catch (Throwable t) {
            XLog.e("WeComForwarder: gettoken failed: %s", t);
            return null;
        }
    }

    private static String buildContent(String sender, String body, String code, long time) {
        // 排版与参考样例一致：短信原文 + 空行 + 来源三行（2026-09-15 用户定稿）
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(body)) {
            String safe = body.length() > 500 ? body.substring(0, 500) + "…" : body;
            sb.append(safe);
        } else if (!TextUtils.isEmpty(code)) {
            sb.append("验证码：").append(code);
        }
        sb.append("\n\n");
        sb.append("来源号码：").append(TextUtils.isEmpty(sender) ? "未知" : sender).append('\n');
        sb.append("来源手机：").append(android.os.Build.MANUFACTURER)
                .append(' ').append(android.os.Build.MODEL).append('\n');
        sb.append("发送时间：").append(
                new SimpleDateFormat("yyyy-M-d HH.mm", Locale.getDefault()).format(new Date(time)));
        return sb.toString();
    }

    private static String request(String urlStr, String json) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod(json == null ? "GET" : "POST");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            if (json != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
                OutputStream os = conn.getOutputStream();
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();
            }
            int code = conn.getResponseCode();
            String resp = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code != 200) {
                XLog.e("WeComForwarder: http=%d", code);
            }
            return resp == null ? "{}" : resp;
        } catch (Throwable t) {
            XLog.e("WeComForwarder: request failed: %s", t);
            return "{}";
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readStream(InputStream is) {
        if (is == null) {
            return "{}";
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            is.close();
            return bos.toString("UTF-8");
        } catch (Throwable t) {
            return "{}";
        }
    }
}
