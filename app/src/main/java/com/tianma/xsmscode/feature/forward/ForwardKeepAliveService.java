package com.tianma.xsmscode.feature.forward;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.smscodf.zhuxf.R;

/**
 * 转发通道保活前台服务（2026-09-15 新增）。
 * <p>
 * 解决两个息屏问题：
 * 1) 进程豁免缓存冻结——息屏时 ContentProvider 通道（电话进程→应用进程）随时可达；
 * 2) 豁免 Doze 网络限制——息屏下 HTTP 转发不被断网。
 * <p>
 * 启动时机：开机自启 + 打开应用 + 打开转发开关。
 */
public class ForwardKeepAliveService extends Service {

    private static final String CHANNEL_ID = "forward_keepalive";
    private static final int NOTIFICATION_ID = 10086;

    public static void start(Context context) {
        Intent intent = new Intent(context, ForwardKeepAliveService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable ignored) {
            // 部分 ROM 限制后台启动时静默失败；开机自启路径仍会尝试
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.forward_keepalive_channel),
                    NotificationManager.IMPORTANCE_MIN);
            channel.setSound(null, null);
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    private Notification buildNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.ic_forward)
                .setContentTitle(getString(R.string.forward_keepalive_title))
                .setContentText(getString(R.string.forward_keepalive_text))
                .setOngoing(true)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
