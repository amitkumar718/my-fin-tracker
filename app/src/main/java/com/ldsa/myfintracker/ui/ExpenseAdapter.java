package com.ldsa.myfintracker.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExpenseAdapter extends BaseAdapter {

    static class ListItem {
        static final int TYPE_HEADER  = 0;
        static final int TYPE_EXPENSE = 1;

        int     type;
        // TYPE_HEADER
        String  monthLabel;
        double  monthTotal;
        // TYPE_EXPENSE
        Expense expense;
    }

    private final Context    mCtx;
    private List<ListItem>   mItems = new ArrayList<ListItem>();

    ExpenseAdapter(Context ctx, List<Expense> list) {
        mCtx   = ctx;
        mItems = wrapExpenses(list);
    }

    /** Replaces the list with plain (ungrouped) expenses — used by TripDetailActivity. */
    void setItems(List<Expense> expenses) {
        mItems = wrapExpenses(expenses);
        notifyDataSetChanged();
    }

    /** Replaces the list with pre-built grouped items — used by MainActivity. */
    void setGroupedItems(List<ListItem> items) {
        mItems = items;
        notifyDataSetChanged();
    }

    @Override public int    getCount()        { return mItems.size(); }
    @Override public Object getItem(int pos)  { return mItems.get(pos); }
    @Override public long   getItemId(int pos) {
        ListItem item = mItems.get(pos);
        return item.type == ListItem.TYPE_EXPENSE ? item.expense.id : -1;
    }
    @Override public int  getViewTypeCount()       { return 2; }
    @Override public int  getItemViewType(int pos) { return mItems.get(pos).type; }
    @Override public boolean isEnabled(int pos)    { return mItems.get(pos).type == ListItem.TYPE_EXPENSE; }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        ListItem item = mItems.get(pos);
        return item.type == ListItem.TYPE_HEADER
            ? getHeaderView(item, convertView, parent)
            : getExpenseView(item, convertView, parent);
    }

    private View getHeaderView(ListItem item, View convertView, ViewGroup parent) {
        HeaderHolder h;
        if (convertView == null) {
            convertView = LayoutInflater.from(mCtx)
                .inflate(R.layout.item_month_header, parent, false);
            h = new HeaderHolder();
            h.tvLabel = (TextView) convertView.findViewById(R.id.tvMonthLabel);
            h.tvTotal = (TextView) convertView.findViewById(R.id.tvMonthTotal);
            convertView.setTag(h);
        } else {
            h = (HeaderHolder) convertView.getTag();
        }
        h.tvLabel.setText(item.monthLabel.toUpperCase(Locale.getDefault()));
        h.tvTotal.setText(formatAmount(item.monthTotal));
        return convertView;
    }

    private View getExpenseView(ListItem item, View convertView, ViewGroup parent) {
        ExpenseHolder h;
        if (convertView == null) {
            convertView = LayoutInflater.from(mCtx)
                .inflate(R.layout.item_expense, parent, false);
            h = new ExpenseHolder();
            h.tvAmount   = (TextView) convertView.findViewById(R.id.tvAmount);
            h.tvDate     = (TextView) convertView.findViewById(R.id.tvDate);
            h.tvMerchant = (TextView) convertView.findViewById(R.id.tvMerchant);
            h.tvBankCard = (TextView) convertView.findViewById(R.id.tvBankCard);
            h.tvOnline   = (TextView) convertView.findViewById(R.id.tvOnline);
            h.tvLabels   = (TextView) convertView.findViewById(R.id.tvLabels);
            convertView.setTag(h);
        } else {
            h = (ExpenseHolder) convertView.getTag();
        }

        Expense e = item.expense;
        if (e.isCredit) {
            SpannableStringBuilder sb = new SpannableStringBuilder(formatAmount(e.amount));
            int cr = sb.length();
            sb.append(" (Cr)");
            sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                cr, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            h.tvAmount.setText(sb);
        } else {
            h.tvAmount.setText(formatAmount(e.amount));
        }
        convertView.setBackgroundTintList(e.isCredit
            ? ColorStateList.valueOf(0xFFE8F5E9)  // light green
            : null);

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        h.tvDate.setText(e.dateMs > 0 ? sdf.format(new Date(e.dateMs)) : "—");

        h.tvMerchant.setText(e.merchant != null && !e.merchant.isEmpty()
            ? e.merchant : "Unknown merchant");

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

    private static List<ListItem> wrapExpenses(List<Expense> expenses) {
        List<ListItem> items = new ArrayList<ListItem>();
        for (Expense e : expenses) {
            ListItem item = new ListItem();
            item.type    = ListItem.TYPE_EXPENSE;
            item.expense = e;
            items.add(item);
        }
        return items;
    }

    static class HeaderHolder {
        TextView tvLabel;
        TextView tvTotal;
    }

    static class ExpenseHolder {
        TextView tvAmount;
        TextView tvDate;
        TextView tvMerchant;
        TextView tvBankCard;
        TextView tvOnline;
        TextView tvLabels;
    }
}
