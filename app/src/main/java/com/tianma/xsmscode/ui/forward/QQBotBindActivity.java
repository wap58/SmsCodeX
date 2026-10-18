package com.tianma.xsmscode.ui.forward;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import com.smscodf.zhuxf.R;
import com.tianma.xsmscode.common.utils.ThemeColors;
import com.tianma.xsmscode.common.utils.XLog;
import com.tianma.xsmscode.feature.forward.qqbot.MiniWebSocket;
import com.tianma.xsmscode.feature.forward.qqbot.QQBotApi;
import com.tianma.xsmscode.feature.forward.qqbot.QQBotStore;
import com.tianma.xsmscode.ui.app.base.BaseActivity;

import org.json.JSONObject;

/**
 * QQ 机器人绑定页（2026-10-17，自「传信」移植）。
 *
 * <p>流程：
 * <ol>
 *   <li>填 AppID / AppSecret（QQ 开放平台创建机器人后获得，平台不提供扫码）</li>
 *   <li>点「获取会话」→ 连 WebSocket 网关（intents = C2C + 群@）</li>
 *   <li>按提示在 QQ 里给机器人发一条消息 → 自动抓取 openid 并保存</li>
 * </ol>
 *
 * <p>★ 为什么必须抓 openid：QQ 平台只暴露 openid（每个机器人一套独立 ID），
 * 无法从 QQ 号推导，只能从真实入站消息里取。
 */
public class QQBotBindActivity extends BaseActivity {

    private static final int CAPTURE_TIMEOUT_MS = 180_000;

    private EditText mEtAppId;
    private EditText mEtSecret;
    private TextView mTvStatus;
    private TextView mTvDetail;
    private Button mBtnCapture;

    private final Handler mUi = new Handler(Looper.getMainLooper());
    private MiniWebSocket mWs;
    private volatile boolean mCapturing = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qqbot_bind);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setHomeButtonEnabled(true);
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle(R.string.forward_channel_page_qqbot);
        }

        mEtAppId = findViewById(R.id.et_appid);
        mEtSecret = findViewById(R.id.et_secret);
        mTvStatus = findViewById(R.id.tv_status);
        mTvDetail = findViewById(R.id.tv_detail);
        mBtnCapture = findViewById(R.id.btn_capture);
        TextView hint = findViewById(R.id.tv_hint);

        mEtAppId.setText(QQBotStore.appId(this));
        mEtSecret.setText(QQBotStore.appSecret(this));

        mBtnCapture.setOnClickListener(v -> startCapture());
        findViewById(R.id.btn_save).setOnClickListener(v -> saveOnly());
        findViewById(R.id.btn_test).setOnClickListener(v -> testSend());
        findViewById(R.id.btn_unbind).setOnClickListener(v -> confirmUnbind());

        hint.setText("配置说明\n\n"
                + "① 打开 QQ 开放平台（q.qq.com）创建机器人，拿到 AppID 与 AppSecret 填到上面\n"
                + "② 点「获取会话」→ 按提示在 QQ 里给机器人发一条消息\n"
                + "③ 本应用会从这条消息里自动抓到会话 ID，绑定完成\n\n"
                + "★ 为什么不能手填会话 ID：QQ 平台的 openid 每个机器人各不相同，\n"
                + "   与 QQ 号无关，只能从真实入站消息里获取。\n\n"
                + "⚠️ 主动推送限额：QQ 官方限制机器人主动发消息的次数。\n"
                + "   若机器人从未与该用户交互过会发送失败；短信转发属低频场景通常够用，\n"
                + "   若报错 22009 / 11298 即触发该限制。");
        hint.setTextColor(ThemeColors.isDark() ? 0xFF9AA0A6 : 0xFF8A8A8E);

        refresh();
    }

    @Override
    protected void onDestroy() {
        closeWs();
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void refresh() {
        boolean dark = ThemeColors.isDark();
        if (QQBotStore.isConfigured(this)) {
            mTvStatus.setText("✅ 已绑定");
            mTvStatus.setTextColor(ThemeColors.accent());
            mTvDetail.setText((QQBotStore.isGroup(this) ? "群聊" : "私聊")
                    + "\n会话 ID：" + QQBotStore.openId(this));
        } else if (!TextUtils.isEmpty(QQBotStore.appId(this))) {
            mTvStatus.setText("已填参数，未获取会话");
            mTvStatus.setTextColor(dark ? 0xFFFFFFFF : 0xFF212121);
            mTvDetail.setText("点「获取会话」后在 QQ 里给机器人发一条消息");
        } else {
            mTvStatus.setText("未配置");
            mTvStatus.setTextColor(dark ? 0xFFFFFFFF : 0xFF212121);
            mTvDetail.setText("");
        }
    }

    private boolean validate() {
        if (TextUtils.isEmpty(mEtAppId.getText().toString().trim())
                || TextUtils.isEmpty(mEtSecret.getText().toString().trim())) {
            toast("请先填写 AppID 与 AppSecret");
            return false;
        }
        return true;
    }

    private void saveOnly() {
        if (!validate()) {
            return;
        }
        QQBotStore.save(this,
                mEtAppId.getText().toString().trim(),
                mEtSecret.getText().toString().trim(),
                QQBotStore.openId(this),
                QQBotStore.isGroup(this));
        toast("已保存");
        refresh();
    }

    private void startCapture() {
        if (mCapturing) {
            toast("正在等待消息…");
            return;
        }
        if (!validate()) {
            return;
        }
        QQBotStore.save(this,
                mEtAppId.getText().toString().trim(),
                mEtSecret.getText().toString().trim(), "", false);

        mCapturing = true;
        mBtnCapture.setEnabled(false);
        mTvStatus.setText("正在连接网关…");
        mTvDetail.setText("");

        new Thread(this::captureLoop, "qqbot-bind").start();
    }

    private void captureLoop() {
        try {
            QQBotApi.Token t = QQBotApi.getAccessToken(
                    mEtAppId.getText().toString().trim(),
                    mEtSecret.getText().toString().trim());
            if (!TextUtils.isEmpty(t.error) || TextUtils.isEmpty(t.accessToken)) {
                mUi.post(() -> fail("取 token 失败：" + t.error));
                return;
            }
            QQBotStore.saveToken(this, t.accessToken, t.expiresIn);

            String gw = QQBotApi.getGatewayUrl(t.accessToken);
            if (TextUtils.isEmpty(gw)) {
                mUi.post(() -> fail("获取网关地址失败，请检查 AppID / AppSecret"));
                return;
            }

            mUi.post(() -> {
                mTvStatus.setText("等待你在 QQ 里给机器人发消息…");
                mTvDetail.setText("现在打开 QQ，找到你的机器人，随便发一句话（如「绑定」）");
            });

            final boolean[] got = {false};
            mWs = new MiniWebSocket();
            mWs.connect(gw, new MiniWebSocket.Listener() {
                @Override
                public void onText(String text) {
                    handleInbound(text, got);
                }

                @Override
                public void onClose(String reason) {
                    if (!got[0] && mCapturing) {
                        mUi.post(() -> fail("网关连接已断开：" + reason));
                    }
                }

                @Override
                public void onError(Throwable e) {
                    if (!got[0] && mCapturing) {
                        mUi.post(() -> fail("网关异常：" + e.getMessage()));
                    }
                }
            });

            long deadline = System.currentTimeMillis() + CAPTURE_TIMEOUT_MS;
            while (!got[0] && mCapturing && System.currentTimeMillis() < deadline) {
                Thread.sleep(500);
            }
            if (!got[0] && mCapturing) {
                mUi.post(() -> fail("等待超时。请确认机器人已上线，并在 QQ 里给它发过消息后重试。"));
            }
        } catch (Throwable e) {
            XLog.e("QQBot bind: %s", e);
            mUi.post(() -> fail("异常：" + e.getMessage()));
        }
    }

    /** 解析网关下发 payload，抓 openid */
    private void handleInbound(String raw, boolean[] got) {
        try {
            JSONObject j = new JSONObject(raw);
            int op = j.optInt("op", -1);

            if (op == 10) {   // Hello → 先起心跳，再鉴权
                long hb = j.optJSONObject("d") == null ? 41250L
                        : j.optJSONObject("d").optLong("heartbeat_interval", 41250L);
                if (mWs != null) {
                    mWs.startHeartbeat(hb);
                    XLog.i("QQBot: heartbeat started interval=%dms", hb);
                }
                String token = QQBotStore.cachedToken(this);
                JSONObject d = new JSONObject();
                d.put("op", 2);
                d.put("d", new JSONObject()
                        .put("token", "QQBot " + token)
                        .put("intents", (1 << 25) | (1 << 30))   // C2C + 群@
                        .put("shard", new org.json.JSONArray().put(0).put(1))
                        .put("properties", new JSONObject()));
                if (mWs != null) {
                    mWs.sendText(d.toString());
                }
                return;
            }

            if (op == 0) {   // 事件
                if (mWs != null && j.has("s")) {
                    mWs.updateSeq(j.optLong("s", 0));
                }
                String type = j.optString("t", "");
                JSONObject d = j.optJSONObject("d");
                if (d == null) {
                    return;
                }
                String openid = null;
                boolean group = false;

                if ("C2C_MESSAGE_CREATE".equals(type)) {
                    JSONObject author = d.optJSONObject("author");
                    if (author != null) {
                        openid = author.optString("user_openid", "");
                    }
                } else if ("GROUP_AT_MESSAGE_CREATE".equals(type)) {
                    String gid = d.optString("group_openid", "");
                    if (!TextUtils.isEmpty(gid)) {
                        openid = gid;
                        group = true;
                    } else {
                        JSONObject author = d.optJSONObject("author");
                        if (author != null) {
                            openid = author.optString("member_openid", "");
                            if (TextUtils.isEmpty(openid)) {
                                openid = author.optString("user_openid", "");
                            }
                        }
                    }
                }

                if (!TextUtils.isEmpty(openid)) {
                    got[0] = true;
                    final String oid = openid;
                    final boolean isGroup = group;
                    mUi.post(() -> {
                        QQBotStore.save(this,
                                mEtAppId.getText().toString().trim(),
                                mEtSecret.getText().toString().trim(),
                                oid, isGroup);
                        mCapturing = false;
                        closeWs();
                        refresh();
                        toast("绑定成功");
                    });
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void fail(String msg) {
        mCapturing = false;
        closeWs();
        mBtnCapture.setEnabled(true);
        mTvStatus.setText("绑定失败");
        mTvDetail.setText(msg);
    }

    private void closeWs() {
        if (mWs != null) {
            mWs.closeQuietly();
            mWs = null;
        }
    }

    private void testSend() {
        if (!QQBotStore.isConfigured(this)) {
            toast("请先完成绑定");
            return;
        }
        toast("正在发送测试消息…");
        new Thread(() -> {
            String content = "【智码通】QQ 机器人测试消息\n时间："
                    + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                    java.util.Locale.getDefault()).format(new java.util.Date());
            QQBotApi.SendResult sr = QQBotApi.sendWithRetry(
                    QQBotStore.appId(this), QQBotStore.appSecret(this),
                    QQBotStore.cachedToken(this), QQBotStore.openId(this),
                    QQBotStore.isGroup(this), content);
            final String err = sr.ok ? null : sr.reason();
            mUi.post(() -> {
                if (err == null) {
                    toast("已发送，请查看 QQ");
                } else {
                    mTvDetail.setText("测试失败：" + err);
                    toast("测试失败：" + err);
                }
            });
        }, "qqbot-test").start();
    }

    private void confirmUnbind() {
        QQBotStore.clearSession(this);
        refresh();
        toast("已解除绑定（AppID/Secret 保留）");
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
