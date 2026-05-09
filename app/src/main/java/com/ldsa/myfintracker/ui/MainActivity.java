package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;
import com.ldsa.myfintracker.db.ExpenseDatabase;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    static final String EXTRA_EXPENSE_ID = "expense_id";
    private static final String PREF_FILE = "fin_prefs";
    private static final String PREF_LANDING = "landing_page";
    private static final String PREF_SORT_COL = "sort_col";
    private static final String PREF_SORT_DESC = "sort_desc";

    private EditText mEtSearch;
    private ListView mListView;
    private TextView mTvEmpty;
    private TextView mTvCount;
    private Button mBtnAll;
    private Button mBtnOnline;
    private Button mBtnOffline;
    private Button mBtnMonth;
    private Button mBtnSortDate;
    private Button mBtnSortAmount;
    private Button mBtnSortMerchant;

    private ExpenseAdapter mAdapter;
    private ExpenseDatabase mDb;
    private List<Expense> mExpenses = new ArrayList<Expense>();

    String mFilter = "all";
    String mSortCol = "date_ms";
    boolean mSortDesc = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check configured landing page
        SharedPreferences prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        String landing = prefs.getString(PREF_LANDING, "expenses");
        if ("sms".equals(landing)) {
            startActivity(new Intent(this, SmsInboxActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        mDb = ExpenseDatabase.getInstance(this);

        mEtSearch        = (EditText)  findViewById(R.id.etSearch);
        mListView        = (ListView)  findViewById(R.id.listExpenses);
        mTvEmpty         = (TextView)  findViewById(R.id.tvEmpty);
        mTvCount         = (TextView)  findViewById(R.id.tvCount);
        mBtnAll          = (Button)    findViewById(R.id.btnFilterAll);
        mBtnOnline       = (Button)    findViewById(R.id.btnFilterOnline);
        mBtnOffline      = (Button)    findViewById(R.id.btnFilterOffline);
        mBtnMonth        = (Button)    findViewById(R.id.btnFilterMonth);
        mBtnSortDate     = (Button)    findViewById(R.id.btnSortDate);
        mBtnSortAmount   = (Button)    findViewById(R.id.btnSortAmount);
        mBtnSortMerchant = (Button)    findViewById(R.id.btnSortMerchant);

        mSortCol  = prefs.getString(PREF_SORT_COL, "date_ms");
        mSortDesc = prefs.getBoolean(PREF_SORT_DESC, true);

        mAdapter = new ExpenseAdapter(this, mExpenses);
        mListView.setAdapter(mAdapter);

        mEtSearch.addTextChangedListener(new SearchWatcher(this));
        mListView.setOnItemClickListener(new ItemClickListener(this));

        mBtnAll.setOnClickListener(new FilterClickListener(this, "all"));
        mBtnOnline.setOnClickListener(new FilterClickListener(this, "online"));
        mBtnOffline.setOnClickListener(new FilterClickListener(this, "offline"));
        mBtnMonth.setOnClickListener(new FilterClickListener(this, "month"));

        mBtnSortDate.setOnClickListener(new SortClickListener(this, "date_ms"));
        mBtnSortAmount.setOnClickListener(new SortClickListener(this, "amount"));
        mBtnSortMerchant.setOnClickListener(new SortClickListener(this, "merchant"));

        Button fabAdd = (Button) findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(new FabClickListener(this));

        updateFilterButtons();
        updateSortButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdapter != null) reload();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_sms_inbox) {
            startActivity(new Intent(this, SmsInboxActivity.class));
            return true;
        }
        if (id == R.id.action_add_expense) {
            startActivity(new Intent(this, AddExpenseActivity.class));
            return true;
        }
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    void reload() {
        String search = mEtSearch.getText().toString().trim();
        if (!search.isEmpty()) {
            mExpenses = mDb.searchExpenses(search);
        } else {
            mExpenses = mDb.getExpenses(mFilter, mSortCol, mSortDesc);
        }
        mAdapter.setItems(mExpenses);
        int count = mExpenses.size();
        mTvCount.setText(count + " expense" + (count == 1 ? "" : "s"));
        mTvEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        mListView.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
    }

    void applyFilter(String filter) {
        mFilter = filter;
        updateFilterButtons();
        reload();
    }

    void toggleSort(String col) {
        if (mSortCol.equals(col)) {
            mSortDesc = !mSortDesc;
        } else {
            mSortCol  = col;
            mSortDesc = !"merchant".equals(col);
        }
        getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit()
            .putString(PREF_SORT_COL, mSortCol)
            .putBoolean(PREF_SORT_DESC, mSortDesc)
            .apply();
        updateSortButtons();
        reload();
    }

    void openDetail(long id) {
        Intent i = new Intent(this, ExpenseDetailActivity.class);
        i.putExtra(EXTRA_EXPENSE_ID, id);
        startActivity(i);
    }

    private void updateFilterButtons() {
        setChipActive(mBtnAll,     "all".equals(mFilter));
        setChipActive(mBtnOnline,  "online".equals(mFilter));
        setChipActive(mBtnOffline, "offline".equals(mFilter));
        setChipActive(mBtnMonth,   "month".equals(mFilter));
    }

    private void updateSortButtons() {
        String arrow = mSortDesc ? " ▼" : " ▲";
        mBtnSortDate.setText("Date" + ("date_ms".equals(mSortCol) ? arrow : ""));
        mBtnSortAmount.setText("Amount" + ("amount".equals(mSortCol) ? arrow : ""));
        mBtnSortMerchant.setText("Merchant" + ("merchant".equals(mSortCol) ? arrow : ""));
    }

    private static void setChipActive(Button btn, boolean active) {
        btn.setBackgroundResource(active ? R.drawable.bg_chip_active : R.drawable.bg_chip);
        btn.setTextColor(active ? 0xFFFFFFFF : 0xFF1565C0);
    }

    // ============================================================
    // Static listener classes — D8: no anonymous/non-static
    // ============================================================

    static class SearchWatcher implements TextWatcher {
        private final MainActivity mMain;
        SearchWatcher(MainActivity m) { mMain = m; }
        public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
        public void onTextChanged(CharSequence s, int st, int b, int c) {}
        public void afterTextChanged(Editable s) { mMain.reload(); }
    }

    static class ItemClickListener implements AdapterView.OnItemClickListener {
        private final MainActivity mMain;
        ItemClickListener(MainActivity m) { mMain = m; }
        public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            if (id >= 0) mMain.openDetail(id);
        }
    }

    static class FilterClickListener implements View.OnClickListener {
        private final MainActivity mMain;
        private final String mFilter;
        FilterClickListener(MainActivity m, String filter) { mMain = m; mFilter = filter; }
        public void onClick(View v) { mMain.applyFilter(mFilter); }
    }

    static class SortClickListener implements View.OnClickListener {
        private final MainActivity mMain;
        private final String mCol;
        SortClickListener(MainActivity m, String col) { mMain = m; mCol = col; }
        public void onClick(View v) { mMain.toggleSort(mCol); }
    }

    static class FabClickListener implements View.OnClickListener {
        private final MainActivity mMain;
        FabClickListener(MainActivity m) { mMain = m; }
        public void onClick(View v) {
            mMain.startActivity(new Intent(mMain, AddExpenseActivity.class));
        }
    }
}
