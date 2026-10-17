package com.tianma.xsmscode.feature.forward.ws;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Random;
import javax.net.ssl.SSLSocketFactory;

/**
 * 极简 WebSocket 客户端（客户端→服务端单向文本帧）。
 *
 * ★ 为什么手写而不引第三方库：
 *   项目一贯零依赖；这里只需要「握手 + 发文本帧 + 收文本帧 + 关闭」，
 *   协议简单，约 200 行即可覆盖。协议已在沙箱用纯脚本验证通过。
 *
 * 仅支持：RFC6455 基础 —— TLS、文本帧、mask、分片收包、关闭。
 * 不支持：二进制帧、扩展协商（不需要）。
 */
public final class SimpleWsClient {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final Random rnd = new SecureRandom();

    public static final int OP_TEXT = 0x1;
    public static final int OP_CLOSE = 0x8;
    public static final int OP_PING = 0x9;
    public static final int OP_PONG = 0xA;

    private SimpleWsClient(Socket s) throws IOException {
        this.socket = s;
        this.in = new BufferedInputStream(s.getInputStream());
        this.out = s.getOutputStream();
    }

    /**
     * 建立连接并完成握手。
     *
     * @param url     形如 wss://host/path
     * @param apiKey  写入 X-API-Key 头（可空）
     * @param timeoutMs 连接与读超时
     */
    public static SimpleWsClient connect(String url, String apiKey, int timeoutMs)
            throws IOException {
        URI uri = URI.create(url.trim());
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();
        boolean tls = "wss".equalsIgnoreCase(scheme);
        if (port < 0) port = tls ? 443 : 80;

        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();

        Socket raw;
        if (tls) {
            raw = SSLSocketFactory.getDefault().createSocket();
        } else {
            raw = new Socket();
        }
        raw.setTcpNoDelay(true);
        raw.connect(new InetSocketAddress(host, port), timeoutMs);
        raw.setSoTimeout(timeoutMs);

        // ---- 握手 ----
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        String key = android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP);

        StringBuilder req = new StringBuilder();
        req.append("GET ").append(path).append(" HTTP/1.1\r\n");
        req.append("Host: ").append(host).append("\r\n");
        req.append("Upgrade: websocket\r\n");
        req.append("Connection: Upgrade\r\n");
        req.append("Sec-WebSocket-Key: ").append(key).append("\r\n");
        req.append("Sec-WebSocket-Version: 13\r\n");
        if (apiKey != null && !apiKey.isEmpty()) {
            req.append("X-API-Key: ").append(apiKey.trim()).append("\r\n");
        }
        req.append("\r\n");

        OutputStream o = raw.getOutputStream();
        o.write(req.toString().getBytes(StandardCharsets.UTF_8));
        o.flush();

        // 读响应头（直到 \r\n\r\n）
        InputStream is = raw.getInputStream();
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {
            int b = is.read();
            if (b < 0) throw new IOException("握手响应中断");
            head.write(b);
            if ((state == 0 || state == 2) && b == '\r') state++;
            else if ((state == 1 || state == 3) && b == '\n') state++;
            else state = 0;
        }
        String resp = new String(head.toByteArray(), StandardCharsets.ISO_8859_1);
        String firstLine = resp.split("\r\n")[0];
        if (!firstLine.contains("101")) {
            throw new IOException("WebSocket 握手失败：" + firstLine);
        }

        // is 已被缓冲读取过，需用同一个流构造客户端（保留剩余数据）
        return new SimpleWsClient(raw, is);
    }

    /** 用已消费过响应头的流构造（避免丢包） */
    private SimpleWsClient(Socket s, InputStream alreadyRead) {
        this.socket = s;
        this.in = alreadyRead instanceof BufferedInputStream
                ? alreadyRead : new BufferedInputStream(alreadyRead);
        try {
            this.out = s.getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** 发送文本帧（客户端必须 mask） */
    public void sendText(String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        sendFrame(OP_TEXT, payload);
    }

    private void sendFrame(int opcode, byte[] payload) throws IOException {
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        f.write(0x80 | (opcode & 0x0F));   // FIN + opcode

        int len = payload.length;
        if (len < 126) {
            f.write(0x80 | len);
        } else if (len < 65536) {
            f.write(0x80 | 126);
            f.write((len >> 8) & 0xFF);
            f.write(len & 0xFF);
        } else {
            f.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) f.write((int) (((long) len >> (8 * i)) & 0xFF));
        }

        byte[] mask = new byte[4];
        rnd.nextBytes(mask);
        f.write(mask, 0, 4);

        byte[] outBytes = f.toByteArray();
        out.write(outBytes);
        byte[] masked = new byte[len];
        for (int i = 0; i < len; i++) masked[i] = (byte) (payload[i] ^ mask[i % 4]);
        out.write(masked);
        out.flush();
    }

    /** 读取一个完整文本帧（自动处理分片与 ping） */
    public String readText() throws IOException {
        ByteArrayOutputStream acc = new ByteArrayOutputStream();
        while (true) {
            int b0 = in.read();
            if (b0 < 0) throw new IOException("连接已关闭");
            int b1 = in.read();
            if (b1 < 0) throw new IOException("连接已关闭");

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

            byte[] mask = new byte[4];
            if (masked) {
                for (int i = 0; i < 4; i++) mask[i] = (byte) readByte();
            }

            byte[] data = new byte[(int) len];
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) throw new IOException("读取中断");
                off += n;
            }
            if (masked) {
                for (int i = 0; i < data.length; i++) data[i] ^= mask[i % 4];
            }

            if (opcode == OP_PING) {
                try { sendFrame(OP_PONG, data); } catch (IOException ignored) { }
                continue;
            }
            if (opcode == OP_PONG) continue;
            if (opcode == OP_CLOSE) throw new IOException("服务端关闭连接");

            acc.write(data);
            if (fin) return new String(acc.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private int readByte() throws IOException {
        int b = in.read();
        if (b < 0) throw new IOException("读取中断");
        return b;
    }

    public void close() {
        try { sendFrame(OP_CLOSE, new byte[0]); } catch (IOException ignored) { }
        try { socket.close(); } catch (IOException ignored) { }
    }
}
