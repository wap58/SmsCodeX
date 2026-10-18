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
    /**
     * @param titles   标题（公司名优先，空则用发件号码）
     * @param senders  发件号码（副标题）
     * @param codes    验证码
     */
    public void setData(List<ForwardResultStore.Record> records,
                        List<String> titles, List<String> senders, List<String> codes) {
        mItems.clear();
        for (int i = 0; i < records.size(); i++) {
            Item it = new Item();
            it.record = records.get(i);
            it.title = get(titles, i);
            it.sender = get(senders, i);
            it.code = get(codes, i);
            mItems.add(it);
        }
        notifyDataSetChanged();
    }

    private static String get(List<String> list, int i) {
        return (list != null && i < list.size() && list.get(i) != null) ? list.get(i) : "";
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

        // 标题：公司名（取不到则退回发件号码）；副标题：发件号码
        // 2026-10-18：原来上下都写通道名，重复且信息量低
        String title = it.title;
        if (title.isEmpty()) {
            title = it.sender.isEmpty()
                    ? h.itemView.getContext().getString(R.string.home_recent)
                    : it.sender;
        }
        h.title.setText(title);
        h.title.setTextColor(mTextPrimary);

        String sub = it.sender.isEmpty() ? it.title : it.sender;
        h.sub.setText(sub);
        h.sub.setTextColor(mTextHint);

        // 点整行打开短信详情（2026-10-18：右侧胶囊太占位，改为整行点击）
        h.itemView.setOnClickListener(v -> {
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

        // 分隔线：最后一项不画；颜色随主题
        if (h.divider != null) {
            h.divider.setVisibility(position == mItems.size() - 1 ? View.GONE : View.VISIBLE);
            h.divider.setBackgroundColor(mDark ? 0x1FFFFFFF : 0x14000000);
        }
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
        String title;
        String sender;
        String code;
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView code;
        final TextView title;
        final TextView sub;
        final TextView state;
        final View divider;

        VH(@NonNull View v) {
            super(v);
            code = v.findViewById(R.id.item_code);
            title = v.findViewById(R.id.item_title);
            sub = v.findViewById(R.id.item_sub);
            state = v.findViewById(R.id.item_state);
            divider = v.findViewById(R.id.item_divider);
        }
    }
}
