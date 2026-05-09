package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.SenderConfig;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsExtractConfigActivity extends Activity {

    public static final String EXTRA_SENDER_ID = "sender_id";
    public static final String EXTRA_SMS_BODY  = "sms_body";

    private String mSmsBody;
    private long mSenderId;
    private ExpenseDatabase mDb;

    private EditText mEtAmountRegex;
    private EditText mEtDateRegex;
    private EditText mEtMerchantRegex;
    private EditText mEtCardRegex;

    private TextView mTvAmountPreview;
    private TextView mTvDatePreview;
    private TextView mTvMerchantPreview;
    private TextView mTvCardPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_extract_config);

        mSenderId = getIntent().getLongExtra(EXTRA_SENDER_ID, -1L);
        mSmsBody  = getIntent().getStringExtra(EXTRA_SMS_BODY);
        mDb       = ExpenseDatabase.getInstance(this);

        TextView tvSmsBody     = (TextView) findViewById(R.id.tvSmsBody);
        mEtAmountRegex         = (EditText) findViewById(R.id.etAmountRegex);
        mEtDateRegex           = (EditText) findViewById(R.id.etDateRegex);
        mEtMerchantRegex       = (EditText) findViewById(R.id.etMerchantRegex);
        mEtCardRegex           = (EditText) findViewById(R.id.etCardRegex);
        mTvAmountPreview       = (TextView) findViewById(R.id.tvAmountPreview);
        mTvDatePreview         = (TextView) findViewById(R.id.tvDatePreview);
        mTvMerchantPreview     = (TextView) findViewById(R.id.tvMerchantPreview);
        mTvCardPreview         = (TextView) findViewById(R.id.tvCardPreview);
        Button btnSave         = (Button)   findViewById(R.id.btnSaveExtraction);

        tvSmsBody.setText(mSmsBody != null ? mSmsBody : "");

        // Pre-fill if this sender already has extraction regexes
        if (mSenderId >= 0) {
            SenderConfig cfg = mDb.getSenderById(mSenderId);
            if (cfg != null) {
                if (cfg.amountRegex   != null) mEtAmountRegex.setText(cfg.amountRegex);
                if (cfg.dateRegex     != null) mEtDateRegex.setText(cfg.dateRegex);
                if (cfg.merchantRegex != null) mEtMerchantRegex.setText(cfg.merchantRegex);
                if (cfg.cardRegex     != null) mEtCardRegex.setText(cfg.cardRegex);
            }
        }

        RegexWatcher watcher = new RegexWatcher(this);
        mEtAmountRegex.addTextChangedListener(watcher);
        mEtDateRegex.addTextChangedListener(watcher);
        mEtMerchantRegex.addTextChangedListener(watcher);
        mEtCardRegex.addTextChangedListener(watcher);

        btnSave.setOnClickListener(new SaveClickListener(this));

        updatePreview();
    }

    void updatePreview() {
        if (mSmsBody == null) return;
        mTvAmountPreview.setText(extract(mSmsBody, mEtAmountRegex.getText().toString()));
        mTvDatePreview.setText(extract(mSmsBody, mEtDateRegex.getText().toString()));
        mTvMerchantPreview.setText(extract(mSmsBody, mEtMerchantRegex.getText().toString()));
        mTvCardPreview.setText(extract(mSmsBody, mEtCardRegex.getText().toString()));
    }

    void save() {
        if (mSenderId < 0) { finish(); return; }
        SenderConfig cfg = mDb.getSenderById(mSenderId);
        if (cfg == null) { finish(); return; }

        cfg.amountRegex   = mEtAmountRegex.getText().toString().trim();
        cfg.dateRegex     = mEtDateRegex.getText().toString().trim();
        cfg.merchantRegex = mEtMerchantRegex.getText().toString().trim();
        cfg.cardRegex     = mEtCardRegex.getText().toString().trim();

        mDb.updateSender(cfg);
        Toast.makeText(this, R.string.msg_extraction_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private String extract(String text, String regex) {
        if (regex == null || regex.trim().isEmpty()) return "";
        try {
            Matcher m = Pattern.compile(regex.trim(), Pattern.CASE_INSENSITIVE).matcher(text);
            if (m.find()) return m.groupCount() > 0 ? m.group(1) : m.group(0);
            return getString(R.string.extract_no_match);
        } catch (Exception e) {
            return getString(R.string.extract_invalid_regex);
        }
    }

    // ============================================================
    // Static classes — D8 constraints
    // ============================================================

    static class RegexWatcher implements TextWatcher {
        private final SmsExtractConfigActivity mA;
        RegexWatcher(SmsExtractConfigActivity a) { mA = a; }
        public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
        public void onTextChanged(CharSequence s, int st, int b, int c) {}
        public void afterTextChanged(Editable s) { mA.updatePreview(); }
    }

    static class SaveClickListener implements View.OnClickListener {
        private final SmsExtractConfigActivity mA;
        SaveClickListener(SmsExtractConfigActivity a) { mA = a; }
        public void onClick(View v) { mA.save(); }
    }
}
