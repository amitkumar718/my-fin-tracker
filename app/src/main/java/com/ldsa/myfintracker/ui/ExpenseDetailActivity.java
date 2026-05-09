package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;
import com.ldsa.myfintracker.db.ExpenseDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ExpenseDetailActivity extends Activity {

    private static final int MENU_EDIT   = 1;
    private static final int MENU_DELETE = 2;

    private TextView mTvAmount;
    private TextView mTvDate;
    private TextView mTvTime;
    private TextView mTvMerchant;
    private TextView mTvReason;
    private TextView mTvCard;
    private TextView mTvOnline;
    private TextView mTvBank;
    private TextView mTvSms;

    private EditText mEtAmount;
    private EditText mEtDate;
    private EditText mEtTime;
    private EditText mEtMerchant;
    private EditText mEtReason;
    private EditText mEtCard;
    private CheckBox mCbOnline;
    private EditText mEtBank;

    private View mViewMode;
    private View mEditMode;
    private LinearLayout mLabelsLayout;
    private Button mBtnSave;
    private Button mBtnAddLabel;

    private ExpenseDatabase mDb;
    private Expense mExpense;
    long mSelectedDateMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expense_detail);

        mDb = ExpenseDatabase.getInstance(this);
        long id = getIntent().getLongExtra(MainActivity.EXTRA_EXPENSE_ID, -1);
        if (id < 0) { finish(); return; }

        mExpense = mDb.getExpenseById(id);
        if (mExpense == null) { finish(); return; }

        mViewMode = findViewById(R.id.layoutViewMode);
        mEditMode = findViewById(R.id.layoutEditMode);

        mTvAmount   = (TextView)  mViewMode.findViewById(R.id.tvAmount);
        mTvDate     = (TextView)  mViewMode.findViewById(R.id.tvDate);
        mTvTime     = (TextView)  mViewMode.findViewById(R.id.tvTime);
        mTvMerchant = (TextView)  mViewMode.findViewById(R.id.tvMerchant);
        mTvReason   = (TextView)  mViewMode.findViewById(R.id.tvReason);
        mTvCard     = (TextView)  mViewMode.findViewById(R.id.tvCard);
        mTvOnline   = (TextView)  mViewMode.findViewById(R.id.tvOnline);
        mTvBank     = (TextView)  mViewMode.findViewById(R.id.tvBank);
        mTvSms      = (TextView)  mViewMode.findViewById(R.id.tvSms);

        mEtAmount   = (EditText)  mEditMode.findViewById(R.id.etAmount);
        mEtDate     = (EditText)  mEditMode.findViewById(R.id.etDate);
        mEtTime     = (EditText)  mEditMode.findViewById(R.id.etTime);
        mEtMerchant = (EditText)  mEditMode.findViewById(R.id.etMerchant);
        mEtReason   = (EditText)  mEditMode.findViewById(R.id.etReason);
        mEtCard     = (EditText)  mEditMode.findViewById(R.id.etCard);
        mCbOnline   = (CheckBox)  mEditMode.findViewById(R.id.cbOnline);
        mEtBank     = (EditText)  mEditMode.findViewById(R.id.etBank);
        mBtnSave    = (Button)    mEditMode.findViewById(R.id.btnSave);

        mLabelsLayout = (LinearLayout) findViewById(R.id.labelsLayout);
        mBtnAddLabel  = (Button)       findViewById(R.id.btnAddLabel);

        mBtnAddLabel.setOnClickListener(new AddLabelClickListener(this));
        mBtnSave.setOnClickListener(new SaveClickListener(this));
        mEtDate.setOnClickListener(new DateClickListener(this));
        mEtTime.setOnClickListener(new TimeClickListener(this));

        showViewMode();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_EDIT,   0, R.string.action_edit);
        menu.add(0, MENU_DELETE, 1, R.string.action_delete);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_EDIT) {
            if (mEditMode.getVisibility() == View.GONE) {
                showEditMode();
            } else {
                showViewMode();
            }
            return true;
        }
        if (item.getItemId() == MENU_DELETE) {
            new AlertDialog.Builder(this)
                .setMessage(R.string.confirm_delete_expense)
                .setPositiveButton(android.R.string.ok, new DeleteConfirmListener(this))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showViewMode() {
        mViewMode.setVisibility(View.VISIBLE);
        mEditMode.setVisibility(View.GONE);
        bindViewMode();
        renderLabels();
    }

    private void showEditMode() {
        mViewMode.setVisibility(View.GONE);
        mEditMode.setVisibility(View.VISIBLE);
        mSelectedDateMs = mExpense.dateMs > 0 ? mExpense.dateMs : System.currentTimeMillis();
        bindEditMode();
        renderLabels();
    }

    private void bindViewMode() {
        mTvAmount.setText(formatAmount(mExpense.amount));
        SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        mTvDate.setText(mExpense.dateMs > 0 ? dateFmt.format(mExpense.dateMs) : "—");
        mTvTime.setText(mExpense.dateMs > 0 ? timeFmt.format(mExpense.dateMs) : "—");
        mTvMerchant.setText(orDash(mExpense.merchant));
        mTvReason.setText(orDash(mExpense.reason));
        mTvCard.setText(orDash(mExpense.card));
        mTvOnline.setText(mExpense.isOnline ? "Online" : "Offline");
        mTvBank.setText(orDash(mExpense.bank));
        mTvSms.setText(orDash(mExpense.originalSms));
    }

    private void bindEditMode() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mSelectedDateMs);
        mEtAmount.setText(String.format(Locale.getDefault(), "%.2f", mExpense.amount));
        mEtDate.setText(new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.getTime()));
        mEtTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.getTime()));
        mEtMerchant.setText(mExpense.merchant != null ? mExpense.merchant : "");
        mEtReason.setText(mExpense.reason != null ? mExpense.reason : "");
        mEtCard.setText(mExpense.card != null ? mExpense.card : "");
        mCbOnline.setChecked(mExpense.isOnline);
        mEtBank.setText(mExpense.bank != null ? mExpense.bank : "");
    }

    void renderLabels() {
        mLabelsLayout.removeAllViews();
        List<String> labels = mExpense.getLabels();
        for (String label : labels) {
            TextView chip = new TextView(this);
            chip.setText(label + "  ✕");
            chip.setTextColor(0xFF1565C0);
            chip.setBackgroundResource(R.drawable.bg_chip);
            chip.setPadding(24, 8, 24, 8);
            chip.setTextSize(12f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 12, 0);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new RemoveLabelListener(this, label));
            mLabelsLayout.addView(chip);
        }
    }

    void removeLabel(String label) {
        List<String> labels = new ArrayList<String>(mExpense.getLabels());
        labels.remove(label);
        mExpense.setLabels(labels);
        mDb.updateExpense(mExpense);
        renderLabels();
    }

    void addLabel(String label) {
        if (label == null || label.trim().isEmpty()) return;
        List<String> labels = new ArrayList<String>(mExpense.getLabels());
        String trimmed = label.trim();
        if (!labels.contains(trimmed)) {
            labels.add(trimmed);
            mExpense.setLabels(labels);
            mDb.updateExpense(mExpense);
            renderLabels();
        }
    }

    void save() {
        String amtStr = mEtAmount.getText().toString().trim().replace(",", "");
        if (amtStr.isEmpty()) {
            Toast.makeText(this, R.string.error_amount_required, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            mExpense.amount = Double.parseDouble(amtStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.error_invalid_amount, Toast.LENGTH_SHORT).show();
            return;
        }
        mExpense.dateMs   = mSelectedDateMs;
        mExpense.merchant = mEtMerchant.getText().toString().trim();
        mExpense.reason   = mEtReason.getText().toString().trim();
        mExpense.card     = mEtCard.getText().toString().trim();
        mExpense.isOnline = mCbOnline.isChecked();
        mExpense.bank     = mEtBank.getText().toString().trim();

        mDb.updateExpense(mExpense);
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        showViewMode();
    }

    void deleteExpense() {
        mDb.deleteExpense(mExpense.id);
        Toast.makeText(this, R.string.msg_expense_deleted, Toast.LENGTH_SHORT).show();
        finish();
    }

    void onDateSet(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mSelectedDateMs);
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, day);
        mSelectedDateMs = cal.getTimeInMillis();
        mEtDate.setText(new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.getTime()));
    }

    void onTimeSet(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mSelectedDateMs);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        mSelectedDateMs = cal.getTimeInMillis();
        mEtTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.getTime()));
    }

    private String formatAmount(double amount) {
        long intPart  = (long) amount;
        long fracPart = Math.round((amount - intPart) * 100);
        return String.format(Locale.getDefault(), "₹%,d.%02d", intPart, fracPart);
    }

    private String orDash(String s) {
        return (s != null && !s.isEmpty()) ? s : "—";
    }

    // ============================================================
    // Static listener classes — D8 constraints
    // ============================================================

    static class AddLabelClickListener implements View.OnClickListener {
        private final ExpenseDetailActivity mA;
        AddLabelClickListener(ExpenseDetailActivity a) { mA = a; }
        public void onClick(View v) {
            final EditText et = new EditText(mA);
            et.setHint(mA.getString(R.string.hint_label));
            new AlertDialog.Builder(mA)
                .setTitle(R.string.btn_add_label)
                .setView(et)
                .setPositiveButton(android.R.string.ok, new AddLabelConfirmListener(mA, et))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        }
    }

    static class AddLabelConfirmListener implements DialogInterface.OnClickListener {
        private final ExpenseDetailActivity mA;
        private final EditText mEt;
        AddLabelConfirmListener(ExpenseDetailActivity a, EditText et) { mA = a; mEt = et; }
        public void onClick(DialogInterface d, int which) {
            mA.addLabel(mEt.getText().toString());
        }
    }

    static class RemoveLabelListener implements View.OnClickListener {
        private final ExpenseDetailActivity mA;
        private final String mLabel;
        RemoveLabelListener(ExpenseDetailActivity a, String label) { mA = a; mLabel = label; }
        public void onClick(View v) { mA.removeLabel(mLabel); }
    }

    static class SaveClickListener implements View.OnClickListener {
        private final ExpenseDetailActivity mA;
        SaveClickListener(ExpenseDetailActivity a) { mA = a; }
        public void onClick(View v) { mA.save(); }
    }

    static class DeleteConfirmListener implements DialogInterface.OnClickListener {
        private final ExpenseDetailActivity mA;
        DeleteConfirmListener(ExpenseDetailActivity a) { mA = a; }
        public void onClick(DialogInterface d, int which) { mA.deleteExpense(); }
    }

    static class DateClickListener implements View.OnClickListener {
        private final ExpenseDetailActivity mA;
        DateClickListener(ExpenseDetailActivity a) { mA = a; }
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
        private final ExpenseDetailActivity mA;
        TimeClickListener(ExpenseDetailActivity a) { mA = a; }
        public void onClick(View v) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(mA.mSelectedDateMs);
            new TimePickerDialog(mA, new TimeSetListener(mA),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE), true).show();
        }
    }

    static class DateSetListener implements DatePickerDialog.OnDateSetListener {
        private final ExpenseDetailActivity mA;
        DateSetListener(ExpenseDetailActivity a) { mA = a; }
        public void onDateSet(DatePicker v, int year, int month, int day) {
            mA.onDateSet(year, month, day);
        }
    }

    static class TimeSetListener implements TimePickerDialog.OnTimeSetListener {
        private final ExpenseDetailActivity mA;
        TimeSetListener(ExpenseDetailActivity a) { mA = a; }
        public void onTimeSet(TimePicker v, int hour, int minute) {
            mA.onTimeSet(hour, minute);
        }
    }
}
