package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;
import com.ldsa.myfintracker.db.ExpenseDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Lists every past month that has at least one SMS expense, showing SMS count,
 * PDF count, and likely-duplicate count. Tap a row to open ReconcileActivity
 * for that month.
 */
public class ReconcileAllActivity extends Activity {

    private static final long MATCH_WINDOW_MS = 3L * 24 * 60 * 60 * 1000;

    private ExpenseDatabase  mDb;
    private MonthAdapter     mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reconcile_all);
        getWindow().setStatusBarColor(0xFF1565C0);

        mDb = ExpenseDatabase.getInstance(this);
        mAdapter = new MonthAdapter(this);

        ListView list = (ListView) findViewById(R.id.listReconcileAll);
        list.setAdapter(mAdapter);
        list.setOnItemClickListener(new RowClickListener(this));

        findViewById(R.id.btnBack).setOnClickListener(new BackListener(this));

        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        // Find the oldest SMS/manual expense to know where to start.
        List<Expense> all = mDb.getExpenses("all", "date_ms", false); // ascending
        long oldest = 0;
        for (int i = 0; i < all.size(); i++) {
            Expense e = all.get(i);
            if (!"pdf".equals(e.source) && e.dateMs > 0) { oldest = e.dateMs; break; }
        }

        List<MonthRow> rows = new ArrayList<MonthRow>();

        if (oldest > 0) {
            Calendar cursor = Calendar.getInstance();
            cursor.setTimeInMillis(oldest);
            cursor.set(Calendar.DAY_OF_MONTH, 1);
            cursor.set(Calendar.HOUR_OF_DAY, 0);
            cursor.set(Calendar.MINUTE, 0);
            cursor.set(Calendar.SECOND, 0);
            cursor.set(Calendar.MILLISECOND, 0);

            Calendar today = Calendar.getInstance();
            today.set(Calendar.DAY_OF_MONTH, 1);
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            SimpleDateFormat fmt = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

            while (!cursor.after(today)) {
                long start = cursor.getTimeInMillis();
                Calendar next = (Calendar) cursor.clone();
                next.add(Calendar.MONTH, 1);
                long end = next.getTimeInMillis() - 1;

                List<Expense> month = mDb.getExpensesInRange(start, end);
                int smsCount = 0, pdfCount = 0;
                List<Expense> smsList = new ArrayList<Expense>();
                List<Expense> pdfList = new ArrayList<Expense>();
                for (int i = 0; i < month.size(); i++) {
                    Expense e = month.get(i);
                    if ("sms".equals(e.source))      { smsCount++; smsList.add(e); }
                    else if ("pdf".equals(e.source)) { pdfCount++; pdfList.add(e); }
                }

                if (smsCount > 0) {
                    int dups = countDuplicates(smsList, pdfList);
                    MonthRow row = new MonthRow();
                    row.label    = fmt.format(cursor.getTime());
                    row.start    = start;
                    row.end      = end;
                    row.smsCount = smsCount;
                    row.pdfCount = pdfCount;
                    row.dupCount = dups;
                    rows.add(0, row); // newest first
                }

                cursor.add(Calendar.MONTH, 1);
            }
        }

        mAdapter.setRows(rows);
        boolean empty = rows.isEmpty();
        findViewById(R.id.tvReconcileAllEmpty).setVisibility(empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.listReconcileAll)   .setVisibility(empty ? View.GONE   : View.VISIBLE);
    }

    private static int countDuplicates(List<Expense> smsList, List<Expense> pdfList) {
        boolean[] pdfUsed = new boolean[pdfList.size()];
        int count = 0;
        for (int i = 0; i < smsList.size(); i++) {
            Expense sms = smsList.get(i);
            if (sms.reconciledWithId > 0) continue; // already handled
            for (int j = 0; j < pdfList.size(); j++) {
                if (pdfUsed[j]) continue;
                Expense pdf = pdfList.get(j);
                if (pdf.reconciledWithId > 0) continue; // already handled
                if (Math.abs(sms.amount - pdf.amount) <= 0.01
                        && Math.abs(sms.dateMs - pdf.dateMs) <= MATCH_WINDOW_MS) {
                    pdfUsed[j] = true;
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    void openMonth(int pos) {
        MonthRow row = mAdapter.getRow(pos);
        Intent i = new Intent(this, ReconcileActivity.class);
        i.putExtra(ReconcileActivity.EXTRA_MONTH_LABEL, row.label);
        i.putExtra(ReconcileActivity.EXTRA_MONTH_START, row.start);
        i.putExtra(ReconcileActivity.EXTRA_MONTH_END,   row.end);
        startActivity(i);
    }

    // ── Data model ────────────────────────────────────────────────────────────

    static class MonthRow {
        String label;
        long   start, end;
        int    smsCount, pdfCount, dupCount;
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    static class MonthAdapter extends BaseAdapter {
        private final ReconcileAllActivity mA;
        private final List<MonthRow> mRows = new ArrayList<MonthRow>();

        MonthAdapter(ReconcileAllActivity a) { mA = a; }

        void setRows(List<MonthRow> rows) {
            mRows.clear();
            mRows.addAll(rows);
            notifyDataSetChanged();
        }

        MonthRow getRow(int pos) { return mRows.get(pos); }

        public int getCount()              { return mRows.size(); }
        public Object getItem(int pos)     { return mRows.get(pos); }
        public long getItemId(int pos)     { return pos; }

        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(mA)
                    .inflate(R.layout.item_reconcile_month, parent, false);
            MonthRow row = mRows.get(pos);
            ((TextView) convertView.findViewById(R.id.tvMonthName)).setText(row.label);
            ((TextView) convertView.findViewById(R.id.tvSmsBadge))
                .setText(row.smsCount + " SMS");
            ((TextView) convertView.findViewById(R.id.tvPdfBadge))
                .setText(row.pdfCount + " PDF");
            TextView dupBadge = (TextView) convertView.findViewById(R.id.tvDupBadge);
            if (row.dupCount > 0) {
                dupBadge.setText("⚠ " + row.dupCount + " dup" + (row.dupCount == 1 ? "" : "s"));
                dupBadge.setVisibility(View.VISIBLE);
            } else {
                dupBadge.setVisibility(View.GONE);
            }
            return convertView;
        }
    }

    // ── Listener classes ──────────────────────────────────────────────────────

    static class BackListener implements View.OnClickListener {
        private final ReconcileAllActivity mA;
        BackListener(ReconcileAllActivity a) { mA = a; }
        public void onClick(View v) { mA.finish(); }
    }

    static class RowClickListener implements AdapterView.OnItemClickListener {
        private final ReconcileAllActivity mA;
        RowClickListener(ReconcileAllActivity a) { mA = a; }
        public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            mA.openMonth(pos);
        }
    }
}
