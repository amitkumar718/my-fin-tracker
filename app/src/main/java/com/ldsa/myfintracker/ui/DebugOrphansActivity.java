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
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Debug → Orphan Transactions: lists every expense with pattern_id = -1. */
public class DebugOrphansActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_orphans);
        setTitle(R.string.title_debug_orphans);
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<Expense> items = ExpenseDatabase.getInstance(this).getOrphanExpenses();
        ListView list  = (ListView) findViewById(R.id.listOrphans);
        TextView empty = (TextView) findViewById(R.id.tvEmpty);
        if (items.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            list.setVisibility(View.GONE);
        } else {
            empty.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
            list.setAdapter(new ExpenseAdapter(this, items));
            list.setOnItemClickListener(new ExpenseClickListener(this, items));
        }
    }

    void openExpenseDetail(long expenseId) {
        Intent i = new Intent(this, ExpenseDetailActivity.class);
        i.putExtra(MainActivity.EXTRA_EXPENSE_ID, expenseId);
        startActivity(i);
    }

    static class ExpenseAdapter extends BaseAdapter {
        private final DebugOrphansActivity mA;
        private final List<Expense>        mItems;
        private final SimpleDateFormat     mFmt =
            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        ExpenseAdapter(DebugOrphansActivity a, List<Expense> items) { mA = a; mItems = items; }
        public int getCount() { return mItems.size(); }
        public Object getItem(int pos) { return mItems.get(pos); }
        public long getItemId(int pos) { return mItems.get(pos).id; }
        public View getView(int pos, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(mA)
                    .inflate(R.layout.item_debug_expense, parent, false);
            }
            Expense e = mItems.get(pos);
            String amt = (e.isCredit ? "+ " : "- ") + String.format(Locale.getDefault(), "%.2f", e.amount);
            ((TextView) v.findViewById(R.id.tvExpenseAmount)).setText(amt);
            ((TextView) v.findViewById(R.id.tvExpenseDate))
                .setText(e.dateMs > 0 ? mFmt.format(new Date(e.dateMs)) : "");
            String bank = (e.bank != null && !e.bank.isEmpty()) ? e.bank : "(no bank)";
            String src  = e.source != null ? e.source : "?";
            ((TextView) v.findViewById(R.id.tvExpenseBank))
                .setText("[" + src + "]  " + bank);
            String orig = e.originalSms != null ? e.originalSms : "";
            ((TextView) v.findViewById(R.id.tvExpenseOriginal)).setText(orig);
            return v;
        }
    }

    static class ExpenseClickListener implements AdapterView.OnItemClickListener {
        private final DebugOrphansActivity mA;
        private final List<Expense>        mItems;
        ExpenseClickListener(DebugOrphansActivity a, List<Expense> items) {
            mA = a; mItems = items;
        }
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            mA.openExpenseDetail(mItems.get(pos).id);
        }
    }
}
