package com.ldsa.myfintracker.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.sms.SmsMessage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SmsAdapter extends BaseAdapter {

    private final Context mCtx;
    private List<SmsMessage> mList;

    SmsAdapter(Context ctx, List<SmsMessage> list) {
        mCtx  = ctx;
        mList = list;
    }

    void setItems(List<SmsMessage> list) {
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
            convertView = LayoutInflater.from(mCtx).inflate(R.layout.item_sms, parent, false);
            h = new ViewHolder();
            h.tvSender = (TextView) convertView.findViewById(R.id.tvSender);
            h.tvDate   = (TextView) convertView.findViewById(R.id.tvDate);
            h.tvBody   = (TextView) convertView.findViewById(R.id.tvBody);
            convertView.setTag(h);
        } else {
            h = (ViewHolder) convertView.getTag();
        }

        SmsMessage msg = mList.get(pos);
        h.tvSender.setText(msg.address != null ? msg.address : "Unknown");
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM  HH:mm", Locale.getDefault());
        h.tvDate.setText(msg.date > 0 ? sdf.format(new Date(msg.date)) : "");
        h.tvBody.setText(msg.body != null ? msg.body : "");

        return convertView;
    }

    static class ViewHolder {
        TextView tvSender;
        TextView tvDate;
        TextView tvBody;
    }
}
