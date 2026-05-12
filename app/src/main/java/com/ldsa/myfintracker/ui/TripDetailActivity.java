package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.Trip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TripDetailActivity extends Activity {

    static final String EXTRA_TRIP_ID = "trip_id";

    ListView        mListView;
    TextView        mTvEmpty;
    TextView        mTvName;
    TextView        mTvRange;
    TextView        mTvSummary;
    ExpenseAdapter  mAdapter;
    ExpenseDatabase mDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_detail);
        setTitle(R.string.title_trip_detail);

        mDb       = ExpenseDatabase.getInstance(this);
        mListView = (ListView) findViewById(R.id.listTripExpenses);
        mTvEmpty  = (TextView) findViewById(R.id.tvDetailEmpty);
        mTvName   = (TextView) findViewById(R.id.tvDetailTripName);
        mTvRange  = (TextView) findViewById(R.id.tvDetailRange);
        mTvSummary = (TextView) findViewById(R.id.tvDetailSummary);

        mAdapter = new ExpenseAdapter(this, new ArrayList<Expense>());
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new ExpenseClickListener(this));

        long tripId = getIntent().getLongExtra(EXTRA_TRIP_ID, -1);
        if (tripId >= 0) {
            new LoadDetailThread(this, tripId, mDb, new Handler(Looper.getMainLooper())).start();
        } else {
            finish();
        }
    }

    void onDetailLoaded(Trip trip, List<Expense> expenses) {
        if (trip == null) { finish(); return; }

        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault());
        mTvName.setText(trip.name);
        mTvRange.setText(fmt.format(new Date(trip.startMs))
            + "  →  " + fmt.format(new Date(trip.endMs)));

        double total = 0;
        for (Expense e : expenses) total += e.amount;
        mTvSummary.setText(expenses.size() + " expense"
            + (expenses.size() == 1 ? "" : "s")
            + "  ·  ₹" + String.format(Locale.getDefault(), "%.2f", total));

        mAdapter.setItems(expenses);
        boolean empty = expenses.isEmpty();
        mTvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        mListView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    void openExpense(long expenseId) {
        Intent i = new Intent(this, ExpenseDetailActivity.class);
        i.putExtra("expense_id", expenseId);
        startActivity(i);
    }

    // ── Static inner classes ──────────────────────────────────────

    static class LoadDetailThread extends Thread {
        private final TripDetailActivity mActivity;
        private final long               mTripId;
        private final ExpenseDatabase    mDb;
        private final Handler            mHandler;
        LoadDetailThread(TripDetailActivity a, long id, ExpenseDatabase db, Handler h) {
            mActivity = a; mTripId = id; mDb = db; mHandler = h;
        }
        public void run() {
            Trip trip = mDb.getTripById(mTripId);
            List<Expense> expenses = trip != null
                ? mDb.getExpensesInRange(trip.startMs, trip.endMs)
                : new ArrayList<Expense>();
            mHandler.post(new LoadDetailRunnable(mActivity, trip, expenses));
        }
    }

    static class LoadDetailRunnable implements Runnable {
        private final TripDetailActivity mActivity;
        private final Trip               mTrip;
        private final List<Expense>      mExpenses;
        LoadDetailRunnable(TripDetailActivity a, Trip trip, List<Expense> expenses) {
            mActivity = a; mTrip = trip; mExpenses = expenses;
        }
        public void run() {
            if (!mActivity.isFinishing()) mActivity.onDetailLoaded(mTrip, mExpenses);
        }
    }

    static class ExpenseClickListener implements AdapterView.OnItemClickListener {
        private final TripDetailActivity mActivity;
        ExpenseClickListener(TripDetailActivity a) { mActivity = a; }
        public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            ExpenseAdapter.ListItem item = (ExpenseAdapter.ListItem) mActivity.mAdapter.getItem(pos);
            mActivity.openExpense(item.expense.id);
        }
    }
}
