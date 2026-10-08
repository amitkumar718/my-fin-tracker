package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.pdf.DropboxPdfHelper;

import java.util.List;

public class DropboxSettingsActivity extends Activity {

    private static final String PREF_FILE = DropboxPdfHelper.PREF_FILE;
    private static final String KEY_TOKEN = DropboxPdfHelper.KEY_TOKEN;
    private static final String KEY_ROOT  = DropboxPdfHelper.KEY_ROOT;

    private EditText mEtToken;
    private EditText mEtRootFolder;
    private TextView mTvStatus;
    private View     mVStatusDot;
    private Button   mBtnTest;
    private Button   mBtnBrowse;
    private Button   mBtnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dropbox_settings);
        getWindow().setStatusBarColor(0xFF1976D2);

        mEtToken      = (EditText) findViewById(R.id.etToken);
        mEtRootFolder = (EditText) findViewById(R.id.etRootFolder);
        mTvStatus     = (TextView) findViewById(R.id.tvStatus);
        mVStatusDot   = findViewById(R.id.vStatusDot);
        mBtnTest      = (Button)   findViewById(R.id.btnTest);
        mBtnBrowse    = (Button)   findViewById(R.id.btnBrowse);
        mBtnSave      = (Button)   findViewById(R.id.btnSave);

        ((TextView) findViewById(R.id.btnBack)).setOnClickListener(new BackListener(this));
        mBtnTest.setOnClickListener(new TestListener(this));
        mBtnBrowse.setOnClickListener(new BrowseListener(this));
        mBtnSave.setOnClickListener(new SaveListener(this));
        ((Button) findViewById(R.id.btnClearToken)).setOnClickListener(new ClearListener(this));

        loadSaved();
    }

    void loadSaved() {
        android.content.SharedPreferences p = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        String token = p.getString(KEY_TOKEN, "");
        String root  = p.getString(KEY_ROOT,  "");
        mEtToken.setText(token != null ? token : "");
        mEtRootFolder.setText(root != null ? root : "");
        updateStatus(token != null && !token.isEmpty());
    }

    void updateStatus(boolean hasToken) {
        if (!hasToken) {
            mTvStatus.setText(R.string.dbx_status_no_token);
            mTvStatus.setTextColor(0xFF9E9E9E);
            mVStatusDot.setBackgroundColor(0xFFBDBDBD);
            mBtnTest.setEnabled(false);
            mBtnBrowse.setEnabled(false);
        } else {
            mTvStatus.setText(R.string.dbx_status_token_saved);
            mTvStatus.setTextColor(0xFF757575);
            mVStatusDot.setBackgroundColor(0xFF1976D2);
            mBtnTest.setEnabled(true);
            mBtnBrowse.setEnabled(true);
        }
    }

    void testConnection() {
        String token = mEtToken.getText().toString().trim();
        if (token.isEmpty()) {
            Toast.makeText(this, R.string.dropbox_token_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        String root = mEtRootFolder.getText().toString().trim();

        mTvStatus.setText(R.string.dbx_status_testing);
        mTvStatus.setTextColor(0xFF757575);
        mVStatusDot.setBackgroundColor(0xFF1976D2);
        mBtnTest.setEnabled(false);
        mBtnSave.setEnabled(false);

        DropboxPdfHelper.listPdfs(this, token, root, new TestCallback(this));
    }

    void onTestSuccess(List<DropboxPdfHelper.PdfEntry> entries) {
        mTvStatus.setText(getString(R.string.dbx_status_ok, entries.size()));
        mTvStatus.setTextColor(0xFF388E3C);
        mVStatusDot.setBackgroundColor(0xFF388E3C);
        mBtnTest.setEnabled(true);
        mBtnSave.setEnabled(true);
    }

    void onTestAuthFail() {
        mTvStatus.setText(R.string.dbx_status_auth_fail);
        mTvStatus.setTextColor(0xFFD32F2F);
        mVStatusDot.setBackgroundColor(0xFFD32F2F);
        mBtnTest.setEnabled(true);
        mBtnSave.setEnabled(true);
    }

    void onTestError(String msg) {
        mTvStatus.setText(getString(R.string.dbx_status_error, msg));
        mTvStatus.setTextColor(0xFFE65100);
        mVStatusDot.setBackgroundColor(0xFFE65100);
        mBtnTest.setEnabled(true);
        mBtnSave.setEnabled(true);
    }

    void browseDropbox() {
        save(); // persist current token/root before opening inbox
        android.content.Intent i = new android.content.Intent(this, DropboxPdfInboxActivity.class);
        startActivity(i);
    }

    void clearToken() {
        mEtToken.setText("");
        updateStatus(false);
    }

    void save() {
        String token = mEtToken.getText().toString().trim();
        String root  = mEtRootFolder.getText().toString().trim();
        android.content.SharedPreferences.Editor ed =
            getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit();
        if (token.isEmpty()) {
            ed.remove(KEY_TOKEN);
        } else {
            ed.putString(KEY_TOKEN, token);
        }
        ed.putString(KEY_ROOT, root);
        ed.apply();
        updateStatus(!token.isEmpty());
        Toast.makeText(this, R.string.msg_token_saved, Toast.LENGTH_SHORT).show();
    }

    // ── Static listeners ──────────────────────────────────────────────────────

    static class BackListener implements View.OnClickListener {
        private final DropboxSettingsActivity mA;
        BackListener(DropboxSettingsActivity a) { mA = a; }
        public void onClick(View v) { mA.finish(); }
    }

    static class TestListener implements View.OnClickListener {
        private final DropboxSettingsActivity mA;
        TestListener(DropboxSettingsActivity a) { mA = a; }
        public void onClick(View v) { mA.testConnection(); }
    }

    static class SaveListener implements View.OnClickListener {
        private final DropboxSettingsActivity mA;
        SaveListener(DropboxSettingsActivity a) { mA = a; }
        public void onClick(View v) { mA.save(); }
    }

    static class ClearListener implements View.OnClickListener {
        private final DropboxSettingsActivity mA;
        ClearListener(DropboxSettingsActivity a) { mA = a; }
        public void onClick(View v) { mA.clearToken(); }
    }

    static class BrowseListener implements View.OnClickListener {
        private final DropboxSettingsActivity mA;
        BrowseListener(DropboxSettingsActivity a) { mA = a; }
        public void onClick(View v) { mA.browseDropbox(); }
    }

    // ── Test callback (static, D8-safe) ───────────────────────────────────────

    static class TestCallback implements DropboxPdfHelper.ListCallback {
        private final DropboxSettingsActivity mA;
        TestCallback(DropboxSettingsActivity a) { mA = a; }
        public void onSuccess(List<DropboxPdfHelper.PdfEntry> entries) {
            mA.onTestSuccess(entries);
        }
        public void onError(String msg) { mA.onTestError(msg); }
        public void onAuthFailed()      { mA.onTestAuthFail(); }
    }
}
