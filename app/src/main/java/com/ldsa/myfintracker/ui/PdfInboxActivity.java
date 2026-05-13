package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.ExtractionPattern;
import com.ldsa.myfintracker.db.SenderConfig;
import com.ldsa.myfintracker.pdf.PdfDecryptor;
import com.ldsa.myfintracker.pdf.PdfTextExtractor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PdfInboxActivity extends Activity {

    private static final int REQ_PICK = 301;

    private Spinner  mSpinnerBank;
    private TextView mTvStatus;
    private TextView mTvEmpty;
    private ListView mListView;

    private PdfLineAdapter     mAdapter;
    private ExpenseDatabase    mDb;
    private List<SenderConfig> mSenders = new ArrayList<SenderConfig>();
    private List<String>       mLines   = new ArrayList<String>();  // raw lines from loaded file
    long    mSelectedSenderId = -1L;
    boolean mReady  = false;
    Uri     mLastUri;      // remembered for password re-try
    boolean mLastIsPdf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_inbox);

        mDb = ExpenseDatabase.getInstance(this);

        mSpinnerBank = (Spinner)  findViewById(R.id.spinnerPdfBank);
        mTvStatus    = (TextView) findViewById(R.id.tvPdfStatus);
        mTvEmpty     = (TextView) findViewById(R.id.tvPdfEmpty);
        mListView    = (ListView) findViewById(R.id.listPdfLines);

        mAdapter = new PdfLineAdapter(this);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new LineClickListener(this));

        Button btnPick = (Button) findViewById(R.id.btnPickPdf);
        btnPick.setOnClickListener(new PickClickListener(this));

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
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null) return;
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
        mTvEmpty.setVisibility(View.GONE);
        mListView.setVisibility(View.GONE);
        new LoadThread(this, mLastUri, mLastIsPdf, password, mSelectedSenderId,
                new Handler(Looper.getMainLooper())).start();
    }

    void onLinesLoaded(List<String> lines, List<PdfLineAdapter.PdfLine> items, String statusMsg) {
        if (PdfDecryptor.NEEDS_PASSWORD.equals(statusMsg)) {
            showPasswordDialog(false);
            return;
        }
        if (statusMsg.startsWith(PdfDecryptor.WRONG_PASSWORD)) {
            String diag = statusMsg.substring(PdfDecryptor.WRONG_PASSWORD.length()).trim();
            if (!diag.isEmpty()) Toast.makeText(this, diag, Toast.LENGTH_LONG).show();
            showPasswordDialog(true);
            return;
        }
        if (statusMsg.startsWith(PdfTextExtractor.EXTRACT_EMPTY)) {
            String diag = statusMsg.substring(PdfTextExtractor.EXTRACT_EMPTY.length()).trim();
            mTvStatus.setText(getString(R.string.status_extract_failed)
                    + (diag.isEmpty() ? "" : "\n" + diag));
            mTvEmpty.setVisibility(View.VISIBLE);
            mListView.setVisibility(View.GONE);
            return;
        }
        mLines = lines;
        mAdapter.setItems(items);
        mTvStatus.setText(statusMsg);
        if (items.isEmpty()) {
            mTvEmpty.setVisibility(View.VISIBLE);
            mListView.setVisibility(View.GONE);
        } else {
            mTvEmpty.setVisibility(View.GONE);
            mListView.setVisibility(View.VISIBLE);
        }
    }

    void showPasswordDialog(boolean wrongPassword) {
        EditText etPass = new EditText(this);
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPass.setHint(R.string.hint_pdf_password);
        new AlertDialog.Builder(this)
            .setTitle(R.string.pdf_password_title)
            .setMessage(wrongPassword ? R.string.pdf_password_wrong : R.string.pdf_password_msg)
            .setView(etPass)
            .setPositiveButton(android.R.string.ok, new PasswordOkListener(this, etPass))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void onBankSelected(int pos) {
        mSelectedSenderId = (pos == 0) ? -1L : mSenders.get(pos - 1).id;
        if (!mLines.isEmpty()) {
            // Re-run match highlighting with new sender's patterns
            new MatchThread(this, new ArrayList<String>(mLines), mSelectedSenderId,
                    mDb, new Handler(Looper.getMainLooper())).start();
        }
    }

    void onMatchDone(List<PdfLineAdapter.PdfLine> items) {
        mAdapter.setItems(items);
    }

    void openLine(int pos) {
        String line = mAdapter.getLineText(pos);
        if (line.isEmpty()) return;
        Intent intent = new Intent(this, SmsMapActivity.class);
        intent.putExtra(SmsMapActivity.EXTRA_SMS_BODY, line);
        intent.putExtra(SmsMapActivity.EXTRA_SENDER_ID, mSelectedSenderId);
        intent.putExtra(SmsMapActivity.EXTRA_BLANK_TEMPLATE, true);
        startActivity(intent);
    }

    void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/pdf", "text/plain", "text/csv"});
        startActivityForResult(intent, REQ_PICK);
    }

    // ============================================================
    // Worker threads
    // ============================================================

    /** Loads a PDF or TXT file, extracts text, splits into lines, runs pattern matching. */
    static class LoadThread extends Thread {
        private final PdfInboxActivity mA;
        private final Uri     mUri;
        private final boolean mIsPdf;
        private final String  mPassword; // null = no password
        private final long    mSenderId;
        private final Handler mHandler;

        LoadThread(PdfInboxActivity a, Uri uri, boolean isPdf, String password,
                   long senderId, Handler h) {
            mA = a; mUri = uri; mIsPdf = isPdf; mPassword = password;
            mSenderId = senderId; mHandler = h;
        }

        public void run() {
            String raw = "";
            try {
                InputStream is = mA.getContentResolver().openInputStream(mUri);
                if (is != null) {
                    if (mIsPdf) {
                        raw = PdfTextExtractor.extract(is, mPassword);
                    } else {
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[8192]; int n;
                        while ((n = is.read(buf)) >= 0) baos.write(buf, 0, n);
                        is.close();
                        raw = baos.toString("UTF-8");
                    }
                }
            } catch (Exception ignored) {}

            // Propagate sentinel strings directly to the UI handler
            if (PdfDecryptor.NEEDS_PASSWORD.equals(raw)
                    || raw.startsWith(PdfDecryptor.WRONG_PASSWORD)
                    || raw.startsWith(PdfTextExtractor.EXTRACT_EMPTY)) {
                mHandler.post(new LoadDoneRunnable(mA, new ArrayList<String>(),
                        new ArrayList<PdfLineAdapter.PdfLine>(), raw));
                return;
            }

            List<String> lines = splitLines(raw);
            List<ExtractionPattern> patterns = loadPatterns(mA.mDb, mSenderId);
            List<PdfLineAdapter.PdfLine> items = matchLines(lines, patterns);

            String statusMsg;
            if (raw.isEmpty()) {
                statusMsg = mA.getString(R.string.status_extract_failed);
            } else {
                int matched = 0;
                for (PdfLineAdapter.PdfLine pl : items) {
                    if (pl.matchSummary != null) matched++;
                }
                statusMsg = mA.getString(R.string.pdf_inbox_status, items.size(), matched);
            }

            mHandler.post(new LoadDoneRunnable(mA, lines, items, statusMsg));
        }
    }

    static class LoadDoneRunnable implements Runnable {
        private final PdfInboxActivity             mA;
        private final List<String>                 mLines;
        private final List<PdfLineAdapter.PdfLine> mItems;
        private final String                       mStatus;
        LoadDoneRunnable(PdfInboxActivity a, List<String> lines,
                         List<PdfLineAdapter.PdfLine> items, String status) {
            mA = a; mLines = lines; mItems = items; mStatus = status;
        }
        public void run() { if (!mA.isFinishing()) mA.onLinesLoaded(mLines, mItems, mStatus); }
    }

    /** Re-runs pattern matching when the bank selector changes. */
    static class MatchThread extends Thread {
        private final PdfInboxActivity mA;
        private final List<String>     mLines;
        private final long             mSenderId;
        private final ExpenseDatabase  mDb;
        private final Handler          mHandler;

        MatchThread(PdfInboxActivity a, List<String> lines, long senderId,
                    ExpenseDatabase db, Handler h) {
            mA = a; mLines = lines; mSenderId = senderId; mDb = db; mHandler = h;
        }

        public void run() {
            List<ExtractionPattern> patterns = loadPatterns(mDb, mSenderId);
            List<PdfLineAdapter.PdfLine> items = matchLines(mLines, patterns);
            mHandler.post(new MatchDoneRunnable(mA, items));
        }
    }

    static class MatchDoneRunnable implements Runnable {
        private final PdfInboxActivity             mA;
        private final List<PdfLineAdapter.PdfLine> mItems;
        MatchDoneRunnable(PdfInboxActivity a, List<PdfLineAdapter.PdfLine> items) {
            mA = a; mItems = items;
        }
        public void run() { if (!mA.isFinishing()) mA.onMatchDone(mItems); }
    }

    // ── Shared static helpers ─────────────────────────────────────────────────

    static List<String> splitLines(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null || text.isEmpty()) return out;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.length() >= 5) out.add(line);
        }
        return out;
    }

    static List<ExtractionPattern> loadPatterns(ExpenseDatabase db, long senderId) {
        if (senderId < 0) return db.getAllPatterns();
        return db.getPatternsBySender(senderId);
    }

    static List<PdfLineAdapter.PdfLine> matchLines(
            List<String> lines, List<ExtractionPattern> patterns) {
        List<PdfLineAdapter.PdfLine> out = new ArrayList<PdfLineAdapter.PdfLine>();
        for (String line : lines) {
            PdfLineAdapter.PdfLine pl = new PdfLineAdapter.PdfLine();
            pl.text = line;
            pl.matchSummary = tryMatch(line, patterns);
            out.add(pl);
        }
        return out;
    }

    /** Returns a short summary string if any pattern matches the line, null otherwise. */
    private static String tryMatch(String line, List<ExtractionPattern> patterns) {
        for (ExtractionPattern p : patterns) {
            if (!p.matches(line)) continue;
            String amtStr = p.extractGroup(line, p.amountGroup).replaceAll("[^0-9.]", "");
            if (amtStr.isEmpty()) continue;
            double amount;
            try { amount = Double.parseDouble(amtStr); }
            catch (NumberFormatException e) { continue; }
            String merchant = p.extractGroup(line, p.merchantGroup).trim();
            String summary = String.format(Locale.getDefault(), "₹%.2f", amount);
            if (!merchant.isEmpty()) summary += "  " + merchant;
            return summary;
        }
        return null;
    }

    // ============================================================
    // Static listener classes
    // ============================================================

    static class PickClickListener implements View.OnClickListener {
        private final PdfInboxActivity mA;
        PickClickListener(PdfInboxActivity a) { mA = a; }
        public void onClick(View v) { mA.pickFile(); }
    }

    static class LineClickListener implements AdapterView.OnItemClickListener {
        private final PdfInboxActivity mA;
        LineClickListener(PdfInboxActivity a) { mA = a; }
        public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            mA.openLine(pos);
        }
    }

    static class BankSelectedListener implements AdapterView.OnItemSelectedListener {
        private final PdfInboxActivity mA;
        BankSelectedListener(PdfInboxActivity a) { mA = a; }
        public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            if (mA.mReady) mA.onBankSelected(pos);
        }
        public void onNothingSelected(AdapterView<?> p) {}
    }

    static class PasswordOkListener implements DialogInterface.OnClickListener {
        private final PdfInboxActivity mA;
        private final EditText         mEt;
        PasswordOkListener(PdfInboxActivity a, EditText et) { mA = a; mEt = et; }
        public void onClick(DialogInterface dialog, int which) {
            mA.startLoad(mEt.getText().toString());
        }
    }
}
