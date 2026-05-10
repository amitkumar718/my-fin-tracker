package com.ldsa.myfintracker.db;

public class Card {
    public static final int TYPE_DEBIT  = 0;
    public static final int TYPE_CREDIT = 1;

    public long   id;
    public long   senderId;
    public String last4;
    public String displayName;
    public int    cardType;

    public String getTypeLabel() {
        return cardType == TYPE_CREDIT ? "Credit" : "Debit";
    }

    public String getLabel() {
        if (displayName != null && !displayName.isEmpty()) return displayName;
        return last4 != null ? "**" + last4 : "Unknown";
    }
}
