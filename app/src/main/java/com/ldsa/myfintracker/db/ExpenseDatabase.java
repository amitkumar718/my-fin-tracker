package com.ldsa.myfintracker.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpenseDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME    = "fin_tracker.db";
    private static final int    DB_VERSION = 18;

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
    static final String E_STMT_ID     = "pdf_statement_id";
    static final String E_SOURCE      = "source";
    static final String E_IS_CREDIT   = "is_credit";

    // ── sender_configs ────────────────────────────────────────────
    static final String T_SENDER   = "sender_configs";
    static final String S_ID       = "_id";
    static final String S_PATTERN  = "pattern";
    static final String S_NAME     = "display_name";
    static final String S_REGEX    = "is_regex";
    static final String S_PDF_AUTO_CRED = "pdf_auto_credit";

    // ── extraction_patterns ───────────────────────────────────────
    static final String T_PATTERN    = "extraction_patterns";
    static final String P_ID         = "_id";
    static final String P_SENDER_ID  = "sender_id";
    static final String P_NAME       = "name";
    static final String P_TXN_TYPE   = "transaction_type";
    static final String P_TEMPLATE   = "template_text";
    static final String P_TMPL_REGEX = "template_regex";
    static final String P_AMT_GRP    = "amount_group";
    static final String P_AMT_CR_GRP = "amount_cr_group";
    static final String P_AMT_DB_GRP = "amount_db_group";
    static final String P_BAL_GRP    = "balance_group";
    static final String P_MERCH_GRP  = "merchant_group";
    static final String P_CARD_GRP   = "card_group";
    static final String P_ACCT_GRP   = "account_group";
    static final String P_DATE_GRP   = "date_group";
    static final String P_TIME_GRP   = "time_group";
    static final String P_IS_PDF        = "is_pdf";
    static final String P_BANK_NAME_PAT = "bank_name_pat";
    static final String P_PERIOD_PAT    = "period_pat";

    // ── trips ─────────────────────────────────────────────────────
    static final String T_TRIP      = "trips";
    static final String TR_ID       = "_id";
    static final String TR_NAME     = "name";
    static final String TR_START    = "start_ms";
    static final String TR_END      = "end_ms";

    // ── pdf_statements ────────────────────────────────────────────
    static final String T_PDF_STMT  = "pdf_statements";
    static final String PS_ID       = "_id";
    static final String PS_SENDER   = "sender_id";
    static final String PS_BANK     = "bank_name";
    static final String PS_IS_PDF   = "is_pdf";
    static final String PS_MONTH    = "month";
    static final String PS_URI      = "uri";
    static final String PS_NAME     = "display_name";
    static final String PS_CREATED   = "created_at";
    static final String PS_TRANS_LINE  = "last_trans_line";
    static final String PS_BANK_LINE   = "bank_orig_line";
    static final String PS_MONTH_LINE  = "month_orig_line";

    // ── pdf_field_patterns ────────────────────────────────────────
    // ── pdf sources ───────────────────────────────────────────────
    static final String T_PDF_SOURCE   = "pdf_sources";
    static final String SRC_ID         = "_id";
    static final String SRC_SENDER     = "sender_id";
    static final String SRC_PATH       = "path";
    static final String SRC_IS_DROPBOX = "is_dropbox";

    static final String T_PDF_FIELD_PAT = "pdf_field_patterns";
    static final String PFP_ID          = "_id";
    static final String PFP_TYPE        = "field_type";
    static final String PFP_PATTERN     = "pattern";
    static final String PFP_CREATED     = "created_at";

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
            E_PATTERN_ID  + " INTEGER NOT NULL DEFAULT -1," +
            E_STMT_ID     + " INTEGER NOT NULL DEFAULT -1," +
            E_SOURCE      + " TEXT NOT NULL DEFAULT 'sms'," +
            E_IS_CREDIT   + " INTEGER NOT NULL DEFAULT 0" +
        ")");
        db.execSQL("CREATE INDEX idx_date ON " + T_EXPENSE + "(" + E_DATE_MS + ")");

        db.execSQL("CREATE TABLE " + T_SENDER + " (" +
            S_ID      + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            S_PATTERN + " TEXT NOT NULL," +
            S_NAME    + " TEXT," +
            S_REGEX   + " INTEGER NOT NULL DEFAULT 0," +
            S_PDF_AUTO_CRED + " INTEGER NOT NULL DEFAULT 0" +
        ")");

        db.execSQL("CREATE TABLE " + T_PATTERN + " (" +
            P_ID         + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            P_SENDER_ID  + " INTEGER NOT NULL," +
            P_NAME       + " TEXT," +
            P_TXN_TYPE   + " TEXT," +
            P_TEMPLATE   + " TEXT," +
            P_TMPL_REGEX + " TEXT," +
            P_AMT_GRP    + " INTEGER NOT NULL DEFAULT -1," +
            P_AMT_CR_GRP + " INTEGER NOT NULL DEFAULT -1," +
            P_AMT_DB_GRP + " INTEGER NOT NULL DEFAULT -1," +
            P_BAL_GRP    + " INTEGER NOT NULL DEFAULT -1," +
            P_MERCH_GRP  + " INTEGER NOT NULL DEFAULT -1," +
            P_CARD_GRP   + " INTEGER NOT NULL DEFAULT -1," +
            P_ACCT_GRP   + " INTEGER NOT NULL DEFAULT -1," +
            P_DATE_GRP   + " INTEGER NOT NULL DEFAULT -1," +
            P_TIME_GRP   + " INTEGER NOT NULL DEFAULT -1," +
            P_IS_PDF        + " INTEGER NOT NULL DEFAULT 0," +
            P_BANK_NAME_PAT + " TEXT," +
            P_PERIOD_PAT    + " TEXT" +
        ")");

        db.execSQL("CREATE TABLE " + T_PDF_STMT + " (" +
            PS_ID         + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            PS_SENDER     + " INTEGER NOT NULL DEFAULT -1," +
            PS_BANK       + " TEXT," +
            PS_IS_PDF     + " INTEGER NOT NULL DEFAULT 0," +
            PS_MONTH      + " TEXT," +
            PS_URI        + " TEXT NOT NULL," +
            PS_NAME       + " TEXT," +
            PS_CREATED    + " INTEGER NOT NULL," +
            PS_TRANS_LINE  + " TEXT," +
            PS_BANK_LINE   + " TEXT," +
            PS_MONTH_LINE  + " TEXT" +
        ")");

        db.execSQL("CREATE TABLE " + T_CARD + " (" +
            C_ID        + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            C_SENDER_ID + " INTEGER NOT NULL," +
            C_LAST4     + " TEXT," +
            C_NAME      + " TEXT," +
            C_TYPE      + " INTEGER NOT NULL DEFAULT 0" +
        ")");

        db.execSQL("CREATE TABLE " + T_TRIP + " (" +
            TR_ID    + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            TR_NAME  + " TEXT NOT NULL," +
            TR_START + " INTEGER NOT NULL," +
            TR_END   + " INTEGER NOT NULL" +
        ")");

        db.execSQL("CREATE TABLE " + T_PDF_FIELD_PAT + " (" +
            PFP_ID      + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            PFP_TYPE    + " TEXT NOT NULL," +
            PFP_PATTERN + " TEXT NOT NULL," +
            PFP_CREATED + " INTEGER NOT NULL" +
        ")");
        db.execSQL("CREATE TABLE " + T_PDF_SOURCE + " (" +
            SRC_ID         + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            SRC_SENDER     + " INTEGER NOT NULL," +
            SRC_PATH       + " TEXT NOT NULL," +
            SRC_IS_DROPBOX + " INTEGER NOT NULL DEFAULT 0" +
        ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 7) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + T_PDF_STMT + " (" +
                PS_ID      + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                PS_SENDER  + " INTEGER NOT NULL DEFAULT -1," +
                PS_BANK    + " TEXT," +
                PS_IS_PDF  + " INTEGER NOT NULL DEFAULT 0," +
                PS_MONTH   + " TEXT," +
                PS_URI     + " TEXT NOT NULL," +
                PS_NAME    + " TEXT," +
                PS_CREATED + " INTEGER NOT NULL" +
            ")");
            db.execSQL("ALTER TABLE " + T_PATTERN +
                " ADD COLUMN " + P_IS_PDF + " INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 8) {
            try {
                db.execSQL("ALTER TABLE " + T_PDF_STMT +
                    " ADD COLUMN " + PS_BANK + " TEXT");
            } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE " + T_PDF_STMT +
                    " ADD COLUMN " + PS_IS_PDF + " INTEGER NOT NULL DEFAULT 0");
            } catch (Exception ignored) {}
        }
        if (oldVersion < 9) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + T_PDF_FIELD_PAT + " (" +
                PFP_ID      + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                PFP_TYPE    + " TEXT NOT NULL," +
                PFP_PATTERN + " TEXT NOT NULL," +
                PFP_CREATED + " INTEGER NOT NULL" +
            ")");
        }
        if (oldVersion < 10) {
            try {
                db.execSQL("ALTER TABLE " + T_PDF_STMT +
                    " ADD COLUMN " + PS_TRANS_LINE + " TEXT");
            } catch (Exception ignored) {}
        }
        if (oldVersion < 11) {
            try {
                db.execSQL("ALTER TABLE " + T_EXPENSE +
                    " ADD COLUMN " + E_SOURCE + " TEXT NOT NULL DEFAULT 'sms'");
            } catch (Exception ignored) {}
        }
        if (oldVersion < 12) {
            try {
                db.execSQL("ALTER TABLE " + T_EXPENSE +
                    " ADD COLUMN " + E_STMT_ID + " INTEGER NOT NULL DEFAULT -1");
            } catch (Exception ignored) {}
        }
        if (oldVersion < 13) {
            try {
                db.execSQL("ALTER TABLE " + T_PDF_STMT +
                    " ADD COLUMN " + PS_TRANS_LINE + " TEXT");
            } catch (Exception ignored) {}
        }
        if (oldVersion < 14) {
            try {
                db.execSQL("ALTER TABLE " + T_PDF_STMT +
                    " ADD COLUMN " + PS_BANK_LINE + " TEXT");
            } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE " + T_PDF_STMT +
                    " ADD COLUMN " + PS_MONTH_LINE + " TEXT");
            } catch (Exception ignored) {}
        }
        if (oldVersion < 15) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + T_PDF_SOURCE + " (" +
                SRC_ID         + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                SRC_SENDER     + " INTEGER NOT NULL," +
                SRC_PATH       + " TEXT NOT NULL," +
                SRC_IS_DROPBOX + " INTEGER NOT NULL DEFAULT 0" +
            ")");
        }
        if (oldVersion < 16) {
            try { db.execSQL("ALTER TABLE " + T_PATTERN + " ADD COLUMN " + P_BANK_NAME_PAT + " TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + T_PATTERN + " ADD COLUMN " + P_PERIOD_PAT    + " TEXT"); } catch (Exception ignored) {}
        }
        if (oldVersion < 17) {
            try { db.execSQL("ALTER TABLE " + T_PATTERN +
                " ADD COLUMN " + P_AMT_CR_GRP + " INTEGER NOT NULL DEFAULT -1"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + T_PATTERN +
                " ADD COLUMN " + P_AMT_DB_GRP + " INTEGER NOT NULL DEFAULT -1"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + T_EXPENSE +
                " ADD COLUMN " + E_IS_CREDIT  + " INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}

            // Migrate legacy "(/amount/)" tokens → "(/amount_db/)". Generated regex
            // is identical for both; only the group index moves fields.
            try {
                db.execSQL(
                    "UPDATE " + T_PATTERN + " SET " +
                        P_TEMPLATE   + " = replace(" + P_TEMPLATE + ", '(/amount/)', '(/amount_db/)')," +
                        P_AMT_DB_GRP + " = " + P_AMT_GRP + "," +
                        P_AMT_GRP    + " = -1 " +
                    "WHERE " + P_TEMPLATE + " LIKE '%(/amount/)%'");
            } catch (Exception ignored) {}
        }
        if (oldVersion < 18) {
            try { db.execSQL("ALTER TABLE " + T_SENDER +
                " ADD COLUMN " + S_PDF_AUTO_CRED + " INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
        }
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

    /** Returns expenses where source matches src OR source is 'manual'. */
    public List<Expense> getExpensesBySource(String src, String filter, String sortCol, boolean sortDesc) {
        String sourceSel = "(" + E_SOURCE + "=? OR " + E_SOURCE + "='manual')";
        String[] sourceArgs = new String[]{src};
        String filterSel = null;
        String[] filterArgs = null;
        if ("online".equals(filter)) {
            filterSel = E_ONLINE + "=1";
        } else if ("offline".equals(filter)) {
            filterSel = E_ONLINE + "=0";
        } else if ("month".equals(filter)) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            filterSel  = E_DATE_MS + ">=?";
            filterArgs = new String[]{String.valueOf(cal.getTimeInMillis())};
        }
        String sel;
        String[] args;
        if (filterSel != null) {
            sel  = sourceSel + " AND " + filterSel;
            if (filterArgs != null) {
                args = new String[]{src, filterArgs[0]};
            } else {
                args = sourceArgs;
            }
        } else {
            sel  = sourceSel;
            args = sourceArgs;
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

    public int countExpensesByStatement(long stmtId) {
        if (stmtId < 0) return 0;
        Cursor c = getReadableDatabase().query(T_EXPENSE, new String[]{"COUNT(*)"},
            E_STMT_ID + "=?", new String[]{String.valueOf(stmtId)}, null, null, null);
        try { if (c.moveToFirst()) return c.getInt(0); } finally { c.close(); }
        return 0;
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

    /** Re-runs the pattern's combined regex against each linked expense's transLine.
     *  Updates derived fields; leaves date, reason, remarks and labels untouched.
     *  Returns the number of expenses actually updated. */
    public static class ReApplyResult {
        public int updated;
        public int orphaned;
        /** Previously-orphaned expenses that now match the edited pattern and
         *  have been re-linked to it. Only populated for PDF patterns. */
        public int adopted;
    }

    /** Returns PDF expenses that are no longer linked to any pattern but
     *  belong to a statement from the given sender. Ordered by dateMs ASC so
     *  the auto-credit classifier can chain balance deltas correctly. */
    public List<Expense> getOrphanedPdfExpensesForSender(long senderId) {
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT * FROM " + T_EXPENSE +
            " WHERE " + E_PATTERN_ID + "=-1" +
            "   AND " + E_STMT_ID + " IN (" +
            "     SELECT " + PS_ID + " FROM " + T_PDF_STMT +
            "       WHERE " + PS_SENDER + "=?" +
            "   )" +
            " ORDER BY " + E_DATE_MS + " ASC",
            new String[]{String.valueOf(senderId)});
        return expenseCursorToList(c);
    }

    public ReApplyResult reApplyPattern(ExtractionPattern p) {
        ReApplyResult r = new ReApplyResult();
        if (p.id <= 0 || p.templateRegex == null) return r;

        List<Expense> candidates = getExpensesByPattern(p.id);
        // For PDF patterns, also try to re-adopt this bank's orphan expenses —
        // rows that lost their pattern link during an earlier edit and might
        // now match the revised regex.
        if (p.isPdf && p.senderId > 0) {
            candidates.addAll(getOrphanedPdfExpensesForSender(p.senderId));
        }

        // Auto-credit re-classifies by balance delta; chain correctness needs
        // date-ascending order across linked + newly-adopted candidates.
        boolean autoCredit = p.senderId > 0 && getSenderPdfAutoCredit(p.senderId);
        if (autoCredit) Collections.sort(candidates, new ExpenseByDateAsc());
        com.ldsa.myfintracker.pdf.PdfCreditClassifier classifier =
                new com.ldsa.myfintracker.pdf.PdfCreditClassifier(autoCredit);

        for (Expense e : candidates) {
            if (e.originalSms == null || e.originalSms.isEmpty()) continue;
            boolean wasLinked = (e.patternId == p.id);
            try {
                Matcher m = Pattern.compile(p.templateRegex,
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(e.originalSms);
                if (!m.find()) {
                    // No match. If it was linked, demote to orphan; if already
                    // orphan, leave alone (don't rewrite rows we didn't change).
                    if (wasLinked) {
                        e.patternId = -1L;
                        updateExpense(e);
                        r.orphaned++;
                    }
                    continue;
                }
                // Credit column → debit column → legacy amount. First non-empty wins.
                String amtStr    = "";
                boolean amountCrMatched = false;
                if (p.amountCrGroup >= 0) {
                    String cr = reGrp(m, p.amountCrGroup).replaceAll("[^0-9.]", "");
                    if (!cr.isEmpty()) { amtStr = cr; amountCrMatched = true; }
                }
                if (amtStr.isEmpty() && p.amountDbGroup >= 0) {
                    amtStr = reGrp(m, p.amountDbGroup).replaceAll("[^0-9.]", "");
                }
                if (amtStr.isEmpty() && p.amountGroup >= 0) {
                    amtStr = reGrp(m, p.amountGroup).replaceAll("[^0-9.]", "");
                }
                String balRaw = reGrp(m, p.balanceGroup).replaceAll("[^0-9.,]", "").replace(",", "");
                boolean hasBalance = !balRaw.isEmpty();
                double balance = hasBalance ? reParseDouble(balRaw) : 0;

                com.ldsa.myfintracker.pdf.PdfCreditClassifier.Decision d =
                        classifier.classify(hasBalance, balance, amountCrMatched);
                if (d.skip) {
                    // Auto-credit decided this row has zero balance delta.
                    // Drop the pattern link if it was previously linked; leave
                    // existing orphans alone.
                    if (wasLinked) {
                        e.patternId = -1L;
                        updateExpense(e);
                        r.orphaned++;
                    }
                    continue;
                }

                e.amount          = reParseDouble(amtStr);
                e.isCredit        = d.isCredit;
                e.merchant        = reGrp(m, p.merchantGroup).trim();
                e.card            = reGrp(m, p.cardGroup).trim();
                e.accountNumber   = reGrp(m, p.accountGroup).trim();
                e.balance         = balance;
                e.transactionType = p.transactionType != null ? p.transactionType : "";
                e.isOnline        = reIsOnline(e.transactionType);
                e.patternId       = p.id;   // (re-)adopt the orphan if that's what it was
                updateExpense(e);
                if (wasLinked) r.updated++;
                else           r.adopted++;
            } catch (Exception ignored) {}
        }
        return r;
    }

    // Raw Comparator (no generic parameter) to match the erased signature that
    // the device-side d8 expects — same pattern as the comparators in PdfTextExtractor.
    private static class ExpenseByDateAsc implements Comparator {
        public int compare(Object oa, Object ob) {
            Expense a = (Expense) oa;
            Expense b = (Expense) ob;
            if (a.dateMs < b.dateMs) return -1;
            if (a.dateMs > b.dateMs) return 1;
            if (a.id < b.id) return -1;
            if (a.id > b.id) return 1;
            return 0;
        }
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

    /** Convenience wrapper so callers can toggle the PDF auto-credit flag
     *  without round-tripping through {@link #updateSender(SenderConfig)}. */
    public void setSenderPdfAutoCredit(long senderId, boolean on) {
        ContentValues cv = new ContentValues();
        cv.put(S_PDF_AUTO_CRED, on ? 1 : 0);
        getWritableDatabase().update(T_SENDER, cv,
            S_ID + "=?", new String[]{String.valueOf(senderId)});
    }

    public boolean getSenderPdfAutoCredit(long senderId) {
        Cursor c = getReadableDatabase().query(T_SENDER, new String[]{S_PDF_AUTO_CRED},
            S_ID + "=?", new String[]{String.valueOf(senderId)}, null, null, null);
        try {
            if (c.moveToFirst()) return c.getInt(0) != 0;
        } finally { c.close(); }
        return false;
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

    /** Sets pattern_id = -1 on every expense tied to the given pattern.
     *  Returns the number of rows orphaned. */
    public int orphanExpensesByPattern(long patternId) {
        if (patternId <= 0) return 0;
        ContentValues cv = new ContentValues();
        cv.put(E_PATTERN_ID, -1L);
        return getWritableDatabase().update(T_EXPENSE, cv,
            E_PATTERN_ID + "=?", new String[]{String.valueOf(patternId)});
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

    public List<ExtractionPattern> getAllPdfPatterns() {
        Cursor c = getReadableDatabase().query(T_PATTERN, null,
            P_IS_PDF + "=1 OR " + P_SENDER_ID + "=-1", null, null, null, P_ID + " ASC");
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

    // ==================== TRIP ====================

    public long insertTrip(Trip t) {
        return getWritableDatabase().insert(T_TRIP, null, tripToValues(t));
    }

    public void updateTrip(Trip t) {
        getWritableDatabase().update(T_TRIP, tripToValues(t),
            TR_ID + "=?", new String[]{String.valueOf(t.id)});
    }

    public void deleteTrip(long id) {
        Trip t = getTripById(id);
        if (t != null) removeTripLabel(t.name);
        getWritableDatabase().delete(T_TRIP, TR_ID + "=?", new String[]{String.valueOf(id)});
    }

    public List<Trip> getAllTrips() {
        Cursor c = getReadableDatabase().query(T_TRIP, null,
            null, null, null, null, TR_START + " DESC");
        return tripCursorToList(c);
    }

    public Trip getTripById(long id) {
        Cursor c = getReadableDatabase().query(T_TRIP, null,
            TR_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        List<Trip> list = tripCursorToList(c);
        return list.isEmpty() ? null : list.get(0);
    }

    // ==================== PDF STATEMENT ====================

    public long insertPdfStatement(PdfStatement s) {
        return getWritableDatabase().insert(T_PDF_STMT, null, pdfStmtToValues(s));
    }

    public void updatePdfStatement(PdfStatement s) {
        getWritableDatabase().update(T_PDF_STMT, pdfStmtToValues(s),
            PS_ID + "=?", new String[]{String.valueOf(s.id)});
    }

    /** Replaces the stored pattern for the given field type ('bank' or 'month'). */
    public void setPdfFieldPattern(String type, String pattern) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(T_PDF_FIELD_PAT, PFP_TYPE + "=?", new String[]{type});
        if (pattern != null && !pattern.isEmpty()) {
            ContentValues cv = new ContentValues();
            cv.put(PFP_TYPE,    type);
            cv.put(PFP_PATTERN, pattern);
            cv.put(PFP_CREATED, System.currentTimeMillis());
            db.insert(T_PDF_FIELD_PAT, null, cv);
        }
    }

    /** Returns the stored pattern for the given field type, or null if none. */
    public String getPdfFieldPattern(String type) {
        Cursor c = getReadableDatabase().query(T_PDF_FIELD_PAT, new String[]{PFP_PATTERN},
            PFP_TYPE + "=?", new String[]{type}, null, null, PFP_CREATED + " DESC", "1");
        try { if (c.moveToFirst()) return c.getString(0); } finally { c.close(); }
        return null;
    }

    /** Updates only senderId and bankName — never touches lastTransLine. */
    public void updatePdfStatementMeta(long id, long senderId, String bankName) {
        ContentValues cv = new ContentValues();
        if (senderId > 0) cv.put(PS_SENDER, senderId);
        if (bankName != null) cv.put(PS_BANK, bankName);
        if (cv.size() > 0) {
            getWritableDatabase().update(T_PDF_STMT, cv,
                PS_ID + "=?", new String[]{String.valueOf(id)});
        }
    }

    public void deletePdfStatement(long id) {
        getWritableDatabase().delete(T_PDF_STMT, PS_ID + "=?", new String[]{String.valueOf(id)});
    }

    // ==================== PDF SOURCES ====================

    public long insertPdfSource(long senderId, String path, boolean isDropbox) {
        android.content.ContentValues cv = new android.content.ContentValues();
        cv.put(SRC_SENDER,     senderId);
        cv.put(SRC_PATH,       path);
        cv.put(SRC_IS_DROPBOX, isDropbox ? 1 : 0);
        return getWritableDatabase().insert(T_PDF_SOURCE, null, cv);
    }

    public List<PdfSource> getPdfSourcesBySender(long senderId) {
        Cursor c = getReadableDatabase().query(T_PDF_SOURCE, null,
            SRC_SENDER + "=?", new String[]{String.valueOf(senderId)},
            null, null, SRC_ID + " ASC");
        List<PdfSource> list = new java.util.ArrayList<PdfSource>();
        if (c == null) return list;
        try {
            while (c.moveToNext()) {
                PdfSource s = new PdfSource();
                s.id         = c.getLong(c.getColumnIndexOrThrow(SRC_ID));
                s.senderId   = c.getLong(c.getColumnIndexOrThrow(SRC_SENDER));
                s.path       = c.getString(c.getColumnIndexOrThrow(SRC_PATH));
                s.isDropbox  = c.getInt(c.getColumnIndexOrThrow(SRC_IS_DROPBOX)) == 1;
                list.add(s);
            }
        } finally { c.close(); }
        return list;
    }

    public void updatePdfSource(long id, String path, boolean isDropbox) {
        android.content.ContentValues cv = new android.content.ContentValues();
        cv.put(SRC_PATH,       path);
        cv.put(SRC_IS_DROPBOX, isDropbox ? 1 : 0);
        getWritableDatabase().update(T_PDF_SOURCE, cv, SRC_ID + "=?",
            new String[]{String.valueOf(id)});
    }

    public void deletePdfSource(long id) {
        getWritableDatabase().delete(T_PDF_SOURCE, SRC_ID + "=?",
            new String[]{String.valueOf(id)});
    }

    public List<PdfStatement> getPdfStatementsBySender(long senderId) {
        Cursor c = getReadableDatabase().query(T_PDF_STMT, null,
            PS_SENDER + "=?", new String[]{String.valueOf(senderId)},
            null, null, PS_CREATED + " DESC");
        return pdfStmtCursorToList(c);
    }

    public List<PdfStatement> getAllPdfStatements() {
        Cursor c = getReadableDatabase().query(T_PDF_STMT, null,
            null, null, null, null, PS_CREATED + " DESC");
        return pdfStmtCursorToList(c);
    }

    public PdfStatement getPdfStatementById(long id) {
        Cursor c = getReadableDatabase().query(T_PDF_STMT, null,
            PS_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        List<PdfStatement> list = pdfStmtCursorToList(c);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<ExtractionPattern> getPdfPatternsBySender(long senderId) {
        Cursor c = getReadableDatabase().query(T_PATTERN, null,
            P_SENDER_ID + "=? AND " + P_IS_PDF + "=1",
            new String[]{String.valueOf(senderId)}, null, null, P_ID + " ASC");
        return patternCursorToList(c);
    }

    public List<SenderConfig> getSendersWithPdfPatterns() {
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT DISTINCT s.* FROM " + T_SENDER + " s" +
            " JOIN " + T_PATTERN + " p ON p." + P_SENDER_ID + "=s." + S_ID +
            " WHERE p." + P_IS_PDF + "=1 ORDER BY s." + S_NAME + " ASC", null);
        return senderCursorToList(c);
    }

    public List<Expense> getExpensesInRange(long startMs, long endMs) {
        Cursor c = getReadableDatabase().query(T_EXPENSE, null,
            E_DATE_MS + " >= ? AND " + E_DATE_MS + " <= ?",
            new String[]{String.valueOf(startMs), String.valueOf(endMs)},
            null, null, E_DATE_MS + " DESC");
        return expenseCursorToList(c);
    }

    /** Adds the trip name as a label to every expense whose date falls within the trip range. */
    public void addTripLabel(Trip t) {
        List<Expense> inRange = getExpensesInRange(t.startMs, t.endMs);
        for (Expense e : inRange) {
            JSONArray arr = parseLabels(e.labelsJson);
            boolean found = false;
            for (int i = 0; i < arr.length(); i++) {
                try { if (t.name.equals(arr.getString(i))) { found = true; break; } }
                catch (JSONException ignored) {}
            }
            if (!found) {
                arr.put(t.name);
                e.labelsJson = serializeLabels(arr);
                updateExpense(e);
            }
        }
    }

    /** Removes the given label string from all expenses that carry it. */
    public void removeTripLabel(String label) {
        if (label == null || label.isEmpty()) return;
        List<Expense> all = expenseCursorToList(getReadableDatabase().query(
            T_EXPENSE, null,
            E_LABELS + " IS NOT NULL AND " + E_LABELS + " LIKE ?",
            new String[]{"%" + label + "%"}, null, null, null));
        for (Expense e : all) {
            JSONArray arr = parseLabels(e.labelsJson);
            JSONArray updated = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                try {
                    String v = arr.getString(i);
                    if (!label.equals(v)) updated.put(v);
                } catch (JSONException ignored) {}
            }
            if (updated.length() != arr.length()) {
                e.labelsJson = serializeLabels(updated);
                updateExpense(e);
            }
        }
    }

    private JSONArray parseLabels(String json) {
        if (json == null || json.isEmpty()) return new JSONArray();
        try { return new JSONArray(json); } catch (JSONException e) { return new JSONArray(); }
    }

    private String serializeLabels(JSONArray arr) {
        return arr.toString();
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
        e.originalSms       = c.getString(c.getColumnIndexOrThrow(E_SMS));
        e.labelsJson      = c.getString(c.getColumnIndexOrThrow(E_LABELS));
        e.createdAt       = c.getLong(c.getColumnIndexOrThrow(E_CREATED));
        e.balance         = c.getDouble(c.getColumnIndexOrThrow(E_BALANCE));
        e.transactionType = c.getString(c.getColumnIndexOrThrow(E_TXN_TYPE));
        e.accountNumber   = c.getString(c.getColumnIndexOrThrow(E_ACCOUNT_NUM));
        e.remarks         = c.getString(c.getColumnIndexOrThrow(E_REMARKS));
        e.patternId       = c.getLong(c.getColumnIndexOrThrow(E_PATTERN_ID));
        int stmtIdx = c.getColumnIndex(E_STMT_ID);
        e.pdfStatementId = (stmtIdx >= 0 && !c.isNull(stmtIdx)) ? c.getLong(stmtIdx) : -1L;
        int srcIdx = c.getColumnIndex(E_SOURCE);
        e.source = (srcIdx >= 0 && !c.isNull(srcIdx)) ? c.getString(srcIdx) : "sms";
        int crIdx = c.getColumnIndex(E_IS_CREDIT);
        e.isCredit = (crIdx >= 0 && !c.isNull(crIdx)) && c.getInt(crIdx) != 0;
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
        cv.put(E_STMT_ID,      e.pdfStatementId);
        cv.put(E_SOURCE,       e.source != null ? e.source : "sms");
        cv.put(E_IS_CREDIT,    e.isCredit ? 1 : 0);
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
        int acIdx = c.getColumnIndex(S_PDF_AUTO_CRED);
        s.pdfAutoCredit = (acIdx >= 0) && c.getInt(acIdx) != 0;
        return s;
    }

    private ContentValues senderToValues(SenderConfig s) {
        ContentValues cv = new ContentValues();
        cv.put(S_PATTERN, s.pattern);
        cv.put(S_NAME,    s.displayName);
        cv.put(S_REGEX,   s.isRegex ? 1 : 0);
        cv.put(S_PDF_AUTO_CRED, s.pdfAutoCredit ? 1 : 0);
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
        p.amountCrGroup   = c.getInt(c.getColumnIndexOrThrow(P_AMT_CR_GRP));
        p.amountDbGroup   = c.getInt(c.getColumnIndexOrThrow(P_AMT_DB_GRP));
        p.balanceGroup    = c.getInt(c.getColumnIndexOrThrow(P_BAL_GRP));
        p.merchantGroup   = c.getInt(c.getColumnIndexOrThrow(P_MERCH_GRP));
        p.cardGroup       = c.getInt(c.getColumnIndexOrThrow(P_CARD_GRP));
        p.accountGroup    = c.getInt(c.getColumnIndexOrThrow(P_ACCT_GRP));
        p.dateGroup       = c.getInt(c.getColumnIndexOrThrow(P_DATE_GRP));
        p.timeGroup       = c.getInt(c.getColumnIndexOrThrow(P_TIME_GRP));
        p.isPdf           = c.getInt(c.getColumnIndexOrThrow(P_IS_PDF)) != 0;
        p.bankNamePat     = c.getString(c.getColumnIndexOrThrow(P_BANK_NAME_PAT));
        p.periodPat       = c.getString(c.getColumnIndexOrThrow(P_PERIOD_PAT));
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
        cv.put(P_AMT_CR_GRP, p.amountCrGroup);
        cv.put(P_AMT_DB_GRP, p.amountDbGroup);
        cv.put(P_BAL_GRP,    p.balanceGroup);
        cv.put(P_MERCH_GRP,  p.merchantGroup);
        cv.put(P_CARD_GRP,   p.cardGroup);
        cv.put(P_ACCT_GRP,   p.accountGroup);
        cv.put(P_DATE_GRP,   p.dateGroup);
        cv.put(P_TIME_GRP,   p.timeGroup);
        cv.put(P_IS_PDF,        p.isPdf ? 1 : 0);
        cv.put(P_BANK_NAME_PAT, p.bankNamePat);
        cv.put(P_PERIOD_PAT,    p.periodPat);
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

    private Trip fromTripCursor(Cursor c) {
        Trip t = new Trip();
        t.id      = c.getLong(c.getColumnIndexOrThrow(TR_ID));
        t.name    = c.getString(c.getColumnIndexOrThrow(TR_NAME));
        t.startMs = c.getLong(c.getColumnIndexOrThrow(TR_START));
        t.endMs   = c.getLong(c.getColumnIndexOrThrow(TR_END));
        return t;
    }

    private ContentValues tripToValues(Trip t) {
        ContentValues cv = new ContentValues();
        cv.put(TR_NAME,  t.name);
        cv.put(TR_START, t.startMs);
        cv.put(TR_END,   t.endMs);
        return cv;
    }

    private List<Trip> tripCursorToList(Cursor c) {
        List<Trip> list = new ArrayList<Trip>();
        try { while (c.moveToNext()) list.add(fromTripCursor(c)); } finally { c.close(); }
        return list;
    }

    private PdfStatement fromPdfStmtCursor(Cursor c) {
        PdfStatement s = new PdfStatement();
        s.id            = c.getLong(c.getColumnIndexOrThrow(PS_ID));
        s.senderId      = c.getLong(c.getColumnIndexOrThrow(PS_SENDER));
        s.bankName      = c.getString(c.getColumnIndexOrThrow(PS_BANK));
        s.isPdf         = c.getInt(c.getColumnIndexOrThrow(PS_IS_PDF)) != 0;
        s.statementPeriod = c.getString(c.getColumnIndexOrThrow(PS_MONTH));
        s.uri             = c.getString(c.getColumnIndexOrThrow(PS_URI));
        s.displayName     = c.getString(c.getColumnIndexOrThrow(PS_NAME));
        s.createdAt       = c.getLong(c.getColumnIndexOrThrow(PS_CREATED));
        int tci = c.getColumnIndex(PS_TRANS_LINE);
        s.sampleTransLine = (tci >= 0 && !c.isNull(tci)) ? c.getString(tci) : null;
        int bci = c.getColumnIndex(PS_BANK_LINE);
        s.bankOrigLine  = (bci >= 0 && !c.isNull(bci)) ? c.getString(bci) : null;
        int mci = c.getColumnIndex(PS_MONTH_LINE);
        s.monthOrigLine = (mci >= 0 && !c.isNull(mci)) ? c.getString(mci) : null;
        return s;
    }

    private ContentValues pdfStmtToValues(PdfStatement s) {
        ContentValues cv = new ContentValues();
        cv.put(PS_SENDER,     s.senderId);
        cv.put(PS_BANK,       s.bankName);
        cv.put(PS_IS_PDF,     s.isPdf ? 1 : 0);
        cv.put(PS_MONTH,      s.statementPeriod);
        cv.put(PS_URI,        s.uri);
        cv.put(PS_NAME,       s.displayName);
        cv.put(PS_CREATED,    s.createdAt);
        cv.put(PS_TRANS_LINE,  s.sampleTransLine);
        cv.put(PS_BANK_LINE,   s.bankOrigLine);
        cv.put(PS_MONTH_LINE,  s.monthOrigLine);
        return cv;
    }

    private List<PdfStatement> pdfStmtCursorToList(Cursor c) {
        List<PdfStatement> list = new ArrayList<PdfStatement>();
        try { while (c.moveToNext()) list.add(fromPdfStmtCursor(c)); } finally { c.close(); }
        return list;
    }
}
