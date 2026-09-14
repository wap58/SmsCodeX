package com.tianma.xsmscode.xp.hook.code.action.impl;

import android.content.Context;
import android.os.Bundle;

import com.tianma.xsmscode.common.utils.ClipboardUtils;
import com.tianma.xsmscode.common.utils.XSPUtils;
import com.tianma.xsmscode.data.db.entity.SmsMsg;
import com.tianma.xsmscode.xp.hook.code.action.RunnableAction;

import de.robv.android.xposed.XSharedPreferences;

/**
 * 将验证码复制到剪切板
 */
public class CopyToClipboardAction extends RunnableAction {

    public CopyToClipboardAction(Context pluginContext, Context phoneContext, SmsMsg smsMsg, XSharedPreferences xsp) {
        super(pluginContext, phoneContext, smsMsg, xsp);
    }

    @Override
    public Bundle action() {
        if (XSPUtils.copyToClipboardEnabled(xsp)) {
            copyToClipboard();
        }
        return null;
    }

    private void copyToClipboard() {
        // 用 phone 进程自身 context 写剪贴板（模块包 context 会被 Android 10+ 的剪贴板访问检查拒绝）
        ClipboardUtils.copyToClipboard(mPhoneContext, mSmsMsg.getSmsCode());
    }
}
