package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.PdfStatement;
import com.ldsa.myfintracker.db.SenderConfig;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * New-look landing dashboard. Phase 1: entered via Settings → "Try new landing"
 * so the regular launcher (MainActivity) is untouched. Later phases flip the
 * launcher to this activity and wire the Reconciliation feature.
 *
 * No sparkline, no income number. Hero shows spent total for the selected
 * month; attention row surfaces orphans, pending PDFs and awaiting-statement
 * months; recent list shows the top-5 expenses in the selected month with a
 * SMS / PDF / MAN source tag per row.
 */
public class LandingActivity extends Activity {

    private ExpenseDatabase mDb;
    private final Calendar  mCursor = Calendar.getInstance();

    private final SimpleDateFormat mFmtMonthLong =
        new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
    private final SimpleDateFormat mFmtMonthShort =
        new SimpleDateFormat("MMM yyyy", Locale.getDefault());
    private final SimpleDateFormat mFmtRecentDate =
        new SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landing);
        getWindow().setStatusBarColor(0xFF1565C0);
        setTitle(R.string.title_landing);

        mDb = ExpenseDatabase.getInstance(this);

        // Start on current month, zeroed to day 1.
        mCursor.set(Calendar.DAY_OF_MONTH, 1);
        zeroTime(mCursor);

        findViewById(R.id.btnPrevMonth).setOnClickListener(new MonthStepListener(this, -1));
        findViewById(R.id.btnNextMonth).setOnClickListener(new MonthStepListener(this, +1));
        findViewById(R.id.btnSettings) .setOnClickListener(new OpenActivityListener(this, SettingsActivity.class));
        findViewById(R.id.btnViewAll)  .setOnClickListener(new OpenActivityListener(this, MainActivity.class));

        // Bottom nav wiring. Home has no listener — we're already on this screen.
        findViewById(R.id.navExpenses).setOnClickListener(new OpenActivityListener(this, MainActivity.class));
        findViewById(R.id.navAdd)     .setOnClickListener(new OpenAddSheetListener(this));
        findViewById(R.id.navTrips)   .setOnClickListener(new OpenActivityListener(this, TripListActivity.class));
        findViewById(R.id.navMore)    .setOnClickListener(new OpenMoreSheetListener(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    void stepMonth(int delta) {
        mCursor.add(Calendar.MONTH, delta);
        render();
    }

    private void render() {
        long[] range = monthRange(mCursor);
        List<Expense> monthExpenses = mDb.getExpensesInRange(range[0], range[1]);
        boolean isCurrent = isCurrentMonth(mCursor);

        renderMonthPager(isCurrent);
        renderHero(monthExpenses, isCurrent);
        renderAttention();
        renderRecent(monthExpenses);
    }

    private void renderMonthPager(boolean isCurrent) {
        ((TextView) findViewById(R.id.tvMonthLabel))
            .setText(mFmtMonthLong.format(mCursor.getTime()));
        View dot = findViewById(R.id.vMonthDot);
        int dotColor = isCurrent
            ? getColorCompat(R.color.statusLiveDot)
            : (monthHasPdfStatement(mCursor)
                ? getColorCompat(R.color.statusReconciledDot)
                : getColorCompat(R.color.statusNoStmtDot));
        dot.setBackgroundTintList(ColorStateList.valueOf(dotColor));
    }

    private void renderHero(List<Expense> monthExpenses, boolean isCurrent) {
        TextView header = (TextView) findViewById(R.id.tvHeroHeader);
        TextView amount = (TextView) findViewById(R.id.tvHeroAmount);
        TextView provisional = (TextView) findViewById(R.id.tvProvisional);
        TextView caption = (TextView) findViewById(R.id.tvHeroCaption);
        View statusPill = findViewById(R.id.pillStatus);
        View statusDot = findViewById(R.id.vStatusDot);
        TextView statusText = (TextView) findViewById(R.id.tvStatusText);

        header.setText(isCurrent
            ? getString(R.string.landing_spent_header_current)
            : getString(R.string.landing_spent_header_fmt,
                mFmtMonthShort.format(mCursor.getTime()).toUpperCase(Locale.getDefault())));

        double spent = 0;
        int    txCount = 0;
        Set<String> banks = new LinkedHashSet<String>();
        for (int i = 0; i < monthExpenses.size(); i++) {
            Expense e = monthExpenses.get(i);
            if (e.isCredit) continue;
            spent += e.amount;
            txCount++;
            if (e.bank != null && !e.bank.isEmpty()) banks.add(e.bank);
        }
        amount.setText(formatAmount(spent));

        // Caption: "N transactions · bank1, bank2, …" (truncate at 3 banks)
        String bankList = banks.isEmpty()
            ? getString(R.string.landing_hero_caption_banks_none)
            : joinTruncated(banks, 3);
        caption.setText(getString(R.string.landing_hero_caption_fmt, txCount, bankList));

        // Status chip + clock badge
        boolean hasPdf = monthHasPdfStatement(mCursor);
        int bgCol, dotCol, txtCol;
        int textRes;
        if (isCurrent) {
            bgCol  = getColorCompat(R.color.statusLiveBg);
            dotCol = getColorCompat(R.color.statusLiveDot);
            txtCol = getColorCompat(R.color.statusLiveText);
            textRes = R.string.landing_status_live;
            provisional.setVisibility(View.VISIBLE);
        } else if (hasPdf) {
            // Phase 1 approximation — treat any past month with a PDF present as
            // reconciled until Phase 3 wires the real state machine.
            bgCol  = getColorCompat(R.color.statusReconciledBg);
            dotCol = getColorCompat(R.color.statusReconciledDot);
            txtCol = getColorCompat(R.color.statusReconciledText);
            textRes = R.string.landing_status_reconciled;
            provisional.setVisibility(View.GONE);
        } else {
            bgCol  = getColorCompat(R.color.statusNoStmtBg);
            dotCol = getColorCompat(R.color.statusNoStmtDot);
            txtCol = getColorCompat(R.color.statusNoStmtText);
            textRes = R.string.landing_status_no_stmt;
            provisional.setVisibility(View.VISIBLE);
        }
        statusPill.setBackgroundTintList(ColorStateList.valueOf(bgCol));
        statusDot.setBackgroundTintList(ColorStateList.valueOf(dotCol));
        statusText.setTextColor(txtCol);
        statusText.setText(textRes);
    }

    private void renderAttention() {
        FlowLayout container = (FlowLayout) findViewById(R.id.attentionContainer);
        container.removeAllViews();
        TextView header = (TextView) findViewById(R.id.tvAttentionHeader);

        int orphans   = mDb.getOrphanExpenses().size();
        int pdfsToScan = countPdfsPendingScan();
        int awaiting  = countMonthsAwaitingStatement();

        if (orphans == 0 && pdfsToScan == 0 && awaiting == 0) {
            header.setVisibility(View.GONE);
            return;
        }
        header.setVisibility(View.VISIBLE);

        if (pdfsToScan > 0) {
            container.addView(makeAttentionPill(
                getString(R.string.landing_attn_pdf_scan_fmt, pdfsToScan, pdfsToScan == 1 ? "" : "s"),
                R.color.attnBlueBg, R.color.pdfTag, R.color.pdfTag,
                new OpenActivityListener(this, PdfInboxActivity.class)));
        }
        if (orphans > 0) {
            container.addView(makeAttentionPill(
                getString(R.string.landing_attn_orphans_fmt, orphans, orphans == 1 ? "" : "s"),
                R.color.attnRedBg, R.color.orphan, R.color.statusNoStmtText,
                new OpenActivityListener(this, DebugOrphansActivity.class)));
        }
        if (awaiting > 0) {
            container.addView(makeAttentionPill(
                getString(R.string.landing_attn_awaiting_stmt_fmt, awaiting, awaiting == 1 ? "" : "s"),
                R.color.attnYellowBg, R.color.attnYellowDot, R.color.attnYellowText,
                new OpenActivityListener(this, PdfInboxActivity.class)));
        }
    }

    private View makeAttentionPill(String text, int bgRes, int dotRes, int textRes,
                                   View.OnClickListener onClick) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(android.view.Gravity.CENTER_VERTICAL);
        pill.setBackgroundResource(R.drawable.bg_pill);
        pill.setBackgroundTintList(ColorStateList.valueOf(getColorCompat(bgRes)));
        int padH = dp(10);
        int padV = dp(7);
        pill.setPadding(padH, padV, padH + dp(2), padV);
        pill.setOnClickListener(onClick);
        // Skip ripple foreground — View#setForeground is API 23+ and minSdk is 21.

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        pill.setLayoutParams(lp);

        View dot = new View(this);
        dot.setBackgroundResource(R.drawable.bg_circle);
        dot.setBackgroundTintList(ColorStateList.valueOf(getColorCompat(dotRes)));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dlp.rightMargin = dp(8);
        pill.addView(dot, dlp);

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setTextColor(getColorCompat(textRes));
        pill.addView(tv);
        return pill;
    }

    private void renderRecent(List<Expense> monthExpenses) {
        LinearLayout container = (LinearLayout) findViewById(R.id.recentContainer);
        TextView empty = (TextView) findViewById(R.id.tvRecentEmpty);
        container.removeAllViews();

        // monthExpenses from getExpensesInRange is already sorted by date desc.
        int shown = Math.min(5, monthExpenses.size());
        if (shown == 0) {
            empty.setVisibility(View.VISIBLE);
            container.setVisibility(View.GONE);
            return;
        }
        empty.setVisibility(View.GONE);
        container.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < shown; i++) {
            Expense e = monthExpenses.get(i);
            View row = inflater.inflate(R.layout.item_landing_recent, container, false);

            TextView tag = (TextView) row.findViewById(R.id.tvRecentTag);
            TextView merchant = (TextView) row.findViewById(R.id.tvRecentMerchant);
            TextView amount = (TextView) row.findViewById(R.id.tvRecentAmount);
            TextView caption = (TextView) row.findViewById(R.id.tvRecentCaption);

            // Source tag + colour
            String src = e.source != null ? e.source : "sms";
            int tagColor;
            String tagText;
            if ("pdf".equals(src))      { tagColor = R.color.pdfTag;    tagText = getString(R.string.landing_tag_pdf); }
            else if ("manual".equals(src)) { tagColor = R.color.manualTag; tagText = getString(R.string.landing_tag_manual); }
            else                           { tagColor = R.color.smsTag;    tagText = getString(R.string.landing_tag_sms); }
            tag.setText(tagText);
            tag.setBackgroundTintList(ColorStateList.valueOf(getColorCompat(tagColor)));

            String name = (e.merchant != null && !e.merchant.isEmpty()) ? e.merchant
                : (e.reason != null && !e.reason.isEmpty() ? e.reason : "(no description)");
            merchant.setText(name);

            amount.setText(formatAmount(e.amount));
            amount.setTextColor(e.isCredit ? getColorCompat(R.color.credit) : getColorCompat(R.color.debit));

            StringBuilder cap = new StringBuilder();
            cap.append(mFmtRecentDate.format(new Date(e.dateMs)));
            if (e.bank != null && !e.bank.isEmpty()) cap.append(" · ").append(e.bank);
            caption.setText(cap.toString());

            row.setOnClickListener(new ExpenseDetailListener(this, e.id));
            container.addView(row);
        }
    }

    void openExpenseDetail(long id) {
        Intent i = new Intent(this, ExpenseDetailActivity.class);
        i.putExtra(MainActivity.EXTRA_EXPENSE_ID, id);
        startActivity(i);
    }

    // ── Bottom sheets (plain Dialog with window gravity = bottom) ────────────

    void openAddSheet() {
        Dialog d = buildBottomSheet(R.layout.sheet_add);
        d.findViewById(R.id.btnSheetClose) .setOnClickListener(new DismissListener(d));
        d.findViewById(R.id.sheetAddExpense).setOnClickListener(new SheetItemListener(this, d, AddExpenseActivity.class));
        d.findViewById(R.id.sheetImportPdf).setOnClickListener(new SheetItemListener(this, d, PdfInboxActivity.class));
        d.findViewById(R.id.sheetScanSms)  .setOnClickListener(new SheetItemListener(this, d, SmsInboxActivity.class));
        d.show();
    }

    void openMoreSheet() {
        Dialog d = buildBottomSheet(R.layout.sheet_more);
        d.findViewById(R.id.btnSheetClose).setOnClickListener(new DismissListener(d));
        d.findViewById(R.id.sheetStatements).setOnClickListener(new SheetItemListener(this, d, PdfInboxActivity.class));
        d.findViewById(R.id.sheetBanks)     .setOnClickListener(new SheetItemListener(this, d, BankListActivity.class));
        d.findViewById(R.id.sheetSettings)  .setOnClickListener(new SheetItemListener(this, d, SettingsActivity.class));
        d.findViewById(R.id.sheetDebug)     .setOnClickListener(new SheetItemListener(this, d, DebugActivity.class));
        d.show();
    }

    private Dialog buildBottomSheet(int layoutRes) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(layoutRes);
        d.setCanceledOnTouchOutside(true);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));   // transparent window; sheet drawable supplies the surface
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setDimAmount(0.4f);
        }
        return d;
    }


    // ── helpers ──────────────────────────────────────────────────────────────

    private int countPdfsPendingScan() {
        int n = 0;
        List<PdfStatement> all = mDb.getAllPdfStatements();
        for (int i = 0; i < all.size(); i++) {
            PdfStatement s = all.get(i);
            if (mDb.countExpensesByStatement(s.id) == 0) n++;
        }
        return n;
    }

    private int countMonthsAwaitingStatement() {
        // "Awaiting" = any past calendar month that has at least one SMS/manual
        // expense recorded but no PdfStatement row anchored to that month.
        // Phase 1 approximation: enumerate months from the oldest SMS/manual
        // expense up to (not including) the current month.
        List<SenderConfig> senders = mDb.getAllSenders();
        if (senders.isEmpty()) return 0;

        // Oldest expense dateMs — all expenses sorted ascending; first row is oldest.
        List<Expense> ascByDate = mDb.getExpenses("all", "date_ms", false);
        if (ascByDate.isEmpty()) return 0;
        long oldest = Long.MAX_VALUE;
        for (int i = 0; i < ascByDate.size(); i++) {
            long d = ascByDate.get(i).dateMs;
            if (d > 0) { oldest = d; break; }
        }
        if (oldest == Long.MAX_VALUE) return 0;

        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(oldest);
        start.set(Calendar.DAY_OF_MONTH, 1);
        zeroTime(start);

        Calendar end = Calendar.getInstance();
        end.set(Calendar.DAY_OF_MONTH, 1);
        zeroTime(end);
        // "end" = first day of current month. We count up to end-1 month.

        List<PdfStatement> pdfs = mDb.getAllPdfStatements();
        int count = 0;
        Calendar c = (Calendar) start.clone();
        while (c.before(end)) {
            if (!hasPdfForMonth(pdfs, c)) count++;
            c.add(Calendar.MONTH, 1);
        }
        return count;
    }

    private boolean monthHasPdfStatement(Calendar month) {
        List<PdfStatement> pdfs = mDb.getAllPdfStatements();
        return hasPdfForMonth(pdfs, month);
    }

    private static boolean hasPdfForMonth(List<PdfStatement> pdfs, Calendar month) {
        // Match by anchoring statement's createdAt into the same calendar month.
        // Statement.statementPeriod is a human string ("Oct 2026") that we don't
        // reliably parse here; createdAt is close enough for Phase 1.
        int y = month.get(Calendar.YEAR);
        int m = month.get(Calendar.MONTH);
        Calendar scratch = Calendar.getInstance();
        for (int i = 0; i < pdfs.size(); i++) {
            PdfStatement s = pdfs.get(i);
            if (s.createdAt <= 0) continue;
            scratch.setTimeInMillis(s.createdAt);
            if (scratch.get(Calendar.YEAR) == y && scratch.get(Calendar.MONTH) == m) return true;
        }
        return false;
    }

    private static long[] monthRange(Calendar month) {
        Calendar s = (Calendar) month.clone();
        s.set(Calendar.DAY_OF_MONTH, 1);
        zeroTime(s);
        Calendar e = (Calendar) s.clone();
        e.add(Calendar.MONTH, 1);
        e.add(Calendar.MILLISECOND, -1);
        return new long[]{s.getTimeInMillis(), e.getTimeInMillis()};
    }

    private static boolean isCurrentMonth(Calendar c) {
        Calendar now = Calendar.getInstance();
        return c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
            && c.get(Calendar.MONTH) == now.get(Calendar.MONTH);
    }

    private static void zeroTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private static String joinTruncated(Set<String> items, int max) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String s : items) {
            if (i >= max) { sb.append(" +").append(items.size() - max); break; }
            if (i > 0) sb.append(", ");
            sb.append(s);
            i++;
        }
        return sb.toString();
    }

    private static String formatAmount(double v) {
        // ₹1,23,456.78 — Indian grouping. Keep simple: use Locale.US grouping
        // and prefix with "₹" since the whole app targets INR.
        return "₹" + String.format(Locale.US, "%,.2f", v);
    }

    private int getColorCompat(int res) {
        return getResources().getColor(res);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ── Listeners (static inner classes, D8-safe) ────────────────────────────

    static class MonthStepListener implements View.OnClickListener {
        private final LandingActivity mA;
        private final int             mDelta;
        MonthStepListener(LandingActivity a, int delta) { mA = a; mDelta = delta; }
        public void onClick(View v) { mA.stepMonth(mDelta); }
    }

    static class OpenActivityListener implements View.OnClickListener {
        private final Activity mA;
        private final Class<?> mTarget;
        OpenActivityListener(Activity a, Class<?> target) { mA = a; mTarget = target; }
        public void onClick(View v) { mA.startActivity(new Intent(mA, mTarget)); }
    }

    static class ExpenseDetailListener implements View.OnClickListener {
        private final LandingActivity mA;
        private final long            mId;
        ExpenseDetailListener(LandingActivity a, long id) { mA = a; mId = id; }
        public void onClick(View v) { mA.openExpenseDetail(mId); }
    }

    static class OpenAddSheetListener implements View.OnClickListener {
        private final LandingActivity mA;
        OpenAddSheetListener(LandingActivity a) { mA = a; }
        public void onClick(View v) { mA.openAddSheet(); }
    }

    static class OpenMoreSheetListener implements View.OnClickListener {
        private final LandingActivity mA;
        OpenMoreSheetListener(LandingActivity a) { mA = a; }
        public void onClick(View v) { mA.openMoreSheet(); }
    }

    static class DismissListener implements View.OnClickListener {
        private final Dialog mDialog;
        DismissListener(Dialog d) { mDialog = d; }
        public void onClick(View v) { mDialog.dismiss(); }
    }

    static class SheetItemListener implements View.OnClickListener {
        private final LandingActivity mA;
        private final Dialog          mDialog;
        private final Class<?>        mTarget;
        SheetItemListener(LandingActivity a, Dialog d, Class<?> target) {
            mA = a; mDialog = d; mTarget = target;
        }
        public void onClick(View v) {
            mDialog.dismiss();
            mA.startActivity(new Intent(mA, mTarget));
        }
    }
}
