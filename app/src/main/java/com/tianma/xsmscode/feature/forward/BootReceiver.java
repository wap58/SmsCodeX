package com.tianma.xsmscode.feature.forward;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机自启：拉起转发保活前台服务，保证息屏转发通道随时可达。
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
            ForwardKeepAliveService.start(context);
        }
    }
}
