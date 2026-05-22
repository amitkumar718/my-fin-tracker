package com.ldsa.myfintracker.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import com.ldsa.myfintracker.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StatementCandidateAdapter extends BaseAdapter {

    /** A single parsed expense candidate from a bank statement. */
    public static class CandidateExpense {
        public double  amount;
        public long    dateMs;
        public String  merchant   = "";
        public String  card       = "";
        public String  account    = "";
        public String  sourceText = "";   // the PDF line this was extracted from
        public long    patternId  = -1;
        public boolean isOnline;
        public String  transactionType = "";
        public boolean selected   = true;
    }

    private final Context            mCtx;
    private final List<CandidateExpense> mItems;
    private final SimpleDateFormat   mDateFmt =
            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    public StatementCandidateAdapter(Context ctx) {
        mCtx   = ctx;
        mItems = new ArrayList<CandidateExpense>();
    }

    public void setItems(List<CandidateExpense> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public List<CandidateExpense> getSelected() {
        List<CandidateExpense> out = new ArrayList<CandidateExpense>();
        for (CandidateExpense c : mItems) { if (c.selected) out.add(c); }
        return out;
    }

    public void toggleSelected(int pos) {
        if (pos >= 0 && pos < mItems.size()) {
            mItems.get(pos).selected = !mItems.get(pos).selected;
            notifyDataSetChanged();
        }
    }

    @Override public int getCount()                { return mItems.size(); }
    @Override public Object getItem(int pos)       { return mItems.get(pos); }
    @Override public long getItemId(int pos)       { return pos; }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null) {
            convertView = LayoutInflater.from(mCtx)
                    .inflate(R.layout.item_statement_candidate, parent, false);
            h = new Holder();
            h.cbSelect   = (CheckBox) convertView.findViewById(R.id.cbCandidateSelect);
            h.tvAmount   = (TextView) convertView.findViewById(R.id.tvCandAmount);
            h.tvDate     = (TextView) convertView.findViewById(R.id.tvCandDate);
            h.tvMerchant = (TextView) convertView.findViewById(R.id.tvCandMerchant);
            h.tvSource   = (TextView) convertView.findViewById(R.id.tvCandSource);
            convertView.setTag(h);
        } else {
            h = (Holder) convertView.getTag();
        }

        CandidateExpense c = mItems.get(pos);
        h.cbSelect.setChecked(c.selected);
        h.tvAmount.setText(String.format(Locale.getDefault(), "%.2f", c.amount));
        h.tvDate.setText(c.dateMs > 0 ? mDateFmt.format(new Date(c.dateMs)) : "");
        h.tvMerchant.setText(c.merchant.isEmpty() ? "(no merchant)" : c.merchant);
        h.tvSource.setText(c.sourceText);
        return convertView;
    }

    static class Holder {
        CheckBox cbSelect;
        TextView tvAmount;
        TextView tvDate;
        TextView tvMerchant;
        TextView tvSource;
    }
}
