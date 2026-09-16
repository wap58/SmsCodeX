package com.tianma.xsmscode.feature.forward;

import android.text.TextUtils;

import com.tianma.xsmscode.common.utils.XLog;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 多通道 Webhook 转发器（2026-09-16，用户指定新增通道）：
 * 企业微信群机器人 / 钉钉机器人（支持加签）/ 飞书机器人。
 * 文案统一复用 {@link WeComForwarder#buildContent} 排版。
 */
public final class ChannelSender {

    private ChannelSender() {
    }

    public static boolean sendWecomRobot(String webhook, String content) {
        if (TextUtils.isEmpty(webhook)) {
            return false;
        }
        JSONObject body = jsonText("msgtype", "text", "content", content);
        try {
            JSONObject r = new JSONObject(post(webhook.trim(), body.toString()));
            // 企微机器人返回 errcode；部分网关直接返回空体
            if (!r.has("errcode")) {
                return true;
            }
            int errcode = r.optInt("errcode", -1);
            if (errcode != 0) {
                XLog.e("WecomRobot: errcode=%d errmsg=%s", errcode, r.optString("errmsg"));
                return false;
            }
            return true;
        } catch (Throwable t) {
            XLog.e("WecomRobot: failed %s", t);
            return false;
        }
    }

    public static boolean sendDingtalk(String webhook, String secret, String content) {
        if (TextUtils.isEmpty(webhook)) {
            return false;
        }
        String url = webhook.trim();
        if (!TextUtils.isEmpty(secret)) {
            long timestamp = System.currentTimeMillis();
            String sign = dingSign(timestamp, secret);
            if (TextUtils.isEmpty(sign)) {
                return false;
            }
            url = url + (url.contains("?") ? "&" : "?") + "timestamp=" + timestamp + "&sign=" + sign;
        }
        JSONObject body = jsonText("msgtype", "text", "content", content);
        try {
            JSONObject r = new JSONObject(post(url, body.toString()));
            int errcode = r.optInt("errcode", -1);
            if (errcode != 0) {
                XLog.e("Dingtalk: errcode=%d errmsg=%s", errcode, r.optString("errmsg"));
                return false;
            }
            return true;
        } catch (Throwable t) {
            XLog.e("Dingtalk: failed %s", t);
            return false;
        }
    }

    public static boolean sendFeishu(String webhook, String content) {
        if (TextUtils.isEmpty(webhook)) {
            return false;
        }
        JSONObject text = new JSONObject();
        try {
            text.put("text", content);
        } catch (Exception ignored) {
        }
        JSONObject body = new JSONObject();
        try {
            body.put("msg_type", "text");
            body.put("content", text);
        } catch (Exception ignored) {
        }
        try {
            JSONObject r = new JSONObject(post(webhook.trim(), body.toString()));
            int code = r.optInt("code", r.optInt("StatusCode", -1));
            if (code != 0) {
                XLog.e("Feishu: code=%d msg=%s", code, r.optString("msg", r.optString("StatusMessage")));
                return false;
            }
            return true;
        } catch (Throwable t) {
            XLog.e("Feishu: failed %s", t);
            return false;
        }
    }


    /** 钉钉加签：HmacSHA256(timestamp + "\n" + secret, secret) → Base64 → URL 编码 */
    private static String dingSign(long timestamp, String secret) {
        try {
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return URLEncoder.encode(Base64.getEncoder().encodeToString(raw), "UTF-8");
        } catch (Throwable t) {
            XLog.e("Dingtalk sign failed %s", t);
            return null;
        }
    }

    private static JSONObject jsonText(String k1, String v1, String k2, String v2) {
        JSONObject inner = new JSONObject();
        JSONObject body = new JSONObject();
        try {
            inner.put(k2, v2);
            body.put(k1, v1);
            body.put(k2.equals("content") ? "text" : k2, inner);
        } catch (Exception ignored) {
        }
        return body;
    }

    private static String post(String urlStr, String jsonBody) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
            byte[] data = jsonBody.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data);
                os.flush();
            }
            int status = conn.getResponseCode();
            InputStream is = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
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
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
