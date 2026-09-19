package com.tianma.xsmscode.common.constant;

import com.smscodf.zhuxf.BuildConfig;

/**
 * Preference相关的常量
 */
public interface PrefConst {

    String PREF_NAME = BuildConfig.APPLICATION_ID + "_preferences";

    // General
    String KEY_ENABLE = "pref_enable";
    String KEY_HIDE_LAUNCHER_ICON = "pref_hide_launcher_icon";
    String KEY_CHOOSE_THEME = "pref_choose_theme";

    // SMS Code
    String KEY_SHOW_TOAST = "pref_show_toast";
    String KEY_COPY_TO_CLIPBOARD = "pref_copy_to_clipboard";
    String KEY_ENABLE_AUTO_INPUT_CODE = "pref_enable_auto_input_code";
    String KEY_AUTO_INPUT_CODE_DELAY = "pref_auto_input_code_delay";
    String KEY_AUTO_INPUT_CODE_DELAY_DEFAULT = "0";
    String KEY_APP_BLOCK_ENTRY = "pref_app_block_entry";
    String KEY_BLOCK_SMS = "pref_block_sms";
    String KEY_DEDUPLICATE_SMS = "pref_deduplicate_sms";


    // Code Notification
    String KEY_SHOW_CODE_NOTIFICATION = "pref_show_code_notification";
    String KEY_AUTO_CANCEL_CODE_NOTIFICATION = "pref_auto_cancel_code_notification";
    String KEY_NOTIFICATION_RETENTION_TIME = "pref_notification_retention_time";
    String NOTIFICATION_RETENTION_TIME_DEFAULT = "5";


    // SMS Forward（2026-09-15 新增：验证码短信转发到企业微信「自建应用」通道，
    // 字段与信驿 Relay 的 WeworkAgent 通道对齐）
    String KEY_FORWARD_HEADER = "pref_forward_header";
    String KEY_ENABLE_FORWARD = "pref_enable_forward";
    String KEY_FORWARD_WECOM_CORPID = "pref_forward_wecom_corpid";
    String KEY_FORWARD_WECOM_AGENTID = "pref_forward_wecom_agentid";
    String KEY_FORWARD_WECOM_SECRET = "pref_forward_wecom_secret";
    String KEY_FORWARD_WECOM_TOUSER = "pref_forward_wecom_touser";
    String KEY_FORWARD_CHANNEL_TYPE = "pref_forward_channel_type";
    String KEY_FORWARD_CHANNEL_CONFIG = "pref_forward_channel_config";
    String KEY_FORWARD_WECOM_ROBOT_WEBHOOK = "pref_forward_wecom_robot_webhook";
    String KEY_FORWARD_DINGTALK_WEBHOOK = "pref_forward_dingtalk_webhook";
    String KEY_FORWARD_DINGTALK_SECRET = "pref_forward_dingtalk_secret";
    String KEY_FORWARD_FEISHU_WEBHOOK = "pref_forward_feishu_webhook";
    String KEY_FORWARD_XIZHI_KEY = "pref_forward_xizhi_key";
    String KEY_FORWARD_PUSHPLUS_TOKEN = "pref_forward_pushplus_token";


    // Code Record
    String KEY_ENABLE_CODE_RECORDS = "pref_enable_code_records";
    int MAX_SMS_RECORDS_COUNT_DEFAULT = 20;
    String KEY_ENTRY_CODE_RECORDS = "pref_entry_code_records";

    // Code Rules
    String KEY_SMSCODE_KEYWORDS = "pref_smscode_keywords";
    String SMSCODE_KEYWORDS_DEFAULT = SmsCodeConst.VERIFICATION_KEYWORDS_REGEX;
    String KEY_SMSCODE_TEST = "pref_smscode_test";
    String KEY_CODE_RULES = "pref_code_rules";

    // Experimental
    String KEY_EXPERIMENTAL = "pref_experimental";
    String KEY_MARK_AS_READ = "pref_mark_as_read";
    String KEY_DELETE_SMS = "pref_delete_sms";
    String KEY_KILL_ME = "pref_kill_me";

    // Collapsible category headers
    String KEY_GENERAL_HEADER = "pref_general_header";
    String KEY_SMS_CODE_HEADER = "pref_sms_code_header";
    String KEY_AUTO_INPUT_HEADER = "pref_auto_input_header";
    String KEY_NOTIFICATION_HEADER = "pref_notification_header";
    String KEY_CODE_RECORDS_HEADER = "pref_code_records_header";
    String KEY_MATCH_RULES_HEADER = "pref_match_rules_header";

    // Others
    String KEY_VERBOSE_LOG_MODE = "pref_verbose_log_mode";

    // About
    String KEY_ABOUT = "pref_about";
    String KEY_VERSION = "pref_version";
    String KEY_JOIN_QQ_GROUP = "pref_join_qq_group";
    String KEY_SOURCE_CODE = "pref_source_code";
    String KEY_DONATE_BY_ALIPAY = "pref_donate_by_alipay";
    String KEY_PRIVACY_POLICY = "pref_privacy_policy";
    String KEY_PRIVACY_POLICY_ACCEPTED = "pref_privacy_policy_accepted";

    /** 模块在电话进程加载时报到时间戳（elapsedRealtime，开机归零），用于 UI 激活态判定 */
    String KEY_MODULE_ACTIVE_ELAPSED = "module_active_elapsed";
}
