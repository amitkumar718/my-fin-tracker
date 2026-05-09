package com.ldsa.myfintracker.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ExpenseDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "fin_tracker.db";
    private static final int DB_VERSION = 1;

    static final String T_EXPENSE  = "expenses";
    static final String E_ID       = "_id";
    static final String E_AMOUNT   = "amount";
    static final String E_DATE_MS  = "date_ms";
    static final String E_MERCHANT = "merchant";
    static final String E_REASON   = "reason";
    static final String E_CARD     = "card";
    static final String E_ONLINE   = "is_online";
    static final String E_BANK     = "bank";
    static final String E_SMS      = "original_sms";
    static final String E_LABELS   = "labels_json";
    static final String E_CREATED  = "created_at";

    static final String T_SENDER     = "sender_configs";
    static final String S_ID         = "_id";
    static final String S_PATTERN    = "pattern";
    static final String S_NAME       = "display_name";
    static final String S_REGEX      = "is_regex";
    static final String S_AMT_REGEX  = "amount_regex";
    static final String S_DATE_REGEX = "date_regex";
    static final String S_MERCH_REG  = "merchant_regex";
    static final String S_CARD_REG   = "card_regex";

    private static ExpenseDatabase sInstance;

    public static ExpenseDatabase getInstance(Context ctx) {
        if (sInstance == null) {
            sInstance = new ExpenseDatabase(ctx.getApplicationContext());
        }
        return sInstance;
    }

    private ExpenseDatabase(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_EXPENSE + " (" +
            E_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            E_AMOUNT + " REAL NOT NULL DEFAULT 0," +
            E_DATE_MS + " INTEGER NOT NULL DEFAULT 0," +
            E_MERCHANT + " TEXT," +
            E_REASON + " TEXT," +
            E_CARD + " TEXT," +
            E_ONLINE + " INTEGER NOT NULL DEFAULT 0," +
            E_BANK + " TEXT," +
            E_SMS + " TEXT," +
            E_LABELS + " TEXT," +
            E_CREATED + " INTEGER NOT NULL" +
        ")");
        db.execSQL("CREATE INDEX idx_date ON " + T_EXPENSE + "(" + E_DATE_MS + ")");
        db.execSQL("CREATE TABLE " + T_SENDER + " (" +
            S_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            S_PATTERN + " TEXT NOT NULL," +
            S_NAME + " TEXT," +
            S_REGEX + " INTEGER NOT NULL DEFAULT 0," +
            S_AMT_REGEX + " TEXT," +
            S_DATE_REGEX + " TEXT," +
            S_MERCH_REG + " TEXT," +
            S_CARD_REG + " TEXT" +
        ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // future migrations
    }

    // ==================== EXPENSE ====================

    public long insertExpense(Expense e) {
        return getWritableDatabase().insert(T_EXPENSE, null, expenseToValues(e));
    }

    public void updateExpense(Expense e) {
        getWritableDatabase().update(T_EXPENSE, expenseToValues(e),
            E_ID + "=?", new String[]{String.valueOf(e.id)});
    }

    public void deleteExpense(long id) {
        getWritableDatabase().delete(T_EXPENSE, E_ID + "=?",
            new String[]{String.valueOf(id)});
    }

    public Expense getExpenseById(long id) {
        Cursor c = getReadableDatabase().query(T_EXPENSE, null,
            E_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        List<Expense> list = expenseCursorToList(c);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<Expense> getExpenses(String filter, String sortCol, boolean sortDesc) {
        String sel = null;
        String[] args = null;

        if ("online".equals(filter)) {
            sel = E_ONLINE + "=1";
        } else if ("offline".equals(filter)) {
            sel = E_ONLINE + "=0";
        } else if ("month".equals(filter)) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            sel  = E_DATE_MS + ">=?";
            args = new String[]{String.valueOf(cal.getTimeInMillis())};
        }

        String col;
        if ("amount".equals(sortCol)) {
            col = E_AMOUNT;
        } else if ("merchant".equals(sortCol)) {
            col = E_MERCHANT + " COLLATE NOCASE";
        } else {
            col = E_DATE_MS;
        }
        String order = col + (sortDesc ? " DESC" : " ASC");

        Cursor c = getReadableDatabase().query(T_EXPENSE, null, sel, args, null, null, order);
        return expenseCursorToList(c);
    }

    public List<Expense> searchExpenses(String query) {
        if (query == null || query.isEmpty()) return getExpenses("all", "date_ms", true);
        String like = "%" + query + "%";
        Cursor c = getReadableDatabase().query(T_EXPENSE, null,
            E_MERCHANT + " LIKE ? OR " + E_REASON + " LIKE ? OR " +
            E_BANK + " LIKE ? OR " + E_CARD + " LIKE ?",
            new String[]{like, like, like, like},
            null, null, E_DATE_MS + " DESC");
        return expenseCursorToList(c);
    }

    // ==================== SENDER ====================

    public long insertSender(SenderConfig s) {
        return getWritableDatabase().insert(T_SENDER, null, senderToValues(s));
    }

    public void updateSender(SenderConfig s) {
        getWritableDatabase().update(T_SENDER, senderToValues(s),
            S_ID + "=?", new String[]{String.valueOf(s.id)});
    }

    public void deleteSender(long id) {
        getWritableDatabase().delete(T_SENDER, S_ID + "=?",
            new String[]{String.valueOf(id)});
    }

    public List<SenderConfig> getAllSenders() {
        Cursor c = getReadableDatabase().query(T_SENDER, null,
            null, null, null, null, S_NAME + " ASC");
        return senderCursorToList(c);
    }

    public SenderConfig getSenderById(long id) {
        Cursor c = getReadableDatabase().query(T_SENDER, null,
            S_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        List<SenderConfig> list = senderCursorToList(c);
        return list.isEmpty() ? null : list.get(0);
    }

    // ==================== Private ====================

    private Expense fromExpenseCursor(Cursor c) {
        Expense e = new Expense();
        e.id          = c.getLong(c.getColumnIndexOrThrow(E_ID));
        e.amount      = c.getDouble(c.getColumnIndexOrThrow(E_AMOUNT));
        e.dateMs      = c.getLong(c.getColumnIndexOrThrow(E_DATE_MS));
        e.merchant    = c.getString(c.getColumnIndexOrThrow(E_MERCHANT));
        e.reason      = c.getString(c.getColumnIndexOrThrow(E_REASON));
        e.card        = c.getString(c.getColumnIndexOrThrow(E_CARD));
        e.isOnline    = c.getInt(c.getColumnIndexOrThrow(E_ONLINE)) != 0;
        e.bank        = c.getString(c.getColumnIndexOrThrow(E_BANK));
        e.originalSms = c.getString(c.getColumnIndexOrThrow(E_SMS));
        e.labelsJson  = c.getString(c.getColumnIndexOrThrow(E_LABELS));
        e.createdAt   = c.getLong(c.getColumnIndexOrThrow(E_CREATED));
        return e;
    }

    private ContentValues expenseToValues(Expense e) {
        ContentValues cv = new ContentValues();
        cv.put(E_AMOUNT,   e.amount);
        cv.put(E_DATE_MS,  e.dateMs);
        cv.put(E_MERCHANT, e.merchant);
        cv.put(E_REASON,   e.reason);
        cv.put(E_CARD,     e.card);
        cv.put(E_ONLINE,   e.isOnline ? 1 : 0);
        cv.put(E_BANK,     e.bank);
        cv.put(E_SMS,      e.originalSms);
        cv.put(E_LABELS,   e.labelsJson);
        cv.put(E_CREATED,  e.createdAt);
        return cv;
    }

    private List<Expense> expenseCursorToList(Cursor c) {
        List<Expense> list = new ArrayList<Expense>();
        try {
            while (c.moveToNext()) list.add(fromExpenseCursor(c));
        } finally {
            c.close();
        }
        return list;
    }

    private SenderConfig fromSenderCursor(Cursor c) {
        SenderConfig s = new SenderConfig();
        s.id            = c.getLong(c.getColumnIndexOrThrow(S_ID));
        s.pattern       = c.getString(c.getColumnIndexOrThrow(S_PATTERN));
        s.displayName   = c.getString(c.getColumnIndexOrThrow(S_NAME));
        s.isRegex       = c.getInt(c.getColumnIndexOrThrow(S_REGEX)) != 0;
        s.amountRegex   = c.getString(c.getColumnIndexOrThrow(S_AMT_REGEX));
        s.dateRegex     = c.getString(c.getColumnIndexOrThrow(S_DATE_REGEX));
        s.merchantRegex = c.getString(c.getColumnIndexOrThrow(S_MERCH_REG));
        s.cardRegex     = c.getString(c.getColumnIndexOrThrow(S_CARD_REG));
        return s;
    }

    private ContentValues senderToValues(SenderConfig s) {
        ContentValues cv = new ContentValues();
        cv.put(S_PATTERN,    s.pattern);
        cv.put(S_NAME,       s.displayName);
        cv.put(S_REGEX,      s.isRegex ? 1 : 0);
        cv.put(S_AMT_REGEX,  s.amountRegex);
        cv.put(S_DATE_REGEX, s.dateRegex);
        cv.put(S_MERCH_REG,  s.merchantRegex);
        cv.put(S_CARD_REG,   s.cardRegex);
        return cv;
    }

    private List<SenderConfig> senderCursorToList(Cursor c) {
        List<SenderConfig> list = new ArrayList<SenderConfig>();
        try {
            while (c.moveToNext()) list.add(fromSenderCursor(c));
        } finally {
            c.close();
        }
        return list;
    }
}
