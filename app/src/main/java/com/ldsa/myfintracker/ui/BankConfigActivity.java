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
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.ExtractionPattern;
import com.ldsa.myfintracker.db.PdfSource;
import com.ldsa.myfintracker.db.PdfStatement;
import com.ldsa.myfintracker.db.SenderConfig;

import java.util.List;
import java.util.Locale;

public class BankConfigActivity extends Activity {

    public static final String EXTRA_SENDER_ID = "sender_id";

    private static final String PREF_FILE     = "fin_prefs";
    private static final String PREF_PDF_PASS = "pdf_pass_";
    private static final int    REQ_PICK_PDF  = 401;

    private long            mSenderId = -1L;
    private SenderConfig    mSender;
    private ExpenseDatabase mDb;

    private TextView     mTvTitle;
    private TextView     mBtnDeleteBank;
    private EditText     mEtBankName;
    private LinearLayout mContainerSenderIds;
    private LinearLayout mContainerSmsPatterns;
    private LinearLayout mContainerPdfPatterns;
    private TextView     mTvNoSenderIds;
    private TextView     mTvNoSmsPatterns;
    private LinearLayout mContainerPdfSources;
    private Button       mBtnAddMorePaths;
    private TextView     mTvNoPdfPatterns;
    private TextView     mTvPdfPassword;
    private CheckBox     mCbPdfAutoCredit;
    private TextView     mTvPdfAutoCreditHint;

    long mPendingDeletePatternId  = -1L;
    long mPendingDeleteSenderIdId = -1L;
    long mPendingDeleteSourceId   = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bank_config);
        getWindow().setStatusBarColor(0xFF1976D2);

        mSenderId = getIntent().getLongExtra(EXTRA_SENDER_ID, -1L);
        mDb = ExpenseDatabase.getInstance(this);

        mTvTitle             = (TextView)     findViewById(R.id.tvBankConfigTitle);
        mBtnDeleteBank       = (TextView)     findViewById(R.id.btnDeleteBank);
        mEtBankName          = (EditText)     findViewById(R.id.etBankName);
        mContainerSenderIds  = (LinearLayout) findViewById(R.id.containerSenderIds);
        mContainerSmsPatterns= (LinearLayout) findViewById(R.id.containerSmsPatterns);
        mContainerPdfSources = (LinearLayout) findViewById(R.id.containerPdfSources);
        mContainerPdfPatterns= (LinearLayout) findViewById(R.id.containerPdfPatterns);
        mTvNoSenderIds       = (TextView)     findViewById(R.id.tvNoSenderIds);
        mTvNoSmsPatterns     = (TextView)     findViewById(R.id.tvNoSmsPatterns);
        mTvNoPdfPatterns     = (TextView)     findViewById(R.id.tvNoPdfPatterns);
        mTvPdfPassword       = (TextView)     findViewById(R.id.tvPdfPassword);
        mCbPdfAutoCredit     = (CheckBox)     findViewById(R.id.cbPdfAutoCredit);
        mTvPdfAutoCreditHint = (TextView)     findViewById(R.id.tvPdfAutoCreditHint);
        mCbPdfAutoCredit.setOnCheckedChangeListener(new PdfAutoCreditChangeListener(this));

        ((TextView) findViewById(R.id.btnBack)).setOnClickListener(new BackClickListener(this));
        ((Button) findViewById(R.id.btnSaveIdentity)).setOnClickListener(new SaveIdentityClickListener(this));
        ((Button) findViewById(R.id.btnAddSenderId)).setOnClickListener(new AddSenderIdClickListener(this));
        ((Button) findViewById(R.id.btnAddSmsPattern)).setOnClickListener(new AddSmsPatternClickListener(this));
        mBtnAddMorePaths = (Button) findViewById(R.id.btnAddMorePaths);
        mBtnAddMorePaths.setOnClickListener(new AddMorePathsClickListener(this));
        ((TextView) findViewById(R.id.btnSetPassword)).setOnClickListener(new SetPasswordClickListener(this));
        ((Button) findViewById(R.id.btnAddPdfPattern)).setOnClickListener(new AddPdfPatternClickListener(this));
        ((TextView) findViewById(R.id.linkPdfInbox)).setOnClickListener(new PdfInboxLinkClickListener(this));
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
            reloadSenderIds();
            reloadSmsPatterns();
            saveAllSourceRows(); // flush any in-progress edits before reload wipes them
            reloadPdfSources();
            reloadPdfPatterns();
            updatePasswordDisplay();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveAllSourceRows();
    }

    private void populate() {
        String label = mSender.displayName != null && !mSender.displayName.isEmpty()
            ? mSender.displayName : mSender.pattern;
        mTvTitle.setText(label);
        mEtBankName.setText(mSender.displayName != null ? mSender.displayName : "");
        mBtnDeleteBank.setVisibility(View.VISIBLE);
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    void saveIdentity() {
        String name = mEtBankName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.error_bank_name_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mSenderId >= 0 && mSender != null) {
            mSender.displayName = name;
            mDb.updateSender(mSender);
        } else {
            // New bank: create a placeholder SenderConfig; user sets the pattern via SENDER IDS
            SenderConfig s = new SenderConfig();
            s.displayName = name;
            s.pattern     = name;
            s.isRegex     = false;
            mSenderId = mDb.insertSender(s);
            mSender   = mDb.getSenderById(mSenderId);
            mBtnDeleteBank.setVisibility(View.VISIBLE);
        }
        mTvTitle.setText(name);
        Toast.makeText(this, R.string.msg_bank_saved, Toast.LENGTH_SHORT).show();
        reloadSenderIds();
        reloadSmsPatterns();
        reloadPdfPatterns();
        updatePasswordDisplay();
    }

    // ── Sender IDs ────────────────────────────────────────────────────────────

    void reloadSenderIds() {
        if (mSenderId < 0 || mSender == null) return;
        String myName = mSender.displayName != null ? mSender.displayName : mSender.pattern;
        List<SenderConfig> all = mDb.getAllSenders();
        mContainerSenderIds.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        float dp = getResources().getDisplayMetrics().density;
        int count = 0;

        for (SenderConfig s : all) {
            String sName = s.displayName != null ? s.displayName : s.pattern;
            if (!sName.equals(myName)) continue;

            View row = inflater.inflate(R.layout.item_extraction_pattern, mContainerSenderIds, false);
            ((TextView) row.findViewById(R.id.tvPatternName)).setText(s.pattern);
            TextView badge = (TextView) row.findViewById(R.id.tvPatternTypeBadge);
            if (s.isRegex) {
                badge.setText("REGEX");
                badge.setBackgroundResource(R.drawable.bg_button_secondary);
                badge.setTextColor(0xFF1976D2);
                badge.setVisibility(View.VISIBLE);
            } else {
                badge.setVisibility(View.GONE);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int)(dp * 6);
            row.setLayoutParams(lp);
            row.setOnClickListener(new SenderIdClickListener(this, s.id));
            row.setOnLongClickListener(new SenderIdLongClickListener(this, s.id));
            mContainerSenderIds.addView(row);
            count++;
        }

        mTvNoSenderIds.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        mContainerSenderIds.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
    }

    void showSenderIdDialog(final long existingId, String currentPattern, boolean currentIsRegex) {
        if (mSenderId < 0) {
            Toast.makeText(this, R.string.msg_save_bank_first, Toast.LENGTH_SHORT).show();
            return;
        }
        View v = getLayoutInflater().inflate(R.layout.dialog_sender_id, null);
        EditText etPattern  = (EditText) v.findViewById(R.id.etSenderPattern);
        CheckBox cbIsRegex  = (CheckBox) v.findViewById(R.id.cbSenderIsRegex);
        etPattern.setText(currentPattern);
        cbIsRegex.setChecked(currentIsRegex);

        new AlertDialog.Builder(this, R.style.RoundedDialog)
            .setTitle(existingId < 0 ? "Add Sender ID" : "Edit Sender ID")
            .setView(v)
            .setPositiveButton(android.R.string.ok,
                new SaveSenderIdListener(this, existingId, etPattern, cbIsRegex))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void saveSenderId(long existingId, EditText etPattern, CheckBox cbIsRegex) {
        String pattern = etPattern.getText().toString().trim();
        if (pattern.isEmpty()) {
            Toast.makeText(this, R.string.error_pattern_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (existingId >= 0) {
            SenderConfig s = mDb.getSenderById(existingId);
            if (s != null) {
                s.pattern = pattern;
                s.isRegex = cbIsRegex.isChecked();
                mDb.updateSender(s);
                if (existingId == mSenderId) mSender = s;
            }
        } else {
            SenderConfig s = new SenderConfig();
            s.displayName = mSender.displayName;
            s.pattern     = pattern;
            s.isRegex     = cbIsRegex.isChecked();
            mDb.insertSender(s);
        }
        reloadSenderIds();
    }

    void confirmDeleteSenderId(long id) {
        mPendingDeleteSenderIdId = id;
        new AlertDialog.Builder(this, R.style.RoundedDialog)
            .setMessage(R.string.confirm_delete_sender_id)
            .setPositiveButton(android.R.string.ok, new DeleteSenderIdConfirmListener(this))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void deleteSenderId() {
        if (mPendingDeleteSenderIdId < 0) return;
        long id = mPendingDeleteSenderIdId;
        mPendingDeleteSenderIdId = -1L;
        mDb.deleteSender(id);
        if (id == mSenderId) { finish(); return; }
        reloadSenderIds();
    }

    // ── SMS Patterns ──────────────────────────────────────────────────────────

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

    // ── PDF ───────────────────────────────────────────────────────────────────

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

        // Auto-credit toggle: only meaningful once at least one PDF pattern
        // exists. Also show a warning line when the toggle is on but no pattern
        // captures /balance/ (classifier would silently fall back for every row).
        boolean showAuto = !empty && mSenderId > 0;
        mCbPdfAutoCredit.setVisibility(showAuto ? View.VISIBLE : View.GONE);
        if (showAuto) {
            // Rebind without re-triggering the listener while we set the saved state.
            mCbPdfAutoCredit.setOnCheckedChangeListener(null);
            mCbPdfAutoCredit.setChecked(mDb.getSenderPdfAutoCredit(mSenderId));
            mCbPdfAutoCredit.setOnCheckedChangeListener(new PdfAutoCreditChangeListener(this));
            boolean anyHasBalance = false;
            for (ExtractionPattern p : patterns) {
                if (p.balanceGroup >= 0) { anyHasBalance = true; break; }
            }
            mTvPdfAutoCreditHint.setVisibility(
                (mCbPdfAutoCredit.isChecked() && !anyHasBalance) ? View.VISIBLE : View.GONE);
        } else {
            mTvPdfAutoCreditHint.setVisibility(View.GONE);
        }
    }

    static class PdfAutoCreditChangeListener implements CompoundButton.OnCheckedChangeListener {
        final BankConfigActivity mA;
        PdfAutoCreditChangeListener(BankConfigActivity a) { mA = a; }
        public void onCheckedChanged(CompoundButton button, boolean checked) {
            if (mA.mSenderId > 0) mA.mDb.setSenderPdfAutoCredit(mA.mSenderId, checked);
            // Re-evaluate the warning line without a full reload.
            if (mA.mTvPdfAutoCreditHint == null) return;
            if (!checked) { mA.mTvPdfAutoCreditHint.setVisibility(View.GONE); return; }
            boolean anyHasBalance = false;
            for (ExtractionPattern p : mA.mDb.getPdfPatternsBySender(mA.mSenderId)) {
                if (p.balanceGroup >= 0) { anyHasBalance = true; break; }
            }
            mA.mTvPdfAutoCreditHint.setVisibility(anyHasBalance ? View.GONE : View.VISIBLE);
        }
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

    void addPdfPattern() {
        if (mSenderId < 0) {
            Toast.makeText(this, R.string.msg_save_bank_first, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, AddPdfPatternActivity.class);
        i.putExtra(AddPdfPatternActivity.EXTRA_SENDER_ID, mSenderId);
        startActivity(i);
    }

    void openPdfPattern(long patternId) {
        Intent i = new Intent(this, AddPdfPatternActivity.class);
        i.putExtra(AddPdfPatternActivity.EXTRA_SENDER_ID,  mSenderId);
        i.putExtra(AddPdfPatternActivity.EXTRA_PATTERN_ID, patternId);
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

    // ── PDF Password ──────────────────────────────────────────────────────────

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

    // ── PDF Sources ───────────────────────────────────────────────────────────

    void saveAllSourceRows() {
        if (mSenderId < 0) return;
        for (int i = 0; i < mContainerPdfSources.getChildCount(); i++) {
            View row = mContainerPdfSources.getChildAt(i);
            EditText et = (EditText) row.findViewById(R.id.etSourcePath);
            CheckBox cb = (CheckBox) row.findViewById(R.id.cbDropbox);
            if (et == null) continue;
            String path = et.getText().toString().trim();
            if (path.isEmpty()) continue;
            long id = (Long) row.getTag();
            if (id < 0) {
                long newId = mDb.insertPdfSource(mSenderId, path, cb != null && cb.isChecked());
                row.setTag(Long.valueOf(newId));
                mBtnAddMorePaths.setVisibility(View.VISIBLE);
            } else {
                mDb.updatePdfSource(id, path, cb != null && cb.isChecked());
            }
        }
    }

    void reloadPdfSources() {
        if (mSenderId < 0) return;
        mContainerPdfSources.removeAllViews();
        List<PdfSource> sources = mDb.getPdfSourcesBySender(mSenderId);
        for (PdfSource src : sources) {
            addSourceRow(src.id, src.path, src.isDropbox);
        }
        if (sources.isEmpty()) {
            addSourceRow(-1L, "", false);
            mBtnAddMorePaths.setVisibility(View.GONE);
        } else {
            mBtnAddMorePaths.setVisibility(View.VISIBLE);
        }
    }

    void addSourceRow(long id, String path, boolean isDropbox) {
        View row = getLayoutInflater().inflate(R.layout.item_pdf_source_row, mContainerPdfSources, false);
        row.setTag(Long.valueOf(id));
        EditText etPath    = (EditText) row.findViewById(R.id.etSourcePath);
        CheckBox cbDropbox = (CheckBox) row.findViewById(R.id.cbDropbox);
        etPath.setText(path);
        cbDropbox.setChecked(isDropbox);
        etPath.setOnFocusChangeListener(new SourceFocusListener(this, row, etPath, cbDropbox));
        etPath.setOnTouchListener(new SourceSwipeFillListener(etPath));
        cbDropbox.setOnCheckedChangeListener(new SourceCheckListener(this, row, etPath));
        row.setOnLongClickListener(new SourceRowLongClickListener(this, row));
        mContainerPdfSources.addView(row);
    }

    void confirmDeleteSource(long id) {
        mPendingDeleteSourceId = id;
        new AlertDialog.Builder(this, R.style.RoundedDialog)
            .setMessage(R.string.confirm_delete_source)
            .setPositiveButton(android.R.string.ok, new DeleteSourceConfirmListener(this))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void deleteSource() {
        if (mPendingDeleteSourceId < 0) return;
        mDb.deletePdfSource(mPendingDeleteSourceId);
        mPendingDeleteSourceId = -1L;
        reloadPdfSources();  // re-inflate all rows from DB
    }

    // ── Delete bank ───────────────────────────────────────────────────────────

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

    // ── Delete pattern ────────────────────────────────────────────────────────

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

    // ============================================================
    // Static listener classes — D8 constraints
    // ============================================================

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

    static class AddSenderIdClickListener implements View.OnClickListener {
        private final BankConfigActivity mA;
        AddSenderIdClickListener(BankConfigActivity a) { mA = a; }
        public void onClick(View v) { mA.showSenderIdDialog(-1L, "", false); }
    }

    static class SenderIdClickListener implements View.OnClickListener {
        private final BankConfigActivity mA;
        private final long mId;
        SenderIdClickListener(BankConfigActivity a, long id) { mA = a; mId = id; }
        public void onClick(View v) {
            SenderConfig s = mA.mDb.getSenderById(mId);
            if (s != null) mA.showSenderIdDialog(mId, s.pattern, s.isRegex);
        }
    }

    static class SenderIdLongClickListener implements View.OnLongClickListener {
        private final BankConfigActivity mA;
        private final long mId;
        SenderIdLongClickListener(BankConfigActivity a, long id) { mA = a; mId = id; }
        public boolean onLongClick(View v) { mA.confirmDeleteSenderId(mId); return true; }
    }

    static class DeleteSenderIdConfirmListener implements DialogInterface.OnClickListener {
        private final BankConfigActivity mA;
        DeleteSenderIdConfirmListener(BankConfigActivity a) { mA = a; }
        public void onClick(DialogInterface d, int w) { mA.deleteSenderId(); }
    }

    static class SaveSenderIdListener implements DialogInterface.OnClickListener {
        private final BankConfigActivity mA;
        private final long    mExistingId;
        private final EditText mEtPattern;
        private final CheckBox mCbIsRegex;
        SaveSenderIdListener(BankConfigActivity a, long id, EditText et, CheckBox cb) {
            mA = a; mExistingId = id; mEtPattern = et; mCbIsRegex = cb;
        }
        public void onClick(DialogInterface d, int w) {
            mA.saveSenderId(mExistingId, mEtPattern, mCbIsRegex);
        }
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

    static class PdfInboxLinkClickListener implements View.OnClickListener {
        private final BankConfigActivity mA;
        PdfInboxLinkClickListener(BankConfigActivity a) { mA = a; }
        public void onClick(View v) {
            android.content.Intent i = new android.content.Intent(mA, PdfInboxActivity.class);
            i.putExtra(PdfInboxActivity.EXTRA_SENDER_ID, mA.mSenderId);
            mA.startActivity(i);
        }
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

    static class AddMorePathsClickListener implements View.OnClickListener {
        private final BankConfigActivity mA;
        AddMorePathsClickListener(BankConfigActivity a) { mA = a; }
        public void onClick(View v) {
            if (mA.mSenderId < 0) {
                Toast.makeText(mA, R.string.msg_save_bank_first, Toast.LENGTH_SHORT).show();
                return;
            }
            mA.addSourceRow(-1L, "", false);
        }
    }

    static class SourceFocusListener implements View.OnFocusChangeListener {
        private final BankConfigActivity mA;
        private final View     mRow;
        private final EditText mEtPath;
        private final CheckBox mCbDropbox;
        SourceFocusListener(BankConfigActivity a, View row, EditText et, CheckBox cb) {
            mA = a; mRow = row; mEtPath = et; mCbDropbox = cb;
        }
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) return;
            String path = mEtPath.getText().toString().trim();
            if (path.isEmpty()) return;
            long id = (Long) mRow.getTag();
            if (id < 0) {
                long newId = mA.mDb.insertPdfSource(mA.mSenderId, path, mCbDropbox.isChecked());
                mRow.setTag(Long.valueOf(newId));
                mA.mBtnAddMorePaths.setVisibility(View.VISIBLE);
            } else {
                mA.mDb.updatePdfSource(id, path, mCbDropbox.isChecked());
            }
        }
    }

    static class SourceCheckListener implements android.widget.CompoundButton.OnCheckedChangeListener {
        private final BankConfigActivity mA;
        private final View     mRow;
        private final EditText mEtPath;
        SourceCheckListener(BankConfigActivity a, View row, EditText et) {
            mA = a; mRow = row; mEtPath = et;
        }
        public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
            if (checked && mEtPath.getText().toString().trim().isEmpty()) {
                String bankName = mA.mSender != null
                    ? (mA.mSender.displayName != null ? mA.mSender.displayName : mA.mSender.pattern)
                    : "bankname";
                mEtPath.setHint("/Apps/myfintracker/" + bankName + "/");
            } else if (!checked && mEtPath.getText().toString().trim().isEmpty()) {
                mEtPath.setHint(mA.getString(R.string.hint_pdf_source_path));
            }
            long id = (Long) mRow.getTag();
            if (id < 0) return;
            String path = mEtPath.getText().toString().trim();
            if (path.isEmpty()) return;
            mA.mDb.updatePdfSource(id, path, checked);
        }
    }

    static class SourceRowLongClickListener implements View.OnLongClickListener {
        private final BankConfigActivity mA;
        private final View mRow;
        SourceRowLongClickListener(BankConfigActivity a, View row) { mA = a; mRow = row; }
        public boolean onLongClick(View v) {
            long id = (Long) mRow.getTag();
            if (id < 0) {
                mA.mContainerPdfSources.removeView(mRow);
            } else {
                mA.confirmDeleteSource(id);
            }
            return true;
        }
    }

    static class DeleteSourceConfirmListener implements DialogInterface.OnClickListener {
        private final BankConfigActivity mA;
        DeleteSourceConfirmListener(BankConfigActivity a) { mA = a; }
        public void onClick(DialogInterface d, int w) { mA.deleteSource(); }
    }

    static class SourceSwipeFillListener implements View.OnTouchListener {
        private final EditText mEt;
        private float mDownX;
        private static final float SWIPE_MIN_DISTANCE = 80f;
        SourceSwipeFillListener(EditText et) { mEt = et; }
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mDownX = e.getX();
                    break;
                case MotionEvent.ACTION_UP:
                    float dx = e.getX() - mDownX;
                    if (dx > SWIPE_MIN_DISTANCE && mEt.getText().toString().isEmpty()) {
                        CharSequence hint = mEt.getHint();
                        if (hint != null) {
                            mEt.setText(hint);
                            mEt.setSelection(mEt.getText().length());
                        }
                        return true;
                    }
                    break;
            }
            return false;
        }
    }
}
