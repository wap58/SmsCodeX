package com.tianma.xsmscode.ui.forward;

import android.content.Context;
import android.os.Bundle;

import com.smscodf.zhuxf.R;
import com.tianma.xsmscode.ui.app.base.BasePreferenceFragment;

/**
 * 通道独立参数配置页 Fragment（2026-09-16，用户指定）：
 * 按通道参数加载对应的 preference 页，每通道一页，互不干扰。
 */
public class ChannelSettingsFragment extends BasePreferenceFragment {

    private static final String ARG_CHANNEL = "channel";

    public static ChannelSettingsFragment newInstance(String channel) {
        ChannelSettingsFragment fragment = new ChannelSettingsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CHANNEL, channel == null ? "wecom_agent" : channel);
        fragment.setArguments(args);
        return fragment;
    }

    /** 各通道配置页标题（2026-09-16） */
    public static String titleFor(Context context, String channel) {
        int id;
        switch (channel == null ? "wecom_agent" : channel) {
            case "wecom_robot":
                id = R.string.forward_channel_page_wecom_robot;
                break;
            case "dingtalk":
                id = R.string.forward_channel_page_dingtalk;
                break;
            case "feishu":
                id = R.string.forward_channel_page_feishu;
                break;
            case "xizhi":
                id = R.string.forward_channel_page_xizhi;
                break;
            case "pushplus":
                id = R.string.forward_channel_page_pushplus;
                break;
            case "wecom_agent":
            default:
                id = R.string.forward_channel_page_wecom_agent;
                break;
        }
        return context.getString(id);
    }

    @Override
    protected void doOnCreatePreferences(Bundle savedInstanceState, String rootKey) {
        String channel = getArguments() != null
                ? getArguments().getString(ARG_CHANNEL, "wecom_agent")
                : "wecom_agent";
        switch (channel) {
            case "wecom_robot":
                addPreferencesFromResource(R.xml.channel_settings_wecom_robot);
                break;
            case "dingtalk":
                addPreferencesFromResource(R.xml.channel_settings_dingtalk);
                break;
            case "feishu":
                addPreferencesFromResource(R.xml.channel_settings_feishu);
                break;
            case "xizhi":
                addPreferencesFromResource(R.xml.channel_settings_xizhi);
                break;
            case "pushplus":
                addPreferencesFromResource(R.xml.channel_settings_pushplus);
                break;
            case "wecom_agent":
            default:
                addPreferencesFromResource(R.xml.channel_settings_wecom_agent);
                break;
        }
    }
}
