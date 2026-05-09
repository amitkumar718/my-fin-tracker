package com.ldsa.myfintracker.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.SenderConfig;

import java.util.List;

public class SenderConfigAdapter extends BaseAdapter {

    private final Context mCtx;
    private List<SenderConfig> mList;

    SenderConfigAdapter(Context ctx, List<SenderConfig> list) {
        mCtx  = ctx;
        mList = list;
    }

    void setItems(List<SenderConfig> list) {
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
            convertView = LayoutInflater.from(mCtx).inflate(R.layout.item_sender, parent, false);
            h = new ViewHolder();
            h.tvName    = (TextView) convertView.findViewById(R.id.tvSenderName);
            h.tvPattern = (TextView) convertView.findViewById(R.id.tvSenderPattern);
            h.tvBadge   = (TextView) convertView.findViewById(R.id.tvRegexBadge);
            convertView.setTag(h);
        } else {
            h = (ViewHolder) convertView.getTag();
        }

        SenderConfig s = mList.get(pos);
        h.tvName.setText(s.getLabel());
        h.tvPattern.setText(s.pattern);
        h.tvBadge.setVisibility(s.isRegex ? View.VISIBLE : View.GONE);

        return convertView;
    }

    static class ViewHolder {
        TextView tvName;
        TextView tvPattern;
        TextView tvBadge;
    }
}
