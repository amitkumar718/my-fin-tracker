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
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.SenderConfig;
import com.ldsa.myfintracker.sms.SmsReader;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsMapActivity extends Activity {

    public static final String EXTRA_SMS_ADDRESS = "sms_address";
    public static final String EXTRA_SMS_BODY    = "sms_body";
    public static final String EXTRA_SMS_DATE    = "sms_date";

    private TextView mTvSmsBody;
    private EditText mEtAmount;
    private EditText mEtDate;
    private EditText mEtTime;
    private EditText mEtMerchant;
    private EditText mEtReason;
    private EditText mEtCard;
    private CheckBox mCbOnline;
    private EditText mEtBank;

    long mSelectedDateMs;
    private String mSmsAddress;
    private String mSmsBody;
    private ExpenseDatabase mDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_map);

        mSmsAddress     = getIntent().getStringExtra(EXTRA_SMS_ADDRESS);
        mSmsBody        = getIntent().getStringExtra(EXTRA_SMS_BODY);
        mSelectedDateMs = getIntent().getLongExtra(EXTRA_SMS_DATE, System.currentTimeMillis());

        mDb = ExpenseDatabase.getInstance(this);

        mTvSmsBody  = (TextView)  findViewById(R.id.tvSmsBody);
        mEtAmount   = (EditText)  findViewById(R.id.etAmount);
        mEtDate     = (EditText)  findViewById(R.id.etDate);
        mEtTime     = (EditText)  findViewById(R.id.etTime);
        mEtMerchant = (EditText)  findViewById(R.id.etMerchant);
        mEtReason   = (EditText)  findViewById(R.id.etReason);
        mEtCard     = (EditText)  findViewById(R.id.etCard);
        mCbOnline   = (CheckBox)  findViewById(R.id.cbOnline);
        mEtBank     = (EditText)  findViewById(R.id.etBank);
        Button btnSave = (Button) findViewById(R.id.btnSave);

        mTvSmsBody.setText(mSmsBody != null ? mSmsBody : "");
        mEtBank.setText(mSmsAddress != null ? mSmsAddress : "");
        updateDateTimeDisplay();

        autoExtract();

        mEtDate.setOnClickListener(new DateClickListener(this));
        mEtTime.setOnClickListener(new TimeClickListener(this));
        btnSave.setOnClickListener(new SaveClickListener(this));
    }

    private void autoExtract() {
        if (mSmsBody == null || mSmsAddress == null) return;
        List<SenderConfig> configs = mDb.getAllSenders();
        SenderConfig cfg = SmsReader.findConfig(mSmsAddress, configs);
        if (cfg == null) return;

        String amount   = extractFirst(mSmsBody, cfg.amountRegex);
        String merchant = extractFirst(mSmsBody, cfg.merchantRegex);
        String card     = extractFirst(mSmsBody, cfg.cardRegex);

        if (!amount.isEmpty())
            mEtAmount.setText(amount.replaceAll("[^0-9.]", ""));
        if (!merchant.isEmpty())
            mEtMerchant.setText(merchant.trim());
        if (!card.isEmpty())
            mEtCard.setText(card.trim());
    }

    private String extractFirst(String text, String regex) {
        if (regex == null || regex.isEmpty()) return "";
        try {
            Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
            if (m.find()) return m.groupCount() > 0 ? m.group(1) : m.group(0);
        } catch (Exception ignored) {}
        return "";
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
        expense.amount      = amount;
        expense.dateMs      = mSelectedDateMs;
        expense.merchant    = mEtMerchant.getText().toString().trim();
        expense.reason      = mEtReason.getText().toString().trim();
        expense.card        = mEtCard.getText().toString().trim();
        expense.isOnline    = mCbOnline.isChecked();
        expense.bank        = mEtBank.getText().toString().trim();
        expense.originalSms = mSmsBody;
        expense.createdAt   = System.currentTimeMillis();

        mDb.insertExpense(expense);
        Toast.makeText(this, R.string.msg_expense_saved, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    // ============================================================
    // Static listener classes — D8 constraints
    // ============================================================

    static class DateClickListener implements View.OnClickListener {
        private final SmsMapActivity mA;
        DateClickListener(SmsMapActivity a) { mA = a; }
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
        private final SmsMapActivity mA;
        TimeClickListener(SmsMapActivity a) { mA = a; }
        public void onClick(View v) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(mA.mSelectedDateMs);
            new TimePickerDialog(mA, new TimeSetListener(mA),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE), true).show();
        }
    }

    static class DateSetListener implements DatePickerDialog.OnDateSetListener {
        private final SmsMapActivity mA;
        DateSetListener(SmsMapActivity a) { mA = a; }
        public void onDateSet(DatePicker v, int year, int month, int day) {
            mA.onDateSet(year, month, day);
        }
    }

    static class TimeSetListener implements TimePickerDialog.OnTimeSetListener {
        private final SmsMapActivity mA;
        TimeSetListener(SmsMapActivity a) { mA = a; }
        public void onTimeSet(TimePicker v, int hour, int minute) {
            mA.onTimeSet(hour, minute);
        }
    }

    static class SaveClickListener implements View.OnClickListener {
        private final SmsMapActivity mA;
        SaveClickListener(SmsMapActivity a) { mA = a; }
        public void onClick(View v) { mA.save(); }
    }
}
