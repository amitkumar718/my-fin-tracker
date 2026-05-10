package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TimePicker;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;
import com.ldsa.myfintracker.db.ExpenseDatabase;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AddExpenseActivity extends Activity {

    private EditText mEtAmount;
    private EditText mEtDate;
    private EditText mEtTime;
    private EditText mEtMerchant;
    private EditText mEtReason;
    private EditText mEtCard;
    private EditText mEtBalance;
    private CheckBox mCbOnline;
    private EditText mEtBank;
    private EditText mEtRemarks;

    long mSelectedDateMs;
    private ExpenseDatabase mDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_expense);

        mDb             = ExpenseDatabase.getInstance(this);
        mSelectedDateMs = System.currentTimeMillis();

        mEtAmount   = (EditText)  findViewById(R.id.etAmount);
        mEtDate     = (EditText)  findViewById(R.id.etDate);
        mEtTime     = (EditText)  findViewById(R.id.etTime);
        mEtMerchant = (EditText)  findViewById(R.id.etMerchant);
        mEtReason   = (EditText)  findViewById(R.id.etReason);
        mEtCard     = (EditText)  findViewById(R.id.etCard);
        mEtBalance  = (EditText)  findViewById(R.id.etBalance);
        mCbOnline   = (CheckBox)  findViewById(R.id.cbOnline);
        mEtBank     = (EditText)  findViewById(R.id.etBank);
        mEtRemarks  = (EditText)  findViewById(R.id.etRemarks);
        Button btnSave = (Button) findViewById(R.id.btnSave);

        updateDateTimeDisplay();

        mEtDate.setOnClickListener(new DateClickListener(this));
        mEtTime.setOnClickListener(new TimeClickListener(this));
        btnSave.setOnClickListener(new SaveClickListener(this));
    }

    void onDateSet(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mSelectedDateMs);
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, day);
        mSelectedDateMs = cal.getTimeInMillis();
        updateDateTimeDisplay();
    }

    void onTimeSet(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mSelectedDateMs);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        mSelectedDateMs = cal.getTimeInMillis();
        updateDateTimeDisplay();
    }

    void updateDateTimeDisplay() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mSelectedDateMs);
        mEtDate.setText(new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.getTime()));
        mEtTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.getTime()));
    }

    void save() {
        String amtStr = mEtAmount.getText().toString().trim().replace(",", "");
        if (amtStr.isEmpty()) {
            Toast.makeText(this, R.string.error_amount_required, Toast.LENGTH_SHORT).show();
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amtStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.error_invalid_amount, Toast.LENGTH_SHORT).show();
            return;
        }

        Expense expense = new Expense();
        expense.amount   = amount;
        expense.dateMs   = mSelectedDateMs;
        expense.merchant = mEtMerchant.getText().toString().trim();
        expense.reason   = mEtReason.getText().toString().trim();
        expense.card     = mEtCard.getText().toString().trim();
        expense.isOnline = mCbOnline.isChecked();
        expense.bank     = mEtBank.getText().toString().trim();
        expense.remarks  = mEtRemarks.getText().toString().trim();
        expense.createdAt = System.currentTimeMillis();
        String balStr = mEtBalance.getText().toString().trim().replace(",", "");
        if (!balStr.isEmpty()) {
            try { expense.balance = Double.parseDouble(balStr); } catch (NumberFormatException ignored) {}
        }

        mDb.insertExpense(expense);
        Toast.makeText(this, R.string.msg_expense_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    static class DateClickListener implements View.OnClickListener {
        private final AddExpenseActivity mA;
        DateClickListener(AddExpenseActivity a) { mA = a; }
        public void onClick(View v) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(mA.mSelectedDateMs);
            new DatePickerDialog(mA, new DateSetListener(mA),
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show();
        }
    }

    static class TimeClickListener implements View.OnClickListener {
        private final AddExpenseActivity mA;
        TimeClickListener(AddExpenseActivity a) { mA = a; }
        public void onClick(View v) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(mA.mSelectedDateMs);
            new TimePickerDialog(mA, new TimeSetListener(mA),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE), true).show();
        }
    }

    static class DateSetListener implements DatePickerDialog.OnDateSetListener {
        private final AddExpenseActivity mA;
        DateSetListener(AddExpenseActivity a) { mA = a; }
        public void onDateSet(DatePicker v, int year, int month, int day) {
            mA.onDateSet(year, month, day);
        }
    }

    static class TimeSetListener implements TimePickerDialog.OnTimeSetListener {
        private final AddExpenseActivity mA;
        TimeSetListener(AddExpenseActivity a) { mA = a; }
        public void onTimeSet(TimePicker v, int hour, int minute) {
            mA.onTimeSet(hour, minute);
        }
    }

    static class SaveClickListener implements View.OnClickListener {
        private final AddExpenseActivity mA;
        SaveClickListener(AddExpenseActivity a) { mA = a; }
        public void onClick(View v) { mA.save(); }
    }
}
