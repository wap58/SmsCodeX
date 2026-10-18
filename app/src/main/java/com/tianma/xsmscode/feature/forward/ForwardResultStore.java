package com.tianma.xsmscode.feature.forward;

import android.content.Context;
import android.text.TextUtils;

import com.tianma.xsmscode.common.utils.StorageUtils;
import com.tianma.xsmscode.common.utils.XLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 转发结果记录（2026-10-17 新增，多通道版）。
 *
 * <p>背景：「短信详情」弹窗要显示逐通道的转发结果，但转发发生在电话进程，
 * 记录却要在 app 进程读。两个进程之间需要一个双方都能访问的落点。
 *
 * <p>方案：写外部文件目录（{@code /sdcard/Android/data/<pkg>/files/forward_last.json}），
 * 与 {@code prefs_export.xml} 同一机制——该目录 SELinux 标签为 media_rw_file，
 * radio uid（phone 进程）可读可写，实测无权限问题。
 *
 * <p>结构与传信一致：一条短信对应一个 channels 数组，每项
 * {@code {channel, ok, reason}}。只保留最近一条（覆盖写），
 * 短信详情是即时查看场景，不需要历史。
 */
public final class ForwardResultStore {

    private static final String FILE_NAME = "forward_last.json";

    private ForwardResultStore() {
    }

    private static File file() {
        File dir = StorageUtils.getFilesDir();
        if (dir == null) {
            return null;
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, FILE_NAME);
    }

    /**
     * 写入一条短信的多通道转发结果。
     *
     * @param time    短信接收时间（与 SmsMsg#getDate 一致），用作匹配键
     * @param results 每个通道一项：{channel, ok, reason}
     */
    public static synchronized void write(long time, List<ChannelResult> results) {
        File f = file();
        if (f == null) {
            return;
        }
        try {
            JSONArray arr = new JSONArray();
            if (results != null) {
                for (ChannelResult r : results) {
                    JSONObject o = new JSONObject();
                    o.put("channel", r.channel == null ? "" : r.channel);
                    o.put("ok", r.ok);
                    o.put("reason", r.reason == null ? "" : r.reason);
                    arr.put(o);
                }
            }
            JSONObject root = new JSONObject();
            root.put("time", time);
            root.put("channels", arr);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(root.toString().getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            // 让 phone 进程（radio uid）也能读
            StorageUtils.setFileWorldWritable(f, 0);
            StorageUtils.setFileWorldWritable(f.getParentFile(), 2);
        } catch (Throwable t) {
            XLog.e("ForwardResultStore: write failed %s", t);
        }
    }

    /** 是否存在任何转发记录文件（用于区分"未转发"与"无记录"） */
    public static boolean hasRecord() {
        File f = file();
        return f != null && f.exists() && f.length() > 0;
    }

    /** 读取最近一条记录；没有则返回 null */
    public static JSONObject read() {
        File f = file();
        if (f == null || !f.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(f)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[2048];
            int n;
            while ((n = fis.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
            }
            String s = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            if (TextUtils.isEmpty(s)) {
                return null;
            }
            return new JSONObject(s);
        } catch (Throwable t) {
            XLog.e("ForwardResultStore: read failed %s", t);
            return null;
        }
    }

    /** 解析指定时间戳的记录，返回逐通道结果（时间不匹配则返回空列表） */
    public static List<ChannelResult> readFor(long time) {
        List<ChannelResult> out = new ArrayList<>();
        JSONObject root = read();
        if (root == null) {
            return out;
        }
        long recTime = root.optLong("time", -1L);
        if (time > 0 && recTime > 0 && recTime != time) {
            return out;
        }
        JSONArray arr = root.optJSONArray("channels");
        if (arr == null) {
            return out;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) {
                continue;
            }
            ChannelResult r = new ChannelResult();
            r.channel = o.optString("channel", "");
            r.ok = o.optBoolean("ok", false);
            r.reason = o.optString("reason", "");
            out.add(r);
        }
        return out;
    }

    /** 清空（配置变更或记录清空时调用） */
    public static void clear() {
        File f = file();
        if (f != null && f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /** 单个通道的转发结果 */
    public static final class ChannelResult {
        public String channel;
        public boolean ok;
        public String reason;

        public ChannelResult() {
        }

        public ChannelResult(String channel, boolean ok, String reason) {
            this.channel = channel;
            this.ok = ok;
            this.reason = reason;
        }
    }
}
