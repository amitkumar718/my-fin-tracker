package com.ldsa.myfintracker.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.PdfStatement;

import java.util.ArrayList;
import java.util.List;

public class PdfStatementAdapter extends BaseAdapter {

    private final LayoutInflater mInflater;
    private List<PdfStatement>   mItems  = new ArrayList<PdfStatement>();
    private List<Integer>        mCounts = new ArrayList<Integer>();

    PdfStatementAdapter(Context ctx) {
        mInflater = LayoutInflater.from(ctx);
    }

    void setData(List<PdfStatement> items, List<Integer> counts) {
        mItems  = items;
        mCounts = counts;
        notifyDataSetChanged();
    }

    @Override public int getCount()          { return mItems.size(); }
    @Override public Object getItem(int pos) { return mItems.get(pos); }
    @Override public long getItemId(int pos) { return mItems.get(pos).id; }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_pdf_statement, parent, false);
        }
        PdfStatement s = mItems.get(pos);
        int count = (pos < mCounts.size()) ? mCounts.get(pos) : 0;

        TextView tvBank  = (TextView) convertView.findViewById(R.id.tvStmtBank);
        TextView tvMonth = (TextView) convertView.findViewById(R.id.tvStmtMonth);
        TextView tvFile  = (TextView) convertView.findViewById(R.id.tvStmtFile);
        TextView tvCount = (TextView) convertView.findViewById(R.id.tvStmtCount);

        tvBank.setText(s.bankName != null ? s.bankName : "—");
        tvMonth.setText(s.statementPeriod   != null ? s.statementPeriod    : "");
        tvFile.setText(s.displayName != null ? s.displayName : s.uri);

        boolean imported = s.id > 0;
        if (imported) {
            tvBank.setTextColor(0xFF212121);
            tvBank.setTypeface(null, Typeface.BOLD);
        } else {
            tvBank.setTextColor(0xFF9E9E9E);
            tvBank.setTypeface(null, Typeface.NORMAL);
        }

        if (imported) {
            tvCount.setText(count + " expenses");
            tvCount.setVisibility(View.VISIBLE);
        } else {
            tvCount.setVisibility(View.GONE);
        }

        return convertView;
    }

    PdfStatement getStatement(int pos) { return mItems.get(pos); }
}
