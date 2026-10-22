package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.graphics.Color;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;
import com.ldsa.myfintracker.db.ExpenseDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    static final String EXTRA_EXPENSE_ID   = "expense_id";
    static final String EXTRA_MONTH_START  = "month_start";
    static final String EXTRA_MONTH_END    = "month_end";
    static final String EXTRA_MONTH_LABEL  = "month_label";

    private static final String PREF_FILE      = "fin_prefs";
    private static final String PREF_LANDING   = "landing_page";
    private static final String PREF_FILTER    = "filter";
    private static final String PREF_SORT_IDX  = "sort_idx";

    // parallel arrays: index → filter value
    static final String[] FILTER_VALUES = {"all", "online", "offline", "month"};

    // parallel arrays: index → (sort col, sort desc)
    static final String[]  SORT_COLS  = {"date_ms", "date_ms", "amount",  "amount",  "merchant"};
    static final boolean[] SORT_DESCS = { true,      false,     true,      false,     false};

    private TextView     mTvTitle;
    private EditText     mEtSearch;
    private Button       mBtnSearch;
    private ListView     mListView;
    private TextView     mTvEmpty;
    private Spinner      mSpinnerFilter;
    private Spinner      mSpinnerSort;
    private Button       mBtnTabSms;
    private Button       mBtnTabPdf;
    private LinearLayout mRowMonthChip;
    private TextView     mTvMonthChip;
    private TextView     mBtnReconcileMonth;

    private ExpenseAdapter  mAdapter;
    private ExpenseDatabase mDb;
    private List<Expense>   mExpenses = new ArrayList<Expense>();

    String  mFilter      = "all";
    String  mSortCol     = "date_ms";
    boolean mSortDesc    = true;
    int     mSortIdx     = 0;
    boolean mReady       = false;
    String  mSourceTab   = "default";
    boolean mSearchOpen  = false;

    // Month filter from landing's View all (0 = no filter)
    long    mMonthStart  = 0;
    long    mMonthEnd    = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Only run the launch-time routing when coming from the launcher icon.
        Intent launchIntent = getIntent();
        boolean fromLauncher = launchIntent != null
            && Intent.ACTION_MAIN.equals(launchIntent.getAction())
            && launchIntent.hasCategory(Intent.CATEGORY_LAUNCHER);

        SharedPreferences prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        if (fromLauncher) {
            if (!prefs.getBoolean("landing_home_default_v1", false)) {
                prefs.edit()
                    .putString(PREF_LANDING, "home")
                    .putBoolean("landing_home_default_v1", true)
                    .apply();
            }
            String landing = prefs.getString(PREF_LANDING, "home");
            if ("home".equals(landing)) {
                startActivity(new Intent(this, LandingActivity.class));
                finish();
                return;
            }
            if ("trips".equals(landing)) {
                startActivity(new Intent(this, TripListActivity.class));
                finish();
                return;
            }
            if ("sms".equals(landing)) {
                startActivity(new Intent(this, SmsInboxActivity.class));
                finish();
                return;
            }
        }

        // Read optional month-filter extras (passed by LandingActivity's View all).
        mMonthStart = launchIntent != null ? launchIntent.getLongExtra(EXTRA_MONTH_START, 0) : 0;
        mMonthEnd   = launchIntent != null ? launchIntent.getLongExtra(EXTRA_MONTH_END,   0) : 0;
        String monthLabel = launchIntent != null ? launchIntent.getStringExtra(EXTRA_MONTH_LABEL) : null;

        setContentView(R.layout.activity_main);
        getWindow().setStatusBarColor(0xFF1565C0);
        mDb = ExpenseDatabase.getInstance(this);

        mTvTitle       = (TextView)     findViewById(R.id.tvTitle);
        mEtSearch      = (EditText)     findViewById(R.id.etSearch);
        mBtnSearch     = (Button)       findViewById(R.id.btnSearch);
        mListView      = (ListView)     findViewById(R.id.listExpenses);
        mTvEmpty       = (TextView)     findViewById(R.id.tvEmpty);
        mSpinnerFilter = (Spinner)      findViewById(R.id.spinnerFilter);
        mSpinnerSort   = (Spinner)      findViewById(R.id.spinnerSort);
        mBtnTabSms     = (Button)       findViewById(R.id.btnTabSms);
        mBtnTabPdf     = (Button)       findViewById(R.id.btnTabPdf);
        mRowMonthChip      = (LinearLayout) findViewById(R.id.rowMonthChip);
        mTvMonthChip       = (TextView)     findViewById(R.id.tvMonthChip);
        mBtnReconcileMonth = (TextView)     findViewById(R.id.btnReconcileMonth);

        // Show month chip if launched with a month filter
        if (mMonthStart > 0 && monthLabel != null) {
            mTvMonthChip.setText("Showing: " + monthLabel);
            mRowMonthChip.setVisibility(View.VISIBLE);
            mBtnReconcileMonth.setVisibility(View.VISIBLE);
            mBtnReconcileMonth.setOnClickListener(new ReconcileMonthListener(this, mMonthStart, mMonthEnd, monthLabel));
        }

        // Restore saved prefs
        mFilter  = prefs.getString(PREF_FILTER,   "all");
        mSortIdx = prefs.getInt(PREF_SORT_IDX, 0);
        mSortCol  = SORT_COLS[mSortIdx];
        mSortDesc = SORT_DESCS[mSortIdx];

        // Filter spinner
        ArrayAdapter<CharSequence> filterAdapter = ArrayAdapter.createFromResource(
            this, R.array.filter_options, android.R.layout.simple_spinner_item);
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerFilter.setAdapter(filterAdapter);
        mSpinnerFilter.setSelection(filterIndexOf(mFilter), false);

        // Sort spinner
        ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(
            this, R.array.sort_options, android.R.layout.simple_spinner_item);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerSort.setAdapter(sortAdapter);
        mSpinnerSort.setSelection(mSortIdx, false);

        mAdapter = new ExpenseAdapter(this, mExpenses);
        mListView.setAdapter(mAdapter);

        mEtSearch.addTextChangedListener(new SearchWatcher(this));
        mListView.setOnItemClickListener(new ItemClickListener(this));

        mBtnTabSms.setOnClickListener(new TabClickListener(this, "sms"));
        mBtnTabPdf.setOnClickListener(new TabClickListener(this, "pdf"));

        findViewById(R.id.fabAdd).setOnClickListener(new FabClickListener(this));
        findViewById(R.id.btnBack).setOnClickListener(new BackClickListener(this));
        mBtnSearch.setOnClickListener(new SearchToggleListener(this));
        findViewById(R.id.btnClearMonth).setOnClickListener(new ClearMonthListener(this));

        mReady = true;

        mSpinnerFilter.setOnItemSelectedListener(new FilterSelectedListener(this));
        mSpinnerSort.setOnItemSelectedListener(new SortSelectedListener(this));

        if (launchIntent != null && launchIntent.getBooleanExtra("open_search", false)) {
            toggleSearch();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdapter != null) reload();
    }

    void reload() {
        updateTabAppearance();
        String search = mEtSearch.getText().toString().trim();
        List<Expense> raw;
        if (!search.isEmpty()) {
            raw = mDb.searchExpenses(search);
        } else if ("default".equals(mSourceTab)) {
            raw = mDb.getExpensesForList(mFilter, mSortCol, mSortDesc);
        } else {
            raw = mDb.getExpensesBySource(mSourceTab, mFilter, mSortCol, mSortDesc);
        }
        // Apply month filter in Java when launched from View all
        if (mMonthStart > 0) {
            List<Expense> filtered = new ArrayList<Expense>();
            for (int i = 0; i < raw.size(); i++) {
                Expense e = raw.get(i);
                if (e.dateMs >= mMonthStart && e.dateMs <= mMonthEnd) {
                    filtered.add(e);
                }
            }
            mExpenses = filtered;
        } else {
            mExpenses = raw;
        }
        mAdapter.setGroupedItems(buildGroupedList(mExpenses));
        boolean empty = mExpenses.isEmpty();
        mTvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        mListView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    void updateTabAppearance() {
        boolean smsActive = "sms".equals(mSourceTab);
        boolean pdfActive = "pdf".equals(mSourceTab);
        mBtnTabSms.setBackgroundResource(smsActive ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        mBtnTabPdf.setBackgroundResource(pdfActive ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        mBtnTabSms.setTextColor(smsActive ? Color.WHITE : Color.parseColor("#B0BEC5"));
        mBtnTabPdf.setTextColor(pdfActive ? Color.WHITE : Color.parseColor("#B0BEC5"));
    }

    void onTabSelected(String src) {
        // Tapping the active tab deselects it and returns to the default reconciled view.
        mSourceTab = mSourceTab.equals(src) ? "default" : src;
        reload();
    }

    void toggleSearch() {
        mSearchOpen = !mSearchOpen;
        if (mSearchOpen) {
            mTvTitle.setVisibility(View.GONE);
            mEtSearch.setVisibility(View.VISIBLE);
            mBtnSearch.setText("✕");
            mEtSearch.requestFocus();
        } else {
            mEtSearch.setText("");
            mEtSearch.setVisibility(View.GONE);
            mTvTitle.setVisibility(View.VISIBLE);
            mBtnSearch.setText("🔍");
            if (mReady) reload();
        }
    }

    void clearMonthFilter() {
        mMonthStart = 0;
        mMonthEnd   = 0;
        mRowMonthChip.setVisibility(View.GONE);
        mBtnReconcileMonth.setVisibility(View.GONE);
        if (mReady) reload();
    }

    private List<ExpenseAdapter.ListItem> buildGroupedList(List<Expense> expenses) {
        List<ExpenseAdapter.ListItem> result = new ArrayList<ExpenseAdapter.ListItem>();
        if (expenses.isEmpty()) return result;

        if (!"date_ms".equals(mSortCol)) {
            for (Expense e : expenses) {
                ExpenseAdapter.ListItem item = new ExpenseAdapter.ListItem();
                item.type    = ExpenseAdapter.ListItem.TYPE_EXPENSE;
                item.expense = e;
                result.add(item);
            }
            return result;
        }

        SimpleDateFormat labelFmt = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        Map<String, Double> totals = new LinkedHashMap<String, Double>();
        Map<String, String> labels = new LinkedHashMap<String, String>();
        Calendar cal = Calendar.getInstance();
        for (Expense e : expenses) {
            cal.setTimeInMillis(e.dateMs);
            String key = cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.MONTH);
            Double prev = totals.get(key);
            totals.put(key, (prev == null ? 0 : prev) + (e.isCredit ? 0 : e.amount));
            if (!labels.containsKey(key)) {
                labels.put(key, labelFmt.format(new Date(e.dateMs)));
            }
        }

        String currentKey = null;
        for (Expense e : expenses) {
            cal.setTimeInMillis(e.dateMs);
            String key = cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.MONTH);
            if (!key.equals(currentKey)) {
                ExpenseAdapter.ListItem header = new ExpenseAdapter.ListItem();
                header.type       = ExpenseAdapter.ListItem.TYPE_HEADER;
                header.monthLabel = labels.get(key);
                header.monthTotal = totals.get(key);
                result.add(header);
                currentKey = key;
            }
            ExpenseAdapter.ListItem item = new ExpenseAdapter.ListItem();
            item.type    = ExpenseAdapter.ListItem.TYPE_EXPENSE;
            item.expense = e;
            result.add(item);
        }
        return result;
    }

    void onFilterSelected(int pos) {
        mFilter = FILTER_VALUES[pos];
        getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit()
            .putString(PREF_FILTER, mFilter).apply();
        reload();
    }

    void onSortSelected(int pos) {
        mSortIdx  = pos;
        mSortCol  = SORT_COLS[pos];
        mSortDesc = SORT_DESCS[pos];
        getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit()
            .putInt(PREF_SORT_IDX, pos).apply();
        reload();
    }

    void openDetail(long id) {
        Intent i = new Intent(this, ExpenseDetailActivity.class);
        i.putExtra(EXTRA_EXPENSE_ID, id);
        startActivity(i);
    }

    private static int filterIndexOf(String filter) {
        for (int i = 0; i < FILTER_VALUES.length; i++) {
            if (FILTER_VALUES[i].equals(filter)) return i;
        }
        return 0;
    }

    // ── Static listener classes ───────────────────────────────────────────────

    static class SearchWatcher implements TextWatcher {
        private final MainActivity mMain;
        SearchWatcher(MainActivity m) { mMain = m; }
        public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
        public void onTextChanged(CharSequence s, int st, int b, int c) {}
        public void afterTextChanged(Editable s) { if (mMain.mReady) mMain.reload(); }
    }

    static class ItemClickListener implements AdapterView.OnItemClickListener {
        private final MainActivity mMain;
        ItemClickListener(MainActivity m) { mMain = m; }
        public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            if (id >= 0) mMain.openDetail(id);
        }
    }

    static class FilterSelectedListener implements AdapterView.OnItemSelectedListener {
        private final MainActivity mMain;
        FilterSelectedListener(MainActivity m) { mMain = m; }
        public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            if (mMain.mReady) mMain.onFilterSelected(pos);
        }
        public void onNothingSelected(AdapterView<?> p) {}
    }

    static class SortSelectedListener implements AdapterView.OnItemSelectedListener {
        private final MainActivity mMain;
        SortSelectedListener(MainActivity m) { mMain = m; }
        public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            if (mMain.mReady) mMain.onSortSelected(pos);
        }
        public void onNothingSelected(AdapterView<?> p) {}
    }

    static class TabClickListener implements View.OnClickListener {
        private final MainActivity mMain;
        private final String mSrc;
        TabClickListener(MainActivity m, String src) { mMain = m; mSrc = src; }
        public void onClick(View v) { mMain.onTabSelected(mSrc); }
    }

    static class FabClickListener implements View.OnClickListener {
        private final MainActivity mMain;
        FabClickListener(MainActivity m) { mMain = m; }
        public void onClick(View v) {
            mMain.startActivity(new Intent(mMain, AddExpenseActivity.class));
        }
    }

    static class BackClickListener implements View.OnClickListener {
        private final MainActivity mMain;
        BackClickListener(MainActivity m) { mMain = m; }
        public void onClick(View v) { mMain.finish(); }
    }

    static class SearchToggleListener implements View.OnClickListener {
        private final MainActivity mMain;
        SearchToggleListener(MainActivity m) { mMain = m; }
        public void onClick(View v) { mMain.toggleSearch(); }
    }

    static class ClearMonthListener implements View.OnClickListener {
        private final MainActivity mMain;
        ClearMonthListener(MainActivity m) { mMain = m; }
        public void onClick(View v) { mMain.clearMonthFilter(); }
    }

    static class ReconcileMonthListener implements View.OnClickListener {
        private final MainActivity mMain;
        private final long mStart, mEnd;
        private final String mLabel;
        ReconcileMonthListener(MainActivity m, long start, long end, String label) {
            mMain = m; mStart = start; mEnd = end; mLabel = label;
        }
        public void onClick(View v) {
            android.content.Intent i = new android.content.Intent(mMain, ReconcileActivity.class);
            i.putExtra(ReconcileActivity.EXTRA_MONTH_START, mStart);
            i.putExtra(ReconcileActivity.EXTRA_MONTH_END,   mEnd);
            i.putExtra(ReconcileActivity.EXTRA_MONTH_LABEL, mLabel);
            mMain.startActivity(i);
        }
    }
}
