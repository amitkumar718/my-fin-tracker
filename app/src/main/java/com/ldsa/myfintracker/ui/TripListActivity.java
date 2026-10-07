package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.Trip;

import java.util.ArrayList;
import java.util.List;

public class TripListActivity extends Activity {

    ListView        mListView;
    TextView        mTvEmpty;
    TripAdapter     mAdapter;
    ExpenseDatabase mDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_list);
        setTitle(R.string.label_trips);

        mDb       = ExpenseDatabase.getInstance(this);
        mListView = (ListView) findViewById(R.id.listTrips);
        mTvEmpty  = (TextView) findViewById(R.id.tvTripEmpty);
        mAdapter  = new TripAdapter(this);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new TripClickListener(this));
        mListView.setOnItemLongClickListener(new TripLongClickListener(this));

        Button btnNew = (Button) findViewById(R.id.btnNewTrip);
        btnNew.setOnClickListener(new NewTripClickListener(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTrips();
    }

    void loadTrips() {
        new LoadTripsThread(this, mDb, new Handler(Looper.getMainLooper())).start();
    }

    void onTripsLoaded(List<TripAdapter.ListItem> items) {
        mAdapter.setItems(items);
        boolean empty = items.isEmpty();
        mTvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        mListView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    void openTripDetail(long tripId) {
        Intent i = new Intent(this, TripDetailActivity.class);
        i.putExtra(TripDetailActivity.EXTRA_TRIP_ID, tripId);
        startActivity(i);
    }

    void confirmDelete(final long tripId, final String tripName) {
        new AlertDialog.Builder(this, R.style.RoundedDialog)
            .setTitle(R.string.label_delete_trip)
            .setMessage(tripName)
            .setPositiveButton(android.R.string.ok, new DeleteConfirmListener(this, tripId))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void deleteTrip(long tripId) {
        new DeleteTripThread(this, tripId, mDb, new Handler(Looper.getMainLooper())).start();
    }

    void onDeleteDone() {
        Toast.makeText(this, R.string.msg_trip_deleted, Toast.LENGTH_SHORT).show();
        loadTrips();
    }

    // ── Static inner classes ──────────────────────────────────────

    static class LoadTripsThread extends Thread {
        private final TripListActivity mActivity;
        private final ExpenseDatabase  mDb;
        private final Handler          mHandler;
        LoadTripsThread(TripListActivity a, ExpenseDatabase db, Handler h) {
            mActivity = a; mDb = db; mHandler = h;
        }
        public void run() {
            List<Trip> trips = mDb.getAllTrips();
            List<TripAdapter.ListItem> items = new ArrayList<TripAdapter.ListItem>();
            for (Trip t : trips) {
                List<Expense> expenses = mDb.getExpensesInRange(t.startMs, t.endMs);
                double total = 0;
                for (Expense e : expenses) total += e.amount;
                TripAdapter.ListItem item = new TripAdapter.ListItem();
                item.trip         = t;
                item.expenseCount = expenses.size();
                item.total        = total;
                items.add(item);
            }
            mHandler.post(new LoadTripsRunnable(mActivity, items));
        }
    }

    static class LoadTripsRunnable implements Runnable {
        private final TripListActivity           mActivity;
        private final List<TripAdapter.ListItem> mItems;
        LoadTripsRunnable(TripListActivity a, List<TripAdapter.ListItem> items) {
            mActivity = a; mItems = items;
        }
        public void run() {
            if (!mActivity.isFinishing()) mActivity.onTripsLoaded(mItems);
        }
    }

    static class DeleteTripThread extends Thread {
        private final TripListActivity mActivity;
        private final long             mTripId;
        private final ExpenseDatabase  mDb;
        private final Handler          mHandler;
        DeleteTripThread(TripListActivity a, long id, ExpenseDatabase db, Handler h) {
            mActivity = a; mTripId = id; mDb = db; mHandler = h;
        }
        public void run() {
            mDb.deleteTrip(mTripId);
            mHandler.post(new DeleteTripDoneRunnable(mActivity));
        }
    }

    static class DeleteTripDoneRunnable implements Runnable {
        private final TripListActivity mActivity;
        DeleteTripDoneRunnable(TripListActivity a) { mActivity = a; }
        public void run() {
            if (!mActivity.isFinishing()) mActivity.onDeleteDone();
        }
    }

    static class TripClickListener implements AdapterView.OnItemClickListener {
        private final TripListActivity mActivity;
        TripClickListener(TripListActivity a) { mActivity = a; }
        public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            TripAdapter.ListItem item = (TripAdapter.ListItem) mActivity.mAdapter.getItem(pos);
            mActivity.openTripDetail(item.trip.id);
        }
    }

    static class TripLongClickListener implements AdapterView.OnItemLongClickListener {
        private final TripListActivity mActivity;
        TripLongClickListener(TripListActivity a) { mActivity = a; }
        public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
            TripAdapter.ListItem item = (TripAdapter.ListItem) mActivity.mAdapter.getItem(pos);
            mActivity.confirmDelete(item.trip.id, item.trip.name);
            return true;
        }
    }

    static class DeleteConfirmListener implements DialogInterface.OnClickListener {
        private final TripListActivity mActivity;
        private final long             mTripId;
        DeleteConfirmListener(TripListActivity a, long id) { mActivity = a; mTripId = id; }
        public void onClick(DialogInterface d, int which) { mActivity.deleteTrip(mTripId); }
    }

    static class NewTripClickListener implements View.OnClickListener {
        private final TripListActivity mActivity;
        NewTripClickListener(TripListActivity a) { mActivity = a; }
        public void onClick(View v) {
            Intent i = new Intent(mActivity, EditTripActivity.class);
            i.putExtra(EditTripActivity.EXTRA_TRIP_ID, -1L);
            mActivity.startActivity(i);
        }
    }
}
