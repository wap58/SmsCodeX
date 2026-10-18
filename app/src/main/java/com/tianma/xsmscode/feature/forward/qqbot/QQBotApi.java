package com.tianma.xsmscode.feature.forward.qqbot;

import android.text.TextUtils;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * QQ 官方开放平台机器人 REST 客户端（2026-10-17 自「传信」移植）。
 *
 * <p>端点（与官方文档一致）：
 * <pre>
 *   POST https://bots.qq.com/app/getAppAccessToken              取 access_token
 *   POST https://api.sgroup.qq.com/v2/users/{openid}/messages   C2C 私聊
 *   POST https://api.sgroup.qq.com/v2/groups/{gid}/messages     群聊
 *   GET  https://api.sgroup.qq.com/gateway                      取 WebSocket 网关
 * </pre>
 *
 * <p>鉴权头：{@code Authorization: QQBot <access_token>}
 *
 * <p>★ 成功判据：QQ 平台失败时同样返回 HTTP 200，必须看 body 里的 {@code code}。
 *
 * <p>★ 超时（2026-10-18）：电话进程（radio uid）在息屏/弱网下建连较慢，
 * 原来 8 秒偏短，实测出现过 HTTP 0（连接层失败）。改为 15 秒，
 * 并对「取 token」与「发消息」各做一次重试。
 */
public final class QQBotApi {

    private static final String API_BASE = "https://api.sgroup.qq.com";
    private static final String TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken";

    /** msg_type：0=文本 */
    private static final int MSG_TEXT = 0;

    /** 连接/读取超时（2026-10-18：8s → 15s） */
    private static final int TIMEOUT_MS = 15000;

    private QQBotApi() {
    }

    // ------------------------------------------------------------ 通用结果

    public static class Resp {
        public int httpCode;
        public String body = "";
        public String error = "";
        public JSONObject json;

        public boolean httpOk() {
            return httpCode >= 200 && httpCode < 300;
        }

        public String failReason() {
            if (httpCode == 0) {
                return TextUtils.isEmpty(error) ? "无法连接（网络不可达）" : "无法连接：" + error;
            }
            String b = body == null ? "" : body.replaceAll("\\s+", " ").trim();
            if (b.length() > 140) {
                b = b.substring(0, 140) + "…";
            }
            return "HTTP " + httpCode + (b.isEmpty() ? "" : " " + b);
        }
    }

    private static Resp request(String method, String url, String body, String auth) {
        Resp r = new Resp();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            // ★★ 顺序很重要（2026-10-18 修）：所有 setRequestProperty 必须在
            //    getOutputStream() / getResponseCode() 之前完成。
            //    写出 body 会隐式建立连接，之后再设头会抛
            //    IllegalStateException: Cannot set request property after connection is made
            conn.setRequestProperty("User-Agent", "SmsCodeX/3.1");
            if (!TextUtils.isEmpty(auth)) {
                conn.setRequestProperty("Authorization", auth);
            }
            if (body != null) {
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                byte[] bs = body.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(bs.length);
                OutputStream os = conn.getOutputStream();
                os.write(bs);
                os.flush();
                os.close();
            }
            r.httpCode = conn.getResponseCode();
            InputStream is = (r.httpCode >= 200 && r.httpCode < 400)
                    ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                is.close();
                r.body = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            }
            try {
                r.json = new JSONObject(r.body);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            r.error = t.getClass().getSimpleName() + "：" + t.getMessage();
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
        return r;
    }

    // ------------------------------------------------------------ 取 token

    public static class Token {
        public String accessToken;
        public int expiresIn = 7200;
        public String error;
    }

    /** appId + clientSecret 换 access_token，有效期通常 7200 秒。失败自动重试一次。 */
    public static Token getAccessToken(String appId, String clientSecret) {
        Token last = getAccessTokenOnce(appId, clientSecret);
        if (!TextUtils.isEmpty(last.accessToken)) {
            return last;
        }
        // 连接层失败（HTTP 0）时重试一次，规避息屏/弱网首次建连失败
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        }
        Token retry = getAccessTokenOnce(appId, clientSecret);
        return TextUtils.isEmpty(retry.accessToken) ? last : retry;
    }

    private static Token getAccessTokenOnce(String appId, String clientSecret) {
        Token t = new Token();
        try {
            JSONObject b = new JSONObject();
            b.put("appId", appId == null ? "" : appId.trim());
            b.put("clientSecret", clientSecret == null ? "" : clientSecret.trim());
            Resp r = request("POST", TOKEN_URL, b.toString(), null);
            if (!r.httpOk() || r.json == null) {
                t.error = r.failReason();
                return t;
            }
            t.accessToken = r.json.optString("access_token", "");
            t.expiresIn = r.json.optInt("expires_in", 7200);
            if (TextUtils.isEmpty(t.accessToken)) {
                t.error = "未返回 access_token：" + r.body;
            }
        } catch (Throwable e) {
            t.error = e.getMessage();
        }
        return t;
    }

    // ------------------------------------------------------------ 发送

    public static class SendResult {
        public boolean ok;
        public int httpCode;
        public String errmsg = "";
        public String raw;

        public String reason() {
            if (ok) {
                return null;
            }
            String m = TextUtils.isEmpty(errmsg) ? raw : errmsg;
            return "HTTP " + httpCode + (TextUtils.isEmpty(m) ? "" : " " + m);
        }
    }

    private static SendResult post(String url, String token, String content) {
        SendResult out = new SendResult();
        try {
            JSONObject b = new JSONObject();
            b.put("content", content);
            b.put("msg_type", MSG_TEXT);
            Resp r = request("POST", url, b.toString(), "QQBot " + token);
            out.httpCode = r.httpCode;
            out.raw = r.body;
            if (r.json != null) {
                out.errmsg = r.json.optString("message", "");
            }
            // ★ 不能只看 HTTP 状态：失败时平台也返回 200，靠 body 里的 code 区分
            boolean bizErr = r.json != null && r.json.has("code")
                    && r.json.optInt("code", 0) != 0;
            out.ok = r.httpOk() && !bizErr;
            if (out.ok) {
                return out;
            }
            // ★ httpCode == 0 表示连接层就失败了（DNS/超时/TLS），
            //   此时 r.error 才是真正原因，必须透出，否则只看到"HTTP 0"
            if (out.httpCode == 0) {
                out.errmsg = TextUtils.isEmpty(r.error)
                        ? "无法连接（网络不可达）" : r.error;
                return out;
            }
            if (r.json != null) {
                int code = r.json.optInt("code", 0);
                if (code == 11244) {
                    out.errmsg = "机器人不在该群/未添加该用户（11244）";
                } else if (code == 22009) {
                    out.errmsg = "主动消息额度不足（22009），需用户先给机器人发过消息";
                } else if (code == 11298) {
                    out.errmsg = "无主动消息推送权限/额度（11298）";
                } else if (code == 40034) {
                    out.errmsg = "access_token 无效或已过期（40034）";
                }
            }
            return out;
        } catch (Throwable t) {
            out.errmsg = t.getClass().getSimpleName() + "：" + t.getMessage();
            return out;
        }
    }

    /** C2C 私聊（主动消息） */
    public static SendResult sendC2C(String token, String openid, String content) {
        return post(API_BASE + "/v2/users/" + openid + "/messages", token, content);
    }

    /** 群聊（主动消息） */
    public static SendResult sendGroup(String token, String groupOpenid, String content) {
        return post(API_BASE + "/v2/groups/" + groupOpenid + "/messages", token, content);
    }

    // ------------------------------------------------------------ 网关

    /** 取 WebSocket 网关地址（绑定阶段接收入站消息、抓 openid 用） */
    public static String getGatewayUrl(String token) {
        Resp r = request("GET", API_BASE + "/gateway", null, "QQBot " + token);
        if (r.json == null) {
            return null;
        }
        String url = r.json.optString("url", "");
        return TextUtils.isEmpty(url) ? null : url;
    }

    /**
     * 发送（带 token 失效重试与连接层重试）。
     *
     * <p>★ 2026-10-18：电话进程无法把新 token 写回配置，所以传进来的 cachedToken
     * 往往是过期快照；这里对「连接层失败（HTTP 0）」也重试一次，
     * 并且只要 cachedToken 为空就现取，避免拿到过期 token 白跑一趟。
     */
    public static SendResult sendWithRetry(String appId, String secret,
                                           String cachedToken, String openid, boolean group,
                                           String content) {
        String token = cachedToken;
        if (TextUtils.isEmpty(token)) {
            Token t = getAccessToken(appId, secret);
            if (TextUtils.isEmpty(t.accessToken)) {
                SendResult r = new SendResult();
                r.errmsg = "取 token 失败：" + t.error;
                return r;
            }
            token = t.accessToken;
        }

        SendResult sr = group ? sendGroup(token, openid, content) : sendC2C(token, openid, content);
        if (sr.ok) {
            return sr;
        }

        // ① token 失效 → 重新取 token 再发一次
        String reason = sr.reason();
        if (reason != null && reason.contains("40034")) {
            Token t2 = getAccessToken(appId, secret);
            if (!TextUtils.isEmpty(t2.accessToken)) {
                SendResult retry = group ? sendGroup(t2.accessToken, openid, content)
                        : sendC2C(t2.accessToken, openid, content);
                if (retry.ok) {
                    return retry;
                }
                sr = retry;
            }
        }

        // ② 连接层失败（HTTP 0）→ 稍等重试一次（弱网/息屏常见）
        if (sr.httpCode == 0) {
            try {
                Thread.sleep(800);
            } catch (InterruptedException ignored) {
            }
            SendResult retry = group ? sendGroup(token, openid, content)
                    : sendC2C(token, openid, content);
            if (retry.ok) {
                return retry;
            }
            return retry;
        }
        return sr;
    }
}
