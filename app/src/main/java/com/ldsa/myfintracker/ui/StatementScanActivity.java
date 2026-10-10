package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.ExtractionPattern;
import com.ldsa.myfintracker.db.PdfStatement;
import com.ldsa.myfintracker.db.SenderConfig;
import com.ldsa.myfintracker.pdf.PdfDecryptor;
import com.ldsa.myfintracker.pdf.PdfTextExtractor;

import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatementScanActivity extends Activity {

    static final String EXTRA_STATEMENT_ID = "statement_id";

    private TextView    mTvStatus;
    private ProgressBar mProgress;
    private Button      mBtnImport;
    private Button      mBtnSetupTemplate;
    private ListView    mListView;

    private ExpenseDatabase mDb;
    private PdfStatement    mStatement;
    private Handler         mHandler;

    private List<Candidate> mCandidates = new ArrayList<Candidate>();
    private List<Boolean>   mSelected   = new ArrayList<Boolean>();
    private CandidateAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statement_scan);

        mDb      = ExpenseDatabase.getInstance(this);
        mHandler = new Handler();

        mTvStatus         = (TextView)    findViewById(R.id.tvScanStatus);
        mProgress         = (ProgressBar) findViewById(R.id.scanProgress);
        mBtnImport        = (Button)      findViewById(R.id.btnImportSelected);
        mBtnSetupTemplate = (Button)      findViewById(R.id.btnSetupTemplate);
        mListView         = (ListView)    findViewById(R.id.listScanCandidates);

        mAdapter = new CandidateAdapter(this);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new ItemClickListener(this));
        mBtnImport.setOnClickListener(new ImportClickListener(this));
        mBtnSetupTemplate.setOnClickListener(new SetupTemplateClickListener(this));
        ((Button) findViewById(R.id.btnScanClose)).setOnClickListener(new CloseClickListener(this));

        long stmtId = getIntent().getLongExtra(EXTRA_STATEMENT_ID, -1L);
        mStatement  = mDb.getPdfStatementById(stmtId);
        if (mStatement == null) {
            Toast.makeText(this, R.string.error_statement_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        startScan();
    }

    // ── Scan ────────────────────────────────────────────────────────

    void startScan() {
        mTvStatus.setText(R.string.status_reading_file);
        mProgress.setVisibility(View.VISIBLE);
        mBtnImport.setVisibility(View.GONE);
        mBtnSetupTemplate.setVisibility(View.GONE);
        mListView.setVisibility(View.GONE);

        String cachedPw = PdfInboxActivity.sCachedPasswords.get(mStatement.uri);
        if (cachedPw == null && mStatement.senderId > 0) {
            cachedPw = getSharedPreferences("fin_prefs", MODE_PRIVATE)
                .getString("pdf_pass_" + mStatement.senderId, null);
        }
        new ScanThread(this, mStatement, cachedPw, mDb, mHandler).start();
    }

    void onScanResult(List<Candidate> candidates, String errorMsg) {
        mProgress.setVisibility(View.GONE);
        if (errorMsg != null) {
            if (PdfDecryptor.NEEDS_PASSWORD.equals(errorMsg)) {
                promptPassword();
            } else {
                mTvStatus.setText(errorMsg);
            }
            return;
        }
        if (candidates == null || candidates.isEmpty()) {
            // Patterns exist but no matches — or no patterns at all
            mTvStatus.setText(R.string.status_no_matches);
            mBtnSetupTemplate.setVisibility(View.VISIBLE);
            return;
        }
        mCandidates = candidates;
        mSelected   = new ArrayList<Boolean>();
        for (int i = 0; i < candidates.size(); i++) mSelected.add(Boolean.TRUE);

        mTvStatus.setText(getString(R.string.status_found_n, candidates.size()));
        mAdapter.notifyDataSetChanged();
        mListView.setVisibility(View.VISIBLE);
        mBtnSetupTemplate.setText(R.string.btn_edit_template);
        mBtnSetupTemplate.setVisibility(View.VISIBLE);
        updateImportButton();
    }

    void onNoPatternsFound() {
        mProgress.setVisibility(View.GONE);
        mTvStatus.setText(R.string.status_no_patterns);
        mBtnSetupTemplate.setVisibility(View.VISIBLE);
    }

    void toggleItem(int pos) {
        if (pos < 0 || pos >= mSelected.size()) return;
        mSelected.set(pos, !mSelected.get(pos));
        mAdapter.notifyDataSetChanged();
        updateImportButton();
    }

    void updateImportButton() {
        int n = 0;
        for (Boolean b : mSelected) if (b) n++;
        if (n > 0) {
            mBtnImport.setText(getString(R.string.btn_import_selected) + " (" + n + ")");
            mBtnImport.setVisibility(View.VISIBLE);
        } else {
            mBtnImport.setVisibility(View.GONE);
        }
    }

    void importSelected() {
        int imported = 0;
        for (int i = 0; i < mCandidates.size(); i++) {
            if (!mSelected.get(i)) continue;
            Candidate c = mCandidates.get(i);
            Expense e = new Expense();
            e.amount          = c.amount;
            e.isCredit        = c.isCredit;
            e.dateMs          = c.dateMs;
            e.merchant        = c.merchant;
            e.card            = c.card;
            e.accountNumber   = c.accountNumber;
            e.balance         = c.balance;
            e.isOnline        = c.isOnline;
            e.transactionType = c.transactionType;
            e.bank            = mStatement.bankName;
            e.originalSms     = c.sourceLine;
            e.patternId       = c.patternId;
            e.pdfStatementId  = mStatement.id;
            e.source          = "pdf";
            e.createdAt       = System.currentTimeMillis();
            mDb.insertExpense(e);
            imported++;
        }
        Toast.makeText(this, getString(R.string.status_imported_n, imported), Toast.LENGTH_SHORT).show();
        finish();
    }

    void openTemplateEditor() {
        Intent intent = new Intent(this, MapExpenseActivity.class);
        intent.putExtra(MapExpenseActivity.EXTRA_STATEMENT_ID, mStatement.id);
        intent.putExtra(MapExpenseActivity.EXTRA_BLANK_TEMPLATE, true);
        intent.putExtra(MapExpenseActivity.EXTRA_IS_PDF,         true);
        if (mStatement.sampleTransLine != null && !mStatement.sampleTransLine.isEmpty()) {
            intent.putExtra(MapExpenseActivity.EXTRA_TRANS_LINE, mStatement.sampleTransLine);
        }
        if (mStatement.senderId > 0) {
            intent.putExtra(MapExpenseActivity.EXTRA_SENDER_ID, mStatement.senderId);
        }
        startActivity(intent);
        finish();
    }

    // ── Password prompt ─────────────────────────────────────────────

    void promptPassword() {
        android.view.View v = getLayoutInflater().inflate(R.layout.dialog_password, null);
        android.widget.EditText et = (android.widget.EditText) v.findViewById(R.id.etDialogPassword);
        et.setHint(R.string.hint_pdf_password);
        new AlertDialog.Builder(this, R.style.RoundedDialog)
            .setTitle(R.string.pdf_password_title)
            .setMessage(R.string.pdf_password_msg)
            .setView(v)
            .setPositiveButton(R.string.save, new PasswordOkListener(this, et))
            .setNegativeButton(R.string.cancel, new PasswordCancelListener(this))
            .setCancelable(false)
            .show();
    }

    void onPasswordEntered(String pw) {
        PdfInboxActivity.sCachedPasswords.put(mStatement.uri, pw);
        mProgress.setVisibility(View.VISIBLE);
        mTvStatus.setText(R.string.status_reading_file);
        new ScanThread(this, mStatement, pw, mDb, mHandler).start();
    }

    // ── Background scan thread ───────────────────────────────────────

    static class ScanThread extends Thread {
        private final StatementScanActivity mA;
        private final PdfStatement          mStmt;
        private final String                mPassword;
        private final ExpenseDatabase       mDb;
        private final Handler               mHandler;

        ScanThread(StatementScanActivity a, PdfStatement stmt,
                   String password, ExpenseDatabase db, Handler h) {
            mA = a; mStmt = stmt; mPassword = password; mDb = db; mHandler = h;
        }

        public void run() {
            // 1. Extract text
            String text;
            try {
                InputStream is = mA.getContentResolver().openInputStream(Uri.parse(mStmt.uri));
                if (is == null) {
                    post(null, mA.getString(R.string.status_extract_failed));
                    return;
                }
                text = PdfTextExtractor.extract(is, mPassword);
                is.close();
            } catch (Exception ex) {
                post(null, mA.getString(R.string.status_extract_failed));
                return;
            }
            if (PdfDecryptor.NEEDS_PASSWORD.equals(text)) {
                post(null, PdfDecryptor.NEEDS_PASSWORD);
                return;
            }
            if (text == null || text.trim().isEmpty()) {
                post(null, mA.getString(R.string.status_extract_failed));
                return;
            }

            // 2. Resolve sender: use stored senderId if known, else detect from field patterns
            long senderId = mStmt.senderId;
            String bankName = mStmt.bankName;
            if (senderId <= 0) {
                String bankPat = mDb.getPdfFieldPattern("bank");
                if (bankPat != null && !bankPat.isEmpty()) {
                    for (String line : text.split("\n")) {
                        String val = applyFieldPattern(bankPat, line.trim());
                        if (!val.isEmpty()) {
                            bankName = val;
                            break;
                        }
                    }
                }
                if (bankName != null && !bankName.isEmpty()) {
                    for (SenderConfig s : mDb.getAllSenders()) {
                        String dn = s.displayName != null ? s.displayName.toLowerCase(Locale.US) : "";
                        String bn = bankName.toLowerCase(Locale.US);
                        if (dn.contains(bn) || bn.contains(dn)) {
                            senderId = s.id;
                            break;
                        }
                    }
                }
            }

            // 3. Load PDF patterns for this sender
            List<ExtractionPattern> patterns;
            if (senderId > 0) {
                patterns = mDb.getPdfPatternsBySender(senderId);
            } else {
                patterns = mDb.getAllPdfPatterns();
            }

            if (patterns.isEmpty()) {
                // Persist detected bank/sender so next visit skips detection
                if (senderId > 0 || bankName != null) {
                    mHandler.post(new MetaUpdateRunnable(mDb, mStmt.id, senderId, bankName));
                }
                mHandler.post(new NoPatternsRunnable(mA));
                return;
            }

            // 4. Scan lines against patterns
            List<String> lines = PdfInboxActivity.splitLines(text);
            List<Candidate> candidates = new ArrayList<Candidate>();
            // Auto-credit infers debit/credit from balance deltas; disabled unless
            // the sender has it toggled on. When on, consecutive candidates are
            // classified in document order (the extractor now emits pages in
            // /Pages tree order so this is chronologically sound).
            boolean autoCredit = (senderId > 0) && mDb.getSenderPdfAutoCredit(senderId);
            com.ldsa.myfintracker.pdf.PdfCreditClassifier classifier =
                    new com.ldsa.myfintracker.pdf.PdfCreditClassifier(autoCredit);

            for (String line : lines) {
                for (ExtractionPattern p : patterns) {
                    if (!p.matches(line)) continue;
                    // Try credit first, then debit, then legacy amount. First non-empty wins.
                    String amtStr    = "";
                    boolean amountCrMatched = false;
                    if (p.amountCrGroup >= 0) {
                        amtStr = p.extractGroup(line, p.amountCrGroup).replaceAll("[^0-9.]", "");
                        if (!amtStr.isEmpty()) amountCrMatched = true;
                    }
                    if (amtStr.isEmpty() && p.amountDbGroup >= 0) {
                        amtStr = p.extractGroup(line, p.amountDbGroup).replaceAll("[^0-9.]", "");
                    }
                    if (amtStr.isEmpty() && p.amountGroup >= 0) {
                        amtStr = p.extractGroup(line, p.amountGroup).replaceAll("[^0-9.]", "");
                    }
                    if (amtStr.isEmpty()) continue;
                    double amount;
                    try { amount = Double.parseDouble(amtStr); }
                    catch (NumberFormatException ignored) { continue; }

                    String balStr = p.extractGroup(line, p.balanceGroup)
                                     .replaceAll("[^0-9.,]", "").replace(",", "");
                    boolean hasBalance = !balStr.isEmpty();
                    double balance = 0;
                    if (hasBalance) {
                        try { balance = Double.parseDouble(balStr); }
                        catch (NumberFormatException ignored) { hasBalance = false; }
                    }

                    com.ldsa.myfintracker.pdf.PdfCreditClassifier.Decision cd =
                            classifier.classify(hasBalance, balance, amountCrMatched);
                    if (cd.skip) {
                        break; // zero balance delta — drop this row
                    }

                    String dateStr = p.extractGroup(line, p.dateGroup).trim();
                    String timeStr = p.extractGroup(line, p.timeGroup).trim();
                    long dateMs = parseDateMs(dateStr);
                    if (dateMs == 0) dateMs = System.currentTimeMillis();
                    dateMs = applyTimeToMs(dateMs, timeStr);

                    Candidate c = new Candidate();
                    c.amount          = amount;
                    c.isCredit        = cd.isCredit;
                    c.dateMs          = dateMs;
                    c.merchant        = p.extractGroup(line, p.merchantGroup).trim();
                    c.card            = p.extractGroup(line, p.cardGroup).trim();
                    c.accountNumber   = p.extractGroup(line, p.accountGroup).trim();
                    c.balance         = balance;
                    c.transactionType = p.transactionType != null ? p.transactionType : "";
                    c.isOnline        = isOnline(c.transactionType);
                    c.patternId       = p.id;
                    c.sourceLine      = line;
                    candidates.add(c);
                    break; // first matching pattern wins for this line
                }
            }

            // Persist detected bank/sender — targeted update, never touches lastTransLine
            mHandler.post(new MetaUpdateRunnable(mDb, mStmt.id, senderId, bankName));

            post(candidates, null);
        }

        private void post(List<Candidate> c, String err) {
            mHandler.post(new ScanResultRunnable(mA, c, err));
        }

        private static String applyFieldPattern(String pat, String line) {
            if (pat == null || pat.isEmpty() || line == null) return "";
            try {
                Matcher m = Pattern.compile(pat, Pattern.CASE_INSENSITIVE).matcher(line);
                if (!m.find()) return "";
                return m.groupCount() > 0 ? (m.group(1) != null ? m.group(1) : "") : m.group(0);
            } catch (Exception e) { return ""; }
        }

        private static long parseDateMs(String s) {
            if (s == null || s.trim().isEmpty()) return 0;
            String[] fmts = {
                "dd/MM/yyyy", "dd-MM-yyyy", "MM/dd/yyyy", "yyyy-MM-dd",
                "dd MMM yyyy", "dd MMM yy", "MMM dd, yyyy"
            };
            for (String fmt : fmts) {
                try {
                    Date d = new SimpleDateFormat(fmt, Locale.US).parse(s.trim());
                    if (d != null) return d.getTime();
                } catch (ParseException ignored) {}
            }
            return 0;
        }

        private static long applyTimeToMs(long dateMs, String timeStr) {
            if (timeStr == null || timeStr.trim().isEmpty()) return dateMs;
            try {
                String[] parts = timeStr.trim().split(":");
                if (parts.length < 2) return dateMs;
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                int s = parts.length > 2 ? Integer.parseInt(parts[2].replaceAll("[^0-9]", "")) : 0;
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(dateMs);
                cal.set(Calendar.HOUR_OF_DAY, h);
                cal.set(Calendar.MINUTE, m);
                cal.set(Calendar.SECOND, s);
                return cal.getTimeInMillis();
            } catch (NumberFormatException ignored) { return dateMs; }
        }

        private static boolean isOnline(String type) {
            return "UPI".equals(type) || "CARD_ONLINE".equals(type)
                || "NETBANKING_PURCHASE".equals(type) || "NETBANKING_TRANSFER".equals(type);
        }
    }

    static class ScanResultRunnable implements Runnable {
        private final StatementScanActivity mA;
        private final List<Candidate>       mCandidates;
        private final String                mError;
        ScanResultRunnable(StatementScanActivity a, List<Candidate> c, String err) {
            mA = a; mCandidates = c; mError = err;
        }
        public void run() {
            if (!mA.isFinishing()) mA.onScanResult(mCandidates, mError);
        }
    }

    static class NoPatternsRunnable implements Runnable {
        private final StatementScanActivity mA;
        NoPatternsRunnable(StatementScanActivity a) { mA = a; }
        public void run() { if (!mA.isFinishing()) mA.onNoPatternsFound(); }
    }

    static class MetaUpdateRunnable implements Runnable {
        private final ExpenseDatabase mDb;
        private final long            mId;
        private final long            mSenderId;
        private final String          mBankName;
        MetaUpdateRunnable(ExpenseDatabase db, long id, long senderId, String bankName) {
            mDb = db; mId = id; mSenderId = senderId; mBankName = bankName;
        }
        public void run() { mDb.updatePdfStatementMeta(mId, mSenderId, mBankName); }
    }

    // ── Candidate model ─────────────────────────────────────────────

    static class Candidate {
        double amount;
        boolean isCredit;
        long   dateMs;
        String merchant;
        String card;
        String accountNumber;
        double balance;
        String transactionType;
        boolean isOnline;
        long   patternId;
        String sourceLine;
    }

    // ── Adapter ─────────────────────────────────────────────────────

    static class CandidateAdapter extends BaseAdapter {
        private final StatementScanActivity mOuter;
        CandidateAdapter(StatementScanActivity a) { mOuter = a; }

        public int  getCount()          { return mOuter.mCandidates.size(); }
        public Object getItem(int pos)  { return mOuter.mCandidates.get(pos); }
        public long getItemId(int pos)  { return pos; }

        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mOuter)
                    .inflate(R.layout.item_statement_candidate, parent, false);
            }
            Candidate c  = mOuter.mCandidates.get(pos);
            boolean sel  = mOuter.mSelected.get(pos);

            CheckBox cb         = (CheckBox) convertView.findViewById(R.id.cbCandidateSelect);
            TextView tvAmount   = (TextView) convertView.findViewById(R.id.tvCandAmount);
            TextView tvDate     = (TextView) convertView.findViewById(R.id.tvCandDate);
            TextView tvMerchant = (TextView) convertView.findViewById(R.id.tvCandMerchant);
            TextView tvSource   = (TextView) convertView.findViewById(R.id.tvCandSource);

            cb.setChecked(sel);
            tvAmount.setText(String.format(Locale.getDefault(), "₹%.2f", c.amount));
            String dateLbl = c.dateMs > 0
                ? new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date(c.dateMs)) : "";
            tvDate.setText(dateLbl);
            tvMerchant.setText(c.merchant.isEmpty() ? "—" : c.merchant);
            tvSource.setText(c.sourceLine);

            return convertView;
        }
    }

    // ── Listeners ───────────────────────────────────────────────────

    static class ItemClickListener implements AdapterView.OnItemClickListener {
        private final StatementScanActivity mA;
        ItemClickListener(StatementScanActivity a) { mA = a; }
        public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            mA.toggleItem(pos);
        }
    }

    static class ImportClickListener implements View.OnClickListener {
        private final StatementScanActivity mA;
        ImportClickListener(StatementScanActivity a) { mA = a; }
        public void onClick(View v) { mA.importSelected(); }
    }

    static class SetupTemplateClickListener implements View.OnClickListener {
        private final StatementScanActivity mA;
        SetupTemplateClickListener(StatementScanActivity a) { mA = a; }
        public void onClick(View v) { mA.openTemplateEditor(); }
    }

    static class CloseClickListener implements View.OnClickListener {
        private final StatementScanActivity mA;
        CloseClickListener(StatementScanActivity a) { mA = a; }
        public void onClick(View v) { mA.finish(); }
    }

    static class PasswordOkListener implements DialogInterface.OnClickListener {
        private final StatementScanActivity mA;
        private final android.widget.EditText mEt;
        PasswordOkListener(StatementScanActivity a, android.widget.EditText et) {
            mA = a; mEt = et;
        }
        public void onClick(DialogInterface d, int w) {
            String pw = mEt.getText().toString();
            if (pw.isEmpty()) { mA.promptPassword(); return; }
            mA.onPasswordEntered(pw);
        }
    }

    static class PasswordCancelListener implements DialogInterface.OnClickListener {
        private final StatementScanActivity mA;
        PasswordCancelListener(StatementScanActivity a) { mA = a; }
        public void onClick(DialogInterface d, int w) { mA.finish(); }
    }
}
