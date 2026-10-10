package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.ExtractionPattern;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Debug → Pattern → Expenses: shows the chosen pattern's name + an Edit
 *  button on top, then every expense linked to that pattern below. */
public class DebugPatternExpensesActivity extends Activity {

    public static final String EXTRA_PATTERN_ID = "pattern_id";

    private long              mPatternId = -1L;
    private ExtractionPattern mPattern;
    private ExpenseDatabase   mDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_pattern_expenses);
        setTitle(R.string.title_debug_pattern_expenses);
        mPatternId = getIntent().getLongExtra(EXTRA_PATTERN_ID, -1L);
        mDb = ExpenseDatabase.getInstance(this);
        ((Button) findViewById(R.id.btnEditPattern))
            .setOnClickListener(new EditPatternClickListener(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPattern = (mPatternId > 0) ? mDb.getPatternById(mPatternId) : null;

        TextView header = (TextView) findViewById(R.id.tvPatternHeader);
        if (mPattern == null) {
            header.setText("(pattern not found)");
        } else {
            String name = (mPattern.name != null && !mPattern.name.isEmpty())
                ? mPattern.name : ("pattern #" + mPattern.id);
            header.setText((mPattern.isPdf ? "[PDF] " : "[SMS] ") + name);
        }

        List<Expense> items = (mPatternId > 0)
            ? mDb.getExpensesByPattern(mPatternId)
            : new java.util.ArrayList<Expense>();
        ListView list  = (ListView) findViewById(R.id.listExpenses);
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

    void editPattern() {
        if (mPattern == null) {
            Toast.makeText(this, "Pattern not found", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mPattern.isPdf) {
            Intent i = new Intent(this, AddPdfPatternActivity.class);
            i.putExtra(AddPdfPatternActivity.EXTRA_SENDER_ID,  mPattern.senderId);
            i.putExtra(AddPdfPatternActivity.EXTRA_PATTERN_ID, mPattern.id);
            startActivity(i);
        } else {
            Intent i = new Intent(this, SmsExtractConfigActivity.class);
            i.putExtra(SmsExtractConfigActivity.EXTRA_SENDER_ID,  mPattern.senderId);
            i.putExtra(SmsExtractConfigActivity.EXTRA_PATTERN_ID, mPattern.id);
            startActivity(i);
        }
    }

    static class EditPatternClickListener implements View.OnClickListener {
        private final DebugPatternExpensesActivity mA;
        EditPatternClickListener(DebugPatternExpensesActivity a) { mA = a; }
        public void onClick(View v) { mA.editPattern(); }
    }

    static class ExpenseAdapter extends BaseAdapter {
        private final DebugPatternExpensesActivity mA;
        private final List<Expense> mItems;
        private final SimpleDateFormat mFmt =
            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        ExpenseAdapter(DebugPatternExpensesActivity a, List<Expense> items) {
            mA = a; mItems = items;
        }
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
            String amt = (e.isCredit ? "+ " : "- ")
                + String.format(Locale.getDefault(), "%.2f", e.amount);
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
        private final DebugPatternExpensesActivity mA;
        private final List<Expense>                mItems;
        ExpenseClickListener(DebugPatternExpensesActivity a, List<Expense> items) {
            mA = a; mItems = items;
        }
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            mA.openExpenseDetail(mItems.get(pos).id);
        }
    }
}
