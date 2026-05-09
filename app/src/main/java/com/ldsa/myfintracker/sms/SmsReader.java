package com.ldsa.myfintracker.sms;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.ldsa.myfintracker.db.SenderConfig;

import java.util.ArrayList;
import java.util.List;

public class SmsReader {

    public static List<SmsMessage> readMatchingSms(Context ctx, List<SenderConfig> configs) {
        List<SmsMessage> result = new ArrayList<SmsMessage>();
        if (configs.isEmpty()) return result;

        Uri uri = Uri.parse("content://sms/inbox");
        String[] proj = {"_id", "address", "body", "date"};
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(uri, proj, null, null, "date DESC");
            if (c == null) return result;
            while (c.moveToNext()) {
                String address = c.getString(c.getColumnIndexOrThrow("address"));
                if (matchesAny(address, configs)) {
                    SmsMessage msg = new SmsMessage();
                    msg.id      = c.getLong(c.getColumnIndexOrThrow("_id"));
                    msg.address = address;
                    msg.body    = c.getString(c.getColumnIndexOrThrow("body"));
                    msg.date    = c.getLong(c.getColumnIndexOrThrow("date"));
                    result.add(msg);
                }
            }
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    public static SenderConfig findConfig(String address, List<SenderConfig> configs) {
        for (SenderConfig cfg : configs) {
            if (cfg.matches(address)) return cfg;
        }
        return null;
    }

    private static boolean matchesAny(String address, List<SenderConfig> configs) {
        for (SenderConfig cfg : configs) {
            if (cfg.matches(address)) return true;
        }
        return false;
    }
}
