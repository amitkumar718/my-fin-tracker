package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.pdf.PdfDecryptor;
import com.ldsa.myfintracker.pdf.PdfTextExtractor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class PdfLinePickerActivity extends Activity {

    public static final String EXTRA_URI         = "uri";
    public static final String EXTRA_IS_PDF      = "is_pdf";
    public static final String EXTRA_PASSWORD    = "password";
    public static final String EXTRA_BANK_LINE   = "bank_line";
    public static final String EXTRA_PERIOD_LINE = "period_line";
    public static final String EXTRA_TRANS_LINE  = "trans_line";

    static final int ROLE_NONE   = -1;
    static final int ROLE_BANK   = 0;
    static final int ROLE_PERIOD = 1;
    static final int ROLE_TRANS  = 2;

    static final int COLOR_BANK   = 0xFF1976D2;
    static final int COLOR_PERIOD = 0xFFD55E00;
    static final int COLOR_TRANS  = 0xFF009E73;

    List<String> mLines = new ArrayList<String>();
    int[] mRoles; // per-line role assignment

    String mBankLine, mPeriodLine, mTransLine;

    TextView mTvBankPreview, mTvPeriodPreview, mTvTransPreview;
    TextView mTvStatus;
    ListView mListView;
    Button   mBtnDone;
    LineAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_line_picker);
        getWindow().setStatusBarColor(0xFF1976D2);

        mTvBankPreview   = (TextView) findViewById(R.id.tvBankLinePreview);
        mTvPeriodPreview = (TextView) findViewById(R.id.tvPeriodLinePreview);
        mTvTransPreview  = (TextView) findViewById(R.id.tvTransLinePreview);
        mTvStatus        = (TextView) findViewById(R.id.tvPickerStatus);
        mListView        = (ListView) findViewById(R.id.listPdfLines);
        mBtnDone         = (Button)   findViewById(R.id.btnDone);

        ((TextView) findViewById(R.id.btnBack)).setOnClickListener(new BackListener(this));
        mBtnDone.setOnClickListener(new DoneListener(this));
        mListView.setOnItemClickListener(new LineClickListener(this));

        loadPdf();
    }

    void loadPdf() {
        String uriStr  = getIntent().getStringExtra(EXTRA_URI);
        boolean isPdf  = getIntent().getBooleanExtra(EXTRA_IS_PDF, true);
        String password = getIntent().getStringExtra(EXTRA_PASSWORD);
        if (uriStr == null) { finish(); return; }
        new LoadThread(this, Uri.parse(uriStr), isPdf, password,
            new Handler(Looper.getMainLooper())).start();
    }

    void onLinesLoaded(List<String> lines, String status) {
        mTvStatus.setVisibility(View.GONE);
        if (PdfDecryptor.NEEDS_PASSWORD.equals(status)
                || (status != null && status.startsWith(PdfDecryptor.WRONG_PASSWORD))) {
            Toast.makeText(this, R.string.msg_pdf_needs_password, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (lines.isEmpty()) {
            mTvStatus.setText(R.string.msg_pdf_no_text);
            mTvStatus.setVisibility(View.VISIBLE);
            return;
        }
        mLines = lines;
        mRoles = new int[lines.size()];
        for (int i = 0; i < mRoles.length; i++) mRoles[i] = ROLE_NONE;
        mAdapter = new LineAdapter(this);
        mListView.setAdapter(mAdapter);
    }

    void onLineTapped(int position) {
        final String line = mLines.get(position);
        final int pos = position;
        new AlertDialog.Builder(this, R.style.RoundedDialog)
            .setItems(new String[]{
                getString(R.string.assign_as_bank),
                getString(R.string.assign_as_period),
                getString(R.string.assign_as_trans)
            }, new AssignDialogListener(this, pos, line))
            .show();
    }

    void assignLine(int position, String line, int role) {
        // Clear any previous assignment for this role
        for (int i = 0; i < mRoles.length; i++) {
            if (mRoles[i] == role) mRoles[i] = ROLE_NONE;
        }
        mRoles[position] = role;
        switch (role) {
            case ROLE_BANK:   mBankLine = line;   updatePreview(mTvBankPreview,   line, COLOR_BANK);   break;
            case ROLE_PERIOD: mPeriodLine = line; updatePreview(mTvPeriodPreview, line, COLOR_PERIOD); break;
            case ROLE_TRANS:  mTransLine = line;  updatePreview(mTvTransPreview,  line, COLOR_TRANS);  break;
        }
        if (mAdapter != null) mAdapter.notifyDataSetChanged();
        mBtnDone.setEnabled(mTransLine != null && !mTransLine.isEmpty());
    }

    void updatePreview(TextView tv, String line, int color) {
        tv.setText(line);
        tv.setTextColor(color);
    }

    void done() {
        if (mTransLine == null || mTransLine.isEmpty()) {
            Toast.makeText(this, R.string.msg_trans_line_required, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent result = new Intent();
        if (mBankLine   != null) result.putExtra(EXTRA_BANK_LINE,   mBankLine);
        if (mPeriodLine != null) result.putExtra(EXTRA_PERIOD_LINE, mPeriodLine);
        result.putExtra(EXTRA_TRANS_LINE, mTransLine);
        setResult(RESULT_OK, result);
        finish();
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    static class LineAdapter extends BaseAdapter {
        private final PdfLinePickerActivity mA;
        LineAdapter(PdfLinePickerActivity a) { mA = a; }

        public int     getCount()             { return mA.mLines.size(); }
        public Object  getItem(int pos)       { return mA.mLines.get(pos); }
        public long    getItemId(int pos)     { return pos; }

        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mA)
                    .inflate(R.layout.item_pdf_line_row, parent, false);
            }
            TextView tvLine  = (TextView) convertView.findViewById(R.id.tvPdfLine);
            View     vStrip  = convertView.findViewById(R.id.vRoleStrip);
            tvLine.setText(mA.mLines.get(pos));
            int role = mA.mRoles != null && pos < mA.mRoles.length ? mA.mRoles[pos] : ROLE_NONE;
            switch (role) {
                case ROLE_BANK:   vStrip.setBackgroundColor(COLOR_BANK);   tvLine.setTextColor(COLOR_BANK);   break;
                case ROLE_PERIOD: vStrip.setBackgroundColor(COLOR_PERIOD); tvLine.setTextColor(COLOR_PERIOD); break;
                case ROLE_TRANS:  vStrip.setBackgroundColor(COLOR_TRANS);  tvLine.setTextColor(COLOR_TRANS);  break;
                default:          vStrip.setBackgroundColor(0x00000000);   tvLine.setTextColor(0xFF212121);   break;
            }
            return convertView;
        }
    }

    // ── Static listener classes ───────────────────────────────────────────────

    static class BackListener implements View.OnClickListener {
        private final PdfLinePickerActivity mA;
        BackListener(PdfLinePickerActivity a) { mA = a; }
        public void onClick(View v) { mA.finish(); }
    }

    static class DoneListener implements View.OnClickListener {
        private final PdfLinePickerActivity mA;
        DoneListener(PdfLinePickerActivity a) { mA = a; }
        public void onClick(View v) { mA.done(); }
    }

    static class LineClickListener implements AdapterView.OnItemClickListener {
        private final PdfLinePickerActivity mA;
        LineClickListener(PdfLinePickerActivity a) { mA = a; }
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            mA.onLineTapped(pos);
        }
    }

    static class AssignDialogListener implements DialogInterface.OnClickListener {
        private final PdfLinePickerActivity mA;
        private final int    mPos;
        private final String mLine;
        AssignDialogListener(PdfLinePickerActivity a, int pos, String line) {
            mA = a; mPos = pos; mLine = line;
        }
        public void onClick(DialogInterface d, int which) {
            mA.assignLine(mPos, mLine, which);
        }
    }

    // ── Post-lines runnable ───────────────────────────────────────────────────

    static class PostLinesRunnable implements Runnable {
        private final PdfLinePickerActivity mA;
        private final List<String> mLines;
        private final String mStatus;
        PostLinesRunnable(PdfLinePickerActivity a, List<String> lines, String status) {
            mA = a; mLines = lines; mStatus = status;
        }
        public void run() {
            if (!mA.isFinishing()) mA.onLinesLoaded(mLines, mStatus);
        }
    }

    // ── PDF load thread ───────────────────────────────────────────────────────

    static class LoadThread extends Thread {
        private final PdfLinePickerActivity mA;
        private final Uri     mUri;
        private final boolean mIsPdf;
        private final String  mPassword;
        private final Handler mHandler;

        LoadThread(PdfLinePickerActivity a, Uri uri, boolean isPdf, String pw, Handler h) {
            mA = a; mUri = uri; mIsPdf = isPdf; mPassword = pw; mHandler = h;
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

            final List<String> lines;
            if (PdfDecryptor.NEEDS_PASSWORD.equals(raw)
                    || raw.startsWith(PdfDecryptor.WRONG_PASSWORD)) {
                lines = new ArrayList<String>();
            } else {
                lines = PdfInboxActivity.splitLines(raw);
            }
            final String status = raw;
            mHandler.post(new PostLinesRunnable(mA, lines, status));
        }
    }
}
