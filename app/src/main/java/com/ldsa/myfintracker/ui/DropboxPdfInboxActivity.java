package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.util.Log;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.PdfStatement;
import com.ldsa.myfintracker.pdf.DropboxPdfHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class DropboxPdfInboxActivity extends Activity {

    public static final String EXTRA_SENDER_ID = "sender_id";
    public static final String EXTRA_PICK_MODE = "pick_mode";
    public static final String EXTRA_FILE_URI  = "file_uri";

    private static final String PREF_FILE  = DropboxPdfHelper.PREF_FILE;
    private static final String KEY_TOKEN  = DropboxPdfHelper.KEY_TOKEN;

    /** Local directory for Dropbox-downloaded PDFs. */
    private static final String DL_DIR = "dropbox_pdfs";

    private TextView      mTvStatus;
    private TextView      mTvEmpty;
    private ProgressBar   mProgress;
    private ListView      mListView;
    private Button        mBtnDownload;

    private ExpenseDatabase mDb;
    private DbxPdfAdapter   mAdapter;
    String                  mToken;
    long                    mSenderId = -1L;

    List<DropboxPdfHelper.PdfEntry> mEntries = new ArrayList<DropboxPdfHelper.PdfEntry>();
    List<Boolean>                   mSelected = new ArrayList<Boolean>();
    HashSet<String>                 mAlreadyImported = new HashSet<String>();

    // Progress of multi-file download
    int mDlTotal;
    int mDlDone;
    int mDlFailed;

    /** Statement IDs inserted this session, to be scanned in order after all downloads. */
    boolean mPickMode = false;

    List<Long> mDownloadedIds = new ArrayList<Long>();
    int mScanIdx = 0;

    private static final int REQ_SCAN = 401;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dropbox_pdf_inbox);

        mDb = ExpenseDatabase.getInstance(this);
        mSenderId = getIntent().getLongExtra(EXTRA_SENDER_ID, -1L);
        mPickMode = getIntent().getBooleanExtra(EXTRA_PICK_MODE, false);

        mTvStatus    = (TextView)    findViewById(R.id.tvDbxPdfStatus);
        mTvEmpty     = (TextView)    findViewById(R.id.tvDbxPdfEmpty);
        mProgress    = (ProgressBar) findViewById(R.id.dbxPdfProgress);
        mListView    = (ListView)    findViewById(R.id.listDbxPdf);
        mBtnDownload = (Button)      findViewById(R.id.btnDbxPdfDownload);
        Button btnCancel = (Button) findViewById(R.id.btnDbxPdfCancel);

        mListView.setOnItemClickListener(new ItemClickListener(this));
        mBtnDownload.setOnClickListener(new DownloadClickListener(this));
        btnCancel.setOnClickListener(new CancelClickListener(this));
        ((TextView) findViewById(R.id.btnDbxChangeToken)).setOnClickListener(new ChangeTokenClickListener(this));

        // Build set of already-imported dropbox paths (dedupe)
        for (PdfStatement s : mDb.getAllPdfStatements()) {
            if (s.displayName != null && s.displayName.startsWith("dbx:")) {
                mAlreadyImported.add(s.displayName.substring(4));
            }
        }

        mToken = getSharedPreferences(PREF_FILE, MODE_PRIVATE).getString(KEY_TOKEN, null);
        if (mToken == null || mToken.isEmpty()) {
            showTokenDialog();
        } else {
            startList();
        }
    }

    void showTokenDialog() {
        android.view.View v = getLayoutInflater().inflate(R.layout.dialog_password, null);
        EditText et = (EditText) v.findViewById(R.id.etDialogPassword);
        et.setHint(R.string.dropbox_token_hint);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        // Pre-fill with stored token so user only needs to update if expired
        String stored = getSharedPreferences(PREF_FILE, MODE_PRIVATE).getString(KEY_TOKEN, null);
        if (stored != null && !stored.isEmpty()) et.setText(stored);
        AlertDialog.Builder b = new AlertDialog.Builder(this, R.style.RoundedDialog);
        b.setTitle(R.string.dropbox_token_title);
        b.setMessage(R.string.dropbox_token_message);
        b.setView(v);
        b.setPositiveButton(R.string.save, new TokenSaveListener(this, et));
        b.setNegativeButton(R.string.cancel, new TokenCancelListener(this));
        b.setCancelable(false);
        b.show();
    }

    void saveTokenAndList(String token) {
        mToken = token;
        getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, token).apply();
        startList();
    }

    void startList() {
        mTvStatus.setText(R.string.dbx_pdf_listing);
        mProgress.setVisibility(View.VISIBLE);
        mListView.setVisibility(View.GONE);
        mTvEmpty.setVisibility(View.GONE);
        mBtnDownload.setEnabled(false);
        String root = getSharedPreferences(DropboxPdfHelper.PREF_FILE, MODE_PRIVATE)
            .getString(DropboxPdfHelper.KEY_ROOT, DropboxPdfHelper.DEFAULT_ROOT);
        if (root == null) root = DropboxPdfHelper.DEFAULT_ROOT;
        DropboxPdfHelper.listPdfs(this, mToken, root, new ListCallbackImpl(this));
    }

    void onListSuccess(List<DropboxPdfHelper.PdfEntry> entries) {
        mProgress.setVisibility(View.GONE);
        java.util.Collections.sort(entries, new ByEntryNameDesc());
        mEntries = entries;
        mSelected = new ArrayList<Boolean>();
        for (int i = 0; i < entries.size(); i++) mSelected.add(Boolean.FALSE);

        if (entries.isEmpty()) {
            mTvEmpty.setText(R.string.dbx_pdf_empty);
            mTvEmpty.setVisibility(View.VISIBLE);
            mTvStatus.setText(R.string.dbx_pdf_empty_short);
            return;
        }

        mAdapter = new DbxPdfAdapter(this);
        mListView.setAdapter(mAdapter);
        mListView.setVisibility(View.VISIBLE);
        mTvStatus.setText(getString(R.string.dbx_pdf_found_fmt, entries.size()));
        updateDownloadButton();
    }

    void onListError(String msg) {
        mProgress.setVisibility(View.GONE);
        Toast.makeText(this, getString(R.string.dbx_pdf_list_failed) + ": " + msg,
            Toast.LENGTH_LONG).show();
        finish();
    }

    void onAuthFailed() {
        mToken = null;
        mProgress.setVisibility(View.GONE);
        mTvStatus.setText(R.string.dropbox_token_invalid);
        // Don't show dialog — user taps "⚙ Token" in the header to update
    }

    void toggleItem(int pos) {
        if (pos < 0 || pos >= mEntries.size()) return;
        if (mAlreadyImported.contains(mEntries.get(pos).pathLower)) return;
        mSelected.set(pos, Boolean.valueOf(!mSelected.get(pos).booleanValue()));
        if (mAdapter != null) mAdapter.notifyDataSetChanged();
        updateDownloadButton();
    }

    void updateDownloadButton() {
        int n = 0;
        for (Boolean b : mSelected) if (b.booleanValue()) n++;
        mBtnDownload.setText(getString(R.string.dbx_pdf_download) + " (" + n + ")");
        mBtnDownload.setEnabled(n > 0);
    }

    void startDownload() {
        List<DropboxPdfHelper.PdfEntry> queue = new ArrayList<DropboxPdfHelper.PdfEntry>();
        for (int i = 0; i < mEntries.size(); i++) {
            if (mSelected.get(i).booleanValue()) queue.add(mEntries.get(i));
        }
        if (queue.isEmpty()) return;

        mDlTotal = queue.size();
        mDlDone = 0;
        mDlFailed = 0;
        mBtnDownload.setEnabled(false);
        mProgress.setVisibility(View.VISIBLE);
        mTvStatus.setText(getString(R.string.dbx_pdf_dl_fmt, 0, mDlTotal));

        downloadNext(queue, 0);
    }

    void downloadNext(List<DropboxPdfHelper.PdfEntry> queue, int idx) {
        if (idx >= queue.size()) {
            finishDownloads();
            return;
        }
        DropboxPdfHelper.PdfEntry entry = queue.get(idx);
        File out = buildOutFile(entry);
        DropboxPdfHelper.downloadPdf(this, mToken, entry.pathLower, out,
            new DownloadCallbackImpl(this, entry, out, queue, idx));
    }

    private File buildOutFile(DropboxPdfHelper.PdfEntry entry) {
        File dir = new File(getFilesDir(), DL_DIR);
        String safe = entry.pathLower.replace('/', '_');
        if (safe.startsWith("_")) safe = safe.substring(1);
        return new File(dir, safe);
    }

    void onOneDownloaded(DropboxPdfHelper.PdfEntry entry, File out,
                         List<DropboxPdfHelper.PdfEntry> queue, int idx) {
        if (mPickMode) {
            Intent result = new Intent();
            result.putExtra(EXTRA_FILE_URI, Uri.fromFile(out).toString());
            setResult(RESULT_OK, result);
            finish();
            return;
        }
        // Insert PdfStatement row — displayName encodes dropbox path for dedupe
        PdfStatement s = new PdfStatement();
        s.senderId    = mSenderId;
        s.bankName    = null;
        s.isPdf       = true;
        s.statementPeriod = null;
        s.uri         = Uri.fromFile(out).toString();
        s.displayName = "dbx:" + entry.pathLower;
        s.createdAt   = System.currentTimeMillis();
        long newId = mDb.insertPdfStatement(s);
        mAlreadyImported.add(entry.pathLower);
        mDownloadedIds.add(newId);

        mDlDone++;
        mTvStatus.setText(getString(R.string.dbx_pdf_dl_fmt, mDlDone + mDlFailed, mDlTotal));
        downloadNext(queue, idx + 1);
    }

    void onOneFailed(DropboxPdfHelper.PdfEntry entry, String msg,
                     List<DropboxPdfHelper.PdfEntry> queue, int idx) {
        mDlFailed++;
        Toast.makeText(this, entry.name + ": " + msg, Toast.LENGTH_SHORT).show();
        downloadNext(queue, idx + 1);
    }

    void finishDownloads() {
        Log.d("myfin.dbx", "finishDownloads done=" + mDlDone + " failed=" + mDlFailed + " ids=" + mDownloadedIds.size());
        mProgress.setVisibility(View.GONE);
        if (mDlFailed > 0 && mDlDone == 0) {
            Toast.makeText(this, getString(R.string.dbx_pdf_dl_partial_fmt, mDlDone, mDlFailed),
                Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        mScanIdx = 0;
        scanNext();
    }

    void scanNext() {
        Log.d("myfin.dbx", "scanNext idx=" + mScanIdx + " total=" + mDownloadedIds.size());
        if (mScanIdx >= mDownloadedIds.size()) {
            finish();
            return;
        }
        long stmtId = mDownloadedIds.get(mScanIdx);
        mScanIdx++;
        Log.d("myfin.dbx", "launching StatementScanActivity stmtId=" + stmtId);
        Intent intent = new Intent(this, StatementScanActivity.class);
        intent.putExtra(StatementScanActivity.EXTRA_STATEMENT_ID, stmtId);
        startActivityForResult(intent, REQ_SCAN);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SCAN) scanNext();
    }

    // ============================================================
    // Adapter
    // ============================================================

    static class DbxPdfAdapter extends BaseAdapter {
        private final DropboxPdfInboxActivity mOuter;
        DbxPdfAdapter(DropboxPdfInboxActivity outer) { mOuter = outer; }

        public int getCount() { return mOuter.mEntries.size(); }
        public Object getItem(int pos) { return mOuter.mEntries.get(pos); }
        public long getItemId(int pos) { return pos; }

        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mOuter)
                    .inflate(R.layout.item_dropbox_pdf, parent, false);
            }
            DropboxPdfHelper.PdfEntry e = mOuter.mEntries.get(pos);
            CheckBox cb      = (CheckBox) convertView.findViewById(R.id.cbDbxPdf);
            TextView tvName  = (TextView) convertView.findViewById(R.id.tvDbxPdfName);
            TextView tvPath  = (TextView) convertView.findViewById(R.id.tvDbxPdfPath);
            TextView tvSize  = (TextView) convertView.findViewById(R.id.tvDbxPdfSize);

            boolean already = mOuter.mAlreadyImported.contains(e.pathLower);
            tvName.setText(e.name);
            // Show parent folder without the filename
            String folder = e.pathDisplay;
            int slash = folder.lastIndexOf('/');
            if (slash > 0) folder = folder.substring(0, slash);
            else folder = "/";
            tvPath.setText(folder);
            tvSize.setText(formatSize(e.size));

            if (already) {
                cb.setChecked(true);
                cb.setEnabled(false);
                tvName.setTextColor(0xFF9E9E9E);
                tvPath.setTextColor(0xFFBDBDBD);
                convertView.setAlpha(0.6f);
            } else {
                cb.setEnabled(true);
                cb.setChecked(mOuter.mSelected.get(pos).booleanValue());
                tvName.setTextColor(0xFF212121);
                tvPath.setTextColor(0xFF757575);
                convertView.setAlpha(1.0f);
            }
            return convertView;
        }

        private static String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    // ============================================================
    // Callbacks
    // ============================================================

    static class ListCallbackImpl implements DropboxPdfHelper.ListCallback {
        private final DropboxPdfInboxActivity mOuter;
        ListCallbackImpl(DropboxPdfInboxActivity outer) { mOuter = outer; }
        public void onSuccess(List<DropboxPdfHelper.PdfEntry> entries) {
            mOuter.onListSuccess(entries);
        }
        public void onError(String msg) { mOuter.onListError(msg); }
        public void onAuthFailed() { mOuter.onAuthFailed(); }
    }

    static class DownloadCallbackImpl implements DropboxPdfHelper.DownloadCallback {
        private final DropboxPdfInboxActivity mOuter;
        private final DropboxPdfHelper.PdfEntry mEntry;
        private final File mOut;
        private final List<DropboxPdfHelper.PdfEntry> mQueue;
        private final int mIdx;
        DownloadCallbackImpl(DropboxPdfInboxActivity outer,
                             DropboxPdfHelper.PdfEntry entry, File out,
                             List<DropboxPdfHelper.PdfEntry> queue, int idx) {
            mOuter = outer; mEntry = entry; mOut = out; mQueue = queue; mIdx = idx;
        }
        public void onSuccess(File f) {
            mOuter.onOneDownloaded(mEntry, mOut, mQueue, mIdx);
        }
        public void onError(String msg) {
            mOuter.onOneFailed(mEntry, msg, mQueue, mIdx);
        }
        public void onAuthFailed() {
            mOuter.onOneFailed(mEntry, mOuter.getString(R.string.dropbox_token_invalid), mQueue, mIdx);
        }
    }

    // ============================================================
    // Listeners
    // ============================================================

    static class ItemClickListener implements AdapterView.OnItemClickListener {
        private final DropboxPdfInboxActivity mA;
        ItemClickListener(DropboxPdfInboxActivity a) { mA = a; }
        public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            mA.toggleItem(pos);
        }
    }

    static class DownloadClickListener implements View.OnClickListener {
        private final DropboxPdfInboxActivity mA;
        DownloadClickListener(DropboxPdfInboxActivity a) { mA = a; }
        public void onClick(View v) { mA.startDownload(); }
    }

    static class CancelClickListener implements View.OnClickListener {
        private final DropboxPdfInboxActivity mA;
        CancelClickListener(DropboxPdfInboxActivity a) { mA = a; }
        public void onClick(View v) { mA.finish(); }
    }

    static class TokenSaveListener implements DialogInterface.OnClickListener {
        private final DropboxPdfInboxActivity mA;
        private final EditText mEt;
        TokenSaveListener(DropboxPdfInboxActivity a, EditText et) { mA = a; mEt = et; }
        public void onClick(DialogInterface d, int w) {
            String t = mEt.getText().toString().trim();
            if (t.isEmpty()) {
                Toast.makeText(mA, R.string.dropbox_token_empty, Toast.LENGTH_SHORT).show();
                mA.showTokenDialog();
                return;
            }
            mA.saveTokenAndList(t);
        }
    }

    static class TokenCancelListener implements DialogInterface.OnClickListener {
        private final DropboxPdfInboxActivity mA;
        TokenCancelListener(DropboxPdfInboxActivity a) { mA = a; }
        public void onClick(DialogInterface d, int w) { mA.finish(); }
    }

    static class ChangeTokenClickListener implements View.OnClickListener {
        private final DropboxPdfInboxActivity mA;
        ChangeTokenClickListener(DropboxPdfInboxActivity a) { mA = a; }
        public void onClick(View v) { mA.showTokenDialog(); }
    }

    static class ByEntryNameDesc implements java.util.Comparator<DropboxPdfHelper.PdfEntry> {
        public int compare(DropboxPdfHelper.PdfEntry a, DropboxPdfHelper.PdfEntry b) {
            String an = a != null && a.name != null ? a.name : "";
            String bn = b != null && b.name != null ? b.name : "";
            return bn.compareToIgnoreCase(an);
        }
    }
}
