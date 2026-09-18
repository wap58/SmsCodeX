package com.tianma.xsmscode.common.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.tianma.xsmscode.common.constant.PrefConst;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 配置导出器（2026-09-19 新增，根治"模块读不到用户配置"）。
 *
 * 背景：LSPosed 的 prefs 代理机制（xposedsharedprefs + MODE_WORLD_READABLE）
 * 只在【模块自身进程被注入】时才会 hook getPreferencesDir 并创建世界可读目录，
 * 而静态作用域下模块自身不可勾选、框架从不注入自身进程 → 该机制对本模块失效。
 * 实测：应用侧用 MODE_WORLD_READABLE 打开配置后，代理目录依然未创建。
 *
 * 方案：应用在保存配置后，主动把配置导出为 XML 到【外部文件目录】
 * （/sdcard/Android/data/<pkg>/files/，SELinux 标签 media_rw_data_file），
 * 并设为全世界可读写。电话进程(radio uid)可直接读取该文件——
 * 项目已有的 prev_code_record 就是同样机制，实测 phone 进程读写正常。
 *
 * 模块侧读取顺序见 ModulePrefs：外部导出文件 → LSPosed 代理目录 → 应用数据目录。
 */
public class PrefsExporter {

    /** 导出文件名（模块侧按同名查找） */
    public static final String EXPORT_FILE_NAME = "prefs_export.xml";

    private PrefsExporter() {
    }

    /** 导出当前配置到世界可读文件（应用每次保存配置后调用） */
    public static void export(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences(
                    PrefConst.PREF_NAME, Context.MODE_PRIVATE);
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n");
            for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                Object v = e.getValue();
                String key = escape(e.getKey());
                if (v instanceof Boolean) {
                    sb.append("    <boolean name=\"").append(key).append("\" value=\"")
                            .append(v).append("\" />\n");
                } else if (v instanceof Integer) {
                    sb.append("    <int name=\"").append(key).append("\" value=\"")
                            .append(v).append("\" />\n");
                } else if (v instanceof Long) {
                    sb.append("    <long name=\"").append(key).append("\" value=\"")
                            .append(v).append("\" />\n");
                } else if (v instanceof Float) {
                    sb.append("    <float name=\"").append(key).append("\" value=\"")
                            .append(v).append("\" />\n");
                } else if (v != null) {
                    sb.append("    <string name=\"").append(key).append("\">")
                            .append(escape(v.toString())).append("</string>\n");
                }
            }
            sb.append("</map>\n");

            File dir = StorageUtils.getFilesDir();
            if (dir == null) {
                XLog.e("PrefsExporter: files dir unavailable");
                return;
            }
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File out = new File(dir, EXPORT_FILE_NAME);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            // 目录与文件都设为全世界可读写（phone 进程 radio uid 需要读权限）
            StorageUtils.setFileWorldWritable(dir, 2);
            StorageUtils.setFileWorldWritable(out, 0);
            XLog.i("PrefsExporter: exported %d key(s) to %s",
                    sp.getAll().size(), out.getAbsolutePath());
        } catch (Throwable t) {
            XLog.e("PrefsExporter: export failed: %s", t);
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
