package com.ldsa.myfintracker.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ldsa.myfintracker.R;

import java.util.ArrayList;
import java.util.List;

public class PdfLineAdapter extends BaseAdapter {

    static class PdfLine {
        String text;
        String matchSummary; // null = no pattern match; "₹500.00  Amazon Pay" = matched
    }

    private final Context        mCtx;
    private final List<PdfLine>  mItems = new ArrayList<PdfLine>();

    public PdfLineAdapter(Context ctx) { mCtx = ctx; }

    public void setItems(List<PdfLine> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    /** Returns the raw text of line at position. */
    public String getLineText(int pos) {
        return (pos >= 0 && pos < mItems.size()) ? mItems.get(pos).text : "";
    }

    @Override public int getCount()          { return mItems.size(); }
    @Override public Object getItem(int pos) { return mItems.get(pos); }
    @Override public long getItemId(int pos) { return pos; }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null) {
            convertView = LayoutInflater.from(mCtx)
                    .inflate(R.layout.item_pdf_line, parent, false);
            h = new Holder();
            h.tvText  = (TextView) convertView.findViewById(R.id.tvPdfLineText);
            h.tvMatch = (TextView) convertView.findViewById(R.id.tvPdfLineMatch);
            convertView.setTag(h);
        } else {
            h = (Holder) convertView.getTag();
        }

        PdfLine line = mItems.get(pos);
        h.tvText.setText(line.text);
        if (line.matchSummary != null && !line.matchSummary.isEmpty()) {
            h.tvMatch.setText(line.matchSummary);
            h.tvMatch.setVisibility(View.VISIBLE);
        } else {
            h.tvMatch.setVisibility(View.GONE);
        }
        return convertView;
    }

    static class Holder {
        TextView tvText;
        TextView tvMatch;
    }
}
