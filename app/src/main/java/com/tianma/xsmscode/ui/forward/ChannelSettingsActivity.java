package com.tianma.xsmscode.ui.forward;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.smscodf.zhuxf.R;
import com.tianma.xsmscode.ui.app.base.BaseActivity;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * 通道独立参数配置页（2026-09-16，用户指定）：
 * 每个转发通道一个参数页（企业微信应用 / 企微机器人 / 钉钉 / 飞书），
 * 样式与"验证码记录"等独立子页一致。
 */
public class ChannelSettingsActivity extends BaseActivity {

    public static final String EXTRA_CHANNEL = "extra_channel";

    @BindView(R.id.toolbar)
    Toolbar mToolbar;

    public static void open(Context context, String channel) {
        Intent intent = new Intent(context, ChannelSettingsActivity.class);
        intent.putExtra(EXTRA_CHANNEL, channel);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel_settings);
        ButterKnife.bind(this);

        setupToolbar();

        String channel = getIntent().getStringExtra(EXTRA_CHANNEL);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.channel_settings_main_content,
                        ChannelSettingsFragment.newInstance(channel))
                .commit();
    }

    private void setupToolbar() {
        setSupportActionBar(mToolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(ChannelSettingsFragment.titleFor(this,
                    getIntent().getStringExtra(ChannelSettingsActivity.EXTRA_CHANNEL)));
        }
    }
}
