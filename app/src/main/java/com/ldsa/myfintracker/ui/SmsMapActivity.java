package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.ExtractionPattern;
import com.ldsa.myfintracker.db.SenderConfig;
import com.ldsa.myfintracker.sms.SmsReader;

import java.text.ParseException;
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
    public static final String EXTRA_SENDER_ID   = "sender_id";

    // token labels inserted into the SMS template
    static final String TOK_AMOUNT   = "(/amount/)";
    static final String TOK_MERCHANT = "(/merchant/)";
    static final String TOK_CARD     = "(/card/)";
    static final String TOK_ACNO     = "(/ac_no/)";
    static final String TOK_UPI      = "(/upi/)";
    static final String TOK_DATE     = "(/date/)";
    static final String TOK_TIME     = "(/time/)";
    static final String TOK_BALANCE  = "(/balance/)";
    static final String TOK_IGNORE   = "(/ignore/)";

    static final String[] ALL_TOKENS = {
        TOK_AMOUNT, TOK_MERCHANT, TOK_CARD, TOK_ACNO, TOK_UPI,
        TOK_DATE, TOK_TIME, TOK_BALANCE, TOK_IGNORE
    };

    static final String[] TYPE_VALUES = {
        "", "UPI", "CARD_ONLINE", "CARD_POS",
        "NETBANKING_PURCHASE", "NETBANKING_TRANSFER", "ATM"
    };

    private TextView mTvSmsBody;
    private TextView mTvExtractPreview;
    private TextView mTvPatternHint;
    private EditText mEtTemplate;
    private EditText mEtDate;
    private EditText mEtTime;
    private EditText mEtReason;
    private EditText mEtBank;
    private EditText mEtRemarks;
    private Spinner  mSpinnerTxnType;
    private CheckBox mCbOnline;

    long   mSelectedDateMs;
    String mTxnType = "";
    String mSmsBody;
    private String mSmsAddress;
    private long   mSenderId = -1L;
    private ExpenseDatabase    mDb;
    private ExtractionPattern  mCurrentPattern; // non-null when autoExtract found a saved pattern

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_map);

        mSmsAddress     = getIntent().getStringExtra(EXTRA_SMS_ADDRESS);
        mSmsBody        = getIntent().getStringExtra(EXTRA_SMS_BODY);
        mSelectedDateMs = getIntent().getLongExtra(EXTRA_SMS_DATE, System.currentTimeMillis());
        mSenderId       = getIntent().getLongExtra(EXTRA_SENDER_ID, -1L);

        mDb = ExpenseDatabase.getInstance(this);

        mTvSmsBody        = (TextView) findViewById(R.id.tvSmsBody);
        mTvExtractPreview = (TextView) findViewById(R.id.tvExtractPreview);
        mTvPatternHint    = (TextView) findViewById(R.id.tvPatternHint);
        mEtTemplate       = (EditText) findViewById(R.id.etTemplate);
        mEtDate           = (EditText) findViewById(R.id.etDate);
        mEtTime           = (EditText) findViewById(R.id.etTime);
        mEtReason         = (EditText) findViewById(R.id.etReason);
        mEtBank           = (EditText) findViewById(R.id.etBank);
        mEtRemarks        = (EditText) findViewById(R.id.etRemarks);
        mSpinnerTxnType   = (Spinner)  findViewById(R.id.spinnerTxnType);
        mCbOnline         = (CheckBox) findViewById(R.id.cbOnline);
        Button btnSave    = (Button)   findViewById(R.id.btnSave);

        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(
            this, R.array.transaction_type_labels, android.R.layout.simple_spinner_item);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerTxnType.setAdapter(typeAdapter);
        mSpinnerTxnType.setOnItemSelectedListener(new TxnTypeSelectedListener(this));

        String[] btnIds_tokens = {
            TOK_AMOUNT, TOK_MERCHANT, TOK_CARD,
            TOK_ACNO,   TOK_UPI,     TOK_DATE,
            TOK_TIME,   TOK_BALANCE, TOK_IGNORE
        };
        int[] btnIds = {
            R.id.btnTokAmount, R.id.btnTokMerchant, R.id.btnTokCard,
            R.id.btnTokAcNo,   R.id.btnTokUpi,     R.id.btnTokDate,
            R.id.btnTokTime,   R.id.btnTokBalance,  R.id.btnTokIgnore
        };
        for (int i = 0; i < btnIds.length; i++) {
            Button b = (Button) findViewById(btnIds[i]);
            b.setOnClickListener(new TokenButtonListener(this, btnIds_tokens[i]));
        }

        mTvSmsBody.setText(mSmsBody != null ? mSmsBody : "");
        mEtBank.setText(mSmsAddress != null ? mSmsAddress : "");
        updateDateTimeDisplay();

        mEtTemplate.setText(mSmsBody != null ? mSmsBody : "");
        autoExtract();
        updatePatternHint();
        updateRegexPreview();

        mEtTemplate.addTextChangedListener(new TemplateWatcher(this));
        mEtDate.setOnClickListener(new DateClickListener(this));
        mEtTime.setOnClickListener(new TimeClickListener(this));
        mTvPatternHint.setOnClickListener(new PatternHintClickListener(this));
        btnSave.setOnClickListener(new SaveClickListener(this));
    }

    private void autoExtract() {
        if (mSmsBody == null || mSmsBody.isEmpty()) return;

        SenderConfig cfg = null;
        if (mSenderId >= 0) cfg = mDb.getSenderById(mSenderId);
        if (cfg == null && mSmsAddress != null)
            cfg = SmsReader.findConfig(mSmsAddress, mDb.getAllSenders());
        if (cfg == null) return;

        List<ExtractionPattern> patterns = mDb.getPatternsBySender(cfg.id);
        for (ExtractionPattern p : patterns) {
            if (!p.matches(mSmsBody)) continue;
            if (p.templateText != null && !p.templateText.isEmpty()) {
                mEtTemplate.setText(p.templateText);
            }
            mCurrentPattern = p;
            mTxnType = p.transactionType != null ? p.transactionType : "";
            for (int i = 0; i < TYPE_VALUES.length; i++) {
                if (TYPE_VALUES[i].equals(mTxnType)) { mSpinnerTxnType.setSelection(i); break; }
            }
            String date = p.extractGroup(mSmsBody, p.dateGroup).trim();
            if (!date.isEmpty()) tryApplyExtractedDate(date);
            String time = p.extractGroup(mSmsBody, p.timeGroup).trim();
            if (!time.isEmpty()) tryApplyExtractedTime(time);
            return;
        }
    }

    void updatePatternHint() {
        if (mCurrentPattern != null && mCurrentPattern.id > 0) {
            mTvPatternHint.setVisibility(View.VISIBLE);
            String name = (mCurrentPattern.name != null && !mCurrentPattern.name.isEmpty())
                ? mCurrentPattern.name : "existing pattern";
            mTvPatternHint.setText("Modifying: " + name + "  ·  tap to save as new instead");
        } else {
            mTvPatternHint.setVisibility(View.GONE);
        }
    }

    void clearCurrentPattern() {
        mCurrentPattern = null;
        updatePatternHint();
    }

    void insertToken(String token) {
        int cursor = mEtTemplate.getSelectionStart();
        if (cursor < 0) cursor = mEtTemplate.getText().length();
        mEtTemplate.getText().insert(cursor, token);
    }

    void save() {
        String template = mEtTemplate.getText().toString();
        ExtractionPattern pattern = buildPatternFromTemplate(template);

        if (pattern.amountGroup < 0) {
            Toast.makeText(this, R.string.error_template_needs_amount, Toast.LENGTH_SHORT).show();
            return;
        }

        Matcher matcher = null;
        if (mSmsBody != null && pattern.templateRegex != null) {
            try {
                matcher = Pattern.compile(pattern.templateRegex,
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(mSmsBody);
                if (!matcher.find()) matcher = null;
            } catch (Exception ignored) {}
        }

        String amtStr   = groupStr(matcher, pattern.amountGroup).replaceAll("[^0-9.]", "");
        String balStr   = groupStr(matcher, pattern.balanceGroup)
                            .replaceAll("[^0-9.,]", "").replaceAll(",", "");
        String merchant = groupStr(matcher, pattern.merchantGroup).trim();
        String card     = groupStr(matcher, pattern.cardGroup).trim();
        String account  = groupStr(matcher, pattern.accountGroup).trim();
        String dateStr  = groupStr(matcher, pattern.dateGroup).trim();
        String timeStr  = groupStr(matcher, pattern.timeGroup).trim();

        if (amtStr.isEmpty()) {
            Toast.makeText(this, R.string.error_amount_required, Toast.LENGTH_SHORT).show();
            return;
        }
        double amount;
        try { amount = Double.parseDouble(amtStr); }
        catch (NumberFormatException e) {
            Toast.makeText(this, R.string.error_invalid_amount, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!dateStr.isEmpty()) tryApplyExtractedDate(dateStr);
        if (!timeStr.isEmpty()) tryApplyExtractedTime(timeStr);

        double balance = 0;
        if (!balStr.isEmpty()) {
            try { balance = Double.parseDouble(balStr); } catch (NumberFormatException ignored) {}
        }

        // ── Persist pattern (update or insert) ──────────────────────
        long patternId = -1;
        boolean isUpdate = (mCurrentPattern != null
                            && mCurrentPattern.id > 0
                            && mSenderId >= 0);
        if (mSenderId >= 0) {
            if (isUpdate) {
                pattern.id       = mCurrentPattern.id;
                pattern.senderId = mCurrentPattern.senderId;
                mDb.updatePattern(pattern);
                patternId = mCurrentPattern.id;
            } else {
                patternId = mDb.insertPattern(pattern);
            }
        }

        // Count BEFORE inserting the new expense so the dialog count reflects prior entries only
        int priorCount = isUpdate ? mDb.countExpensesByPattern(patternId) : 0;

        // ── Persist expense ──────────────────────────────────────────
        Expense expense = new Expense();
        expense.amount          = amount;
        expense.dateMs          = mSelectedDateMs;
        expense.merchant        = merchant;
        expense.reason          = mEtReason.getText().toString().trim();
        expense.card            = card;
        expense.accountNumber   = account;
        expense.isOnline        = mCbOnline.isChecked();
        expense.bank            = mEtBank.getText().toString().trim();
        expense.originalSms     = mSmsBody;
        expense.balance         = balance;
        expense.transactionType = mTxnType;
        expense.remarks         = mEtRemarks.getText().toString().trim();
        expense.patternId       = patternId;
        expense.createdAt       = System.currentTimeMillis();
        mDb.insertExpense(expense);

        // ── Offer re-apply if updating an existing pattern ───────────
        if (isUpdate && priorCount > 0) {
            showReApplyDialog(pattern, priorCount);
            return;
        }

        finishWithSuccess(mSenderId >= 0);
    }

    private void showReApplyDialog(ExtractionPattern pattern, int count) {
        new AlertDialog.Builder(this)
            .setTitle("Pattern updated")
            .setMessage("Re-apply to " + count + " existing expense"
                + (count == 1 ? "" : "s") + "?\n\nAmount, merchant, card and balance will be "
                + "recalculated. Date, reason and notes are kept as-is.")
            .setPositiveButton("Re-apply", new PatternUpdateDialogListener(this, pattern, true))
            .setNegativeButton("Skip",     new PatternUpdateDialogListener(this, pattern, false))
            .setCancelable(false)
            .show();
    }

    void startReApply(ExtractionPattern pattern) {
        Toast.makeText(this, "Updating expenses…", Toast.LENGTH_SHORT).show();
        new ReApplyThread(this, pattern, mDb, new Handler(Looper.getMainLooper())).start();
    }

    void onReApplyDone(int updated) {
        Toast.makeText(this,
            updated + " expense" + (updated == 1 ? "" : "s") + " updated",
            Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    void finishWithSuccess(boolean patternSaved) {
        String msg = patternSaved
            ? getString(R.string.msg_expense_and_pattern_saved)
            : getString(R.string.msg_expense_saved);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    // ── Pattern building from template ──────────────────────────────────────

    private ExtractionPattern buildPatternFromTemplate(String template) {
        ExtractionPattern p = new ExtractionPattern();
        p.senderId        = mSenderId;
        p.templateText    = template;
        p.transactionType = mTxnType;

        ExtractionPattern tmp = new ExtractionPattern();
        tmp.transactionType = mTxnType;
        p.name = tmp.getTypeLabel() + " "
            + new SimpleDateFormat("dd MMM", Locale.getDefault())
                  .format(new java.util.Date(mSelectedDateMs));

        template = template.trim(); // strip trailing whitespace to avoid literal mismatch

        StringBuilder regex   = new StringBuilder();
        int pos      = 0;
        int groupNum = 0;
        StringBuilder litBuf = new StringBuilder();

        while (pos < template.length()) {
            String found = null;
            for (String tok : ALL_TOKENS) {
                if (template.startsWith(tok, pos)) { found = tok; break; }
            }
            if (found != null) {
                if (litBuf.length() > 0) {
                    regex.append(Pattern.quote(litBuf.toString()));
                    litBuf.setLength(0);
                }
                if (TOK_IGNORE.equals(found)) {
                    regex.append(".*?");
                } else {
                    groupNum++;
                    regex.append(captureGroupFor(tokenToFieldType(found)));
                    assignGroup(p, found, groupNum);
                }
                pos += found.length();
            } else {
                litBuf.append(template.charAt(pos));
                pos++;
            }
        }
        if (litBuf.length() > 0) regex.append(Pattern.quote(litBuf.toString()));

        p.templateRegex = regex.toString();
        return p;
    }

    private String tokenToFieldType(String token) {
        if (TOK_AMOUNT.equals(token))   return "amount";
        if (TOK_BALANCE.equals(token))  return "balance";
        if (TOK_MERCHANT.equals(token)) return "merchant";
        if (TOK_CARD.equals(token))     return "card";
        if (TOK_ACNO.equals(token))     return "account";
        if (TOK_UPI.equals(token))      return "upi";
        if (TOK_DATE.equals(token))     return "date";
        if (TOK_TIME.equals(token))     return "time";
        return "text";
    }

    private void assignGroup(ExtractionPattern p, String token, int group) {
        if (TOK_AMOUNT.equals(token))   { p.amountGroup   = group; return; }
        if (TOK_BALANCE.equals(token))  { p.balanceGroup  = group; return; }
        if (TOK_MERCHANT.equals(token)) { p.merchantGroup = group; return; }
        if (TOK_CARD.equals(token))     { p.cardGroup     = group; return; }
        if (TOK_ACNO.equals(token))     { p.accountGroup  = group; return; }
        if (TOK_UPI.equals(token))      { return; } // structural anchor — not stored
        if (TOK_DATE.equals(token))     { p.dateGroup     = group; return; }
        if (TOK_TIME.equals(token))     { p.timeGroup     = group; return; }
    }

    private String captureGroupFor(String fieldType) {
        switch (fieldType) {
            case "amount":
            case "balance": return "([\\d,]+\\.?\\d{0,2})";
            case "card":    return "(\\d{4})";
            case "upi":     return "([\\w@.\\-]+)";
            case "account": return "([X\\dx*]+)";
            case "date":    return "(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}|\\d{1,2}[\\- ][A-Za-z]{3}[\\- ]\\d{2,4})";
            case "time":    return "(\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s?[AP]M)?)";
            default:        return "([^\\n]+?)";
        }
    }

    private String groupStr(Matcher m, int group) {
        if (m == null || group < 0 || group > m.groupCount()) return "";
        String v = m.group(group);
        return v != null ? v : "";
    }

    // ── Date / time helpers ─────────────────────────────────────────────────

    private void tryApplyExtractedDate(String raw) {
        String[] formats = {"dd/MM/yyyy", "dd-MM-yyyy", "dd/MM/yy", "dd-MM-yy",
                            "yyyy-MM-dd", "dd MMM yyyy", "dd MMM yy",
                            "dd-MMM-yyyy", "dd-MMM-yy"};
        for (String fmt : formats) {
            try {
                java.util.Date d = new SimpleDateFormat(fmt, Locale.US).parse(raw);
                if (d != null) { mSelectedDateMs = d.getTime(); updateDateTimeDisplay(); return; }
            } catch (ParseException ignored) {}
        }
    }

    private void tryApplyExtractedTime(String raw) {
        String[] formats = {"HH:mm:ss", "HH:mm", "hh:mm:ss a", "hh:mm a"};
        for (String fmt : formats) {
            try {
                java.util.Date d = new SimpleDateFormat(fmt, Locale.US).parse(raw.trim());
                if (d == null) continue;
                Calendar timeCal = Calendar.getInstance();
                timeCal.setTime(d);
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(mSelectedDateMs);
                cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
                cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
                cal.set(Calendar.SECOND, 0);
                mSelectedDateMs = cal.getTimeInMillis();
                updateDateTimeDisplay();
                return;
            } catch (ParseException ignored) {}
        }
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

    // ── Extraction preview ──────────────────────────────────────────────────

    void updateRegexPreview() {
        String template = mEtTemplate.getText().toString();
        if (mSmsBody == null || mSmsBody.isEmpty() || template.isEmpty()) {
            mTvExtractPreview.setVisibility(View.GONE);
            return;
        }
        ExtractionPattern p = buildPatternFromTemplate(template);
        StringBuilder sb = new StringBuilder();
        Matcher m = null;
        try {
            m = Pattern.compile(p.templateRegex,
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(mSmsBody);
            if (!m.find()) m = null;
        } catch (Exception e) {
            mTvExtractPreview.setText("Regex error: " + e.getMessage());
            mTvExtractPreview.setVisibility(View.VISIBLE);
            return;
        }
        if (m == null) {
            sb.append("(no match)\n\n");
        } else {
            addPreviewLine(sb, "Amount",   groupStr(m, p.amountGroup));
            addPreviewLine(sb, "Balance",  groupStr(m, p.balanceGroup));
            addPreviewLine(sb, "Merchant", groupStr(m, p.merchantGroup));
            addPreviewLine(sb, "Card",     groupStr(m, p.cardGroup));
            addPreviewLine(sb, "Account",  groupStr(m, p.accountGroup));
            addPreviewLine(sb, "Date",     groupStr(m, p.dateGroup));
            addPreviewLine(sb, "Time",     groupStr(m, p.timeGroup));
            if (sb.length() > 0) sb.append("\n");
        }
        sb.append(p.templateRegex);
        mTvExtractPreview.setText(sb.toString());
        mTvExtractPreview.setVisibility(View.VISIBLE);
    }

    private void addPreviewLine(StringBuilder sb, String label, String value) {
        if (!value.isEmpty()) sb.append(label).append(": ").append(value).append("\n");
    }

    // ============================================================
    // Static listener classes — D8 constraints
    // ============================================================

    static class TemplateWatcher implements TextWatcher {
        private final SmsMapActivity mA;
        TemplateWatcher(SmsMapActivity a) { mA = a; }
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
        public void afterTextChanged(Editable s) { mA.updateRegexPreview(); }
    }

    static class TokenButtonListener implements View.OnClickListener {
        private final SmsMapActivity mA;
        private final String mToken;
        TokenButtonListener(SmsMapActivity a, String token) { mA = a; mToken = token; }
        public void onClick(View v) { mA.insertToken(mToken); }
    }

    static class PatternHintClickListener implements View.OnClickListener {
        private final SmsMapActivity mA;
        PatternHintClickListener(SmsMapActivity a) { mA = a; }
        public void onClick(View v) { mA.clearCurrentPattern(); }
    }

    static class PatternUpdateDialogListener implements DialogInterface.OnClickListener {
        private final SmsMapActivity    mA;
        private final ExtractionPattern mPattern;
        private final boolean           mDoReApply;

        PatternUpdateDialogListener(SmsMapActivity a, ExtractionPattern p, boolean doReApply) {
            mA = a; mPattern = p; mDoReApply = doReApply;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (mDoReApply) {
                mA.startReApply(mPattern);
            } else {
                mA.finishWithSuccess(true);
            }
        }
    }

    static class ReApplyThread extends Thread {
        private final SmsMapActivity    mA;
        private final ExtractionPattern mPattern;
        private final ExpenseDatabase   mDb;
        private final Handler           mHandler;

        ReApplyThread(SmsMapActivity a, ExtractionPattern p, ExpenseDatabase db, Handler h) {
            mA = a; mPattern = p; mDb = db; mHandler = h;
        }

        public void run() {
            int updated = mDb.reApplyPattern(mPattern);
            mHandler.post(new ReApplyDoneRunnable(mA, updated));
        }
    }

    static class ReApplyDoneRunnable implements Runnable {
        private final SmsMapActivity mA;
        private final int            mUpdated;
        ReApplyDoneRunnable(SmsMapActivity a, int updated) { mA = a; mUpdated = updated; }
        public void run() {
            if (!mA.isFinishing()) mA.onReApplyDone(mUpdated);
        }
    }

    static class TxnTypeSelectedListener implements AdapterView.OnItemSelectedListener {
        private final SmsMapActivity mA;
        TxnTypeSelectedListener(SmsMapActivity a) { mA = a; }
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            mA.mTxnType = TYPE_VALUES[pos];
            ExtractionPattern tmp = new ExtractionPattern();
            tmp.transactionType = mA.mTxnType;
            mA.mCbOnline.setChecked(tmp.isOnlineType());
        }
        public void onNothingSelected(AdapterView<?> parent) {}
    }

    static class DateClickListener implements View.OnClickListener {
        private final SmsMapActivity mA;
        DateClickListener(SmsMapActivity a) { mA = a; }
        public void onClick(View v) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(mA.mSelectedDateMs);
            new DatePickerDialog(mA, new DateSetListener(mA),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
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
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
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
        public void onTimeSet(TimePicker v, int hour, int minute) { mA.onTimeSet(hour, minute); }
    }

    static class SaveClickListener implements View.OnClickListener {
        private final SmsMapActivity mA;
        SaveClickListener(SmsMapActivity a) { mA = a; }
        public void onClick(View v) { mA.save(); }
    }
}
