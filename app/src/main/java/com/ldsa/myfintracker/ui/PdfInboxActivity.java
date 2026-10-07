package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.ExtractionPattern;
import com.ldsa.myfintracker.db.PdfStatement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class PdfInboxActivity extends Activity {

    /** Session-scoped password cache: URI string → password. Cleared when process dies. */
    static final HashMap<String, String> sCachedPasswords = new HashMap<String, String>();

    private static final int REQ_PICK = 301;

    private ListView             mListView;
    private TextView             mTvEmpty;
    private PdfStatementAdapter  mAdapter;
    private ExpenseDatabase      mDb;

    private Uri     mPendingUri;
    private String  mPendingDisplayName;
    private boolean mPendingIsPdf;

    private long mPendingDeleteId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_inbox);
        getWindow().setStatusBarColor(0xFF1976D2);
        mDb = ExpenseDatabase.getInstance(this);

        mTvEmpty  = (TextView) findViewById(R.id.tvStatementsEmpty);
        mListView = (ListView) findViewById(R.id.listStatements);

        mAdapter = new PdfStatementAdapter(this);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new ItemClickListener(this));
        mListView.setOnItemLongClickListener(new ItemLongClickListener(this));

        ((Button) findViewById(R.id.btnAddStatement))
            .setOnClickListener(new AddClickListener(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        List<PdfStatement> stmts = mDb.getAllPdfStatements();
        List<Integer> counts = new ArrayList<Integer>();
        for (PdfStatement s : stmts) counts.add(mDb.countExpensesByStatement(s.id));
        mAdapter.setData(stmts, counts);
        if (stmts.isEmpty()) {
            mTvEmpty.setVisibility(View.VISIBLE);
            mListView.setVisibility(View.GONE);
        } else {
            mTvEmpty.setVisibility(View.GONE);
            mListView.setVisibility(View.VISIBLE);
        }
    }

    void openStatement(int pos) {
        PdfStatement s = mAdapter.getStatement(pos);
        Intent intent = new Intent(this, StatementScanActivity.class);
        intent.putExtra(StatementScanActivity.EXTRA_STATEMENT_ID, s.id);
        startActivity(intent);
    }

    void openTemplateEditor(int pos) {
        PdfStatement s = mAdapter.getStatement(pos);
        Intent intent = new Intent(this, MapExpenseActivity.class);
        intent.putExtra(MapExpenseActivity.EXTRA_STATEMENT_ID, s.id);
        intent.putExtra(MapExpenseActivity.EXTRA_BLANK_TEMPLATE, true);
        intent.putExtra(MapExpenseActivity.EXTRA_IS_PDF,         true);
        startActivity(intent);
    }

    void confirmDelete(int pos) {
        mPendingDeleteId = mAdapter.getStatement(pos).id;
        new AlertDialog.Builder(this, R.style.RoundedDialog)
            .setMessage(R.string.confirm_delete_statement)
            .setPositiveButton(android.R.string.ok, new DeleteConfirmListener(this))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void deleteStatement() {
        if (mPendingDeleteId >= 0) {
            mDb.deletePdfStatement(mPendingDeleteId);
            mPendingDeleteId = -1L;
            Toast.makeText(this, R.string.msg_statement_deleted, Toast.LENGTH_SHORT).show();
            reload();
        }
    }

    void showPickDialog() {
        CharSequence[] items = new CharSequence[] {
            getString(R.string.pdf_source_local),
            getString(R.string.pdf_source_dropbox)
        };
        new AlertDialog.Builder(this, R.style.RoundedDialog)
            .setTitle(R.string.pdf_source_title)
            .setItems(items, new SourceChoiceListener(this))
            .show();
    }

    void openDropboxInbox() {
        startActivity(new Intent(this, DropboxPdfInboxActivity.class));
    }

    void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/pdf", "text/plain", "text/csv"});
        startActivityForResult(intent, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        // Take persistable permission so the URI survives across restarts
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {}

        // Resolve proper display name from the document provider
        String displayName = null;
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (col >= 0) displayName = cursor.getString(col);
                }
            } finally {
                cursor.close();
            }
        }
        if (displayName == null || displayName.isEmpty()) {
            String seg = uri.getLastPathSegment();
            // Strip "primary:Download/" prefix that SAF paths often have
            displayName = (seg != null && seg.contains("/"))
                    ? seg.substring(seg.lastIndexOf('/') + 1) : seg;
        }
        if (displayName == null) displayName = "statement";

        // Determine if it's a PDF via MIME type (most reliable)
        String mimeType = getContentResolver().getType(uri);
        boolean isPdf = "application/pdf".equals(mimeType)
                || (displayName.toLowerCase(Locale.US).endsWith(".pdf"));

        mPendingUri         = uri;
        mPendingDisplayName = displayName;
        mPendingIsPdf       = isPdf;

        saveAndOpen();
    }

    void saveAndOpen() {
        if (mPendingUri == null) return;

        PdfStatement s = new PdfStatement();
        s.senderId    = -1L;
        s.bankName    = null;
        s.isPdf       = mPendingIsPdf;
        s.statementPeriod       = null;
        s.uri         = mPendingUri.toString();
        s.displayName = mPendingDisplayName;
        s.createdAt   = System.currentTimeMillis();

        long newId = mDb.insertPdfStatement(s);

        reload();

        Intent intent = new Intent(this, StatementScanActivity.class);
        intent.putExtra(StatementScanActivity.EXTRA_STATEMENT_ID, newId);
        startActivity(intent);
    }

    // ============================================================
    // Shared static helpers (also used by PdfLinesActivity)
    // ============================================================

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

    static class AddClickListener implements View.OnClickListener {
        private final PdfInboxActivity mA;
        AddClickListener(PdfInboxActivity a) { mA = a; }
        public void onClick(View v) { mA.showPickDialog(); }
    }

    static class ItemClickListener implements AdapterView.OnItemClickListener {
        private final PdfInboxActivity mA;
        ItemClickListener(PdfInboxActivity a) { mA = a; }
        public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            mA.openStatement(pos);
        }
    }

    static class ItemLongClickListener implements AdapterView.OnItemLongClickListener {
        private final PdfInboxActivity mA;
        ItemLongClickListener(PdfInboxActivity a) { mA = a; }
        public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
            mA.confirmDelete(pos);
            return true;
        }
    }

    static class DeleteConfirmListener implements DialogInterface.OnClickListener {
        private final PdfInboxActivity mA;
        DeleteConfirmListener(PdfInboxActivity a) { mA = a; }
        public void onClick(DialogInterface d, int which) { mA.deleteStatement(); }
    }

    static class SourceChoiceListener implements DialogInterface.OnClickListener {
        private final PdfInboxActivity mA;
        SourceChoiceListener(PdfInboxActivity a) { mA = a; }
        public void onClick(DialogInterface d, int which) {
            if (which == 0) mA.pickFile();
            else mA.openDropboxInbox();
        }
    }
}
