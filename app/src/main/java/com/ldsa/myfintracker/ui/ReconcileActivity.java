package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;
import com.ldsa.myfintracker.db.ExpenseDatabase;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Shows SMS vs PDF expenses for a given month side-by-side.
 *
 * Matching rule: same amount (±₹0.01) AND dates within ±3 days → likely duplicate.
 * Already-reconciled pairs are excluded from the LIKELY DUPLICATES section.
 *
 * Three sections:
 *   LIKELY DUPLICATES  — unreconciled matched pairs; ✓ Accept links them, 🗑 deletes one
 *   SMS ONLY           — unmatched SMS expenses (not reconciled)
 *   PDF ONLY           — unmatched PDF expenses (not reconciled)
 */
public class ReconcileActivity extends Activity {

    public static final String EXTRA_MONTH_LABEL = "month_label";
    public static final String EXTRA_MONTH_START = "month_start";
    public static final String EXTRA_MONTH_END   = "month_end";

    static final int TYPE_HEADER = 0;
    static final int TYPE_PAIR   = 1;
    static final int TYPE_SINGLE = 2;

    private static final long MATCH_WINDOW_MS = 3L * 24 * 60 * 60 * 1000;

    private ExpenseDatabase mDb;
    private ReconcileAdapter mAdapter;
    private long mMonthStart, mMonthEnd;
    private List<Expense[]> mPendingPairs = new ArrayList<Expense[]>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reconcile);
        getWindow().setStatusBarColor(0xFF1565C0);

        mDb = ExpenseDatabase.getInstance(this);
        mMonthStart = getIntent().getLongExtra(EXTRA_MONTH_START, 0);
        mMonthEnd   = getIntent().getLongExtra(EXTRA_MONTH_END,   0);
        String label = getIntent().getStringExtra(EXTRA_MONTH_LABEL);
        if (label != null) {
            ((TextView) findViewById(R.id.tvTitle)).setText("⇄ " + label);
        }

        mAdapter = new ReconcileAdapter(this);
        ((ListView) findViewById(R.id.listReconcile)).setAdapter(mAdapter);

        findViewById(R.id.btnBack).setOnClickListener(new BackListener(this));
        findViewById(R.id.btnAcceptAll).setOnClickListener(new AcceptAllListener(this));

        load();
    }

    void load() {
        List<Expense> all = mDb.getExpensesInRange(mMonthStart, mMonthEnd);

        List<Expense> smsList = new ArrayList<Expense>();
        List<Expense> pdfList = new ArrayList<Expense>();
        for (int i = 0; i < all.size(); i++) {
            Expense e = all.get(i);
            if ("sms".equals(e.source))      smsList.add(e);
            else if ("pdf".equals(e.source)) pdfList.add(e);
        }

        // Greedy 1-to-1 match by amount + date proximity.
        // Skip expenses that are already reconciled with each other.
        boolean[] pdfMatched = new boolean[pdfList.size()];
        mPendingPairs.clear();
        List<Expense>   smsOnly = new ArrayList<Expense>();

        for (int i = 0; i < smsList.size(); i++) {
            Expense sms = smsList.get(i);
            // Already reconciled — skip from duplicates section.
            if (sms.reconciledWithId > 0) continue;

            int bestIdx  = -1;
            long bestGap = Long.MAX_VALUE;
            for (int j = 0; j < pdfList.size(); j++) {
                if (pdfMatched[j]) continue;
                Expense pdf = pdfList.get(j);
                if (pdf.reconciledWithId > 0) continue; // already reconciled
                if (Math.abs(sms.amount - pdf.amount) > 0.01) continue;
                long gap = Math.abs(sms.dateMs - pdf.dateMs);
                if (gap <= MATCH_WINDOW_MS && gap < bestGap) {
                    bestGap = gap;
                    bestIdx = j;
                }
            }
            if (bestIdx >= 0) {
                pdfMatched[bestIdx] = true;
                mPendingPairs.add(new Expense[]{sms, pdfList.get(bestIdx)});
            } else {
                smsOnly.add(sms);
            }
        }

        List<Expense> pdfOnly = new ArrayList<Expense>();
        for (int j = 0; j < pdfList.size(); j++) {
            if (!pdfMatched[j] && pdfList.get(j).reconciledWithId <= 0)
                pdfOnly.add(pdfList.get(j));
        }

        // Build flat list
        List<Object> items = new ArrayList<Object>();
        if (!mPendingPairs.isEmpty()) {
            items.add("LIKELY DUPLICATES  (" + mPendingPairs.size() + ")");
            for (int i = 0; i < mPendingPairs.size(); i++) items.add(mPendingPairs.get(i));
        }
        if (!smsOnly.isEmpty()) {
            items.add("SMS ONLY  (" + smsOnly.size() + ")");
            for (int i = 0; i < smsOnly.size(); i++) items.add(smsOnly.get(i));
        }
        if (!pdfOnly.isEmpty()) {
            items.add("PDF ONLY  (" + pdfOnly.size() + ")");
            for (int i = 0; i < pdfOnly.size(); i++) items.add(pdfOnly.get(i));
        }
        mAdapter.setItems(items);

        // Summary strip
        int totalSms = smsList.size(), totalPdf = pdfList.size();
        ((TextView) findViewById(R.id.tvSummaryPairs))
            .setText(mPendingPairs.size() + " duplicate" + (mPendingPairs.size() == 1 ? "" : "s"));
        ((TextView) findViewById(R.id.tvSummarySms)).setText(totalSms + " SMS");
        ((TextView) findViewById(R.id.tvSummaryPdf)).setText(totalPdf + " PDF");

        Button btnAcceptAll = (Button) findViewById(R.id.btnAcceptAll);
        btnAcceptAll.setVisibility(mPendingPairs.isEmpty() ? View.GONE : View.VISIBLE);
    }

    void acceptPair(Expense sms, Expense pdf) {
        mDb.reconcileExpenses(sms.id, pdf.id);
        Toast.makeText(this, "Linked ✓", Toast.LENGTH_SHORT).show();
        load();
    }

    void acceptAll() {
        for (int i = 0; i < mPendingPairs.size(); i++) {
            Expense[] pair = mPendingPairs.get(i);
            mDb.reconcileExpenses(pair[0].id, pair[1].id);
        }
        Toast.makeText(this,
            mPendingPairs.size() + " pair" + (mPendingPairs.size() == 1 ? "" : "s") + " linked ✓",
            Toast.LENGTH_SHORT).show();
        load();
    }

    void confirmDelete(final long expenseId, final String label) {
        new AlertDialog.Builder(this)
            .setTitle("Delete expense?")
            .setMessage("Delete the " + label + " expense? This cannot be undone.")
            .setPositiveButton("Delete", new DeleteConfirmedListener(this, expenseId))
            .setNegativeButton("Cancel", null)
            .show();
    }

    void doDelete(long expenseId) {
        mDb.deleteExpense(expenseId);
        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
        load();
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    static class ReconcileAdapter extends BaseAdapter {
        private final ReconcileActivity mA;
        private final List<Object> mItems = new ArrayList<Object>();
        private final SimpleDateFormat mFmt =
            new SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault());
        private final NumberFormat mNumFmt = NumberFormat.getInstance();

        ReconcileAdapter(ReconcileActivity a) {
            mA = a;
            mNumFmt.setMinimumFractionDigits(2);
            mNumFmt.setMaximumFractionDigits(2);
        }

        void setItems(List<Object> items) {
            mItems.clear();
            mItems.addAll(items);
            notifyDataSetChanged();
        }

        public int getCount()        { return mItems.size(); }
        public Object getItem(int p) { return mItems.get(p); }
        public long getItemId(int p) { return p; }
        public int getViewTypeCount(){ return 3; }

        public int getItemViewType(int pos) {
            Object o = mItems.get(pos);
            if (o instanceof String)    return TYPE_HEADER;
            if (o instanceof Expense[]) return TYPE_PAIR;
            return TYPE_SINGLE;
        }

        public View getView(int pos, View convertView, ViewGroup parent) {
            int type = getItemViewType(pos);
            LayoutInflater inf = LayoutInflater.from(mA);

            if (type == TYPE_HEADER) {
                if (convertView == null)
                    convertView = inf.inflate(R.layout.item_reconcile_header, parent, false);
                ((TextView) convertView.findViewById(R.id.tvSectionHeader))
                    .setText((String) mItems.get(pos));
                return convertView;
            }

            if (type == TYPE_PAIR) {
                if (convertView == null)
                    convertView = inf.inflate(R.layout.item_reconcile_pair, parent, false);
                Expense[] pair = (Expense[]) mItems.get(pos);
                Expense sms = pair[0], pdf = pair[1];

                bind(convertView, R.id.tvSmsMerchant, label(sms));
                bind(convertView, R.id.tvSmsDate,     mFmt.format(new Date(sms.dateMs)));
                bind(convertView, R.id.tvSmsAmount,   "₹" + mNumFmt.format(sms.amount));
                bind(convertView, R.id.tvPdfMerchant, label(pdf));
                bind(convertView, R.id.tvPdfDate,     mFmt.format(new Date(pdf.dateMs)));
                bind(convertView, R.id.tvPdfAmount,   "₹" + mNumFmt.format(pdf.amount));

                convertView.findViewById(R.id.btnAcceptPair)
                    .setOnClickListener(new AcceptPairListener(mA, sms, pdf));
                convertView.findViewById(R.id.btnDeleteSms)
                    .setOnClickListener(new DeleteClickListener(mA, sms.id, "SMS"));
                convertView.findViewById(R.id.btnDeletePdf)
                    .setOnClickListener(new DeleteClickListener(mA, pdf.id, "PDF"));
                return convertView;
            }

            // TYPE_SINGLE
            if (convertView == null)
                convertView = inf.inflate(R.layout.item_reconcile_single, parent, false);
            Expense e = (Expense) mItems.get(pos);
            boolean isPdf = "pdf".equals(e.source);
            TextView tag = (TextView) convertView.findViewById(R.id.tvSingleTag);
            tag.setText(isPdf ? "PDF" : "SMS");
            tag.setBackgroundColor(isPdf ? 0xFF2E7D32 : 0xFF1976D2);
            bind(convertView, R.id.tvSingleMerchant, label(e));
            bind(convertView, R.id.tvSingleDate,     mFmt.format(new Date(e.dateMs)));
            bind(convertView, R.id.tvSingleAmount,   "₹" + mNumFmt.format(e.amount));
            return convertView;
        }

        private static String label(Expense e) {
            if (e.merchant != null && !e.merchant.isEmpty()) return e.merchant;
            if (e.reason   != null && !e.reason.isEmpty())   return e.reason;
            return "(no label)";
        }

        private static void bind(View root, int id, String text) {
            ((TextView) root.findViewById(id)).setText(text);
        }
    }

    // ── Listener classes ─────────────────────────────────────────────────────

    static class BackListener implements View.OnClickListener {
        private final ReconcileActivity mA;
        BackListener(ReconcileActivity a) { mA = a; }
        public void onClick(View v) { mA.finish(); }
    }

    static class AcceptAllListener implements View.OnClickListener {
        private final ReconcileActivity mA;
        AcceptAllListener(ReconcileActivity a) { mA = a; }
        public void onClick(View v) { mA.acceptAll(); }
    }

    static class AcceptPairListener implements View.OnClickListener {
        private final ReconcileActivity mA;
        private final Expense mSms, mPdf;
        AcceptPairListener(ReconcileActivity a, Expense sms, Expense pdf) {
            mA = a; mSms = sms; mPdf = pdf;
        }
        public void onClick(View v) { mA.acceptPair(mSms, mPdf); }
    }

    static class DeleteClickListener implements View.OnClickListener {
        private final ReconcileActivity mA;
        private final long mId;
        private final String mLabel;
        DeleteClickListener(ReconcileActivity a, long id, String label) {
            mA = a; mId = id; mLabel = label;
        }
        public void onClick(View v) { mA.confirmDelete(mId, mLabel); }
    }

    static class DeleteConfirmedListener implements DialogInterface.OnClickListener {
        private final ReconcileActivity mA;
        private final long mId;
        DeleteConfirmedListener(ReconcileActivity a, long id) { mA = a; mId = id; }
        public void onClick(DialogInterface d, int which) { mA.doDelete(mId); }
    }
}
