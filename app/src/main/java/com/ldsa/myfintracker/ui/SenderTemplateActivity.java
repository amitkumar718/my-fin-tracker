package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.SenderConfig;

public class SenderTemplateActivity extends Activity {

    public static final String EXTRA_SENDER_ID = "sender_id";

    private EditText mEtDisplayName;
    private EditText mEtPattern;
    private CheckBox mCbIsRegex;

    private ExpenseDatabase mDb;
    private long mSenderId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sender_template);

        mDb       = ExpenseDatabase.getInstance(this);
        mSenderId = getIntent().getLongExtra(EXTRA_SENDER_ID, -1L);

        mEtDisplayName = (EditText)  findViewById(R.id.etDisplayName);
        mEtPattern     = (EditText)  findViewById(R.id.etPattern);
        mCbIsRegex     = (CheckBox)  findViewById(R.id.cbIsRegex);
        Button btnSave = (Button)    findViewById(R.id.btnSaveSender);

        if (mSenderId >= 0) {
            SenderConfig existing = mDb.getSenderById(mSenderId);
            if (existing != null) populate(existing);
        }

        btnSave.setOnClickListener(new SaveClickListener(this));
    }

    private void populate(SenderConfig s) {
        mEtDisplayName.setText(s.displayName != null ? s.displayName : "");
        mEtPattern.setText(s.pattern != null ? s.pattern : "");
        mCbIsRegex.setChecked(s.isRegex);
    }

    void save() {
        String pattern = mEtPattern.getText().toString().trim();
        if (pattern.isEmpty()) {
            Toast.makeText(this, R.string.error_pattern_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (mSenderId >= 0) {
            // Preserve existing extraction regexes when editing identification only
            SenderConfig existing = mDb.getSenderById(mSenderId);
            if (existing != null) {
                existing.displayName = mEtDisplayName.getText().toString().trim();
                existing.pattern     = pattern;
                existing.isRegex     = mCbIsRegex.isChecked();
                mDb.updateSender(existing);
                Toast.makeText(this, R.string.msg_sender_saved, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        SenderConfig s = new SenderConfig();
        s.displayName = mEtDisplayName.getText().toString().trim();
        s.pattern     = pattern;
        s.isRegex     = mCbIsRegex.isChecked();
        mDb.insertSender(s);
        Toast.makeText(this, R.string.msg_sender_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    static class SaveClickListener implements View.OnClickListener {
        private final SenderTemplateActivity mA;
        SaveClickListener(SenderTemplateActivity a) { mA = a; }
        public void onClick(View v) { mA.save(); }
    }
}
