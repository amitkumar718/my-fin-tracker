package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.SenderConfig;
import com.ldsa.myfintracker.sms.SmsMessage;
import com.ldsa.myfintracker.sms.SmsReader;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SenderTemplateActivity extends Activity {

    public static final String EXTRA_SENDER_ID = "sender_id";

    private EditText mEtDisplayName;
    private EditText mEtPattern;
    private CheckBox mCbIsRegex;
    private EditText mEtAmountRegex;
    private EditText mEtDateRegex;
    private EditText mEtMerchantRegex;
    private EditText mEtCardRegex;
    private TextView mTvTestResult;

    private ExpenseDatabase mDb;
    private long mSenderId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sender_template);

        mDb       = ExpenseDatabase.getInstance(this);
        mSenderId = getIntent().getLongExtra(EXTRA_SENDER_ID, -1L);

        mEtDisplayName   = (EditText)  findViewById(R.id.etDisplayName);
        mEtPattern       = (EditText)  findViewById(R.id.etPattern);
        mCbIsRegex       = (CheckBox)  findViewById(R.id.cbIsRegex);
        mEtAmountRegex   = (EditText)  findViewById(R.id.etAmountRegex);
        mEtDateRegex     = (EditText)  findViewById(R.id.etDateRegex);
        mEtMerchantRegex = (EditText)  findViewById(R.id.etMerchantRegex);
        mEtCardRegex     = (EditText)  findViewById(R.id.etCardRegex);
        mTvTestResult    = (TextView)  findViewById(R.id.tvTestResult);
        Button btnSave   = (Button)    findViewById(R.id.btnSaveSender);
        Button btnTest   = (Button)    findViewById(R.id.btnTestRegex);

        if (mSenderId >= 0) {
            SenderConfig existing = mDb.getSenderById(mSenderId);
            if (existing != null) populate(existing);
        }

        btnSave.setOnClickListener(new SaveClickListener(this));
        btnTest.setOnClickListener(new TestClickListener(this));
    }

    private void populate(SenderConfig s) {
        mEtDisplayName.setText(s.displayName != null ? s.displayName : "");
        mEtPattern.setText(s.pattern != null ? s.pattern : "");
        mCbIsRegex.setChecked(s.isRegex);
        mEtAmountRegex.setText(s.amountRegex != null ? s.amountRegex : "");
        mEtDateRegex.setText(s.dateRegex != null ? s.dateRegex : "");
        mEtMerchantRegex.setText(s.merchantRegex != null ? s.merchantRegex : "");
        mEtCardRegex.setText(s.cardRegex != null ? s.cardRegex : "");
    }

    void save() {
        String pattern = mEtPattern.getText().toString().trim();
        if (pattern.isEmpty()) {
            Toast.makeText(this, R.string.error_pattern_required, Toast.LENGTH_SHORT).show();
            return;
        }

        SenderConfig s = new SenderConfig();
        s.displayName   = mEtDisplayName.getText().toString().trim();
        s.pattern       = pattern;
        s.isRegex       = mCbIsRegex.isChecked();
        s.amountRegex   = mEtAmountRegex.getText().toString().trim();
        s.dateRegex     = mEtDateRegex.getText().toString().trim();
        s.merchantRegex = mEtMerchantRegex.getText().toString().trim();
        s.cardRegex     = mEtCardRegex.getText().toString().trim();

        if (mSenderId >= 0) {
            s.id = mSenderId;
            mDb.updateSender(s);
        } else {
            mDb.insertSender(s);
        }
        Toast.makeText(this, R.string.msg_sender_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    void testRegex() {
        String pattern = mEtPattern.getText().toString().trim();
        if (pattern.isEmpty()) {
            mTvTestResult.setText("Enter a sender pattern first.");
            return;
        }

        SenderConfig cfg = new SenderConfig();
        cfg.pattern       = pattern;
        cfg.isRegex       = mCbIsRegex.isChecked();
        cfg.amountRegex   = mEtAmountRegex.getText().toString().trim();
        cfg.dateRegex     = mEtDateRegex.getText().toString().trim();
        cfg.merchantRegex = mEtMerchantRegex.getText().toString().trim();
        cfg.cardRegex     = mEtCardRegex.getText().toString().trim();

        java.util.List<SenderConfig> cfgList = new java.util.ArrayList<SenderConfig>();
        cfgList.add(cfg);
        List<SmsMessage> msgs = SmsReader.readMatchingSms(this, cfgList);

        if (msgs.isEmpty()) {
            mTvTestResult.setText("No SMS found matching this sender pattern.");
            return;
        }

        SmsMessage sample = msgs.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("Sample SMS:\n").append(sample.body).append("\n\n");
        sb.append("Extracted:\n");
        sb.append("Amount:   ").append(extract(sample.body, cfg.amountRegex)).append("\n");
        sb.append("Date:     ").append(extract(sample.body, cfg.dateRegex)).append("\n");
        sb.append("Merchant: ").append(extract(sample.body, cfg.merchantRegex)).append("\n");
        sb.append("Card:     ").append(extract(sample.body, cfg.cardRegex));
        mTvTestResult.setText(sb.toString());
    }

    private String extract(String text, String regex) {
        if (regex == null || regex.isEmpty()) return "(no regex)";
        try {
            Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
            if (m.find()) return m.groupCount() > 0 ? m.group(1) : m.group(0);
        } catch (Exception e) {
            return "invalid regex";
        }
        return "(no match)";
    }

    // ============================================================
    // Static listener classes — D8 constraints
    // ============================================================

    static class SaveClickListener implements View.OnClickListener {
        private final SenderTemplateActivity mA;
        SaveClickListener(SenderTemplateActivity a) { mA = a; }
        public void onClick(View v) { mA.save(); }
    }

    static class TestClickListener implements View.OnClickListener {
        private final SenderTemplateActivity mA;
        TestClickListener(SenderTemplateActivity a) { mA = a; }
        public void onClick(View v) { mA.testRegex(); }
    }
}
