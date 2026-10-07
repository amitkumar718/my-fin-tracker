package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.ExtractionPattern;
import com.ldsa.myfintracker.db.SenderConfig;
import com.ldsa.myfintracker.pdf.PdfDecryptor;
import com.ldsa.myfintracker.pdf.PdfTextExtractor;

import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class StatementImportActivity extends Activity {

    private static final int REQ_PICK_FILE = 201;

    private Spinner  mSpinnerBank;
    private TextView mTvStatus;
    private TextView mTvPreview;
    private Button   mBtnScan;
    private ListView mLvCandidates;
    private Button   mBtnImport;

    private ExpenseDatabase          mDb;
    private StatementCandidateAdapter mAdapter;
    private List<SenderConfig>       mSenders = new ArrayList<SenderConfig>();
    private long    mSelectedSenderId = -1L;
    boolean mReady = false;
    String  mExtractedText = null;
    Uri     mLastUri;
    boolean mLastIsPdf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statement_import);
        getWindow().setStatusBarColor(0xFF1976D2);
        mDb = ExpenseDatabase.getInstance(this);

        mSpinnerBank  = (Spinner)  findViewById(R.id.spinnerBank);
        mTvStatus     = (TextView) findViewById(R.id.tvImportStatus);
        mTvPreview    = (TextView) findViewById(R.id.tvTextPreview);
        mBtnScan      = (Button)   findViewById(R.id.btnScan);
        mLvCandidates = (ListView) findViewById(R.id.lvCandidates);
        mBtnImport    = (Button)   findViewById(R.id.btnImport);

        mAdapter = new StatementCandidateAdapter(this);
        mLvCandidates.setAdapter(mAdapter);
        mLvCandidates.setOnItemClickListener(new CandidateClickListener(this));

        Button btnPick = (Button) findViewById(R.id.btnPickFile);
        btnPick.setOnClickListener(new PickFileClickListener(this));
        mBtnScan.setOnClickListener(new ScanClickListener(this));
        mBtnImport.setOnClickListener(new ImportClickListener(this));

        loadSenders();
    }

    private void loadSenders() {
        mSenders = mDb.getAllSenders();
        List<String> labels = new ArrayList<String>();
        labels.add(getString(R.string.label_all_banks));
        for (SenderConfig s : mSenders) labels.add(s.getLabel());

        ArrayAdapter<String> a = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerBank.setAdapter(a);
        mReady = true;
        mSpinnerBank.setOnItemSelectedListener(new BankSelectedListener(this));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_FILE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        mLastUri = uri;
        String mimeType = getContentResolver().getType(uri);
        mLastIsPdf = "application/pdf".equals(mimeType)
                || (uri.getLastPathSegment() != null
                    && uri.getLastPathSegment().toLowerCase(Locale.US).endsWith(".pdf"));
        startLoad(null);
    }

    void startLoad(String password) {
        mTvStatus.setText(R.string.status_reading_file);
        mBtnScan.setVisibility(View.GONE);
        mTvPreview.setVisibility(View.GONE);
        mLvCandidates.setVisibility(View.GONE);
        mBtnImport.setVisibility(View.GONE);
        new LoadFileThread(this, mLastUri, mLastIsPdf, password,
                new Handler(Looper.getMainLooper())).start();
    }

    void showPasswordDialog(boolean wrongPassword) {
        android.view.View v = getLayoutInflater().inflate(R.layout.dialog_password, null);
        EditText etPass = (EditText) v.findViewById(R.id.etDialogPassword);
        etPass.setHint(R.string.hint_pdf_password);
        new AlertDialog.Builder(this, R.style.RoundedDialog)
            .setTitle(R.string.pdf_password_title)
            .setMessage(wrongPassword ? R.string.pdf_password_wrong : R.string.pdf_password_msg)
            .setView(v)
            .setPositiveButton(android.R.string.ok, new PasswordOkListener(this, etPass))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void onFileLoaded(String text) {
        if (PdfDecryptor.NEEDS_PASSWORD.equals(text)) { showPasswordDialog(false); return; }
        if (text.startsWith(PdfDecryptor.WRONG_PASSWORD)) {
            String diag = text.substring(PdfDecryptor.WRONG_PASSWORD.length()).trim();
            if (!diag.isEmpty()) Toast.makeText(this, diag, Toast.LENGTH_LONG).show();
            showPasswordDialog(true);
            return;
        }
        if (text.startsWith(PdfTextExtractor.EXTRACT_EMPTY)) {
            String diag = text.substring(PdfTextExtractor.EXTRACT_EMPTY.length()).trim();
            mTvStatus.setText(getString(R.string.status_extract_failed)
                    + (diag.isEmpty() ? "" : "\n" + diag));
            return;
        }
        mExtractedText = text;
        if (text == null || text.isEmpty()) {
            mTvStatus.setText(R.string.status_extract_failed);
            return;
        }

        // Garbled text heuristic: if < 40% printable ASCII, likely encoding issue
        int printable = 0;
        for (int i = 0; i < Math.min(text.length(), 500); i++) {
            char c = text.charAt(i);
            if (c >= 32 && c < 127) printable++;
        }
        int sampleLen = Math.min(text.length(), 500);
        if (sampleLen > 0 && (printable * 100 / sampleLen) < 40) {
            mTvStatus.setText(R.string.status_extract_garbled);
            mTvPreview.setText(text.substring(0, Math.min(text.length(), 800)));
            mTvPreview.setVisibility(View.VISIBLE);
            return;
        }

        String preview = text.length() > 1500 ? text.substring(0, 1500) + "…" : text;
        mTvPreview.setText(preview);
        mTvPreview.setVisibility(View.VISIBLE);
        mBtnScan.setVisibility(View.VISIBLE);
        mTvStatus.setText(R.string.status_file_ready);
    }

    void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/pdf", "text/plain", "text/csv"});
        startActivityForResult(intent, REQ_PICK_FILE);
    }

    void onBankSelected(int pos) {
        // pos 0 = "All Banks" (id=-1), pos 1+ = mSenders[pos-1]
        mSelectedSenderId = (pos == 0) ? -1L : mSenders.get(pos - 1).id;
    }

    void scan() {
        if (mExtractedText == null || mExtractedText.isEmpty()) {
            Toast.makeText(this, R.string.status_no_text, Toast.LENGTH_SHORT).show();
            return;
        }
        List<ExtractionPattern> patterns;
        if (mSelectedSenderId < 0) {
            patterns = mDb.getAllPatterns();
        } else {
            patterns = mDb.getPatternsBySender(mSelectedSenderId);
        }
        if (patterns.isEmpty()) {
            Toast.makeText(this, R.string.status_no_patterns, Toast.LENGTH_SHORT).show();
            return;
        }
        mBtnScan.setEnabled(false);
        mTvStatus.setText(R.string.status_scanning);
        mLvCandidates.setVisibility(View.GONE);
        mBtnImport.setVisibility(View.GONE);

        new ScanThread(this, mExtractedText, patterns,
                new Handler(Looper.getMainLooper())).start();
    }

    void onScanDone(List<StatementCandidateAdapter.CandidateExpense> candidates) {
        mBtnScan.setEnabled(true);
        mAdapter.setItems(candidates);
        if (candidates.isEmpty()) {
            mTvStatus.setText(R.string.status_no_matches);
        } else {
            mTvStatus.setText(getString(R.string.status_found_n, candidates.size()));
            mTvPreview.setVisibility(View.GONE);
            mLvCandidates.setVisibility(View.VISIBLE);
            mBtnImport.setVisibility(View.VISIBLE);
        }
    }

    void importSelected() {
        List<StatementCandidateAdapter.CandidateExpense> selected = mAdapter.getSelected();
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.status_none_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        mBtnImport.setEnabled(false);
        mTvStatus.setText(R.string.status_importing);
        new ImportThread(this, selected, mDb, new Handler(Looper.getMainLooper())).start();
    }

    void onImportDone(int count) {
        mBtnImport.setEnabled(true);
        Toast.makeText(this,
                getString(R.string.status_imported_n, count),
                Toast.LENGTH_LONG).show();
        finish();
    }

    // ── Static helper methods ─────────────────────────────────────────────────

    static long parseDate(String raw) {
        if (raw == null || raw.isEmpty()) return System.currentTimeMillis();
        String[] formats = {
            "dd/MM/yyyy", "dd-MM-yyyy", "dd/MM/yy", "dd-MM-yy",
            "yyyy-MM-dd", "dd MMM yyyy", "dd MMM yy",
            "dd-MMM-yyyy", "dd-MMM-yy", "MM/dd/yyyy"
        };
        for (String fmt : formats) {
            try {
                java.util.Date d = new SimpleDateFormat(fmt, Locale.US).parse(raw);
                if (d != null) return d.getTime();
            } catch (ParseException ignored) {}
        }
        return System.currentTimeMillis();
    }

    // ============================================================
    // Worker threads
    // ============================================================

    static class LoadFileThread extends Thread {
        private final StatementImportActivity mA;
        private final Uri     mUri;
        private final boolean mIsPdf;
        private final String  mPassword;
        private final Handler mHandler;

        LoadFileThread(StatementImportActivity a, Uri uri, boolean isPdf, String password,
                       Handler h) {
            mA = a; mUri = uri; mIsPdf = isPdf; mPassword = password; mHandler = h;
        }

        public void run() {
            String text = "";
            try {
                InputStream is = mA.getContentResolver().openInputStream(mUri);
                if (is == null) throw new Exception("Cannot open file");
                if (mIsPdf) {
                    text = PdfTextExtractor.extract(is, mPassword);
                } else {
                    // Plain text — read directly
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) >= 0) baos.write(buf, 0, n);
                    is.close();
                    text = baos.toString("UTF-8");
                }
            } catch (Exception e) {
                text = "";
            }
            mHandler.post(new LoadFileDoneRunnable(mA, text));
        }
    }

    // D8: we use a named static class for the actual callback
    static class LoadFileDoneRunnable implements Runnable {
        private final StatementImportActivity mA;
        private final String mText;
        LoadFileDoneRunnable(StatementImportActivity a, String t) { mA = a; mText = t; }
        public void run() { if (!mA.isFinishing()) mA.onFileLoaded(mText); }
    }

    static class ScanThread extends Thread {
        private final StatementImportActivity  mA;
        private final String                   mText;
        private final List<ExtractionPattern>  mPatterns;
        private final Handler                  mHandler;

        ScanThread(StatementImportActivity a, String text,
                   List<ExtractionPattern> patterns, Handler h) {
            mA = a; mText = text; mPatterns = patterns; mHandler = h;
        }

        public void run() {
            List<StatementCandidateAdapter.CandidateExpense> candidates =
                    new ArrayList<StatementCandidateAdapter.CandidateExpense>();
            Set<String> seen = new HashSet<String>();
            String[] lines = mText.split("\n");

            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.length() < 4) continue;

                for (ExtractionPattern p : mPatterns) {
                    if (!p.matches(line)) continue;
                    StatementCandidateAdapter.CandidateExpense cand = buildCandidate(line, p);
                    if (cand == null) continue;
                    String key = line; // source line is the best dedup key
                    if (!seen.contains(key)) {
                        seen.add(key);
                        candidates.add(cand);
                    }
                    break; // first matching pattern wins for this line
                }
            }

            mHandler.post(new ScanDoneRunnable(mA, candidates));
        }

        private static StatementCandidateAdapter.CandidateExpense buildCandidate(
                String line, ExtractionPattern p) {
            String amtStr = p.extractGroup(line, p.amountGroup)
                    .replaceAll("[^0-9.]", "");
            if (amtStr.isEmpty()) return null;
            double amount;
            try { amount = Double.parseDouble(amtStr); }
            catch (NumberFormatException e) { return null; }
            if (amount <= 0) return null;

            StatementCandidateAdapter.CandidateExpense c =
                    new StatementCandidateAdapter.CandidateExpense();
            c.amount          = amount;
            c.merchant        = p.extractGroup(line, p.merchantGroup).trim();
            c.card            = p.extractGroup(line, p.cardGroup).trim();
            c.account         = p.extractGroup(line, p.accountGroup).trim();
            c.sourceText      = line;
            c.patternId       = p.id;
            c.isOnline        = p.isOnlineType();
            c.transactionType = (p.transactionType != null) ? p.transactionType : "";
            c.dateMs          = parseDate(p.extractGroup(line, p.dateGroup).trim());
            return c;
        }
    }

    static class ScanDoneRunnable implements Runnable {
        private final StatementImportActivity mA;
        private final List<StatementCandidateAdapter.CandidateExpense> mList;
        ScanDoneRunnable(StatementImportActivity a,
                         List<StatementCandidateAdapter.CandidateExpense> list) {
            mA = a; mList = list;
        }
        public void run() { if (!mA.isFinishing()) mA.onScanDone(mList); }
    }

    static class ImportThread extends Thread {
        private final StatementImportActivity mA;
        private final List<StatementCandidateAdapter.CandidateExpense> mList;
        private final ExpenseDatabase mDb;
        private final Handler mHandler;

        ImportThread(StatementImportActivity a,
                     List<StatementCandidateAdapter.CandidateExpense> list,
                     ExpenseDatabase db, Handler h) {
            mA = a; mList = list; mDb = db; mHandler = h;
        }

        public void run() {
            int count = 0;
            for (StatementCandidateAdapter.CandidateExpense c : mList) {
                Expense e = new Expense();
                e.amount          = c.amount;
                e.dateMs          = c.dateMs;
                e.merchant        = c.merchant;
                e.card            = c.card;
                e.accountNumber   = c.account;
                e.isOnline        = c.isOnline;
                e.transactionType = c.transactionType;
                e.originalSms     = c.sourceText;   // store source line for reference
                e.patternId       = c.patternId;
                e.createdAt       = System.currentTimeMillis();
                mDb.insertExpense(e);
                count++;
            }
            mHandler.post(new ImportDoneRunnable(mA, count));
        }
    }

    static class ImportDoneRunnable implements Runnable {
        private final StatementImportActivity mA;
        private final int mCount;
        ImportDoneRunnable(StatementImportActivity a, int count) { mA = a; mCount = count; }
        public void run() { if (!mA.isFinishing()) mA.onImportDone(mCount); }
    }

    // ============================================================
    // Static listener classes
    // ============================================================

    static class PickFileClickListener implements View.OnClickListener {
        private final StatementImportActivity mA;
        PickFileClickListener(StatementImportActivity a) { mA = a; }
        public void onClick(View v) { mA.pickFile(); }
    }

    static class ScanClickListener implements View.OnClickListener {
        private final StatementImportActivity mA;
        ScanClickListener(StatementImportActivity a) { mA = a; }
        public void onClick(View v) { mA.scan(); }
    }

    static class ImportClickListener implements View.OnClickListener {
        private final StatementImportActivity mA;
        ImportClickListener(StatementImportActivity a) { mA = a; }
        public void onClick(View v) { mA.importSelected(); }
    }

    static class CandidateClickListener implements AdapterView.OnItemClickListener {
        private final StatementImportActivity mA;
        CandidateClickListener(StatementImportActivity a) { mA = a; }
        public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
            mA.mAdapter.toggleSelected(pos);
        }
    }

    static class PasswordOkListener implements DialogInterface.OnClickListener {
        private final StatementImportActivity mA;
        private final EditText                mEt;
        PasswordOkListener(StatementImportActivity a, EditText et) { mA = a; mEt = et; }
        public void onClick(DialogInterface dialog, int which) {
            mA.startLoad(mEt.getText().toString());
        }
    }

    static class BankSelectedListener implements AdapterView.OnItemSelectedListener {
        private final StatementImportActivity mA;
        BankSelectedListener(StatementImportActivity a) { mA = a; }
        public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            if (mA.mReady) mA.onBankSelected(pos);
        }
        public void onNothingSelected(AdapterView<?> p) {}
    }
}
