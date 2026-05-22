package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.graphics.Color;
import android.widget.Button;
import android.widget.EditText;
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

    static final String EXTRA_EXPENSE_ID = "expense_id";
    private static final String PREF_FILE      = "fin_prefs";
    private static final String PREF_LANDING   = "landing_page";
    private static final String PREF_FILTER    = "filter";
    private static final String PREF_SORT_IDX  = "sort_idx";

    // parallel arrays: index → filter value
    static final String[] FILTER_VALUES = {"all", "online", "offline", "month"};

    // parallel arrays: index → (sort col, sort desc)
    static final String[]  SORT_COLS  = {"date_ms", "date_ms", "amount",  "amount",  "merchant"};
    static final boolean[] SORT_DESCS = { true,      false,     true,      false,     false};

    private EditText  mEtSearch;
    private ListView  mListView;
    private TextView  mTvEmpty;
    private Spinner   mSpinnerFilter;
    private Spinner   mSpinnerSort;
    private Button    mBtnTabSms;
    private Button    mBtnTabPdf;

    private ExpenseAdapter  mAdapter;
    private ExpenseDatabase mDb;
    private List<Expense>   mExpenses = new ArrayList<Expense>();

    String  mFilter   = "all";
    String  mSortCol  = "date_ms";
    boolean mSortDesc = true;
    int     mSortIdx  = 0;
    boolean mReady    = false;
    String  mSourceTab = "sms"; // "sms" or "pdf"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        String landing = prefs.getString(PREF_LANDING, "expenses");
        if ("sms".equals(landing)) {
            startActivity(new Intent(this, SmsInboxActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        mDb = ExpenseDatabase.getInstance(this);

        mEtSearch      = (EditText)  findViewById(R.id.etSearch);
        mListView      = (ListView)  findViewById(R.id.listExpenses);
        mTvEmpty       = (TextView)  findViewById(R.id.tvEmpty);
        mSpinnerFilter = (Spinner)   findViewById(R.id.spinnerFilter);
        mSpinnerSort   = (Spinner)   findViewById(R.id.spinnerSort);
        mBtnTabSms     = (Button)    findViewById(R.id.btnTabSms);
        mBtnTabPdf     = (Button)    findViewById(R.id.btnTabPdf);

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

        Button fabAdd = (Button) findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(new FabClickListener(this));

        Button btnMenu = (Button) findViewById(R.id.btnMenu);
        btnMenu.setOnClickListener(new MenuClickListener(this));

        mReady = true;

        // Attach listeners after mReady = true so initial setSelection doesn't trigger reload
        mSpinnerFilter.setOnItemSelectedListener(new FilterSelectedListener(this));
        mSpinnerSort.setOnItemSelectedListener(new SortSelectedListener(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdapter != null) reload();
    }

    void reload() {
        updateTabAppearance();
        String search = mEtSearch.getText().toString().trim();
        if (!search.isEmpty()) {
            mExpenses = mDb.searchExpenses(search);
        } else {
            mExpenses = mDb.getExpensesBySource(mSourceTab, mFilter, mSortCol, mSortDesc);
        }
        mAdapter.setGroupedItems(buildGroupedList(mExpenses));
        boolean empty = mExpenses.isEmpty();
        mTvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        mListView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    void updateTabAppearance() {
        boolean smsActive = "sms".equals(mSourceTab);
        mBtnTabSms.setBackgroundColor(smsActive  ? Color.parseColor("#1976D2") : Color.parseColor("#1565C0"));
        mBtnTabPdf.setBackgroundColor(!smsActive ? Color.parseColor("#1976D2") : Color.parseColor("#1565C0"));
        mBtnTabSms.setTextColor(smsActive  ? Color.WHITE : Color.parseColor("#B0BEC5"));
        mBtnTabPdf.setTextColor(!smsActive ? Color.WHITE : Color.parseColor("#B0BEC5"));
    }

    void onTabSelected(String src) {
        mSourceTab = src;
        reload();
    }

    private List<ExpenseAdapter.ListItem> buildGroupedList(List<Expense> expenses) {
        List<ExpenseAdapter.ListItem> result = new ArrayList<ExpenseAdapter.ListItem>();
        if (expenses.isEmpty()) return result;

        if (!"date_ms".equals(mSortCol)) {
            // Non-date sort: flat list, no headers
            for (Expense e : expenses) {
                ExpenseAdapter.ListItem item = new ExpenseAdapter.ListItem();
                item.type    = ExpenseAdapter.ListItem.TYPE_EXPENSE;
                item.expense = e;
                result.add(item);
            }
            return result;
        }

        // Date sort: group by year-month
        // First pass — compute totals per month key (insertion-ordered)
        SimpleDateFormat labelFmt = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        Map<String, Double> totals = new LinkedHashMap<String, Double>();
        Map<String, String> labels = new LinkedHashMap<String, String>();
        Calendar cal = Calendar.getInstance();
        for (Expense e : expenses) {
            cal.setTimeInMillis(e.dateMs);
            String key = cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.MONTH);
            Double prev = totals.get(key);
            totals.put(key, (prev == null ? 0 : prev) + e.amount);
            if (!labels.containsKey(key)) {
                labels.put(key, labelFmt.format(new Date(e.dateMs)));
            }
        }

        // Second pass — build list with header before each new month group
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

    // ============================================================
    // Static listener classes — D8: no anonymous/non-static
    // ============================================================

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

    static class MenuClickListener implements View.OnClickListener {
        private final MainActivity mMain;
        MenuClickListener(MainActivity m) { mMain = m; }
        public void onClick(View v) {
            PopupMenu popup = new PopupMenu(mMain, v);
            popup.getMenuInflater().inflate(R.menu.menu_main, popup.getMenu());
            popup.setOnMenuItemClickListener(new MenuItemClickListener(mMain));
            popup.show();
        }
    }

    static class MenuItemClickListener implements PopupMenu.OnMenuItemClickListener {
        private final MainActivity mMain;
        MenuItemClickListener(MainActivity m) { mMain = m; }
        public boolean onMenuItemClick(MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_sms_inbox) {
                mMain.startActivity(new Intent(mMain, SmsInboxActivity.class));
                return true;
            }
            if (id == R.id.action_settings) {
                mMain.startActivity(new Intent(mMain, SettingsActivity.class));
                return true;
            }
            if (id == R.id.action_trips) {
                mMain.startActivity(new Intent(mMain, TripListActivity.class));
                return true;
            }
            if (id == R.id.action_pdf_inbox) {
                mMain.startActivity(new Intent(mMain, PdfInboxActivity.class));
                return true;
            }
            return false;
        }
    }
}
