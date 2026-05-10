package com.ldsa.myfintracker.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpenseDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME    = "fin_tracker.db";
    private static final int    DB_VERSION = 5;

    // ── expenses ──────────────────────────────────────────────────
    static final String T_EXPENSE    = "expenses";
    static final String E_ID         = "_id";
    static final String E_AMOUNT     = "amount";
    static final String E_DATE_MS    = "date_ms";
    static final String E_MERCHANT   = "merchant";
    static final String E_REASON     = "reason";
    static final String E_CARD       = "card";
    static final String E_ONLINE     = "is_online";
    static final String E_BANK       = "bank";
    static final String E_SMS        = "original_sms";
    static final String E_LABELS     = "labels_json";
    static final String E_CREATED    = "created_at";
    static final String E_BALANCE     = "balance";
    static final String E_TXN_TYPE   = "transaction_type";
    static final String E_ACCOUNT_NUM = "account_number";
    static final String E_REMARKS    = "remarks";
    static final String E_PATTERN_ID  = "pattern_id";

    // ── sender_configs ────────────────────────────────────────────
    static final String T_SENDER   = "sender_configs";
    static final String S_ID       = "_id";
    static final String S_PATTERN  = "pattern";
    static final String S_NAME     = "display_name";
    static final String S_REGEX    = "is_regex";

    // ── extraction_patterns ───────────────────────────────────────
    static final String T_PATTERN    = "extraction_patterns";
    static final String P_ID         = "_id";
    static final String P_SENDER_ID  = "sender_id";
    static final String P_NAME       = "name";
    static final String P_TXN_TYPE   = "transaction_type";
    static final String P_TEMPLATE   = "template_text";
    static final String P_TMPL_REGEX = "template_regex";
    static final String P_AMT_GRP    = "amount_group";
    static final String P_BAL_GRP    = "balance_group";
    static final String P_MERCH_GRP  = "merchant_group";
    static final String P_CARD_GRP   = "card_group";
    static final String P_ACCT_GRP   = "account_group";
    static final String P_DATE_GRP   = "date_group";
    static final String P_TIME_GRP   = "time_group";

    // ── cards ─────────────────────────────────────────────────────
    static final String T_CARD      = "cards";
    static final String C_ID        = "_id";
    static final String C_SENDER_ID = "sender_id";
    static final String C_LAST4     = "last4";
    static final String C_NAME      = "display_name";
    static final String C_TYPE      = "card_type";

    private static ExpenseDatabase sInstance;

    public static ExpenseDatabase getInstance(Context ctx) {
        if (sInstance == null) sInstance = new ExpenseDatabase(ctx.getApplicationContext());
        return sInstance;
    }

    private ExpenseDatabase(Context ctx) { super(ctx, DB_NAME, null, DB_VERSION); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_EXPENSE + " (" +
            E_ID       + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            E_AMOUNT   + " REAL NOT NULL DEFAULT 0," +
            E_DATE_MS  + " INTEGER NOT NULL DEFAULT 0," +
            E_MERCHANT + " TEXT," +
            E_REASON   + " TEXT," +
            E_CARD     + " TEXT," +
            E_ONLINE   + " INTEGER NOT NULL DEFAULT 0," +
            E_BANK     + " TEXT," +
            E_SMS      + " TEXT," +
            E_LABELS   + " TEXT," +
            E_CREATED  + " INTEGER NOT NULL," +
            E_BALANCE     + " REAL NOT NULL DEFAULT 0," +
            E_TXN_TYPE    + " TEXT," +
            E_ACCOUNT_NUM + " TEXT," +
            E_REMARKS     + " TEXT," +
            E_PATTERN_ID  + " INTEGER NOT NULL DEFAULT -1" +
        ")");
        db.execSQL("CREATE INDEX idx_date ON " + T_EXPENSE + "(" + E_DATE_MS + ")");

        db.execSQL("CREATE TABLE " + T_SENDER + " (" +
            S_ID      + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            S_PATTERN + " TEXT NOT NULL," +
            S_NAME    + " TEXT," +
            S_REGEX   + " INTEGER NOT NULL DEFAULT 0" +
        ")");

        db.execSQL("CREATE TABLE " + T_PATTERN + " (" +
            P_ID         + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            P_SENDER_ID  + " INTEGER NOT NULL," +
            P_NAME       + " TEXT," +
            P_TXN_TYPE   + " TEXT," +
            P_TEMPLATE   + " TEXT," +
            P_TMPL_REGEX + " TEXT," +
            P_AMT_GRP    + " INTEGER NOT NULL DEFAULT -1," +
            P_BAL_GRP    + " INTEGER NOT NULL DEFAULT -1," +
            P_MERCH_GRP  + " INTEGER NOT NULL DEFAULT -1," +
            P_CARD_GRP   + " INTEGER NOT NULL DEFAULT -1," +
            P_ACCT_GRP   + " INTEGER NOT NULL DEFAULT -1," +
            P_DATE_GRP   + " INTEGER NOT NULL DEFAULT -1," +
            P_TIME_GRP   + " INTEGER NOT NULL DEFAULT -1" +
        ")");

        db.execSQL("CREATE TABLE " + T_CARD + " (" +
            C_ID        + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            C_SENDER_ID + " INTEGER NOT NULL," +
            C_LAST4     + " TEXT," +
            C_NAME      + " TEXT," +
            C_TYPE      + " INTEGER NOT NULL DEFAULT 0" +
        ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + T_EXPENSE);
        db.execSQL("DROP TABLE IF EXISTS " + T_SENDER);
        db.execSQL("DROP TABLE IF EXISTS " + T_PATTERN);
        db.execSQL("DROP TABLE IF EXISTS " + T_CARD);
        onCreate(db);
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
        getWritableDatabase().delete(T_EXPENSE, E_ID + "=?", new String[]{String.valueOf(id)});
    }

    public Expense getExpenseById(long id) {
        Cursor c = getReadableDatabase().query(T_EXPENSE, null,
            E_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        List<Expense> list = expenseCursorToList(c);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<Expense> getExpenses(String filter, String sortCol, boolean sortDesc) {
        String sel  = null;
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
        if ("amount".equals(sortCol))        col = E_AMOUNT;
        else if ("merchant".equals(sortCol)) col = E_MERCHANT + " COLLATE NOCASE";
        else                                 col = E_DATE_MS;
        Cursor c = getReadableDatabase().query(T_EXPENSE, null, sel, args, null, null,
            col + (sortDesc ? " DESC" : " ASC"));
        return expenseCursorToList(c);
    }

    /** Returns the set of original_sms bodies that already have an expense record. */
    public Set<String> getAppliedSmsBodies() {
        Set<String> set = new HashSet<String>();
        Cursor c = getReadableDatabase().query(
            T_EXPENSE, new String[]{E_SMS},
            E_SMS + " IS NOT NULL AND " + E_SMS + " != ''",
            null, null, null, null);
        try {
            while (c.moveToNext()) {
                String body = c.getString(0);
                if (body != null && !body.isEmpty()) set.add(body);
            }
        } finally { c.close(); }
        return set;
    }

    public List<Expense> searchExpenses(String query) {
        if (query == null || query.isEmpty()) return getExpenses("all", "date_ms", true);
        String like = "%" + query + "%";
        Cursor c = getReadableDatabase().query(T_EXPENSE, null,
            E_MERCHANT + " LIKE ? OR " + E_REASON + " LIKE ? OR " +
            E_BANK + " LIKE ? OR " + E_CARD + " LIKE ?",
            new String[]{like, like, like, like}, null, null, E_DATE_MS + " DESC");
        return expenseCursorToList(c);
    }

    public int countExpensesByPattern(long patternId) {
        if (patternId < 0) return 0;
        Cursor c = getReadableDatabase().query(T_EXPENSE, new String[]{"COUNT(*)"},
            E_PATTERN_ID + "=?", new String[]{String.valueOf(patternId)}, null, null, null);
        try { if (c.moveToFirst()) return c.getInt(0); } finally { c.close(); }
        return 0;
    }

    public List<Expense> getExpensesByPattern(long patternId) {
        Cursor c = getReadableDatabase().query(T_EXPENSE, null,
            E_PATTERN_ID + "=?", new String[]{String.valueOf(patternId)}, null, null, null);
        return expenseCursorToList(c);
    }

    /** Re-runs the pattern's combined regex against each linked expense's originalSms.
     *  Updates derived fields; leaves date, reason, remarks and labels untouched.
     *  Returns the number of expenses actually updated. */
    public int reApplyPattern(ExtractionPattern p) {
        if (p.id <= 0 || p.templateRegex == null) return 0;
        List<Expense> candidates = getExpensesByPattern(p.id);
        int updated = 0;
        for (Expense e : candidates) {
            if (e.originalSms == null || e.originalSms.isEmpty()) continue;
            try {
                Matcher m = Pattern.compile(p.templateRegex,
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(e.originalSms);
                if (!m.find()) continue;
                e.amount          = reParseDouble(reGrp(m, p.amountGroup));
                e.merchant        = reGrp(m, p.merchantGroup).trim();
                e.card            = reGrp(m, p.cardGroup).trim();
                e.accountNumber   = reGrp(m, p.accountGroup).trim();
                e.balance         = reParseDouble(reGrp(m, p.balanceGroup));
                e.transactionType = p.transactionType != null ? p.transactionType : "";
                e.isOnline        = reIsOnline(e.transactionType);
                updateExpense(e);
                updated++;
            } catch (Exception ignored) {}
        }
        return updated;
    }

    private static String reGrp(Matcher m, int group) {
        if (group < 0 || group > m.groupCount()) return "";
        String v = m.group(group);
        return v != null ? v : "";
    }

    private static double reParseDouble(String s) {
        String clean = s.replaceAll(",", "").replaceAll("[^0-9.]", "");
        if (clean.isEmpty()) return 0;
        try { return Double.parseDouble(clean); } catch (NumberFormatException e) { return 0; }
    }

    private static boolean reIsOnline(String type) {
        return "UPI".equals(type) || "CARD_ONLINE".equals(type)
            || "NETBANKING_PURCHASE".equals(type) || "NETBANKING_TRANSFER".equals(type);
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
        getWritableDatabase().delete(T_SENDER, S_ID + "=?", new String[]{String.valueOf(id)});
        // cascade delete patterns and cards
        getWritableDatabase().delete(T_PATTERN, P_SENDER_ID + "=?", new String[]{String.valueOf(id)});
        getWritableDatabase().delete(T_CARD,    C_SENDER_ID + "=?", new String[]{String.valueOf(id)});
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

    // ==================== EXTRACTION PATTERN ====================

    public long insertPattern(ExtractionPattern p) {
        return getWritableDatabase().insert(T_PATTERN, null, patternToValues(p));
    }

    public void updatePattern(ExtractionPattern p) {
        getWritableDatabase().update(T_PATTERN, patternToValues(p),
            P_ID + "=?", new String[]{String.valueOf(p.id)});
    }

    public void deletePattern(long id) {
        getWritableDatabase().delete(T_PATTERN, P_ID + "=?", new String[]{String.valueOf(id)});
    }

    public List<ExtractionPattern> getPatternsBySender(long senderId) {
        Cursor c = getReadableDatabase().query(T_PATTERN, null,
            P_SENDER_ID + "=?", new String[]{String.valueOf(senderId)}, null, null, P_ID + " ASC");
        return patternCursorToList(c);
    }

    public List<ExtractionPattern> getAllPatterns() {
        Cursor c = getReadableDatabase().query(T_PATTERN, null,
            null, null, null, null, P_SENDER_ID + " ASC, " + P_ID + " ASC");
        return patternCursorToList(c);
    }

    public ExtractionPattern getPatternById(long id) {
        Cursor c = getReadableDatabase().query(T_PATTERN, null,
            P_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        List<ExtractionPattern> list = patternCursorToList(c);
        return list.isEmpty() ? null : list.get(0);
    }

    // ==================== CARD ====================

    public long insertCard(Card card) {
        return getWritableDatabase().insert(T_CARD, null, cardToValues(card));
    }

    public void updateCard(Card card) {
        getWritableDatabase().update(T_CARD, cardToValues(card),
            C_ID + "=?", new String[]{String.valueOf(card.id)});
    }

    public void deleteCard(long id) {
        getWritableDatabase().delete(T_CARD, C_ID + "=?", new String[]{String.valueOf(id)});
    }

    public List<Card> getCardsBySender(long senderId) {
        Cursor c = getReadableDatabase().query(T_CARD, null,
            C_SENDER_ID + "=?", new String[]{String.valueOf(senderId)}, null, null, C_ID + " ASC");
        return cardCursorToList(c);
    }

    public Card getCardById(long id) {
        Cursor c = getReadableDatabase().query(T_CARD, null,
            C_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        List<Card> list = cardCursorToList(c);
        return list.isEmpty() ? null : list.get(0);
    }

    public Card findCard(long senderId, String last4) {
        if (last4 == null || last4.isEmpty()) return null;
        Cursor c = getReadableDatabase().query(T_CARD, null,
            C_SENDER_ID + "=? AND " + C_LAST4 + "=?",
            new String[]{String.valueOf(senderId), last4}, null, null, null);
        List<Card> list = cardCursorToList(c);
        return list.isEmpty() ? null : list.get(0);
    }

    // ==================== Private helpers ====================

    private Expense fromExpenseCursor(Cursor c) {
        Expense e = new Expense();
        e.id              = c.getLong(c.getColumnIndexOrThrow(E_ID));
        e.amount          = c.getDouble(c.getColumnIndexOrThrow(E_AMOUNT));
        e.dateMs          = c.getLong(c.getColumnIndexOrThrow(E_DATE_MS));
        e.merchant        = c.getString(c.getColumnIndexOrThrow(E_MERCHANT));
        e.reason          = c.getString(c.getColumnIndexOrThrow(E_REASON));
        e.card            = c.getString(c.getColumnIndexOrThrow(E_CARD));
        e.isOnline        = c.getInt(c.getColumnIndexOrThrow(E_ONLINE)) != 0;
        e.bank            = c.getString(c.getColumnIndexOrThrow(E_BANK));
        e.originalSms     = c.getString(c.getColumnIndexOrThrow(E_SMS));
        e.labelsJson      = c.getString(c.getColumnIndexOrThrow(E_LABELS));
        e.createdAt       = c.getLong(c.getColumnIndexOrThrow(E_CREATED));
        e.balance         = c.getDouble(c.getColumnIndexOrThrow(E_BALANCE));
        e.transactionType = c.getString(c.getColumnIndexOrThrow(E_TXN_TYPE));
        e.accountNumber   = c.getString(c.getColumnIndexOrThrow(E_ACCOUNT_NUM));
        e.remarks         = c.getString(c.getColumnIndexOrThrow(E_REMARKS));
        e.patternId       = c.getLong(c.getColumnIndexOrThrow(E_PATTERN_ID));
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
        cv.put(E_BALANCE,      e.balance);
        cv.put(E_TXN_TYPE,     e.transactionType);
        cv.put(E_ACCOUNT_NUM,  e.accountNumber);
        cv.put(E_REMARKS,      e.remarks);
        cv.put(E_PATTERN_ID,   e.patternId);
        return cv;
    }

    private List<Expense> expenseCursorToList(Cursor c) {
        List<Expense> list = new ArrayList<Expense>();
        try { while (c.moveToNext()) list.add(fromExpenseCursor(c)); } finally { c.close(); }
        return list;
    }

    private SenderConfig fromSenderCursor(Cursor c) {
        SenderConfig s = new SenderConfig();
        s.id          = c.getLong(c.getColumnIndexOrThrow(S_ID));
        s.pattern     = c.getString(c.getColumnIndexOrThrow(S_PATTERN));
        s.displayName = c.getString(c.getColumnIndexOrThrow(S_NAME));
        s.isRegex     = c.getInt(c.getColumnIndexOrThrow(S_REGEX)) != 0;
        return s;
    }

    private ContentValues senderToValues(SenderConfig s) {
        ContentValues cv = new ContentValues();
        cv.put(S_PATTERN, s.pattern);
        cv.put(S_NAME,    s.displayName);
        cv.put(S_REGEX,   s.isRegex ? 1 : 0);
        return cv;
    }

    private List<SenderConfig> senderCursorToList(Cursor c) {
        List<SenderConfig> list = new ArrayList<SenderConfig>();
        try { while (c.moveToNext()) list.add(fromSenderCursor(c)); } finally { c.close(); }
        return list;
    }

    private ExtractionPattern fromPatternCursor(Cursor c) {
        ExtractionPattern p = new ExtractionPattern();
        p.id              = c.getLong(c.getColumnIndexOrThrow(P_ID));
        p.senderId        = c.getLong(c.getColumnIndexOrThrow(P_SENDER_ID));
        p.name            = c.getString(c.getColumnIndexOrThrow(P_NAME));
        p.transactionType = c.getString(c.getColumnIndexOrThrow(P_TXN_TYPE));
        p.templateText    = c.getString(c.getColumnIndexOrThrow(P_TEMPLATE));
        p.templateRegex   = c.getString(c.getColumnIndexOrThrow(P_TMPL_REGEX));
        p.amountGroup     = c.getInt(c.getColumnIndexOrThrow(P_AMT_GRP));
        p.balanceGroup    = c.getInt(c.getColumnIndexOrThrow(P_BAL_GRP));
        p.merchantGroup   = c.getInt(c.getColumnIndexOrThrow(P_MERCH_GRP));
        p.cardGroup       = c.getInt(c.getColumnIndexOrThrow(P_CARD_GRP));
        p.accountGroup    = c.getInt(c.getColumnIndexOrThrow(P_ACCT_GRP));
        p.dateGroup       = c.getInt(c.getColumnIndexOrThrow(P_DATE_GRP));
        p.timeGroup       = c.getInt(c.getColumnIndexOrThrow(P_TIME_GRP));
        return p;
    }

    private ContentValues patternToValues(ExtractionPattern p) {
        ContentValues cv = new ContentValues();
        cv.put(P_SENDER_ID,  p.senderId);
        cv.put(P_NAME,       p.name);
        cv.put(P_TXN_TYPE,   p.transactionType);
        cv.put(P_TEMPLATE,   p.templateText);
        cv.put(P_TMPL_REGEX, p.templateRegex);
        cv.put(P_AMT_GRP,    p.amountGroup);
        cv.put(P_BAL_GRP,    p.balanceGroup);
        cv.put(P_MERCH_GRP,  p.merchantGroup);
        cv.put(P_CARD_GRP,   p.cardGroup);
        cv.put(P_ACCT_GRP,   p.accountGroup);
        cv.put(P_DATE_GRP,   p.dateGroup);
        cv.put(P_TIME_GRP,   p.timeGroup);
        return cv;
    }

    private List<ExtractionPattern> patternCursorToList(Cursor c) {
        List<ExtractionPattern> list = new ArrayList<ExtractionPattern>();
        try { while (c.moveToNext()) list.add(fromPatternCursor(c)); } finally { c.close(); }
        return list;
    }

    private Card fromCardCursor(Cursor c) {
        Card card = new Card();
        card.id          = c.getLong(c.getColumnIndexOrThrow(C_ID));
        card.senderId    = c.getLong(c.getColumnIndexOrThrow(C_SENDER_ID));
        card.last4       = c.getString(c.getColumnIndexOrThrow(C_LAST4));
        card.displayName = c.getString(c.getColumnIndexOrThrow(C_NAME));
        card.cardType    = c.getInt(c.getColumnIndexOrThrow(C_TYPE));
        return card;
    }

    private ContentValues cardToValues(Card card) {
        ContentValues cv = new ContentValues();
        cv.put(C_SENDER_ID, card.senderId);
        cv.put(C_LAST4,     card.last4);
        cv.put(C_NAME,      card.displayName);
        cv.put(C_TYPE,      card.cardType);
        return cv;
    }

    private List<Card> cardCursorToList(Cursor c) {
        List<Card> list = new ArrayList<Card>();
        try { while (c.moveToNext()) list.add(fromCardCursor(c)); } finally { c.close(); }
        return list;
    }
}
