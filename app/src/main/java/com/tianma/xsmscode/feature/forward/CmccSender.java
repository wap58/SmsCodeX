package com.tianma.xsmscode.feature.forward;

import android.text.TextUtils;

import com.tianma.xsmscode.common.utils.XLog;
import com.tianma.xsmscode.feature.forward.ws.SimpleWsClient;

import org.json.JSONObject;

/**
 * 中国移动「新消息」（5G 消息 / RCS）发送器（2026-10-17 自「传信」移植）。
 *
 * <p>★ 前提（缺一不可）：
 * <ol>
 *   <li>中国移动用户（联通/电信不支持）</li>
 *   <li>短信里已开通 5G 消息</li>
 *   <li>已在 5G 消息里搜索并绑定应用号（拿到 {@code ak_xxx} 形式的 API Key）</li>
 * </ol>
 *
 * <p>★ 协议是 WebSocket + JSON 帧，不是 HTTP：
 * <pre>
 *   地址 wss://5gvas01.cmicmaap.com/gtw-ai/openclaw/ws/msg
 *   握手带 HTTP 头 X-API-Key
 *   认证帧 {"type":"auth","apiKey":..,"version":"2.0"} → {"type":"auth_ok"}
 *   发送帧 {"type":"send","apiKey":..,"to":&lt;apiKey&gt;,"content":..,"messageId":..}
 *   to 填 API Key 本身（服务端按 Key 路由到绑定机主）
 * </pre>
 *
 * <p>★ 连接策略：临时连接，发完即断——无需保活，延迟约 1~2 秒。
 */
public final class CmccSender {

    private static final String WS_URL = "wss://5gvas01.cmicmaap.com/gtw-ai/openclaw/ws/msg";
    private static final String VERSION = "2.0";

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int AUTH_TIMEOUT_MS = 10000;

    private CmccSender() {
    }

    /**
     * @return null 表示已发送（服务端不回 ACK，与官方插件语义一致）；否则为失败原因
     */
    public static String send(String apiKey, String content) {
        if (TextUtils.isEmpty(apiKey)) {
            return "未配置 API Key";
        }
        apiKey = apiKey.trim();

        SimpleWsClient ws = null;
        try {
            // ① 连接（握手带 X-API-Key）
            ws = SimpleWsClient.connect(WS_URL, apiKey, CONNECT_TIMEOUT_MS);

            // ② 认证
            JSONObject auth = new JSONObject();
            auth.put("type", "auth");
            auth.put("apiKey", apiKey);
            auth.put("version", VERSION);
            ws.sendText(auth.toString());

            long deadline = System.currentTimeMillis() + AUTH_TIMEOUT_MS;
            boolean authed = false;
            while (System.currentTimeMillis() < deadline) {
                String msg = ws.readText();
                if (msg == null) {
                    continue;
                }
                JSONObject j = new JSONObject(msg);
                String type = j.optString("type", "");
                if ("auth_ok".equals(type)) {
                    authed = true;
                    break;
                }
                if ("auth_failed".equals(type)) {
                    return "API Key 无效或未绑定应用号";
                }
            }
            if (!authed) {
                return "认证超时，请检查网络或 5G 消息是否已开通";
            }

            // ③ 发送（目标即 apiKey 本身）
            String msgId = "msg_" + System.currentTimeMillis() + "_"
                    + Integer.toHexString(new java.util.Random().nextInt(0xFFFF));
            JSONObject send = new JSONObject();
            send.put("type", "send");
            send.put("apiKey", apiKey);
            send.put("to", apiKey);
            send.put("content", content);
            send.put("messageId", msgId);
            ws.sendText(send.toString());

            XLog.i("Cmcc: sent (msgId=%s, len=%d)", msgId, content.length());
            return null;

        } catch (Throwable t) {
            String m = t.getMessage();
            XLog.w("Cmcc: send failed %s", m);
            if (m != null && (m.contains("握手失败") || m.contains("101"))) {
                return "连接被拒，请确认已开通 5G 消息";
            }
            return "发送失败：" + (m == null ? t.getClass().getSimpleName() : m);
        } finally {
            if (ws != null) {
                ws.close();
            }
        }
    }
}
