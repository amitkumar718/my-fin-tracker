package com.ldsa.myfintracker.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Trip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TripAdapter extends BaseAdapter {

    static class ListItem {
        Trip   trip;
        int    expenseCount;
        double total;
    }

    private final Context          mCtx;
    private final SimpleDateFormat mFmt =
        new SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault());
    private List<ListItem> mItems = new ArrayList<ListItem>();

    TripAdapter(Context ctx) { mCtx = ctx; }

    void setItems(List<ListItem> items) {
        mItems = items;
        notifyDataSetChanged();
    }

    @Override public int    getCount()        { return mItems.size(); }
    @Override public Object getItem(int pos)  { return mItems.get(pos); }
    @Override public long   getItemId(int pos){ return mItems.get(pos).trip.id; }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        TripHolder h;
        if (convertView == null) {
            convertView = LayoutInflater.from(mCtx).inflate(R.layout.item_trip, parent, false);
            h = new TripHolder();
            h.tvName    = (TextView) convertView.findViewById(R.id.tvTripName);
            h.tvRange   = (TextView) convertView.findViewById(R.id.tvTripRange);
            h.tvSummary = (TextView) convertView.findViewById(R.id.tvTripSummary);
            convertView.setTag(h);
        } else {
            h = (TripHolder) convertView.getTag();
        }

        ListItem item = mItems.get(pos);
        h.tvName.setText(item.trip.name);
        h.tvRange.setText(mFmt.format(new Date(item.trip.startMs))
            + "  →  " + mFmt.format(new Date(item.trip.endMs)));
        h.tvSummary.setText(item.expenseCount + " expense"
            + (item.expenseCount == 1 ? "" : "s")
            + "  ·  ₹" + String.format(Locale.getDefault(), "%.2f", item.total));
        return convertView;
    }

    static class TripHolder {
        TextView tvName;
        TextView tvRange;
        TextView tvSummary;
    }
}
