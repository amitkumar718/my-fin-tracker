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
import com.ldsa.myfintracker.db.PdfSource;
import com.ldsa.myfintracker.db.PdfStatement;
import com.ldsa.myfintracker.db.SenderConfig;
import com.ldsa.myfintracker.pdf.DropboxPdfHelper;

import java.io.File;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PdfInboxActivity extends Activity {

    public static final String EXTRA_SENDER_ID = "sender_id";
    public static final String EXTRA_PICK_MODE = "pick_mode";
    public static final String EXTRA_FILE_URI  = "file_uri";

    /** Session-scoped password cache: URI string → password. Cleared when process dies. */
    static final HashMap<String, String> sCachedPasswords = new HashMap<String, String>();

    private static final int REQ_PICK = 301;

    private ListView             mListView;
    private TextView             mTvEmpty;
    private PdfStatementAdapter  mAdapter;
    private ExpenseDatabase      mDb;

    private long    mSenderId   = -1L;
    private boolean mPickMode;
    private Uri     mPendingUri;
    private String  mPendingDisplayName;
    private boolean mPendingIsPdf;

    private long mPendingDeleteId = -1L;

    /** Current merged list; appended-to as async Dropbox listings arrive. */
    private final List<PdfStatement> mCurrentList = new ArrayList<PdfStatement>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_inbox);
        getWindow().setStatusBarColor(0xFF1976D2);
        mDb = ExpenseDatabase.getInstance(this);
        mSenderId = getIntent().getLongExtra(EXTRA_SENDER_ID, -1L);
        mPickMode = getIntent().getBooleanExtra(EXTRA_PICK_MODE, false);

        mTvEmpty  = (TextView) findViewById(R.id.tvStatementsEmpty);
        mListView = (ListView) findViewById(R.id.listStatements);

        mAdapter = new PdfStatementAdapter(this);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new ItemClickListener(this));
        mListView.setOnItemLongClickListener(new ItemLongClickListener(this));

        Button addBtn = (Button) findViewById(R.id.btnAddStatement);
        if (mSenderId > 0) {
            addBtn.setVisibility(View.GONE);
        } else {
            addBtn.setOnClickListener(new AddClickListener(this));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        List<PdfStatement> stmts = (mSenderId > 0)
            ? mDb.getPdfStatementsBySender(mSenderId)
            : mDb.getAllPdfStatements();

        if (mSenderId > 0) {
            // Enumerate configured local tree-URI sources on a background thread, then merge.
            new Thread(new EnumRunnable(this,
                new android.os.Handler(android.os.Looper.getMainLooper()),
                mSenderId, stmts)).start();
        } else {
            publishList(stmts);
        }
    }

    void publishList(List<PdfStatement> stmts) {
        mCurrentList.clear();
        mCurrentList.addAll(stmts);
        refreshAdapter();
        if (mSenderId > 0) kickoffDropboxEnum();
    }

    void refreshAdapter() {
        java.util.Collections.sort(mCurrentList, new ByDisplayNameDesc());
        List<Integer> counts = new ArrayList<Integer>();
        for (PdfStatement s : mCurrentList) {
            counts.add(s.id > 0 ? mDb.countExpensesByStatement(s.id) : 0);
        }
        mAdapter.setData(new ArrayList<PdfStatement>(mCurrentList), counts);
        if (mCurrentList.isEmpty()) {
            mTvEmpty.setVisibility(View.VISIBLE);
            mListView.setVisibility(View.GONE);
        } else {
            mTvEmpty.setVisibility(View.GONE);
            mListView.setVisibility(View.VISIBLE);
        }
    }

    void kickoffDropboxEnum() {
        String token = getSharedPreferences(DropboxPdfHelper.PREF_FILE, MODE_PRIVATE)
            .getString(DropboxPdfHelper.KEY_TOKEN, null);
        if (token == null || token.isEmpty()) return;
        String bankName = null;
        SenderConfig sc = mDb.getSenderById(mSenderId);
        if (sc != null) bankName = sc.displayName;
        List<PdfSource> sources = mDb.getPdfSourcesBySender(mSenderId);
        for (PdfSource src : sources) {
            if (!src.isDropbox) continue;
            if (src.path == null) continue;
            String path = src.path.trim();
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            DropboxPdfHelper.listPdfs(this, token, path,
                new DropboxListCallback(this, bankName));
        }
    }

    void onDropboxEntries(String bankName, List<DropboxPdfHelper.PdfEntry> entries) {
        // Map pathLower → already-imported PdfStatement (if any)
        java.util.Map<String, PdfStatement> importedByPath = new java.util.HashMap<String, PdfStatement>();
        Set<String> listedPaths = new HashSet<String>();
        for (PdfStatement s : mCurrentList) {
            if (s.displayName != null && s.displayName.startsWith("dbx:")) {
                importedByPath.put(s.displayName.substring(4), s);
            }
            if (s.uri != null && s.uri.startsWith("dbx:")) {
                listedPaths.add(s.uri.substring(4));
            }
        }
        // Remove bare imported Dropbox rows — we'll re-add them under their Dropbox entry
        java.util.Iterator<PdfStatement> it = mCurrentList.iterator();
        while (it.hasNext()) {
            PdfStatement s = it.next();
            if (s.displayName != null && s.displayName.startsWith("dbx:")) it.remove();
        }
        for (DropboxPdfHelper.PdfEntry e : entries) {
            if (listedPaths.contains(e.pathLower)) continue;
            PdfStatement imported = importedByPath.get(e.pathLower);
            if (imported != null) {
                // Keep imported styling (id > 0, real file URI) but show Dropbox entry name
                imported.displayName = e.name;
                mCurrentList.add(imported);
            } else {
                PdfStatement v = new PdfStatement();
                v.id              = -1L;
                v.senderId        = mSenderId;
                v.bankName        = bankName;
                v.isPdf           = true;
                v.statementPeriod = null;
                v.uri             = "dbx:" + e.pathLower;
                v.displayName     = e.name;
                v.createdAt       = System.currentTimeMillis();
                mCurrentList.add(v);
            }
            listedPaths.add(e.pathLower);
        }
        refreshAdapter();
    }

    void openStatement(int pos) {
        PdfStatement s = mAdapter.getStatement(pos);
        if (s.uri != null && s.uri.startsWith("dbx:")) {
            downloadAndOpenDropbox(s);
            return;
        }
        if (mPickMode) {
            Intent result = new Intent();
            result.putExtra(EXTRA_FILE_URI, s.uri);
            setResult(RESULT_OK, result);
            finish();
            return;
        }
        long id = s.id;
        if (id <= 0) {
            // "New" entry discovered from a configured source — import first.
            PdfStatement row = new PdfStatement();
            row.senderId        = s.senderId;
            row.bankName        = s.bankName;
            row.isPdf           = s.isPdf;
            row.statementPeriod = null;
            row.uri             = s.uri;
            row.displayName     = s.displayName;
            row.createdAt       = System.currentTimeMillis();
            id = mDb.insertPdfStatement(row);
        }
        Intent intent = new Intent(this, StatementScanActivity.class);
        intent.putExtra(StatementScanActivity.EXTRA_STATEMENT_ID, id);
        startActivity(intent);
    }

    void downloadAndOpenDropbox(PdfStatement entry) {
        String token = getSharedPreferences(DropboxPdfHelper.PREF_FILE, MODE_PRIVATE)
            .getString(DropboxPdfHelper.KEY_TOKEN, null);
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Dropbox not linked", Toast.LENGTH_SHORT).show();
            return;
        }
        String pathLower = entry.uri.substring(4);
        File dir  = new File(getFilesDir(), "dropbox_pdfs");
        if (!dir.exists()) dir.mkdirs();
        String safe = pathLower.replace('/', '_');
        if (safe.startsWith("_")) safe = safe.substring(1);
        File out = new File(dir, safe);
        Toast.makeText(this, "Downloading…", Toast.LENGTH_SHORT).show();
        DropboxPdfHelper.downloadPdf(this, token, pathLower, out,
            new DropboxDownloadCallback(this, entry, out));
    }

    void onDropboxDownloaded(PdfStatement entry, File out) {
        if (mPickMode) {
            Intent result = new Intent();
            result.putExtra(EXTRA_FILE_URI, Uri.fromFile(out).toString());
            setResult(RESULT_OK, result);
            finish();
            return;
        }
        PdfStatement row = new PdfStatement();
        row.senderId        = entry.senderId;
        row.bankName        = entry.bankName;
        row.isPdf           = true;
        row.statementPeriod = null;
        row.uri             = Uri.fromFile(out).toString();
        row.displayName     = "dbx:" + entry.uri.substring(4);
        row.createdAt       = System.currentTimeMillis();
        long id = mDb.insertPdfStatement(row);
        Intent intent = new Intent(this, StatementScanActivity.class);
        intent.putExtra(StatementScanActivity.EXTRA_STATEMENT_ID, id);
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
        long id = mAdapter.getStatement(pos).id;
        if (id <= 0) return; // "new" discovered entries aren't deletable from here
        mPendingDeleteId = id;
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

    // ============================================================
    // Sender-scoped enumeration (local tree URI sources merged with imported statements)
    // ============================================================

    static class EnumRunnable implements Runnable {
        private final PdfInboxActivity    mA;
        private final android.os.Handler  mH;
        private final long                mSenderId;
        private final List<PdfStatement>  mImported;

        EnumRunnable(PdfInboxActivity a, android.os.Handler h,
                     long senderId, List<PdfStatement> imported) {
            mA = a; mH = h; mSenderId = senderId; mImported = imported;
        }

        public void run() {
            String bankName = null;
            SenderConfig sc = mA.mDb.getSenderById(mSenderId);
            if (sc != null) bankName = sc.displayName;

            Set<String> seen = new HashSet<String>();
            List<PdfStatement> merged = new ArrayList<PdfStatement>(mImported);
            for (PdfStatement s : mImported) if (s.uri != null) seen.add(s.uri);

            List<PdfSource> sources = mA.mDb.getPdfSourcesBySender(mSenderId);
            for (PdfSource src : sources) {
                if (src.isDropbox) continue; // TODO: enumerate Dropbox folders
                if (src.path == null || src.path.isEmpty()) continue;
                Uri treeUri;
                try { treeUri = Uri.parse(src.path); } catch (Exception e) { continue; }
                if (treeUri.getPath() == null || !treeUri.getPath().contains("/tree/")) continue;

                try {
                    String treeId = android.provider.DocumentsContract.getTreeDocumentId(treeUri);
                    Uri childrenUri = android.provider.DocumentsContract
                        .buildChildDocumentsUriUsingTree(treeUri, treeId);
                    Cursor c = mA.getContentResolver().query(childrenUri, new String[]{
                        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                    }, null, null, null);
                    if (c == null) continue;
                    try {
                        while (c.moveToNext()) {
                            String docId = c.getString(0);
                            String name  = c.getString(1);
                            String mime  = c.getString(2);
                            boolean isPdf = "application/pdf".equals(mime)
                                || (name != null && name.toLowerCase(Locale.US).endsWith(".pdf"));
                            if (!isPdf) continue;
                            Uri docUri = android.provider.DocumentsContract
                                .buildDocumentUriUsingTree(treeUri, docId);
                            String uriStr = docUri.toString();
                            if (seen.contains(uriStr)) continue;
                            seen.add(uriStr);
                            PdfStatement v = new PdfStatement();
                            v.id              = -1L;
                            v.senderId        = mSenderId;
                            v.bankName        = bankName;
                            v.isPdf           = true;
                            v.statementPeriod = null;
                            v.uri             = uriStr;
                            v.displayName     = name;
                            v.createdAt       = System.currentTimeMillis();
                            merged.add(v);
                        }
                    } finally {
                        c.close();
                    }
                } catch (Exception ignored) {}
            }

            mH.post(new PublishRunnable(mA, merged));
        }
    }

    static class ByDisplayNameDesc implements java.util.Comparator<PdfStatement> {
        public int compare(PdfStatement a, PdfStatement b) {
            String an = a.displayName != null ? a.displayName : (a.uri != null ? a.uri : "");
            String bn = b.displayName != null ? b.displayName : (b.uri != null ? b.uri : "");
            return bn.compareToIgnoreCase(an); // descending
        }
    }

    static class PublishRunnable implements Runnable {
        private final PdfInboxActivity   mA;
        private final List<PdfStatement> mAll;
        PublishRunnable(PdfInboxActivity a, List<PdfStatement> all) { mA = a; mAll = all; }
        public void run() {
            if (mA.isFinishing()) return;
            mA.publishList(mAll);
        }
    }

    static class DropboxListCallback implements DropboxPdfHelper.ListCallback {
        private final PdfInboxActivity mA;
        private final String           mBankName;
        DropboxListCallback(PdfInboxActivity a, String bankName) {
            mA = a; mBankName = bankName;
        }
        public void onSuccess(List<DropboxPdfHelper.PdfEntry> entries) {
            if (mA.isFinishing()) return;
            mA.onDropboxEntries(mBankName, entries);
        }
        public void onError(String message) {
            android.util.Log.w("myfin.dropbox", "list error: " + message);
            if (mA.isFinishing()) return;
            Toast.makeText(mA, "Dropbox: " + message, Toast.LENGTH_SHORT).show();
        }
        public void onAuthFailed() {
            android.util.Log.w("myfin.dropbox", "list auth failed");
            if (mA.isFinishing()) return;
            Toast.makeText(mA, "Dropbox auth failed", Toast.LENGTH_SHORT).show();
        }
    }

    static class DropboxDownloadCallback implements DropboxPdfHelper.DownloadCallback {
        private final PdfInboxActivity mA;
        private final PdfStatement     mEntry;
        private final File             mOut;
        DropboxDownloadCallback(PdfInboxActivity a, PdfStatement entry, File out) {
            mA = a; mEntry = entry; mOut = out;
        }
        public void onSuccess(File file) {
            if (mA.isFinishing()) return;
            mA.onDropboxDownloaded(mEntry, mOut);
        }
        public void onError(String message) {
            if (mA.isFinishing()) return;
            Toast.makeText(mA, "Download failed: " + message, Toast.LENGTH_SHORT).show();
        }
        public void onAuthFailed() {
            if (mA.isFinishing()) return;
            Toast.makeText(mA, "Dropbox auth failed", Toast.LENGTH_SHORT).show();
        }
    }
}
