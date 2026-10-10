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
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.ExtractionPattern;
import com.ldsa.myfintracker.db.SenderConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Debug → All Patterns: flat list of every pattern (SMS + PDF) with badges
 *  for pdf/sms/orphan and a count of linked expenses. Row click → the pattern's
 *  expense list. */
public class DebugPatternsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_patterns);
        setTitle(R.string.title_debug_patterns);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ExpenseDatabase db = ExpenseDatabase.getInstance(this);
        List<ExtractionPattern> patterns = db.getAllPatterns();
        // Resolve sender display names up front so the row adapter doesn't
        // round-trip to the DB for each item.
        Map<Long, String> senderName = new HashMap<Long, String>();
        for (SenderConfig s : db.getAllSenders()) {
            senderName.put(Long.valueOf(s.id),
                s.displayName != null ? s.displayName : s.pattern);
        }
        ListView list   = (ListView) findViewById(R.id.listPatterns);
        TextView empty  = (TextView) findViewById(R.id.tvEmpty);
        if (patterns.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            list.setVisibility(View.GONE);
        } else {
            empty.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
            list.setAdapter(new PatternAdapter(this, patterns, senderName, db));
            list.setOnItemClickListener(new PatternClickListener(this, patterns));
        }
    }

    void openPatternExpenses(long patternId) {
        Intent i = new Intent(this, DebugPatternExpensesActivity.class);
        i.putExtra(DebugPatternExpensesActivity.EXTRA_PATTERN_ID, patternId);
        startActivity(i);
    }

    static class PatternAdapter extends BaseAdapter {
        private final DebugPatternsActivity   mA;
        private final List<ExtractionPattern> mItems;
        private final Map<Long, String>       mSenderName;
        private final ExpenseDatabase         mDb;
        PatternAdapter(DebugPatternsActivity a, List<ExtractionPattern> items,
                       Map<Long, String> senderName, ExpenseDatabase db) {
            mA = a; mItems = items; mSenderName = senderName; mDb = db;
        }
        public int getCount() { return mItems.size(); }
        public Object getItem(int pos) { return mItems.get(pos); }
        public long getItemId(int pos) { return mItems.get(pos).id; }

        public View getView(int pos, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(mA)
                    .inflate(R.layout.item_debug_pattern, parent, false);
            }
            ExtractionPattern p = mItems.get(pos);
            String name = (p.name != null && !p.name.isEmpty())
                ? p.name : ("pattern #" + p.id);
            String sender = (p.senderId > 0)
                ? mSenderName.get(Long.valueOf(p.senderId))
                : null;
            if (sender == null) sender = (p.senderId == -1) ? "(orphan)" : "(unknown sender)";

            StringBuilder badges = new StringBuilder();
            badges.append(p.isPdf ? "[PDF] " : "[SMS] ");
            if (p.senderId <= 0) badges.append("[ORPHAN] ");
            int n = mDb.countExpensesByPattern(p.id);

            ((TextView) v.findViewById(R.id.tvPatternName)).setText(name);
            ((TextView) v.findViewById(R.id.tvPatternMeta)).setText(
                badges.toString() + sender + "  ·  " + n + " expense" + (n == 1 ? "" : "s"));
            return v;
        }
    }

    static class PatternClickListener implements AdapterView.OnItemClickListener {
        private final DebugPatternsActivity   mA;
        private final List<ExtractionPattern> mItems;
        PatternClickListener(DebugPatternsActivity a, List<ExtractionPattern> items) {
            mA = a; mItems = items;
        }
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            mA.openPatternExpenses(mItems.get(pos).id);
        }
    }
}
