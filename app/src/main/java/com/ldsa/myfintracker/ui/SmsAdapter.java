package com.ldsa.myfintracker.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.SenderConfig;
import com.ldsa.myfintracker.sms.SmsMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SmsAdapter extends BaseAdapter {

    static class ListItem {
        static final int TYPE_HEADER = 0;
        static final int TYPE_SMS    = 1;

        int type;
        SenderConfig senderConfig;
        SmsMessage sms;
    }

    private final Context mCtx;
    private List<ListItem> mItems = new ArrayList<ListItem>();

    SmsAdapter(Context ctx) {
        mCtx = ctx;
    }

    void setItems(List<ListItem> items) {
        mItems = items;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return mItems.size(); }
    @Override public Object getItem(int pos) { return mItems.get(pos); }
    @Override public long getItemId(int pos) {
        ListItem item = mItems.get(pos);
        return item.type == ListItem.TYPE_SMS ? item.sms.id : -1;
    }
    @Override public int getViewTypeCount() { return 2; }
    @Override public int getItemViewType(int pos) { return mItems.get(pos).type; }
    @Override public boolean isEnabled(int pos) { return mItems.get(pos).type == ListItem.TYPE_SMS; }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        ListItem item = mItems.get(pos);
        if (item.type == ListItem.TYPE_HEADER) {
            return getHeaderView(item, convertView, parent);
        }
        return getSmsView(item, convertView, parent);
    }

    private View getHeaderView(ListItem item, View convertView, ViewGroup parent) {
        HeaderHolder h;
        if (convertView == null) {
            convertView = LayoutInflater.from(mCtx).inflate(R.layout.item_sms_header, parent, false);
            h = new HeaderHolder();
            h.tvName    = (TextView) convertView.findViewById(R.id.tvHeaderName);
            h.tvPattern = (TextView) convertView.findViewById(R.id.tvHeaderPattern);
            convertView.setTag(h);
        } else {
            h = (HeaderHolder) convertView.getTag();
        }
        h.tvName.setText(item.senderConfig.getLabel());
        h.tvPattern.setText(item.senderConfig.pattern
            + (item.senderConfig.isRegex ? "  (regex)" : ""));
        return convertView;
    }

    private View getSmsView(ListItem item, View convertView, ViewGroup parent) {
        SmsHolder h;
        if (convertView == null) {
            convertView = LayoutInflater.from(mCtx).inflate(R.layout.item_sms, parent, false);
            h = new SmsHolder();
            h.tvSender = (TextView) convertView.findViewById(R.id.tvSender);
            h.tvDate   = (TextView) convertView.findViewById(R.id.tvDate);
            h.tvBody   = (TextView) convertView.findViewById(R.id.tvBody);
            convertView.setTag(h);
        } else {
            h = (SmsHolder) convertView.getTag();
        }
        SmsMessage msg = item.sms;
        h.tvSender.setText(msg.address != null ? msg.address : "Unknown");
        h.tvDate.setText(msg.date > 0
            ? new SimpleDateFormat("dd MMM  HH:mm", Locale.getDefault()).format(new Date(msg.date))
            : "");
        h.tvBody.setText(msg.body != null ? msg.body : "");
        return convertView;
    }

    static class HeaderHolder {
        TextView tvName;
        TextView tvPattern;
    }

    static class SmsHolder {
        TextView tvSender;
        TextView tvDate;
        TextView tvBody;
    }
}
