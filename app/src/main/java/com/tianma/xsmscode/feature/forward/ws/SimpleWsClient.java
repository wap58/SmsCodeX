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
 * 极简 WebSocket 客户端（2026-10-17 自「传信」移植，用于移动 5G 新消息通道）。
 *
 * <p>为什么手写而不引第三方库：只用到「握手 + 发文本帧 + 收文本帧 + 关闭」，
 * 约 200 行即可覆盖，保持项目零依赖。
 *
 * <p>仅支持 RFC6455 基础——TLS、文本帧、mask、分片收包、关闭。
 * 不支持二进制帧与扩展协商。
 */
public final class SimpleWsClient {

    public static final int OP_TEXT = 0x1;
    public static final int OP_CLOSE = 0x8;
    public static final int OP_PING = 0x9;
    public static final int OP_PONG = 0xA;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final Random rnd = new SecureRandom();

    private SimpleWsClient(Socket s) throws IOException {
        this.socket = s;
        this.in = new BufferedInputStream(s.getInputStream());
        this.out = s.getOutputStream();
    }

    /**
     * 建立连接并完成握手。
     *
     * @param url       形如 wss://host/path
     * @param apiKey    写入 X-API-Key 头（可空）
     * @param timeoutMs 连接与读超时
     */
    public static SimpleWsClient connect(String url, String apiKey, int timeoutMs)
            throws IOException {
        URI uri = URI.create(url.trim());
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();
        boolean tls = "wss".equalsIgnoreCase(scheme);
        if (port < 0) {
            port = tls ? 443 : 80;
        }
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (uri.getRawQuery() != null) {
            path = path + "?" + uri.getRawQuery();
        }

        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(host, port), timeoutMs);
        raw.setSoTimeout(timeoutMs);
        Socket sock = raw;
        if (tls) {
            javax.net.ssl.SSLSocket ssl =
                    (javax.net.ssl.SSLSocket) SSLSocketFactory.getDefault()
                            .createSocket(raw, host, port, true);
            ssl.startHandshake();
            sock = ssl;
        }

        SimpleWsClient c = new SimpleWsClient(sock);

        String key = base64(randomBytes(16));
        StringBuilder req = new StringBuilder();
        req.append("GET ").append(path).append(" HTTP/1.1\r\n");
        req.append("Host: ").append(host).append("\r\n");
        req.append("Upgrade: websocket\r\n");
        req.append("Connection: Upgrade\r\n");
        req.append("Sec-WebSocket-Key: ").append(key).append("\r\n");
        req.append("Sec-WebSocket-Version: 13\r\n");
        if (apiKey != null && !apiKey.isEmpty()) {
            req.append("X-API-Key: ").append(apiKey).append("\r\n");
        }
        req.append("\r\n");
        c.out.write(req.toString().getBytes(StandardCharsets.UTF_8));
        c.out.flush();

        String statusLine = readLine(c.in);
        if (statusLine == null || !statusLine.contains("101")) {
            try {
                sock.close();
            } catch (IOException ignored) {
            }
            throw new IOException("握手失败：" + statusLine);
        }
        // 消费握手响应头
        String line;
        do {
            line = readLine(c.in);
        } while (line != null && !line.isEmpty());

        return c;
    }

    /** 发送一个文本帧（客户端帧必须 mask） */
    public void sendText(String text) throws IOException {
        sendFrame(OP_TEXT, text.getBytes(StandardCharsets.UTF_8));
    }

    private void sendFrame(int opcode, byte[] payload) throws IOException {
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        f.write(0x80 | (opcode & 0x0F));
        int len = payload.length;
        if (len < 126) {
            f.write(0x80 | len);
        } else if (len <= 0xFFFF) {
            f.write(0x80 | 126);
            f.write((len >> 8) & 0xFF);
            f.write(len & 0xFF);
        } else {
            f.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) {
                f.write((int) (((long) len >> (8 * i)) & 0xFF));
            }
        }
        byte[] mask = randomBytes(4);
        f.write(mask, 0, 4);
        for (int i = 0; i < len; i++) {
            f.write(payload[i] ^ mask[i % 4]);
        }
        out.write(f.toByteArray());
        out.flush();
    }

    /**
     * 读一条文本消息；超时返回 null，连接关闭抛 IOException。
     * 自动应答 ping 并处理分片。
     */
    public String readText() throws IOException {
        ByteArrayOutputStream acc = new ByteArrayOutputStream();
        try {
            while (true) {
                int b0 = in.read();
                if (b0 < 0) {
                    throw new IOException("连接已关闭");
                }
                int opcode = b0 & 0x0F;
                boolean fin = (b0 & 0x80) != 0;

                int b1 = in.read();
                if (b1 < 0) {
                    throw new IOException("连接已关闭");
                }
                boolean masked = (b1 & 0x80) != 0;
                long len = b1 & 0x7F;
                if (len == 126) {
                    len = ((long) readByte() << 8) | readByte();
                } else if (len == 127) {
                    len = 0;
                    for (int i = 0; i < 8; i++) {
                        len = (len << 8) | readByte();
                    }
                }
                byte[] mask = null;
                if (masked) {
                    mask = new byte[4];
                    readFully(mask);
                }
                byte[] payload = new byte[(int) len];
                readFully(payload);
                if (mask != null) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] ^= mask[i % 4];
                    }
                }

                if (opcode == OP_PING) {
                    sendFrame(OP_PONG, payload);
                    continue;
                }
                if (opcode == OP_CLOSE) {
                    throw new IOException("服务端关闭连接");
                }
                if (opcode == OP_PONG) {
                    continue;
                }
                acc.write(payload, 0, payload.length);
                if (fin) {
                    return new String(acc.toByteArray(), StandardCharsets.UTF_8);
                }
            }
        } catch (java.net.SocketTimeoutException e) {
            return null;
        }
    }

    private int readByte() throws IOException {
        int v = in.read();
        if (v < 0) {
            throw new IOException("连接已关闭");
        }
        return v;
    }

    private void readFully(byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) {
                throw new IOException("连接已关闭");
            }
            off += n;
        }
    }

    public void close() {
        try {
            sendFrame(OP_CLOSE, new byte[0]);
        } catch (Throwable ignored) {
        }
        try {
            socket.close();
        } catch (Throwable ignored) {
        }
    }

    private static String readLine(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int c;
        while ((c = is.read()) >= 0) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                buf.write(c);
            }
        }
        if (c < 0 && buf.size() == 0) {
            return null;
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    private byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        rnd.nextBytes(b);
        return b;
    }

    private static String base64(byte[] data) {
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
    }
}
