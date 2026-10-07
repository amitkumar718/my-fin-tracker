package com.ldsa.myfintracker.ui;

import android.content.Context;
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
    private List<PdfStatement>   mItems = new ArrayList<PdfStatement>();

    PdfStatementAdapter(Context ctx) {
        mInflater = LayoutInflater.from(ctx);
    }

    void setData(List<PdfStatement> items) {
        mItems = items;
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

        TextView tvBank  = (TextView) convertView.findViewById(R.id.tvStmtBank);
        TextView tvMonth = (TextView) convertView.findViewById(R.id.tvStmtMonth);
        TextView tvFile  = (TextView) convertView.findViewById(R.id.tvStmtFile);

        tvBank.setText(s.bankName != null ? s.bankName : "—");
        tvMonth.setText(s.statementPeriod   != null ? s.statementPeriod    : "");
        tvFile.setText(s.displayName != null ? s.displayName : s.uri);

        return convertView;
    }

    PdfStatement getStatement(int pos) { return mItems.get(pos); }
}
