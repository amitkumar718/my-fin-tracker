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

        int          type;
        SenderConfig senderConfig;
        SmsMessage   sms;

        // extraction results (TYPE_SMS only)
        boolean hasExtraction;
        boolean isNewMatch;       // extraction succeeded AND no expense exists yet
        long    matchedPatternId = -1;
        String  extractedAmount;
        String  extractedBalance;
        String  extractedMerchant;
        String  extractedCard;
        String  extractedAccount;
        String  extractedDate;
        String  transactionType;
    }

    private final Context         mCtx;
    private final SimpleDateFormat mDateFmt =
        new SimpleDateFormat("dd MMM  HH:mm", Locale.getDefault());
    private List<ListItem> mItems = new ArrayList<ListItem>();

    SmsAdapter(Context ctx) { mCtx = ctx; }

    void setItems(List<ListItem> items) {
        mItems = items;
        notifyDataSetChanged();
    }

    @Override public int    getCount()                { return mItems.size(); }
    @Override public Object getItem(int pos)          { return mItems.get(pos); }
    @Override public long   getItemId(int pos) {
        ListItem item = mItems.get(pos);
        return item.type == ListItem.TYPE_SMS ? item.sms.id : -1;
    }
    @Override public int     getViewTypeCount()       { return 2; }
    @Override public int     getItemViewType(int pos) { return mItems.get(pos).type; }
    @Override public boolean isEnabled(int pos)       { return mItems.get(pos).type == ListItem.TYPE_SMS; }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        ListItem item = mItems.get(pos);
        return item.type == ListItem.TYPE_HEADER
            ? getHeaderView(item, convertView, parent)
            : getSmsView(item, convertView, parent);
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
            h.tvSender    = (TextView) convertView.findViewById(R.id.tvSender);
            h.tvDate      = (TextView) convertView.findViewById(R.id.tvDate);
            h.tvBody      = (TextView) convertView.findViewById(R.id.tvBody);
            h.tvExtracted = (TextView) convertView.findViewById(R.id.tvExtracted);
            convertView.setTag(h);
        } else {
            h = (SmsHolder) convertView.getTag();
        }

        SmsMessage msg = item.sms;
        h.tvSender.setText(msg.address != null ? msg.address : "Unknown");
        h.tvDate.setText(msg.date > 0 ? mDateFmt.format(new Date(msg.date)) : "");
        h.tvBody.setText(msg.body != null ? msg.body : "");

        if (item.isNewMatch) {
            convertView.setBackgroundResource(R.drawable.bg_card_extracted); // green = untracked match
            h.tvExtracted.setVisibility(View.VISIBLE);
            h.tvExtracted.setText(buildExtractedLine(item));
        } else if (item.hasExtraction) {
            convertView.setBackgroundResource(R.drawable.bg_card_blue);      // blue = already an expense
            h.tvExtracted.setVisibility(View.VISIBLE);
            h.tvExtracted.setText(buildExtractedLine(item));
        } else {
            convertView.setBackgroundResource(R.drawable.bg_card);
            h.tvExtracted.setVisibility(View.GONE);
        }

        return convertView;
    }

    private String buildExtractedLine(ListItem item) {
        StringBuilder sb = new StringBuilder();
        if (item.extractedAmount != null && !item.extractedAmount.isEmpty())
            sb.append("₹").append(item.extractedAmount);
        if (item.extractedMerchant != null && !item.extractedMerchant.isEmpty()) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(item.extractedMerchant);
        }
        if (item.extractedCard != null && !item.extractedCard.isEmpty()) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append("**").append(item.extractedCard);
        }
        if (item.extractedAccount != null && !item.extractedAccount.isEmpty()) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(item.extractedAccount);
        }
        if (item.extractedBalance != null && !item.extractedBalance.isEmpty()) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append("Bal ₹").append(item.extractedBalance);
        }
        if (item.transactionType != null && !item.transactionType.isEmpty()) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(typeShortLabel(item.transactionType));
        }
        return sb.toString();
    }

    private String typeShortLabel(String type) {
        if ("UPI".equals(type))                  return "UPI";
        if ("CARD_ONLINE".equals(type))           return "Card Online";
        if ("CARD_POS".equals(type))              return "Card POS";
        if ("NETBANKING_PURCHASE".equals(type))   return "Netbanking";
        if ("NETBANKING_TRANSFER".equals(type))   return "Transfer";
        if ("ATM".equals(type))                   return "ATM";
        return "";
    }

    static class HeaderHolder {
        TextView tvName;
        TextView tvPattern;
    }

    static class SmsHolder {
        TextView tvSender;
        TextView tvDate;
        TextView tvBody;
        TextView tvExtracted;
    }
}
