package com.tianma.xsmscode.feature.forward.qqbot;

import android.text.TextUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Random;

import javax.net.ssl.SSLSocketFactory;

/**
 * 极简 WebSocket 客户端（纯 Java，无第三方依赖）。
 *
 * 只实现 QQ 机器人网关用得到的部分：
 *   - wss:// 握手（HTTP Upgrade）
 *   - 文本帧收发、掩码、分片重组
 *   - ping/pong、close
 *   - 遮断上限，防止恶意/异常超大帧拖垮内存
 *
 * 为什么不用 OkHttp：本项目刻意保持零第三方网络依赖，
 * 且构建环境（Termux + 腾讯镜像）首次拉新依赖存在不确定性。
 */
public final class MiniWebSocket {

    private static final int MAX_FRAME = 512 * 1024;   // 单帧上限 512KB
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    public interface Listener {
        /** 收到一条完整文本消息 */
        void onText(String text);

        void onClose(String reason);

        void onError(Throwable t);
    }

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private Thread reader;
    private volatile boolean running = false;
    private Listener listener;

    /** 心跳线程（2026-10-18）：QQ 网关要求按 heartbeat_interval 主动上报 op=1，
     *  否则连接约 41 秒后被服务端断开 —— 这也是"发了几十条消息都绑不上"的根因 */
    private java.util.Timer heartbeatTimer;
    private volatile long heartbeatSeq = 0;

    /** 阻塞完成握手，失败抛异常 */
    public void connect(String wssUrl, Listener l) throws IOException {
        this.listener = l;

        String url = wssUrl;
        boolean tls = true;
        if (url.startsWith("ws://")) {
            tls = false;
            url = url.substring(5);
        } else if (url.startsWith("wss://")) {
            url = url.substring(6);
        }

        String host;
        int port;
        String path = "/";
        int slash = url.indexOf('/');
        if (slash >= 0) {
            path = url.substring(slash);
            host = url.substring(0, slash);
        } else {
            host = url;
        }
        int colon = host.lastIndexOf(':');
        if (colon >= 0) {
            port = Integer.parseInt(host.substring(colon + 1));
            host = host.substring(0, colon);
        } else {
            port = tls ? 443 : 80;
        }

        Socket s = tls
                ? SSLSocketFactory.getDefault().createSocket()
                : new Socket();
        s.connect(new InetSocketAddress(host, port), 15000);
        s.setSoTimeout(0);   // 读由心跳/关闭控制，不设读超时

        this.socket = s;
        this.in = s.getInputStream();
        this.out = s.getOutputStream();

        // ---- 握手 ----
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        String key = android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP);

        String req = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + host + ":" + port + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "User-Agent: SmsCodeX/3.1\r\n"
                + "\r\n";
        out.write(req.getBytes(StandardCharsets.UTF_8));
        out.flush();

        // ---- 读响应头 ----
        StringBuilder head = new StringBuilder();
        int prev = -1, cur;
        while ((cur = in.read()) != -1) {
            head.append((char) cur);
            if (prev == '\r' && cur == '\n'
                    && head.length() > 4
                    && head.substring(head.length() - 4).equals("\r\n\r\n")) {
                break;
            }
            prev = cur;
        }
        String h = head.toString();
        if (!h.startsWith("HTTP/1.1 101") && !h.contains(" 101 ")) {
            throw new IOException("WebSocket 握手失败：" + firstLine(h));
        }

        running = true;
        reader = new Thread(this::readLoop, "minis-ws");
        reader.setDaemon(true);
        reader.start();
    }

    /**
     * 启动心跳（2026-10-18 新增）。
     *
     * <p>QQ 网关在 op=10 Hello 里下发 heartbeat_interval（单位毫秒），
     * 客户端必须按该间隔发送 {@code {"op":1,"d":<上次序号或null>}}，
     * 否则服务端判定连接失活并断开（实测约 41 秒）。
     *
     * <p>传信版本没有实现心跳，只在绑定窗口内被动收帧，
     * 短超时下勉强能收到，稍慢就断开 —— 属于移植时漏掉的一环。
     */
    public void startHeartbeat(long intervalMs) {
        stopHeartbeat();
        if (intervalMs <= 0) {
            intervalMs = 41250L;
        }
        heartbeatTimer = new java.util.Timer("minis-ws-hb", true);
        heartbeatTimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                if (!running) {
                    cancel();
                    return;
                }
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("op", 1);
                    if (heartbeatSeq > 0) {
                        d.put("d", heartbeatSeq);
                    } else {
                        d.put("d", org.json.JSONObject.NULL);
                    }
                    sendText(d.toString());
                } catch (Throwable ignored) {
                    // 发送失败由 readLoop 的异常路径处理
                }
            }
        }, intervalMs, intervalMs);
    }

    public void stopHeartbeat() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
    }

    /** 记录事件序号（服务端 s 字段），心跳时回填 */
    public void updateSeq(long seq) {
        this.heartbeatSeq = seq;
    }

    private static String firstLine(String s) {
        int i = s.indexOf("\r\n");
        return i > 0 ? s.substring(0, i) : s;
    }

    // ------------------------------------------------------------ 读

    /** 分片重组缓冲 */
    private final java.io.ByteArrayOutputStream frag = new java.io.ByteArrayOutputStream();
    private int fragOpcode = -1;

    private void readLoop() {
        try {
            while (running) {
                int b0 = in.read();
                if (b0 < 0) break;
                int b1 = in.read();
                if (b1 < 0) break;

                boolean fin = (b0 & 0x80) != 0;
                int opcode = b0 & 0x0F;
                boolean masked = (b1 & 0x80) != 0;
                long len = b1 & 0x7F;

                if (len == 126) {
                    len = ((long) readByte() << 8) | readByte();
                } else if (len == 127) {
                    len = 0;
                    for (int i = 0; i < 8; i++) len = (len << 8) | readByte();
                }
                if (len > MAX_FRAME) throw new IOException("帧过大：" + len);

                byte[] maskKey = new byte[4];
                if (masked) readFully(maskKey);

                byte[] payload = new byte[(int) len];
                readFully(payload);
                if (masked) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] ^= maskKey[i & 3];
                    }
                }

                switch (opcode) {
                    case 0x0: // continuation
                        frag.write(payload);
                        if (fin) {
                            deliver(fragOpcode, frag.toByteArray());
                            frag.reset();
                            fragOpcode = -1;
                        }
                        break;
                    case 0x1: // text
                    case 0x2: // binary
                        if (fin) {
                            deliver(opcode, payload);
                        } else {
                            frag.reset();
                            frag.write(payload);
                            fragOpcode = opcode;
                        }
                        break;
                    case 0x8: // close
                        running = false;
                        if (listener != null) listener.onClose("服务端关闭连接");
                        return;
                    case 0x9: // ping -> pong
                        writeFrame(0xA, payload);
                        break;
                    case 0xA: // pong，忽略
                        break;
                    default:
                        break;
                }
            }
            if (running && listener != null) listener.onClose("连接断开");
        } catch (Throwable t) {
            if (running && listener != null) listener.onError(t);
        } finally {
            running = false;
            closeQuietly();
        }
    }

    private void deliver(int opcode, byte[] payload) {
        if (listener == null) return;
        listener.onText(new String(payload, StandardCharsets.UTF_8));
    }

    private int readByte() throws IOException {
        int v = in.read();
        if (v < 0) throw new IOException("连接已关闭");
        return v & 0xFF;
    }

    private void readFully(byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) throw new IOException("连接已关闭");
            off += n;
        }
    }

    // ------------------------------------------------------------ 写

    private static final Random RND = new Random();

    public synchronized void sendText(String text) throws IOException {
        writeFrame(0x1, text.getBytes(StandardCharsets.UTF_8));
    }

    private synchronized void writeFrame(int opcode, byte[] payload) throws IOException {
        if (out == null) return;
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        buf.write(0x80 | opcode);   // FIN + opcode

        int len = payload.length;
        if (len < 126) {
            buf.write(0x80 | len);           // MASK + len
        } else if (len < 65536) {
            buf.write(0x80 | 126);
            buf.write((len >>> 8) & 0xFF);
            buf.write(len & 0xFF);
        } else {
            buf.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) buf.write((int) (((long) len >>> (8 * i)) & 0xFF));
        }

        // 客户端帧必须掩码
        byte[] mask = new byte[4];
        RND.nextBytes(mask);
        buf.write(mask, 0, 4);
        for (int i = 0; i < len; i++) {
            buf.write(payload[i] ^ mask[i & 3]);
        }
        out.write(buf.toByteArray());
        out.flush();
    }

    public void closeQuietly() {
        running = false;
        stopHeartbeat();
        try {
            if (socket != null) socket.close();
        } catch (Throwable ignored) {
        }
        socket = null;
        in = null;
        out = null;
    }

    public boolean isRunning() {
        return running;
    }
}
