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
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.ExtractionPattern;
import com.ldsa.myfintracker.db.PdfStatement;
import com.ldsa.myfintracker.pdf.PdfDecryptor;
import com.ldsa.myfintracker.pdf.PdfTextExtractor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class PdfLinesActivity extends Activity {

    public static final String EXTRA_STATEMENT_ID = "statement_id";

    private TextView         mTvHeader;
    private TextView         mTvStatus;
    private TextView         mTvEmpty;
    private ListView         mListView;
    private PdfLineAdapter   mAdapter;
    private ExpenseDatabase  mDb;

    private PdfStatement     mStatement;
    private List<String>     mLines = new ArrayList<String>();
    private Uri              mUri;
    private boolean          mIsPdf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_lines);

        mDb = ExpenseDatabase.getInstance(this);

        mTvHeader = (TextView) findViewById(R.id.tvPdfLinesHeader);
        mTvStatus = (TextView) findViewById(R.id.tvPdfStatus);
        mTvEmpty  = (TextView) findViewById(R.id.tvPdfEmpty);
        mListView = (ListView) findViewById(R.id.listPdfLines);

        mAdapter = new PdfLineAdapter(this);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new LineClickListener(this));

        long stmtId = getIntent().getLongExtra(EXTRA_STATEMENT_ID, -1L);
        if (stmtId < 0) { finish(); return; }

        mStatement = mDb.getPdfStatementById(stmtId);
        if (mStatement == null) { finish(); return; }

        String bank  = (mStatement.bankName != null && !mStatement.bankName.isEmpty())
                ? mStatement.bankName : getString(R.string.title_pdf_lines);
        String month = mStatement.statementPeriod != null ? mStatement.statementPeriod : "";
        mTvHeader.setText(bank + (month.isEmpty() ? "" : "  ·  " + month));

        mUri   = Uri.parse(mStatement.uri);
        mIsPdf = mStatement.isPdf;

        startLoad(null);
    }

    void startLoad(String password) {
        mTvStatus.setText(R.string.status_reading_file);
        mTvEmpty.setVisibility(View.GONE);
        mListView.setVisibility(View.GONE);
        new LoadThread(this, mUri, mIsPdf, password,
                mStatement.senderId, new Handler(Looper.getMainLooper())).start();
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

    void openLine(int pos) {
        String line = mAdapter.getLineText(pos);
        if (line.isEmpty()) return;
        Intent intent = new Intent(this, MapExpenseActivity.class);
        intent.putExtra(MapExpenseActivity.EXTRA_TRANS_LINE,       line);
        intent.putExtra(MapExpenseActivity.EXTRA_SENDER_ID,      mStatement.senderId);
        intent.putExtra(MapExpenseActivity.EXTRA_BLANK_TEMPLATE, true);
        intent.putExtra(MapExpenseActivity.EXTRA_IS_PDF,         true);
        startActivity(intent);
    }

    // ── Worker threads ────────────────────────────────────────────────────────

    static class LoadThread extends Thread {
        private final PdfLinesActivity mA;
        private final Uri     mUri;
        private final boolean mIsPdf;
        private final String  mPassword;
        private final long    mSenderId;
        private final Handler mHandler;

        LoadThread(PdfLinesActivity a, Uri uri, boolean isPdf, String password,
                   long senderId, Handler h) {
            mA = a; mUri = uri; mIsPdf = isPdf; mPassword = password;
            mSenderId = senderId; mHandler = h;
        }

        public void run() {
            String raw = "";
            String uriKey = mUri.toString();
            String pw = mPassword;
            if (pw == null && mIsPdf) pw = PdfInboxActivity.sCachedPasswords.get(uriKey);
            try {
                InputStream is = mA.getContentResolver().openInputStream(mUri);
                if (is != null) {
                    if (mIsPdf) {
                        raw = PdfTextExtractor.extract(is, pw);
                    } else {
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[8192]; int n;
                        while ((n = is.read(buf)) >= 0) baos.write(buf, 0, n);
                        is.close();
                        raw = baos.toString("UTF-8");
                    }
                }
            } catch (Exception ignored) {}

            if (mIsPdf && pw != null && !PdfDecryptor.NEEDS_PASSWORD.equals(raw)
                    && !raw.startsWith(PdfDecryptor.WRONG_PASSWORD)
                    && !raw.startsWith(PdfTextExtractor.EXTRACT_EMPTY)) {
                PdfInboxActivity.sCachedPasswords.put(uriKey, pw);
            }

            if (PdfDecryptor.NEEDS_PASSWORD.equals(raw)
                    || raw.startsWith(PdfDecryptor.WRONG_PASSWORD)
                    || raw.startsWith(PdfTextExtractor.EXTRACT_EMPTY)) {
                mHandler.post(new LoadDoneRunnable(mA,
                        new ArrayList<String>(), new ArrayList<PdfLineAdapter.PdfLine>(), raw));
                return;
            }

            List<String> lines = PdfInboxActivity.splitLines(raw);
            List<ExtractionPattern> patterns = PdfInboxActivity.loadPatterns(mA.mDb, mSenderId);
            List<PdfLineAdapter.PdfLine> items = PdfInboxActivity.matchLines(lines, patterns);

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
        private final PdfLinesActivity             mA;
        private final List<String>                 mLines;
        private final List<PdfLineAdapter.PdfLine> mItems;
        private final String                       mStatus;
        LoadDoneRunnable(PdfLinesActivity a, List<String> lines,
                         List<PdfLineAdapter.PdfLine> items, String status) {
            mA = a; mLines = lines; mItems = items; mStatus = status;
        }
        public void run() { if (!mA.isFinishing()) mA.onLinesLoaded(mLines, mItems, mStatus); }
    }

    // ── Listener classes ──────────────────────────────────────────────────────

    static class LineClickListener implements AdapterView.OnItemClickListener {
        private final PdfLinesActivity mA;
        LineClickListener(PdfLinesActivity a) { mA = a; }
        public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            mA.openLine(pos);
        }
    }

    static class PasswordOkListener implements DialogInterface.OnClickListener {
        private final PdfLinesActivity mA;
        private final EditText         mEt;
        PasswordOkListener(PdfLinesActivity a, EditText et) { mA = a; mEt = et; }
        public void onClick(DialogInterface dialog, int which) {
            mA.startLoad(mEt.getText().toString());
        }
    }
}
