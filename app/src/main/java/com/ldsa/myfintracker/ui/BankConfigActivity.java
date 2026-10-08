package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.ExtractionPattern;
import com.ldsa.myfintracker.db.PdfStatement;
import com.ldsa.myfintracker.db.SenderConfig;

import java.util.List;
import java.util.Locale;

public class BankConfigActivity extends Activity {

    public static final String EXTRA_SENDER_ID = "sender_id";

    private static final String PREF_FILE      = "fin_prefs";
    private static final String PREF_PDF_PASS  = "pdf_pass_";
    private static final int    REQ_PICK_PDF   = 401;

    private long            mSenderId = -1L;
    private SenderConfig    mSender;
    private ExpenseDatabase mDb;

    private TextView     mTvTitle;
    private TextView     mBtnDeleteBank;
    private EditText     mEtBankName;
    private EditText     mEtSenderPattern;
    private CheckBox     mCbIsRegex;
    private LinearLayout mContainerSmsPatterns;
    private LinearLayout mContainerPdfPatterns;
    private TextView     mTvNoSmsPatterns;
    private TextView     mTvNoPdfPatterns;
    private TextView     mTvPdfPassword;

    long mPendingDeletePatternId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bank_config);
        getWindow().setStatusBarColor(0xFF1976D2);

        mSenderId = getIntent().getLongExtra(EXTRA_SENDER_ID, -1L);
        mDb = ExpenseDatabase.getInstance(this);

        mTvTitle              = (TextView)     findViewById(R.id.tvBankConfigTitle);
        mBtnDeleteBank        = (TextView)     findViewById(R.id.btnDeleteBank);
        mEtBankName           = (EditText)     findViewById(R.id.etBankName);
        mEtSenderPattern      = (EditText)     findViewById(R.id.etSenderPattern);
        mCbIsRegex            = (CheckBox)     findViewById(R.id.cbIsRegex);
        mContainerSmsPatterns = (LinearLayout) findViewById(R.id.containerSmsPatterns);
        mContainerPdfPatterns = (LinearLayout) findViewById(R.id.containerPdfPatterns);
        mTvNoSmsPatterns      = (TextView)     findViewById(R.id.tvNoSmsPatterns);
        mTvNoPdfPatterns      = (TextView)     findViewById(R.id.tvNoPdfPatterns);
        mTvPdfPassword        = (TextView)     findViewById(R.id.tvPdfPassword);

        ((TextView) findViewById(R.id.btnBack)).setOnClickListener(new BackClickListener(this));
        ((Button) findViewById(R.id.btnSaveIdentity)).setOnClickListener(new SaveIdentityClickListener(this));
        ((Button) findViewById(R.id.btnAddSmsPattern)).setOnClickListener(new AddSmsPatternClickListener(this));
        ((Button) findViewById(R.id.btnAddPdf)).setOnClickListener(new AddPdfClickListener(this));
        ((Button) findViewById(R.id.btnDropbox)).setOnClickListener(new DropboxClickListener(this));
        ((Button) findViewById(R.id.btnSetPassword)).setOnClickListener(new SetPasswordClickListener(this));
        ((Button) findViewById(R.id.btnAddPdfPattern)).setOnClickListener(new AddPdfPatternClickListener(this));
        mBtnDeleteBank.setOnClickListener(new DeleteBankClickListener(this));

        if (mSenderId >= 0) {
            mSender = mDb.getSenderById(mSenderId);
            if (mSender != null) populate();
        } else {
            mTvTitle.setText("New Bank");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mSenderId >= 0) {
            reloadSmsPatterns();
            reloadPdfPatterns();
            updatePasswordDisplay();
        }
    }

    private void populate() {
        mTvTitle.setText(mSender.displayName != null && !mSender.displayName.isEmpty()
            ? mSender.displayName : mSender.pattern);
        mEtBankName.setText(mSender.displayName != null ? mSender.displayName : "");
        mEtSenderPattern.setText(mSender.pattern != null ? mSender.pattern : "");
        mCbIsRegex.setChecked(mSender.isRegex);
        mBtnDeleteBank.setVisibility(View.VISIBLE);
    }

    void saveIdentity() {
        String name    = mEtBankName.getText().toString().trim();
        String pattern = mEtSenderPattern.getText().toString().trim();
        if (pattern.isEmpty()) {
            Toast.makeText(this, R.string.error_pattern_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mSenderId >= 0 && mSender != null) {
            mSender.displayName = name;
            mSender.pattern     = pattern;
            mSender.isRegex     = mCbIsRegex.isChecked();
            mDb.updateSender(mSender);
        } else {
            SenderConfig s = new SenderConfig();
            s.displayName = name;
            s.pattern     = pattern;
            s.isRegex     = mCbIsRegex.isChecked();
            mSenderId = mDb.insertSender(s);
            mSender   = mDb.getSenderById(mSenderId);
            mBtnDeleteBank.setVisibility(View.VISIBLE);
        }
        String label = name.isEmpty() ? pattern : name;
        mTvTitle.setText(label);
        Toast.makeText(this, R.string.msg_bank_saved, Toast.LENGTH_SHORT).show();
        reloadSmsPatterns();
        reloadPdfPatterns();
        updatePasswordDisplay();
    }

    void reloadSmsPatterns() {
        if (mSenderId < 0) return;
        List<ExtractionPattern> patterns = mDb.getPatternsBySender(mSenderId);
        mContainerSmsPatterns.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        float dp = getResources().getDisplayMetrics().density;

        for (ExtractionPattern p : patterns) {
            View row = inflater.inflate(R.layout.item_extraction_pattern, mContainerSmsPatterns, false);
            String label = (p.name != null && !p.name.isEmpty()) ? p.name : "Pattern #" + p.id;
            ((TextView) row.findViewById(R.id.tvPatternName)).setText(label);
            TextView badge = (TextView) row.findViewById(R.id.tvPatternTypeBadge);
            if (p.transactionType != null && !p.transactionType.isEmpty()) {
                badge.setText(p.getTypeLabel());
                badge.setVisibility(View.VISIBLE);
            } else {
                badge.setVisibility(View.GONE);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int)(dp * 6);
            row.setLayoutParams(lp);
            row.setOnClickListener(new SmsPatternClickListener(this, p.id));
            row.setOnLongClickListener(new PatternLongClickListener(this, p.id));
            mContainerSmsPatterns.addView(row);
        }

        boolean empty = patterns.isEmpty();
        mTvNoSmsPatterns.setVisibility(empty ? View.VISIBLE : View.GONE);
        mContainerSmsPatterns.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    void reloadPdfPatterns() {
        if (mSenderId < 0) return;
        List<ExtractionPattern> patterns = mDb.getPdfPatternsBySender(mSenderId);
        mContainerPdfPatterns.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        float dp = getResources().getDisplayMetrics().density;

        for (ExtractionPattern p : patterns) {
            View row = inflater.inflate(R.layout.item_extraction_pattern, mContainerPdfPatterns, false);
            String label = (p.name != null && !p.name.isEmpty()) ? p.name : "PDF Pattern #" + p.id;
            ((TextView) row.findViewById(R.id.tvPatternName)).setText(label);
            TextView badge = (TextView) row.findViewById(R.id.tvPatternTypeBadge);
            if (p.transactionType != null && !p.transactionType.isEmpty()) {
                badge.setText(p.getTypeLabel());
                badge.setVisibility(View.VISIBLE);
            } else {
                badge.setVisibility(View.GONE);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int)(dp * 6);
            row.setLayoutParams(lp);
            row.setOnClickListener(new PdfPatternClickListener(this, p.id));
            row.setOnLongClickListener(new PatternLongClickListener(this, p.id));
            mContainerPdfPatterns.addView(row);
        }

        boolean empty = patterns.isEmpty();
        mTvNoPdfPatterns.setVisibility(empty ? View.VISIBLE : View.GONE);
        mContainerPdfPatterns.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    void addSmsPattern() {
        if (mSenderId < 0) {
            Toast.makeText(this, R.string.msg_save_bank_first, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, SmsExtractConfigActivity.class);
        i.putExtra(SmsExtractConfigActivity.EXTRA_SENDER_ID, mSenderId);
        startActivity(i);
    }

    void openSmsPattern(long patternId) {
        Intent i = new Intent(this, SmsExtractConfigActivity.class);
        i.putExtra(SmsExtractConfigActivity.EXTRA_SENDER_ID, mSenderId);
        i.putExtra(SmsExtractConfigActivity.EXTRA_PATTERN_ID, patternId);
        startActivity(i);
    }

    void pickPdf() {
        if (mSenderId < 0) {
            Toast.makeText(this, R.string.msg_save_bank_first, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,
            new String[]{"application/pdf", "text/plain"});
        startActivityForResult(i, REQ_PICK_PDF);
    }

    void openDropbox() {
        startActivity(new Intent(this, DropboxPdfInboxActivity.class));
    }

    void addPdfPattern() {
        if (mSenderId < 0) {
            Toast.makeText(this, R.string.msg_save_bank_first, Toast.LENGTH_SHORT).show();
            return;
        }
        // PDF patterns are created by scanning a PDF — reuse the file picker flow
        pickPdf();
    }

    void openPdfPattern(long patternId) {
        Intent i = new Intent(this, SmsExtractConfigActivity.class);
        i.putExtra(SmsExtractConfigActivity.EXTRA_SENDER_ID, mSenderId);
        i.putExtra(SmsExtractConfigActivity.EXTRA_PATTERN_ID, patternId);
        startActivity(i);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK_PDF || res != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        try {
            getContentResolver().takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {}

        String displayName = null;
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                int col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (cursor.moveToFirst() && col >= 0) displayName = cursor.getString(col);
            } finally { cursor.close(); }
        }
        if (displayName == null || displayName.isEmpty()) {
            String seg = uri.getLastPathSegment();
            displayName = (seg != null && seg.contains("/"))
                ? seg.substring(seg.lastIndexOf('/') + 1) : seg;
        }
        if (displayName == null) displayName = "statement";

        String mimeType = getContentResolver().getType(uri);
        boolean isPdf = "application/pdf".equals(mimeType)
            || displayName.toLowerCase(Locale.US).endsWith(".pdf");

        PdfStatement s = new PdfStatement();
        s.senderId    = mSenderId;
        s.isPdf       = isPdf;
        s.uri         = uri.toString();
        s.displayName = displayName;
        s.createdAt   = System.currentTimeMillis();
        if (mSender != null) s.bankName = mSender.displayName != null
            ? mSender.displayName : mSender.pattern;
        long newId = mDb.insertPdfStatement(s);

        Intent intent = new Intent(this, StatementScanActivity.class);
        intent.putExtra(StatementScanActivity.EXTRA_STATEMENT_ID, newId);
        startActivity(intent);
    }

    void showPasswordDialog() {
        if (mSenderId < 0) {
            Toast.makeText(this, R.string.msg_save_bank_first, Toast.LENGTH_SHORT).show();
            return;
        }
        View v = getLayoutInflater().inflate(R.layout.dialog_password, null);
        EditText et = (EditText) v.findViewById(R.id.etDialogPassword);
        et.setHint(R.string.hint_pdf_password);
        String existing = getSharedPreferences(PREF_FILE, MODE_PRIVATE)
            .getString(PREF_PDF_PASS + mSenderId, null);
        if (existing != null) et.setText(existing);
        new AlertDialog.Builder(this, R.style.RoundedDialog)
            .setTitle(R.string.pdf_password_title)
            .setMessage(R.string.pdf_password_dialog_msg)
            .setView(v)
            .setPositiveButton(R.string.save, new SavePasswordListener(this, et))
            .setNegativeButton("Clear", new ClearPasswordListener(this))
            .setNeutralButton(android.R.string.cancel, null)
            .show();
    }

    void savePassword(String password) {
        SharedPreferences.Editor ed = getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit();
        if (password.isEmpty()) {
            ed.remove(PREF_PDF_PASS + mSenderId);
        } else {
            ed.putString(PREF_PDF_PASS + mSenderId, password);
        }
        ed.apply();
        updatePasswordDisplay();
    }

    void clearPassword() {
        getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit()
            .remove(PREF_PDF_PASS + mSenderId).apply();
        updatePasswordDisplay();
    }

    void updatePasswordDisplay() {
        if (mSenderId < 0 || mTvPdfPassword == null) return;
        String pw = getSharedPreferences(PREF_FILE, MODE_PRIVATE)
            .getString(PREF_PDF_PASS + mSenderId, null);
        if (pw != null && !pw.isEmpty()) {
            mTvPdfPassword.setText("••••••");
            mTvPdfPassword.setTextColor(0xFF212121);
        } else {
            mTvPdfPassword.setText(R.string.pdf_password_not_set);
            mTvPdfPassword.setTextColor(0xFF9E9E9E);
        }
    }

    void confirmDeleteBank() {
        new AlertDialog.Builder(this, R.style.RoundedDialog)
            .setMessage(R.string.confirm_delete_bank)
            .setPositiveButton(android.R.string.ok, new DeleteBankConfirmListener(this))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void deleteBank() {
        if (mSenderId < 0) { finish(); return; }
        List<ExtractionPattern> sms = mDb.getPatternsBySender(mSenderId);
        for (ExtractionPattern p : sms) mDb.deletePattern(p.id);
        List<ExtractionPattern> pdf = mDb.getPdfPatternsBySender(mSenderId);
        for (ExtractionPattern p : pdf) mDb.deletePattern(p.id);
        mDb.deleteSender(mSenderId);
        getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit()
            .remove(PREF_PDF_PASS + mSenderId).apply();
        Toast.makeText(this, R.string.msg_bank_deleted, Toast.LENGTH_SHORT).show();
        finish();
    }

    void confirmDeletePattern(long id) {
        mPendingDeletePatternId = id;
        new AlertDialog.Builder(this, R.style.RoundedDialog)
            .setMessage(R.string.confirm_delete_pattern)
            .setPositiveButton(android.R.string.ok, new DeletePatternConfirmListener(this))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void deletePattern() {
        if (mPendingDeletePatternId < 0) return;
        mDb.deletePattern(mPendingDeletePatternId);
        mPendingDeletePatternId = -1L;
        Toast.makeText(this, R.string.msg_pattern_deleted, Toast.LENGTH_SHORT).show();
        reloadSmsPatterns();
        reloadPdfPatterns();
    }

    // ── Static listener classes ───────────────────────────────────────────────

    static class BackClickListener implements View.OnClickListener {
        private final BankConfigActivity mA;
        BackClickListener(BankConfigActivity a) { mA = a; }
        public void onClick(View v) { mA.finish(); }
    }

    static class SaveIdentityClickListener implements View.OnClickListener {
        private final BankConfigActivity mA;
        SaveIdentityClickListener(BankConfigActivity a) { mA = a; }
        public void onClick(View v) { mA.saveIdentity(); }
    }

    static class DeleteBankClickListener implements View.OnClickListener {
        private final BankConfigActivity mA;
        DeleteBankClickListener(BankConfigActivity a) { mA = a; }
        public void onClick(View v) { mA.confirmDeleteBank(); }
    }

    static class DeleteBankConfirmListener implements DialogInterface.OnClickListener {
        private final BankConfigActivity mA;
        DeleteBankConfirmListener(BankConfigActivity a) { mA = a; }
        public void onClick(DialogInterface d, int w) { mA.deleteBank(); }
    }

    static class AddSmsPatternClickListener implements View.OnClickListener {
        private final BankConfigActivity mA;
        AddSmsPatternClickListener(BankConfigActivity a) { mA = a; }
        public void onClick(View v) { mA.addSmsPattern(); }
    }

    static class SmsPatternClickListener implements View.OnClickListener {
        private final BankConfigActivity mA;
        private final long mId;
        SmsPatternClickListener(BankConfigActivity a, long id) { mA = a; mId = id; }
        public void onClick(View v) { mA.openSmsPattern(mId); }
    }

    static class PdfPatternClickListener implements View.OnClickListener {
        private final BankConfigActivity mA;
        private final long mId;
        PdfPatternClickListener(BankConfigActivity a, long id) { mA = a; mId = id; }
        public void onClick(View v) { mA.openPdfPattern(mId); }
    }

    static class PatternLongClickListener implements View.OnLongClickListener {
        private final BankConfigActivity mA;
        private final long mId;
        PatternLongClickListener(BankConfigActivity a, long id) { mA = a; mId = id; }
        public boolean onLongClick(View v) { mA.confirmDeletePattern(mId); return true; }
    }

    static class DeletePatternConfirmListener implements DialogInterface.OnClickListener {
        private final BankConfigActivity mA;
        DeletePatternConfirmListener(BankConfigActivity a) { mA = a; }
        public void onClick(DialogInterface d, int w) { mA.deletePattern(); }
    }

    static class AddPdfClickListener implements View.OnClickListener {
        private final BankConfigActivity mA;
        AddPdfClickListener(BankConfigActivity a) { mA = a; }
        public void onClick(View v) { mA.pickPdf(); }
    }

    static class DropboxClickListener implements View.OnClickListener {
        private final BankConfigActivity mA;
        DropboxClickListener(BankConfigActivity a) { mA = a; }
        public void onClick(View v) { mA.openDropbox(); }
    }

    static class SetPasswordClickListener implements View.OnClickListener {
        private final BankConfigActivity mA;
        SetPasswordClickListener(BankConfigActivity a) { mA = a; }
        public void onClick(View v) { mA.showPasswordDialog(); }
    }

    static class AddPdfPatternClickListener implements View.OnClickListener {
        private final BankConfigActivity mA;
        AddPdfPatternClickListener(BankConfigActivity a) { mA = a; }
        public void onClick(View v) { mA.addPdfPattern(); }
    }

    static class SavePasswordListener implements DialogInterface.OnClickListener {
        private final BankConfigActivity mA;
        private final EditText mEt;
        SavePasswordListener(BankConfigActivity a, EditText et) { mA = a; mEt = et; }
        public void onClick(DialogInterface d, int w) {
            mA.savePassword(mEt.getText().toString());
        }
    }

    static class ClearPasswordListener implements DialogInterface.OnClickListener {
        private final BankConfigActivity mA;
        ClearPasswordListener(BankConfigActivity a) { mA = a; }
        public void onClick(DialogInterface d, int w) { mA.clearPassword(); }
    }
}
