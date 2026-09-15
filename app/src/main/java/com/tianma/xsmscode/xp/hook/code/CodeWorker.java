package com.tianma.xsmscode.xp.hook.code;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.smscodf.zhuxf.BuildConfig;
import com.tianma.xsmscode.common.constant.PrefConst;
import com.tianma.xsmscode.common.utils.XLog;
import com.tianma.xsmscode.common.utils.XSPUtils;
import com.tianma.xsmscode.data.db.entity.SmsMsg;
import com.tianma.xsmscode.xp.hook.code.action.impl.AutoInputAction;
import com.tianma.xsmscode.xp.hook.code.action.impl.CancelNotifyAction;
import com.tianma.xsmscode.xp.hook.code.action.impl.CopyToClipboardAction;
import com.tianma.xsmscode.xp.hook.code.action.impl.KillMeAction;
import com.tianma.xsmscode.xp.hook.code.action.impl.NotifyAction;
import com.tianma.xsmscode.xp.hook.code.action.impl.OperateSmsAction;
import com.tianma.xsmscode.xp.hook.code.action.impl.RecordSmsAction;
import com.tianma.xsmscode.xp.hook.code.action.impl.SmsParseAction;
import com.tianma.xsmscode.xp.hook.code.action.impl.ToastAction;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XSharedPreferences;

public class CodeWorker {

    private final Context mPhoneContext;
    private final Context mPluginContext;
    private final XSharedPreferences xsp;
    private final Intent mSmsIntent;

    private final Handler mUIHandler;

    private final ScheduledExecutorService mScheduledExecutor;

    CodeWorker(Context pluginContext, Context phoneContext, Intent smsIntent) {
        mPluginContext = pluginContext;
        mPhoneContext = phoneContext;
        xsp = new XSharedPreferences(BuildConfig.APPLICATION_ID, PrefConst.PREF_NAME);
        mSmsIntent = smsIntent;

        mUIHandler = new Handler(Looper.getMainLooper());

        mScheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    public ParseResult parse() {
        if (!XSPUtils.isEnabled(xsp)) {
            XLog.i("SmsCodeX disabled, exiting");
            return null;
        }

        boolean verboseLog = XSPUtils.isVerboseLogMode(xsp);
        if (verboseLog) {
            XLog.setLogLevel(Log.VERBOSE);
        } else {
            XLog.setLogLevel(BuildConfig.LOG_LEVEL);
        }

        SmsParseAction smsParseAction = new SmsParseAction(mPluginContext, mPhoneContext, null, xsp);
        smsParseAction.setSmsIntent(mSmsIntent);
        ScheduledFuture<Bundle> smsParseFuture = mScheduledExecutor.schedule(smsParseAction, 0, TimeUnit.MILLISECONDS);

        final SmsMsg smsMsg;
        try {
            Bundle parseBundle = smsParseFuture.get();
            if (parseBundle == null) {
                // the SMS message doesn't contain verification code
                return null;
            }

            boolean duplicated = parseBundle.getBoolean(SmsParseAction.SMS_DUPLICATED, false);
            if (duplicated) {
                return buildParseResult();
            }

            smsMsg = parseBundle.getParcelable(SmsParseAction.SMS_MSG);
        } catch (Exception e) {
            XLog.e("Error occurs when get SmsParseAction call value", e);
            return null;
        }


        // 复制到剪切板 Action
        mUIHandler.post(new CopyToClipboardAction(mPluginContext, mPhoneContext, smsMsg, xsp));

        // 显示Toast Action
        mUIHandler.post(new ToastAction(mPluginContext, mPhoneContext, smsMsg, xsp));

        // 转发到企业微信（2026-09-15 定稿）：用户要求最高优先级——最先调度；
        // 应用进程可能已被自杀/被系统冻结导致失败，自动重试（0s/2s/4s 共 3 次）
        final java.util.concurrent.atomic.AtomicBoolean forwardSent =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        final Bundle fwdArgs = new Bundle();
        fwdArgs.putString("sender", smsMsg.getSender());
        fwdArgs.putString("body", smsMsg.getBody());
        fwdArgs.putString("code", smsMsg.getSmsCode());
        fwdArgs.putLong("time", smsMsg.getDate());
        for (int attempt = 0; attempt < 3; attempt++) {
            final int attemptNo = attempt + 1;
            mScheduledExecutor.schedule(new Runnable() {
                @Override
                public void run() {
                    if (forwardSent.get()) {
                        return;
                    }
                    try {
                        android.net.Uri uri = android.net.Uri.parse(
                                "content://" + com.tianma.xsmscode.data.db.DBProvider.AUTHORITY);
                        Bundle r = mPluginContext.getContentResolver().call(uri, "forward", null, fwdArgs);
                        boolean ok = r != null && r.getBoolean("ok");
                        String reason = r == null ? "null" : r.getString("reason");
                        if (ok) {
                            forwardSent.set(true);
                            XLog.i("Forward to app (attempt %d): succeed", attemptNo);
                        } else if ("disabled".equals(reason)) {
                            forwardSent.set(true);
                            XLog.i("Forward to app (attempt %d): disabled by config", attemptNo);
                        } else {
                            XLog.w("Forward to app (attempt %d): failed(%s)", attemptNo, reason);
                        }
                    } catch (Throwable t) {
                        XLog.e("Forward to app (attempt %d) failed: %s", attemptNo, t);
                    }
                }
            }, attempt * 2000L, TimeUnit.MILLISECONDS);
        }

        // 自动输入 Action
        if (XSPUtils.autoInputCodeEnabled(xsp)) {
            AutoInputAction autoInputAction = new AutoInputAction(mPluginContext, mPhoneContext, smsMsg, xsp);
            // 延时填入功能已按用户决定移除（设置读取链路在 hook 进程不可靠），固定立即输入
            mScheduledExecutor.schedule(autoInputAction, 0, TimeUnit.MILLISECONDS);
        }


        // 显示通知 Action
        NotifyAction notifyAction = new NotifyAction(mPluginContext, mPhoneContext, smsMsg, xsp);
        ScheduledFuture<Bundle> notificationFuture = mScheduledExecutor.schedule(notifyAction, 0, TimeUnit.MILLISECONDS);

        // 记录验证码短信 Action
        RecordSmsAction recordSmsAction = new RecordSmsAction(mPluginContext, mPhoneContext, smsMsg, xsp);
        mScheduledExecutor.schedule(recordSmsAction, 0, TimeUnit.MILLISECONDS);

        // 操作验证码短信（标记为已读 或者 删除） Action
        OperateSmsAction operateSmsAction = new OperateSmsAction(mPluginContext, mPhoneContext, smsMsg, xsp);
        mScheduledExecutor.schedule(operateSmsAction, 3000, TimeUnit.MILLISECONDS);

        // 自杀 Action（2026-09-15 延至 8s：等待企微转发 HTTP 完成，避免转发被中途杀死）
        KillMeAction action = new KillMeAction(mPluginContext, mPhoneContext, smsMsg, xsp);
        mScheduledExecutor.schedule(action, 8000, TimeUnit.MILLISECONDS);

        try {
            // 清除通知
            Bundle bundle = notificationFuture.get();
            if (bundle != null && bundle.containsKey(NotifyAction.NOTIFY_RETENTION_TIME)) {
                long delay = bundle.getLong(NotifyAction.NOTIFY_RETENTION_TIME, 0L);
                int notificationId = bundle.getInt(NotifyAction.NOTIFY_ID, 0);
                CancelNotifyAction cancelNotifyAction = new CancelNotifyAction(mPluginContext, mPhoneContext, smsMsg, xsp);
                cancelNotifyAction.setNotificationId(notificationId);

                mScheduledExecutor.schedule(cancelNotifyAction, delay, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            XLog.e("Error in notification future get()", e);
        }

        return buildParseResult();
    }

    private ParseResult buildParseResult() {
        ParseResult parseResult = new ParseResult();
        parseResult.setBlockSms(XSPUtils.blockSmsEnabled(xsp));
        return parseResult;
    }
}
