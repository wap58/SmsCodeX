package com.tianma.xsmscode.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.smscodf.zhuxf.R;
import com.tianma.xsmscode.feature.forward.ForwardChannels;
import com.tianma.xsmscode.feature.forward.ForwardResultStore;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页「最近转发」列表适配器（2026-10-18 新增）。
 *
 * <p>数据来源是 {@link ForwardResultStore}（转发结果历史），
 * 不是短信记录表 —— 因为要显示"哪个通道送达"。
 * 每条记录对应一条短信，时间与 {@code SmsMsg#getDate} 相同，
 * 点「短信详情」用它去查真正的短信正文。
 */
public class HomeAdapter extends RecyclerView.Adapter<HomeAdapter.VH> {

    /** 点击「短信详情」的回调 */
    public interface OnDetailClick {
        void onDetail(long time);
    }

    private final List<Item> mItems = new ArrayList<>();
    private OnDetailClick mListener;

    private int mTextPrimary = 0xFF212121;
    private int mTextHint = 0xFF8A8A8E;
    private int mAccent = 0xFF2F9D56;
    private boolean mDark = false;

    public void setColors(int textPrimary, int textHint, int accent, boolean dark) {
        mTextPrimary = textPrimary;
        mTextHint = textHint;
        mAccent = accent;
        mDark = dark;
    }

    public void setOnDetailClick(OnDetailClick l) {
        mListener = l;
    }

    /** 用记录数据刷新列表 */
    public void setData(List<ForwardResultStore.Record> records, List<String> codes) {
        mItems.clear();
        for (int i = 0; i < records.size(); i++) {
            Item it = new Item();
            it.record = records.get(i);
            it.code = (codes != null && i < codes.size()) ? codes.get(i) : "";
            mItems.add(it);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_home_record, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Item it = mItems.get(position);
        ForwardResultStore.Record r = it.record;

        h.code.setText(it.code == null || it.code.isEmpty() ? "—" : it.code);
        h.code.setTextColor(mTextPrimary);

        // 标题：取第一个成功通道的显示名；全失败则取第一个通道
        String channelId = null;
        for (ForwardResultStore.ChannelResult c : r.channels) {
            if (c.ok) {
                channelId = c.channel;
                break;
            }
        }
        if (channelId == null && !r.channels.isEmpty()) {
            channelId = r.channels.get(0).channel;
        }
        String title = channelId == null
                ? h.itemView.getContext().getString(R.string.home_recent)
                : ForwardChannels.name(h.itemView.getContext(), channelId);
        h.title.setText(title);
        h.title.setTextColor(mTextPrimary);

        // 副标题：成功通道串联，如「QQ机器人#息知」
        StringBuilder sub = new StringBuilder();
        for (ForwardResultStore.ChannelResult c : r.channels) {
            if (!c.ok) {
                continue;
            }
            if (sub.length() > 0) {
                sub.append('#');
            }
            sub.append(ForwardChannels.name(h.itemView.getContext(), c.channel));
        }
        if (sub.length() == 0) {
            sub.append(h.itemView.getContext().getString(R.string.home_state_pending));
        }
        h.sub.setText(sub.toString());
        h.sub.setTextColor(mTextHint);

        // 「短信详情」描边胶囊
        h.detail.setTextColor(mAccent);
        h.detail.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onDetail(r.time);
            }
        });

        // 送达状态胶囊：全成功=已送达；有失败=有失败
        boolean allOk = r.allOk() && !r.channels.isEmpty();
        h.state.setText(allOk
                ? h.itemView.getContext().getString(R.string.home_state_sent)
                : h.itemView.getContext().getString(R.string.home_state_failed));
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        pill.setCornerRadius(dp(h.itemView, 14));
        int color = allOk ? mAccent : 0xFFE05252;
        pill.setColor(withAlpha(color, mDark ? 0.22f : 0.14f));
        h.state.setBackground(pill);
        h.state.setTextColor(color);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    private static int dp(View v, float value) {
        return (int) (value * v.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int withAlpha(int color, float alpha) {
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static final class Item {
        ForwardResultStore.Record record;
        String code;
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView code;
        final TextView title;
        final TextView sub;
        final TextView detail;
        final TextView state;

        VH(@NonNull View v) {
            super(v);
            code = v.findViewById(R.id.item_code);
            title = v.findViewById(R.id.item_title);
            sub = v.findViewById(R.id.item_sub);
            detail = v.findViewById(R.id.item_detail);
            state = v.findViewById(R.id.item_state);
        }
    }
}
