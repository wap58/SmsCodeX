package com.tianma.xsmscode.feature.forward;

import android.content.Context;
import android.text.TextUtils;

import com.smscodf.zhuxf.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 多通道配置解析（2026-10-17 新增）。
 *
 * <p>存储格式：{@code pref_forward_channels} 存逗号分隔的通道 id，
 * 例如 {@code "wecom_agent,qqbot,cmcc"}。用字符串而非 StringSet 的原因：
 * {@link com.tianma.xsmscode.common.utils.PrefsExporter} 导出 XML 时只处理
 * 标量类型，StringSet 会退化成不可靠的 toString 形式，电话进程读不到。
 *
 * <p>向后兼容：该键为空时回退到旧的单选键 {@code pref_forward_channel_type}，
 * 老用户升级后行为不变。
 */
public final class ForwardChannels {

    /** 全部可选通道 id，顺序即界面显示顺序 */
    public static final String[] ALL = {
            "wecom_agent", "pushplus", "cmcc", "qqbot",
            "xizhi", "wecom_robot", "dingtalk", "feishu"
    };

    private ForwardChannels() {
    }

    /** 解析逗号分隔串 → 去重保序列表（跳过空值） */
    public static List<String> parse(String raw) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) {
            return out;
        }
        for (String s : raw.split(",")) {
            String id = s.trim();
            if (!id.isEmpty() && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** 集合 → 逗号分隔串（保序） */
    public static String serialize(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String id : new LinkedHashSet<>(ids)) {
            if (TextUtils.isEmpty(id)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id.trim());
        }
        return sb.toString();
    }

    /**
     * 实际生效的通道列表。
     *
     * @param multi  多选值（逗号分隔）
     * @param single 旧的单选值（多选为空时回退）
     */
    public static List<String> effective(String multi, String single) {
        List<String> list = parse(multi);
        if (!list.isEmpty()) {
            return list;
        }
        if (!TextUtils.isEmpty(single)) {
            list.add(single.trim());
        }
        return list;
    }

    /**
     * 该通道的参数是否已配置齐全（2026-10-17）。
     *
     * <p>用途：多选弹窗里禁止勾选未配置的通道——否则转发时会被
     * {@link ForwardDispatcher} 当作"未配置"跳过，用户以为已推送却收不到。
     *
     * <p>判据与 {@link ForwardDispatcher} 内各分支的前置检查保持一致。
     */
    public static boolean isConfigured(Context ctx, String id) {
        if (id == null) {
            return false;
        }
        android.content.SharedPreferences sp = ctx.getSharedPreferences(
                com.tianma.xsmscode.common.constant.PrefConst.PREF_NAME, Context.MODE_PRIVATE);
        switch (id) {
            case "wecom_agent":
                return !sp.getString(PrefKeys.WECOM_CORPID, "").trim().isEmpty()
                        && !sp.getString(PrefKeys.WECOM_AGENTID, "").trim().isEmpty()
                        && !sp.getString(PrefKeys.WECOM_SECRET, "").trim().isEmpty();
            case "wecom_robot":
                return !sp.getString(PrefKeys.WECOM_ROBOT_WEBHOOK, "").trim().isEmpty();
            case "dingtalk":
                return !sp.getString(PrefKeys.DINGTALK_WEBHOOK, "").trim().isEmpty();
            case "feishu":
                return !sp.getString(PrefKeys.FEISHU_WEBHOOK, "").trim().isEmpty();
            case "xizhi":
                return !sp.getString(PrefKeys.XIZHI_KEY, "").trim().isEmpty();
            case "pushplus":
                return !sp.getString(PrefKeys.PUSHPLUS_TOKEN, "").trim().isEmpty();
            case "qqbot":
                return !sp.getString(PrefKeys.QQBOT_APPID, "").trim().isEmpty()
                        && !sp.getString(PrefKeys.QQBOT_SECRET, "").trim().isEmpty()
                        && !sp.getString(PrefKeys.QQBOT_OPENID, "").trim().isEmpty();
            case "cmcc":
                return !sp.getString(PrefKeys.CMCC_API_KEY, "").trim().isEmpty();
            default:
                return false;
        }
    }

    /** 通道 id → 显示名 */    /** 通道 id → 显示名 */
    public static String name(Context ctx, String id) {
        if (id == null) {
            return "-";
        }
        switch (id) {
            case "wecom_agent":
                return ctx.getString(R.string.channel_name_wecom_agent);
            case "wecom_robot":
                return ctx.getString(R.string.channel_name_wecom_robot);
            case "dingtalk":
                return ctx.getString(R.string.channel_name_dingtalk);
            case "feishu":
                return ctx.getString(R.string.channel_name_feishu);
            case "xizhi":
                return ctx.getString(R.string.channel_name_xizhi);
            case "pushplus":
                return ctx.getString(R.string.channel_name_pushplus);
            case "qqbot":
                return ctx.getString(R.string.channel_name_qqbot);
            case "cmcc":
                return ctx.getString(R.string.channel_name_cmcc);
            default:
                return id;
        }
    }

    /** 列表 → 「A、B、C」形式的摘要 */
    public static String summary(Context ctx, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0) {
                sb.append('、');
            }
            sb.append(name(ctx, id));
        }
        return sb.toString();
    }
}
