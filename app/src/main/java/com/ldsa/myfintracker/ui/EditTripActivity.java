package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TimePicker;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.Trip;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class EditTripActivity extends Activity {

    static final String EXTRA_TRIP_ID = "trip_id";

    EditText        mEtName;
    Button          mBtnStartDate;
    Button          mBtnStartTime;
    Button          mBtnEndDate;
    Button          mBtnEndTime;
    long            mStartMs;
    long            mEndMs;
    ExpenseDatabase mDb;
    SimpleDateFormat mDateFmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
    SimpleDateFormat mTimeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_trip);
        setTitle(R.string.label_new_trip);

        mDb          = ExpenseDatabase.getInstance(this);
        mEtName      = (EditText) findViewById(R.id.etTripName);
        mBtnStartDate = (Button) findViewById(R.id.btnStartDate);
        mBtnStartTime = (Button) findViewById(R.id.btnStartTime);
        mBtnEndDate   = (Button) findViewById(R.id.btnEndDate);
        mBtnEndTime   = (Button) findViewById(R.id.btnEndTime);

        // Default: start = start of today, end = now
        Calendar cal = Calendar.getInstance();
        mEndMs = cal.getTimeInMillis();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        mStartMs = cal.getTimeInMillis();
        updateDateButtons();

        mBtnStartDate.setOnClickListener(new StartDateClickListener(this));
        mBtnStartTime.setOnClickListener(new StartTimeClickListener(this));
        mBtnEndDate.setOnClickListener(new EndDateClickListener(this));
        mBtnEndTime.setOnClickListener(new EndTimeClickListener(this));

        Button btnSave = (Button) findViewById(R.id.btnSaveTrip);
        btnSave.setOnClickListener(new SaveClickListener(this));
    }

    void updateDateButtons() {
        mBtnStartDate.setText(mDateFmt.format(new Date(mStartMs)));
        mBtnStartTime.setText(mTimeFmt.format(new Date(mStartMs)));
        mBtnEndDate.setText(mDateFmt.format(new Date(mEndMs)));
        mBtnEndTime.setText(mTimeFmt.format(new Date(mEndMs)));
    }

    void save() {
        String name = mEtName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.error_trip_name_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mEndMs <= mStartMs) {
            Toast.makeText(this, R.string.error_trip_date_range, Toast.LENGTH_SHORT).show();
            return;
        }
        Trip t = new Trip();
        t.name    = name;
        t.startMs = mStartMs;
        t.endMs   = mEndMs;
        new SaveTripThread(this, t, mDb, new Handler(Looper.getMainLooper())).start();
    }

    void onSaveDone() {
        Toast.makeText(this, R.string.msg_trip_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    // ── Static inner classes ──────────────────────────────────────

    static class SaveClickListener implements View.OnClickListener {
        private final EditTripActivity mActivity;
        SaveClickListener(EditTripActivity a) { mActivity = a; }
        public void onClick(View v) { mActivity.save(); }
    }

    static class StartDateClickListener implements View.OnClickListener {
        private final EditTripActivity mActivity;
        StartDateClickListener(EditTripActivity a) { mActivity = a; }
        public void onClick(View v) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(mActivity.mStartMs);
            new DatePickerDialog(mActivity,
                new StartDateSetListener(mActivity),
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show();
        }
    }

    static class StartTimeClickListener implements View.OnClickListener {
        private final EditTripActivity mActivity;
        StartTimeClickListener(EditTripActivity a) { mActivity = a; }
        public void onClick(View v) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(mActivity.mStartMs);
            new TimePickerDialog(mActivity,
                new StartTimeSetListener(mActivity),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE), true).show();
        }
    }

    static class EndDateClickListener implements View.OnClickListener {
        private final EditTripActivity mActivity;
        EndDateClickListener(EditTripActivity a) { mActivity = a; }
        public void onClick(View v) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(mActivity.mEndMs);
            new DatePickerDialog(mActivity,
                new EndDateSetListener(mActivity),
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show();
        }
    }

    static class EndTimeClickListener implements View.OnClickListener {
        private final EditTripActivity mActivity;
        EndTimeClickListener(EditTripActivity a) { mActivity = a; }
        public void onClick(View v) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(mActivity.mEndMs);
            new TimePickerDialog(mActivity,
                new EndTimeSetListener(mActivity),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE), true).show();
        }
    }

    static class StartDateSetListener implements DatePickerDialog.OnDateSetListener {
        private final EditTripActivity mActivity;
        StartDateSetListener(EditTripActivity a) { mActivity = a; }
        public void onDateSet(DatePicker view, int year, int month, int day) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(mActivity.mStartMs);
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.DAY_OF_MONTH, day);
            mActivity.mStartMs = cal.getTimeInMillis();
            mActivity.updateDateButtons();
        }
    }

    static class StartTimeSetListener implements TimePickerDialog.OnTimeSetListener {
        private final EditTripActivity mActivity;
        StartTimeSetListener(EditTripActivity a) { mActivity = a; }
        public void onTimeSet(TimePicker view, int hour, int minute) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(mActivity.mStartMs);
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, 0);
            mActivity.mStartMs = cal.getTimeInMillis();
            mActivity.updateDateButtons();
        }
    }

    static class EndDateSetListener implements DatePickerDialog.OnDateSetListener {
        private final EditTripActivity mActivity;
        EndDateSetListener(EditTripActivity a) { mActivity = a; }
        public void onDateSet(DatePicker view, int year, int month, int day) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(mActivity.mEndMs);
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.DAY_OF_MONTH, day);
            mActivity.mEndMs = cal.getTimeInMillis();
            mActivity.updateDateButtons();
        }
    }

    static class EndTimeSetListener implements TimePickerDialog.OnTimeSetListener {
        private final EditTripActivity mActivity;
        EndTimeSetListener(EditTripActivity a) { mActivity = a; }
        public void onTimeSet(TimePicker view, int hour, int minute) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(mActivity.mEndMs);
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, 59);
            mActivity.mEndMs = cal.getTimeInMillis();
            mActivity.updateDateButtons();
        }
    }

    static class SaveTripThread extends Thread {
        private final EditTripActivity mActivity;
        private final Trip             mTrip;
        private final ExpenseDatabase  mDb;
        private final Handler          mHandler;
        SaveTripThread(EditTripActivity a, Trip t, ExpenseDatabase db, Handler h) {
            mActivity = a; mTrip = t; mDb = db; mHandler = h;
        }
        public void run() {
            long id = mDb.insertTrip(mTrip);
            if (id > 0) {
                mTrip.id = id;
                mDb.addTripLabel(mTrip);
            }
            mHandler.post(new SaveTripDoneRunnable(mActivity));
        }
    }

    static class SaveTripDoneRunnable implements Runnable {
        private final EditTripActivity mActivity;
        SaveTripDoneRunnable(EditTripActivity a) { mActivity = a; }
        public void run() {
            if (!mActivity.isFinishing()) mActivity.onSaveDone();
        }
    }
}
