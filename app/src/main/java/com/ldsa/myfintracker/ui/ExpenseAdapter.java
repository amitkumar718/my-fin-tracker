package com.ldsa.myfintracker.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExpenseAdapter extends BaseAdapter {

    private final Context mCtx;
    private List<Expense> mList;

    ExpenseAdapter(Context ctx, List<Expense> list) {
        mCtx  = ctx;
        mList = list;
    }

    void setItems(List<Expense> list) {
        mList = list;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return mList.size(); }
    @Override public Object getItem(int pos) { return mList.get(pos); }
    @Override public long getItemId(int pos) { return mList.get(pos).id; }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        ViewHolder h;
        if (convertView == null) {
            convertView = LayoutInflater.from(mCtx).inflate(R.layout.item_expense, parent, false);
            h = new ViewHolder();
            h.tvAmount   = (TextView) convertView.findViewById(R.id.tvAmount);
            h.tvDate     = (TextView) convertView.findViewById(R.id.tvDate);
            h.tvMerchant = (TextView) convertView.findViewById(R.id.tvMerchant);
            h.tvBankCard = (TextView) convertView.findViewById(R.id.tvBankCard);
            h.tvOnline   = (TextView) convertView.findViewById(R.id.tvOnline);
            h.tvLabels   = (TextView) convertView.findViewById(R.id.tvLabels);
            convertView.setTag(h);
        } else {
            h = (ViewHolder) convertView.getTag();
        }

        Expense e = mList.get(pos);

        h.tvAmount.setText(formatAmount(e.amount));

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        h.tvDate.setText(e.dateMs > 0 ? sdf.format(new Date(e.dateMs)) : "—");

        h.tvMerchant.setText(e.merchant != null && !e.merchant.isEmpty() ? e.merchant : "Unknown merchant");

        StringBuilder bankCard = new StringBuilder();
        if (e.bank != null && !e.bank.isEmpty()) bankCard.append(e.bank);
        if (e.card != null && !e.card.isEmpty()) {
            if (bankCard.length() > 0) bankCard.append("  •  ");
            bankCard.append(e.card);
        }
        h.tvBankCard.setText(bankCard.toString());

        h.tvOnline.setVisibility(e.isOnline ? View.VISIBLE : View.GONE);
        h.tvOnline.setText("Online");

        List<String> labels = e.getLabels();
        if (labels.isEmpty()) {
            h.tvLabels.setVisibility(View.GONE);
        } else {
            h.tvLabels.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < labels.size(); i++) {
                if (i > 0) sb.append("  ");
                sb.append(labels.get(i));
            }
            h.tvLabels.setText(sb.toString());
        }

        return convertView;
    }

    private static String formatAmount(double amount) {
        long intPart  = (long) amount;
        long fracPart = Math.round((amount - intPart) * 100);
        return String.format(Locale.getDefault(), "₹%,d.%02d", intPart, fracPart);
    }

    static class ViewHolder {
        TextView tvAmount;
        TextView tvDate;
        TextView tvMerchant;
        TextView tvBankCard;
        TextView tvOnline;
        TextView tvLabels;
    }
}
