package com.tianma.xsmscode.feature.forward;

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
import java.util.Calendar;
import java.util.List;

/**
 * 转发结果记录（2026-10-18 改为保留历史）。
 *
 * <p>背景：首页要显示「今日转发 / 累计 / 失败」统计与「最近转发」列表，
 * 只保留最近一条不够用；但也不能无限增长，故保留最近 {@link #MAX_RECORDS} 条。
 *
 * <p>存储位置：外部文件目录 {@code /sdcard/Android/data/<pkg>/files/forward_history.json}，
 * 与 prefs_export.xml 同一机制——该目录 SELinux 标签为 media_rw_data_file，
 * 电话进程（radio uid）可读可写，本应用与 hook 进程共享同一份数据。
 *
 * <p>结构（按时间倒序，第 0 条最新）：
 * <pre>
 * [ {"time":..., "channels":[{"channel":"qqbot","ok":true,"reason":""}, ...]}, ... ]
 * </pre>
 */
public final class ForwardResultStore {

    private static final String FILE_NAME = "forward_history.json";
    /** 保留的历史条数 */
    private static final int MAX_RECORDS = 200;

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

    // ------------------------------------------------------------ 写

    /**
     * 追加一条短信的多通道转发结果。
     *
     * @param time    短信接收时间（SmsMsg#getDate），作为去重键
     * @param results 每个通道一项
     */
    public static synchronized void write(long time, List<ChannelResult> results) {
        File f = file();
        if (f == null) {
            return;
        }
        try {
            JSONArray arr = readArray();
            JSONArray next = new JSONArray();
            boolean replaced = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                if (o.optLong("time", -1L) == time && !replaced) {
                    // 同一条短信重复写（电话进程 + app 进程各写一次）→ 覆盖而非追加
                    next.put(buildRecord(time, results));
                    replaced = true;
                } else {
                    next.put(o);
                }
            }
            if (!replaced) {
                JSONArray shifted = new JSONArray();
                shifted.put(buildRecord(time, results));
                for (int i = 0; i < next.length() && i < MAX_RECORDS - 1; i++) {
                    shifted.put(next.get(i));
                }
                next = shifted;
            }

            JSONArray trimmed = new JSONArray();
            for (int i = 0; i < next.length() && i < MAX_RECORDS; i++) {
                JSONObject o = next.optJSONObject(i);
                if (o != null) {
                    trimmed.put(o);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(trimmed.toString().getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            StorageUtils.setFileWorldWritable(f, 0);
            StorageUtils.setFileWorldWritable(f.getParentFile(), 2);
        } catch (Throwable t) {
            XLog.e("ForwardResultStore: write failed %s", t);
        }
    }

    private static JSONObject buildRecord(long time, List<ChannelResult> results)
            throws Exception {
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
        return root;
    }

    // ------------------------------------------------------------ 读

    private static JSONArray readArray() {
        File f = file();
        if (f == null || !f.exists()) {
            return new JSONArray();
        }
        try (FileInputStream fis = new FileInputStream(f)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = fis.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
            }
            String s = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            if (TextUtils.isEmpty(s)) {
                return new JSONArray();
            }
            return new JSONArray(s);
        } catch (Throwable t) {
            XLog.e("ForwardResultStore: read failed %s", t);
            return new JSONArray();
        }
    }

    /** 是否已有任何记录 */
    public static boolean hasRecord() {
        File f = file();
        return f != null && f.exists() && f.length() > 0;
    }

    /** 最近 N 条记录（时间倒序） */
    public static List<Record> readRecent(int limit) {
        List<Record> out = new ArrayList<>();
        JSONArray arr = readArray();
        for (int i = 0; i < arr.length() && out.size() < limit; i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) {
                out.add(parseRecord(o));
            }
        }
        return out;
    }

    /** 指定短信时间戳的记录（详情弹窗用）；找不到返回空列表 */
    public static List<ChannelResult> readFor(long time) {
        JSONArray arr = readArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && o.optLong("time", -1L) == time) {
                return parseRecord(o).channels;
            }
        }
        return new ArrayList<>();
    }

    private static Record parseRecord(JSONObject o) {
        Record r = new Record();
        r.time = o.optLong("time", 0L);
        JSONArray ch = o.optJSONArray("channels");
        if (ch != null) {
            for (int j = 0; j < ch.length(); j++) {
                JSONObject c = ch.optJSONObject(j);
                if (c == null) {
                    continue;
                }
                ChannelResult cr = new ChannelResult();
                cr.channel = c.optString("channel", "");
                cr.ok = c.optBoolean("ok", false);
                cr.reason = c.optString("reason", "");
                r.channels.add(cr);
            }
        }
        return r;
    }

    // ------------------------------------------------------------ 统计

    /** 首页统计结果 */
    public static final class Stats {
        public int today;
        public int total;
        public int failed;
    }

    /**
     * 统计：以「短信条数」计。
     * <ul>
     *   <li>total：有转发记录的条数</li>
     *   <li>today：今天 0 点起的条数</li>
     *   <li>failed：至少有一个通道失败的条数（不是通道失败次数）</li>
     * </ul>
     */
    public static Stats stats() {
        Stats st = new Stats();
        JSONArray arr = readArray();
        long todayStart = startOfToday();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) {
                continue;
            }
            st.total++;
            if (o.optLong("time", 0L) >= todayStart) {
                st.today++;
            }
            JSONArray ch = o.optJSONArray("channels");
            boolean anyFail = false;
            if (ch != null) {
                for (int j = 0; j < ch.length(); j++) {
                    JSONObject c = ch.optJSONObject(j);
                    if (c != null && !c.optBoolean("ok", true)) {
                        anyFail = true;
                        break;
                    }
                }
            }
            if (anyFail) {
                st.failed++;
            }
        }
        return st;
    }

    private static long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** 清空全部记录 */
    public static void clear() {
        File f = file();
        if (f != null && f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /** 一条短信的转发记录 */
    public static final class Record {
        public long time;
        public final List<ChannelResult> channels = new ArrayList<>();

        /** 是否全部通道都成功 */
        public boolean allOk() {
            for (ChannelResult c : channels) {
                if (!c.ok) {
                    return false;
                }
            }
            return true;
        }

        /** 成功通道名拼接，用于列表副标题 */
        public String okChannelNames() {
            StringBuilder sb = new StringBuilder();
            for (ChannelResult c : channels) {
                if (c.ok) {
                    if (sb.length() > 0) {
                        sb.append('#');
                    }
                    sb.append(c.channel);
                }
            }
            return sb.toString();
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
