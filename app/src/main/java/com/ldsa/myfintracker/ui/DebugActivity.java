package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;

/** Diagnostic hub: two rows, one for all patterns, one for orphan expenses. */
public class DebugActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);
        setTitle(R.string.title_debug);
        ((LinearLayout) findViewById(R.id.rowPatterns))
            .setOnClickListener(new PatternsClickListener(this));
        ((LinearLayout) findViewById(R.id.rowOrphans))
            .setOnClickListener(new OrphansClickListener(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the counts shown in the row labels (cheap counts over the DB).
        ExpenseDatabase db = ExpenseDatabase.getInstance(this);
        int nPatterns = db.getAllPatterns().size();
        int nOrphans  = db.getOrphanExpenses().size();
        ((TextView) findViewById(R.id.tvPatternsLabel))
            .setText(getString(R.string.debug_row_patterns) + "  (" + nPatterns + ")");
        ((TextView) findViewById(R.id.tvOrphansLabel))
            .setText(getString(R.string.debug_row_orphans) + "  (" + nOrphans + ")");
    }

    void openPatterns() { startActivity(new Intent(this, DebugPatternsActivity.class)); }
    void openOrphans()  { startActivity(new Intent(this, DebugOrphansActivity.class));  }

    static class PatternsClickListener implements View.OnClickListener {
        private final DebugActivity mA;
        PatternsClickListener(DebugActivity a) { mA = a; }
        public void onClick(View v) { mA.openPatterns(); }
    }
    static class OrphansClickListener implements View.OnClickListener {
        private final DebugActivity mA;
        OrphansClickListener(DebugActivity a) { mA = a; }
        public void onClick(View v) { mA.openOrphans(); }
    }
}
