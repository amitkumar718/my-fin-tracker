package com.ldsa.myfintracker.db;

public class PdfStatement {
    public long   id;
    public long   senderId;   // -1 (reserved for future sender linking)
    public String bankName;   // free-text bank label entered by user
    public boolean isPdf;     // true = PDF extraction; false = plain text
    public String month;      // "YYYY-MM"
    public String uri;        // persistable content:// URI string
    public String displayName;
    public long   createdAt;
    public String lastTransLine; // last transaction line selected from picker
}
