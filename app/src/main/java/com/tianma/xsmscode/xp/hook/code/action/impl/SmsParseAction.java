package com.tianma.xsmscode.xp.hook.code.action.impl;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.smscodf.zhuxf.BuildConfig;
import com.tianma.xsmscode.common.utils.SmsCodeUtils;
import com.tianma.xsmscode.common.utils.StringUtils;
import com.tianma.xsmscode.common.utils.XLog;
import com.tianma.xsmscode.common.utils.XSPUtils;
import com.tianma.xsmscode.data.db.entity.SmsMsg;
import com.tianma.xsmscode.feature.store.EntityStoreManager;
import com.tianma.xsmscode.feature.store.EntityType;
import com.tianma.xsmscode.xp.hook.code.action.CallableAction;

import de.robv.android.xposed.XSharedPreferences;

/**
 * 解析短信中的验证码
 */
public class SmsParseAction extends CallableAction {

    public static final String SMS_MSG = "sms_msg";
    public static final String SMS_DUPLICATED = "sms_duplicated";
    /** 本条是否为验证码短信（2026-09-19）：非验证码短信仅转发，其余动作全部跳过 */
    public static final String SMS_IS_CODE = "sms_is_code";

    private Intent mSmsIntent;

    public SmsParseAction(Context pluginContext, Context phoneContext, SmsMsg smsMsg, XSharedPreferences xsp) {
        super(pluginContext, phoneContext, smsMsg, xsp);
    }

    public void setSmsIntent(Intent smsIntent) {
        mSmsIntent = smsIntent;
    }

    @Override
    public Bundle action() {
        return parseSmsMsg();
    }

    private Bundle parseSmsMsg() {
        mSmsMsg = SmsMsg.fromIntent(mSmsIntent);

        String sender = mSmsMsg.getSender();
        String msgBody = mSmsMsg.getBody();
        if (BuildConfig.DEBUG) {
            XLog.d("Sender: %s", sender);
            XLog.d("Body: %s", msgBody);
        } else {
            XLog.d("Sender: %s", StringUtils.escape(sender));
            XLog.d("Body: %s", StringUtils.escape(msgBody));
        }

        if (TextUtils.isEmpty(sender) || TextUtils.isEmpty(msgBody)) {
            return null;
        }

        String smsCode = SmsCodeUtils.parseSmsCodeIfExists(mPluginContext, msgBody, true);
        boolean isCodeMsg = !TextUtils.isEmpty(smsCode);
        if (!isCodeMsg) { // isn't code message
            // 2026-09-19：转发范围为"全部短信"时，非验证码短信也要放行——
            // 否则 CodeWorker 直接退出，普通短信永远进不了转发流程。
            // 注意：仅放行转发，验证码相关的动作（复制/自动输入/标记已读/删除/记录）
            // 全部由 CodeWorker 依据 isCodeMsg 跳过，绝不能作用到普通短信上。
            if (!XSPUtils.forwardAllSmsEnabled(xsp)) {
                return null;
            }
            mSmsMsg.setSmsCode(null);
            mSmsMsg.setCompany(SmsCodeUtils.parseCompany(msgBody));
            long ts = System.currentTimeMillis();
            mSmsMsg.setDate(ts);

            Bundle b = new Bundle();
            b.putParcelable(SMS_MSG, mSmsMsg);
            b.putBoolean(SMS_DUPLICATED, false);
            b.putBoolean(SMS_IS_CODE, false);
            XLog.i("Non-code SMS, forwarded due to scope=all");
            return b;
        }

        mSmsMsg.setSmsCode(smsCode);
        mSmsMsg.setCompany(SmsCodeUtils.parseCompany(msgBody));
        long timestamp = System.currentTimeMillis();
        mSmsMsg.setDate(timestamp);

        Bundle bundle = new Bundle();
        bundle.putParcelable(SMS_MSG, mSmsMsg);
        bundle.putBoolean(SMS_IS_CODE, true);

        // 去除重复短信
        boolean duplicated = false;
        if (XSPUtils.deduplicateSms(xsp)) {
            SmsMsg prevSmsMsg = EntityStoreManager.loadEntityFromFile(EntityType.PREV_SMS_MSG, SmsMsg.class);
            if (prevSmsMsg != null) {
                if (Math.abs(timestamp - prevSmsMsg.getDate()) <= 15000) {
                    if ((sender.equals(prevSmsMsg.getSender()) && smsCode.equals(prevSmsMsg.getSmsCode()))
                            || msgBody.equals(prevSmsMsg.getBody())) {
                        duplicated = true;
                        XLog.d("Duplicated message, ignore");
                    }
                }
            }
            // 保存当前验证码记录 Action
            EntityStoreManager.storeEntityToFile(EntityType.PREV_SMS_MSG, mSmsMsg);
        }

        bundle.putBoolean(SMS_DUPLICATED, duplicated);
        return bundle;
    }


}
