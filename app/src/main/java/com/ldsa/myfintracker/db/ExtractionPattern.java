package com.ldsa.myfintracker.db;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExtractionPattern {

    public static final String TYPE_UPI                  = "UPI";
    public static final String TYPE_CARD_ONLINE          = "CARD_ONLINE";
    public static final String TYPE_CARD_POS             = "CARD_POS";
    public static final String TYPE_NETBANKING_PURCHASE  = "NETBANKING_PURCHASE";
    public static final String TYPE_NETBANKING_TRANSFER  = "NETBANKING_TRANSFER";
    public static final String TYPE_ATM                  = "ATM";
    public static final String TYPE_OTHER                = "";

    public long   id;
    public long   senderId;
    public String name;
    public String transactionType;
    public String templateText;    // editable SMS template with tokens
    public String templateRegex;   // single combined regex built from templateText
    public boolean isPdf;
    public int    amountGroup   = -1;
    public int    balanceGroup  = -1;
    public int    merchantGroup = -1;
    public int    cardGroup     = -1;
    public int    accountGroup  = -1;
    public int    dateGroup     = -1;
    public int    timeGroup     = -1;

    /** Run the combined regex and return the value of the given capture group. */
    public String extractGroup(String smsBody, int group) {
        if (smsBody == null || templateRegex == null || group < 0) return "";
        try {
            Matcher m = Pattern.compile(templateRegex,
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(smsBody);
            if (m.find() && group <= m.groupCount()) {
                String v = m.group(group);
                return v != null ? v : "";
            }
        } catch (Exception ignored) {}
        return "";
    }

    /** True if the combined regex matches and yields a non-empty amount. */
    public boolean matches(String smsBody) {
        return amountGroup >= 0 && !extractGroup(smsBody, amountGroup).isEmpty();
    }

    public String getTypeLabel() {
        if (TYPE_UPI.equals(transactionType))                 return "UPI";
        if (TYPE_CARD_ONLINE.equals(transactionType))         return "Card Online";
        if (TYPE_CARD_POS.equals(transactionType))            return "Card POS";
        if (TYPE_NETBANKING_PURCHASE.equals(transactionType)) return "Netbanking Purchase";
        if (TYPE_NETBANKING_TRANSFER.equals(transactionType)) return "Netbanking Transfer";
        if (TYPE_ATM.equals(transactionType))                 return "ATM";
        return "Other";
    }

    public boolean isOnlineType() {
        return TYPE_UPI.equals(transactionType)
            || TYPE_CARD_ONLINE.equals(transactionType)
            || TYPE_NETBANKING_PURCHASE.equals(transactionType)
            || TYPE_NETBANKING_TRANSFER.equals(transactionType);
    }
}
