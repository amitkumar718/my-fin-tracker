package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import android.os.Handler;
import android.os.Looper;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.ExtractionPattern;
import com.ldsa.myfintracker.db.PdfSource;
import com.ldsa.myfintracker.pdf.PdfDecryptor;
import com.ldsa.myfintracker.pdf.PdfTextExtractor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AddPdfPatternActivity extends Activity {

    public static final String EXTRA_SENDER_ID  = "sender_id";
    public static final String EXTRA_PATTERN_ID = "pattern_id";

    // Transaction tokens (shared with MapExpenseActivity)
    static final String TOK_AMOUNT_CR = "(/amount_cr/)";
    static final String TOK_AMOUNT_DB = "(/amount_db/)";
    static final String TOK_MERCHANT  = "(/merchant/)";
    static final String TOK_CARD      = "(/card/)";
    static final String TOK_ACNO      = "(/ac_no/)";
    static final String TOK_UPI       = "(/upi/)";
    static final String TOK_DATE      = "(/date/)";
    static final String TOK_TIME      = "(/time/)";
    static final String TOK_BALANCE   = "(/balance/)";
    static final String TOK_IGNORE    = "(/ignore/)";

    // PDF-specific tokens
    static final String TOK_NAME     = "(/name/)";
    static final String TOK_MONTH    = "(/month/)";
    static final String TOK_YEAR     = "(/year/)";

    static final String[] ALL_TOKENS = {
        TOK_AMOUNT_CR, TOK_AMOUNT_DB, TOK_MERCHANT, TOK_CARD, TOK_ACNO, TOK_UPI,
        TOK_DATE, TOK_TIME, TOK_BALANCE, TOK_IGNORE,
        TOK_NAME, TOK_MONTH, TOK_YEAR
    };

    private static final int REQ_PICK_LOCAL      = 601;
    private static final int REQ_PICK_DROPBOX    = 602;
    private static final int REQ_PICK_LINES      = 603;
    private static final int REQ_PICK_FROM_INBOX = 604;
    private static final int REQ_TEST_PICK_LOCAL   = 611;
    private static final int REQ_TEST_PICK_DROPBOX = 612;

    long             mSenderId  = -1L;
    long             mPatternId = -1L;
    ExpenseDatabase  mDb;

    EditText mEtPatternName;
    EditText mEtBankPat;
    EditText mEtPeriodPat;
    EditText mEtTransPat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_pdf_pattern);
        getWindow().setStatusBarColor(0xFF1976D2);

        mSenderId  = getIntent().getLongExtra(EXTRA_SENDER_ID,  -1L);
        mPatternId = getIntent().getLongExtra(EXTRA_PATTERN_ID, -1L);
        mDb = ExpenseDatabase.getInstance(this);

        mEtPatternName = (EditText) findViewById(R.id.etPatternName);
        mEtBankPat     = (EditText) findViewById(R.id.etBankPat);
        mEtPeriodPat   = (EditText) findViewById(R.id.etPeriodPat);
        mEtTransPat    = (EditText) findViewById(R.id.etTransPat);

        mEtBankPat.addTextChangedListener(new ChipColorWatcher());
        mEtPeriodPat.addTextChangedListener(new ChipColorWatcher());
        mEtTransPat.addTextChangedListener(new ChipColorWatcher());

        ((TextView) findViewById(R.id.btnBack)).setOnClickListener(new BackListener(this));
        ((Button) findViewById(R.id.btnPickFromStatement)).setOnClickListener(new PickListener(this));
        ((Button) findViewById(R.id.btnSavePattern)).setOnClickListener(new SaveListener(this));
        ((Button) findViewById(R.id.btnTestPattern)).setOnClickListener(new TestListener(this));
        Button btnDelete = (Button) findViewById(R.id.btnDeletePattern);
        if (mPatternId >= 0) {
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(new DeleteListener(this));
        }

        // Bank chips
        wireFieldChip(R.id.btnTokNameBank,    mEtBankPat,   TOK_NAME);

        // Period chips
        wireFieldChip(R.id.btnTokMonthPeriod, mEtPeriodPat, TOK_MONTH);
        wireFieldChip(R.id.btnTokYearPeriod,  mEtPeriodPat, TOK_YEAR);
        wireFieldChip(R.id.btnTokSkipPeriod,  mEtPeriodPat, TOK_IGNORE);

        if (mPatternId >= 0) populateFromPattern(mPatternId);

        // Trans chips
        wireFieldChip(R.id.btnTokAmountCr, mEtTransPat, TOK_AMOUNT_CR);
        wireFieldChip(R.id.btnTokAmountDb, mEtTransPat, TOK_AMOUNT_DB);
        wireFieldChip(R.id.btnTokMerchant, mEtTransPat, TOK_MERCHANT);
        wireFieldChip(R.id.btnTokCard,     mEtTransPat, TOK_CARD);
        wireFieldChip(R.id.btnTokAcNo,     mEtTransPat, TOK_ACNO);
        wireFieldChip(R.id.btnTokUpi,      mEtTransPat, TOK_UPI);
        wireFieldChip(R.id.btnTokDate,     mEtTransPat, TOK_DATE);
        wireFieldChip(R.id.btnTokTime,     mEtTransPat, TOK_TIME);
        wireFieldChip(R.id.btnTokBalance,  mEtTransPat, TOK_BALANCE);
        wireFieldChip(R.id.btnTokIgnore,   mEtTransPat, TOK_IGNORE);
    }

    void wireFieldChip(int id, EditText field, String token) {
        ((Button) findViewById(id)).setOnClickListener(new FieldChipListener(this, field, token));
    }

    void insertInto(EditText field, String token) {
        int start = Math.max(field.getSelectionStart(), 0);
        int end   = Math.max(field.getSelectionEnd(), 0);
        if (start > end) { int t = start; start = end; end = t; }
        android.text.Editable e = field.getText();
        if (end > e.length()) end = e.length();
        e.replace(start, end, token);
    }

    static class ChipColorWatcher implements android.text.TextWatcher {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
        public void afterTextChanged(android.text.Editable s) {
            for (android.text.style.BackgroundColorSpan sp :
                 s.getSpans(0, s.length(), android.text.style.BackgroundColorSpan.class)) {
                s.removeSpan(sp);
            }
            for (android.text.style.ForegroundColorSpan sp :
                 s.getSpans(0, s.length(), android.text.style.ForegroundColorSpan.class)) {
                s.removeSpan(sp);
            }
            String raw = s.toString();
            int pos = 0;
            while (pos < raw.length()) {
                String found = null;
                for (String tok : ALL_TOKENS) {
                    if (raw.startsWith(tok, pos)) { found = tok; break; }
                }
                if (found != null) {
                    int color = tokenColor(found);
                    s.setSpan(new android.text.style.BackgroundColorSpan(color),
                        pos, pos + found.length(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    s.setSpan(new android.text.style.ForegroundColorSpan(0xFFFFFFFF),
                        pos, pos + found.length(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    pos += found.length();
                } else {
                    pos++;
                }
            }
        }
    }

    static String regexToTokens(String regex, String defaultToken) {
        if (regex == null || regex.isEmpty()) return "";
        for (String tok : ALL_TOKENS) {
            if (regex.contains(tok)) return regex;
        }
        StringBuilder out = new StringBuilder();
        int pos = 0;
        while (pos < regex.length()) {
            // \Q...\E literal block → unquote
            if (pos + 2 <= regex.length()
                && regex.charAt(pos) == '\\' && regex.charAt(pos + 1) == 'Q') {
                int end = regex.indexOf("\\E", pos + 2);
                if (end < 0) break;
                out.append(regex, pos + 2, end);
                pos = end + 2;
                continue;
            }
            // .*? → (/ignore/)
            if (pos + 3 <= regex.length()
                && regex.charAt(pos) == '.' && regex.charAt(pos + 1) == '*'
                && regex.charAt(pos + 2) == '?') {
                out.append(TOK_IGNORE);
                pos += 3;
                continue;
            }
            // (...) with balanced parens → token (distinguish year vs default)
            if (regex.charAt(pos) == '(') {
                int depth = 1;
                int end = pos + 1;
                while (end < regex.length() && depth > 0) {
                    char ch = regex.charAt(end);
                    if (ch == '\\' && end + 1 < regex.length()) { end += 2; continue; }
                    if (ch == '(') depth++;
                    else if (ch == ')') { depth--; if (depth == 0) break; }
                    end++;
                }
                if (depth == 0) {
                    String content = regex.substring(pos + 1, end);
                    String token;
                    if ("\\d{4}".equals(content)) token = TOK_YEAR;
                    else                           token = defaultToken;
                    out.append(token);
                    pos = end + 1;
                    continue;
                }
            }
            out.append(regex.charAt(pos));
            pos++;
        }
        return out.toString();
    }

    void populateFromPattern(long id) {
        ExtractionPattern p = mDb.getPatternById(id);
        if (p == null) return;
        mEtPatternName.setText(p.name != null ? p.name : "");

        String bank   = (p.bankNamePat != null && !p.bankNamePat.isEmpty())
                        ? p.bankNamePat : nullToEmpty(mDb.getPdfFieldPattern("bank"));
        String period = (p.periodPat   != null && !p.periodPat.isEmpty())
                        ? p.periodPat  : nullToEmpty(mDb.getPdfFieldPattern("month"));
        mEtBankPat.setText(regexToTokens(bank,   TOK_NAME));
        mEtPeriodPat.setText(regexToTokens(period, TOK_MONTH));
        mEtTransPat.setText(p.templateText != null ? p.templateText : "");
    }


    static String tokenLabel(String token) {
        if (TOK_AMOUNT_CR.equals(token)) return "AMT CR";
        if (TOK_AMOUNT_DB.equals(token)) return "AMT DB";
        if (TOK_MERCHANT.equals(token)) return "MERCHANT";
        if (TOK_CARD.equals(token))     return "CARD";
        if (TOK_ACNO.equals(token))     return "AC.NO";
        if (TOK_UPI.equals(token))      return "UPI";
        if (TOK_DATE.equals(token))     return "DATE";
        if (TOK_TIME.equals(token))     return "TIME";
        if (TOK_BALANCE.equals(token))  return "BAL";
        if (TOK_IGNORE.equals(token))   return "SKIP";
        return token;
    }

    static int tokenColor(String token) {
        if (TOK_AMOUNT_CR.equals(token)) return 0xFF2E7D32; // deeper green = credit
        if (TOK_AMOUNT_DB.equals(token)) return 0xFFC62828; // red = debit
        if (TOK_BALANCE.equals(token))  return 0xFF56B4E9;
        if (TOK_MERCHANT.equals(token)) return 0xFF0072B2;
        if (TOK_CARD.equals(token))     return 0xFF0072B2;
        if (TOK_ACNO.equals(token))     return 0xFF0072B2;
        if (TOK_UPI.equals(token))      return 0xFF0072B2;
        if (TOK_DATE.equals(token))     return 0xFFD55E00;
        if (TOK_TIME.equals(token))     return 0xFFCC79A7;
        if (TOK_NAME.equals(token))     return 0xFF0072B2;
        if (TOK_MONTH.equals(token))    return 0xFFD55E00;
        if (TOK_YEAR.equals(token))     return 0xFFD55E00;
        if (TOK_IGNORE.equals(token))   return 0xFF999999;
        return 0xFF999999;
    }

    static String nullToEmpty(String s) { return s != null ? s : ""; }

    void pickFromStatement() {
        if (mSenderId < 0) {
            Toast.makeText(this, R.string.msg_save_bank_first, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, PdfInboxActivity.class);
        i.putExtra(PdfInboxActivity.EXTRA_SENDER_ID, mSenderId);
        i.putExtra(PdfInboxActivity.EXTRA_PICK_MODE, true);
        startActivityForResult(i, REQ_PICK_FROM_INBOX);
    }

    void openLocalPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,
            new String[]{"application/pdf", "text/plain"});
        startActivityForResult(i, REQ_PICK_LOCAL);
    }

    void openDropboxPicker() {
        Intent i = new Intent(this, DropboxPdfInboxActivity.class);
        i.putExtra(DropboxPdfInboxActivity.EXTRA_SENDER_ID, mSenderId);
        i.putExtra(DropboxPdfInboxActivity.EXTRA_PICK_MODE, true);
        startActivityForResult(i, REQ_PICK_DROPBOX);
    }

    void openLinePicker(Uri uri, boolean isPdf) {
        String password = null;
        if (mSenderId > 0) {
            password = getSharedPreferences("fin_prefs", MODE_PRIVATE)
                .getString("pdf_pass_" + mSenderId, null);
        }
        Intent i = new Intent(this, PdfLinePickerActivity.class);
        i.putExtra(PdfLinePickerActivity.EXTRA_URI,    uri.toString());
        i.putExtra(PdfLinePickerActivity.EXTRA_IS_PDF, isPdf);
        if (password != null && !password.isEmpty()) {
            i.putExtra(PdfLinePickerActivity.EXTRA_PASSWORD, password);
        }
        startActivityForResult(i, REQ_PICK_LINES);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;

        if (req == REQ_PICK_LOCAL) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {}
            String name = uri.getLastPathSegment();
            boolean isPdf = name != null && name.toLowerCase().endsWith(".pdf");
            openLinePicker(uri, isPdf);

        } else if (req == REQ_PICK_DROPBOX) {
            String uriStr = data.getStringExtra(DropboxPdfInboxActivity.EXTRA_FILE_URI);
            if (uriStr == null) return;
            openLinePicker(Uri.parse(uriStr), true);

        } else if (req == REQ_PICK_LINES) {
            String bankLine   = data.getStringExtra(PdfLinePickerActivity.EXTRA_BANK_LINE);
            String periodLine = data.getStringExtra(PdfLinePickerActivity.EXTRA_PERIOD_LINE);
            String transLine  = data.getStringExtra(PdfLinePickerActivity.EXTRA_TRANS_LINE);
            if (bankLine   != null) mEtBankPat.setText(bankLine);
            if (periodLine != null) mEtPeriodPat.setText(periodLine);
            if (transLine  != null) mEtTransPat.setText(transLine);

        } else if (req == REQ_PICK_FROM_INBOX) {
            String uriStr = data.getStringExtra(PdfInboxActivity.EXTRA_FILE_URI);
            if (uriStr == null) return;
            Uri uri = Uri.parse(uriStr);
            String name = uri.getLastPathSegment();
            boolean isPdf = name == null || name.toLowerCase().endsWith(".pdf")
                || "application/pdf".equals(getContentResolver().getType(uri));
            openLinePicker(uri, isPdf);

        } else if (req == REQ_TEST_PICK_LOCAL) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {}
            runTestOnPdf(uri);

        } else if (req == REQ_TEST_PICK_DROPBOX) {
            String uriStr = data.getStringExtra(DropboxPdfInboxActivity.EXTRA_FILE_URI);
            if (uriStr == null) return;
            runTestOnPdf(Uri.parse(uriStr));
        }
    }

    void savePattern() {
        String transRaw  = mEtTransPat.getText().toString();
        String bankRaw   = mEtBankPat.getText().toString();
        String periodRaw = mEtPeriodPat.getText().toString();
        if (transRaw.trim().isEmpty()) {
            Toast.makeText(this, R.string.msg_trans_pat_required, Toast.LENGTH_SHORT).show();
            return;
        }

        ExtractionPattern p = buildPatternFromTemplate(transRaw.trim());
        p.senderId    = mSenderId;
        p.isPdf       = true;
        p.bankNamePat = toRegexIfTokens(bankRaw);
        p.periodPat   = toRegexIfTokens(periodRaw);

        String name = mEtPatternName.getText().toString().trim();
        if (name.isEmpty()) {
            name = "PDF Pattern " + new java.text.SimpleDateFormat("dd MMM",
                java.util.Locale.getDefault()).format(new java.util.Date());
        }
        p.name = name;

        boolean isUpdate = mPatternId >= 0;
        if (isUpdate) {
            p.id = mPatternId;
            mDb.updatePattern(p);
        } else {
            p.id = mDb.insertPattern(p);
        }
        Toast.makeText(this, R.string.msg_pattern_saved, Toast.LENGTH_SHORT).show();

        if (isUpdate) {
            int priorCount = mDb.countExpensesByPattern(p.id);
            if (priorCount > 0) {
                final ExtractionPattern pFinal = p;
                final int countFinal = priorCount;
                new AlertDialog.Builder(this, R.style.RoundedDialog)
                    .setTitle("Pattern updated")
                    .setMessage("Re-apply to " + countFinal + " existing expense"
                        + (countFinal == 1 ? "" : "s") + "? Non-matching rows will be orphaned.")
                    .setPositiveButton("Re-apply", new ReApplyDialogListener(this, pFinal))
                    .setNegativeButton("Skip", new SkipDialogListener(this))
                    .setCancelable(false)
                    .show();
                return;
            }
        }
        setResult(RESULT_OK);
        finish();
    }

    void confirmDeletePattern() {
        if (mPatternId < 0) return;
        new AlertDialog.Builder(this, R.style.RoundedDialog)
            .setMessage("Delete this pattern? Linked expenses stay but are orphaned (pattern_id = -1).")
            .setPositiveButton(android.R.string.ok, new DeleteConfirmListener(this))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void deletePattern() {
        if (mPatternId < 0) return;
        int orphaned = mDb.orphanExpensesByPattern(mPatternId);
        mDb.deletePattern(mPatternId);
        Toast.makeText(this,
            orphaned > 0
                ? "Pattern deleted; " + orphaned + " expense"
                    + (orphaned == 1 ? "" : "s") + " orphaned"
                : "Pattern deleted",
            Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    void startReApply(ExtractionPattern p) {
        Toast.makeText(this, "Updating expenses…", Toast.LENGTH_SHORT).show();
        new ReApplyThread(this, p, mDb,
            new android.os.Handler(android.os.Looper.getMainLooper())).start();
    }

    void onReApplyDone(int updated, int orphaned, int adopted) {
        StringBuilder sb = new StringBuilder();
        sb.append(updated).append(" expense").append(updated == 1 ? "" : "s").append(" updated");
        if (adopted > 0) {
            sb.append(", ").append(adopted).append(" orphan").append(adopted == 1 ? "" : "s").append(" re-adopted");
        }
        if (orphaned > 0) {
            sb.append(", ").append(orphaned).append(" orphaned (no longer match)");
        }
        Toast.makeText(this, sb.toString(), Toast.LENGTH_LONG).show();
        setResult(RESULT_OK);
        finish();
    }

    // TODO: adapt StatementScanActivity to accept draft pattern + URI as extras
    //       and reuse its candidate list for test results, removing TestRunnable /
    //       TestResult / TestResultRunnable in this file.
    void testPattern() {
        if (mEtTransPat.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, R.string.msg_trans_pat_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mSenderId < 0) {
            Toast.makeText(this, R.string.msg_save_bank_first, Toast.LENGTH_SHORT).show();
            return;
        }
        List<PdfSource> sources = mDb.getPdfSourcesBySender(mSenderId);
        boolean hasDropbox = false;
        for (PdfSource s : sources) {
            if (s.isDropbox) { hasDropbox = true; break; }
        }
        if (hasDropbox) {
            new AlertDialog.Builder(this, R.style.RoundedDialog)
                .setTitle(R.string.title_pick_source)
                .setItems(new String[]{
                    getString(R.string.btn_from_phone),
                    getString(R.string.btn_from_dropbox)
                }, new TestPickSourceDialogListener(this))
                .show();
        } else {
            openTestLocalPicker();
        }
    }

    void openTestLocalPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,
            new String[]{"application/pdf", "text/plain"});
        startActivityForResult(i, REQ_TEST_PICK_LOCAL);
    }

    void openTestDropboxPicker() {
        Intent i = new Intent(this, DropboxPdfInboxActivity.class);
        i.putExtra(DropboxPdfInboxActivity.EXTRA_SENDER_ID, mSenderId);
        i.putExtra(DropboxPdfInboxActivity.EXTRA_PICK_MODE, true);
        startActivityForResult(i, REQ_TEST_PICK_DROPBOX);
    }

    void runTestOnPdf(Uri uri) {
        String password = getSharedPreferences("fin_prefs", MODE_PRIVATE)
            .getString("pdf_pass_" + mSenderId, null);
        ExtractionPattern transPat = buildPatternFromTemplate(mEtTransPat.getText().toString().trim());
        String bankRegex   = toRegexIfTokens(mEtBankPat.getText().toString());
        String periodRegex = toRegexIfTokens(mEtPeriodPat.getText().toString());
        Toast.makeText(this, "Testing…", Toast.LENGTH_SHORT).show();
        Handler h = new Handler(Looper.getMainLooper());
        new Thread(new TestRunnable(this, h, uri, transPat, bankRegex, periodRegex, password)).start();
    }

    String toRegexIfTokens(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        for (String tok : ALL_TOKENS) {
            if (s.contains(tok)) return buildPatternFromTemplate(s).templateRegex;
        }
        return s;
    }

    ExtractionPattern buildPatternFromTemplate(String template) {
        ExtractionPattern p = new ExtractionPattern();
        p.templateText    = template;
        p.transactionType = ExtractionPattern.TYPE_UPI;

        template = template.trim();
        StringBuilder regex  = new StringBuilder();
        int pos              = 0;
        int groupNum         = 0;
        StringBuilder litBuf = new StringBuilder();

        while (pos < template.length()) {
            String found = null;
            for (String tok : ALL_TOKENS) {
                if (template.startsWith(tok, pos)) { found = tok; break; }
            }
            if (found != null) {
                if (litBuf.length() > 0) {
                    regex.append(Pattern.quote(litBuf.toString()));
                    litBuf.setLength(0);
                }
                if (TOK_IGNORE.equals(found)) {
                    regex.append(".*?");
                } else {
                    groupNum++;
                    regex.append(captureGroupFor(tokenToFieldType(found)));
                    assignGroup(p, found, groupNum);
                }
                pos += found.length();
            } else {
                litBuf.append(template.charAt(pos));
                pos++;
            }
        }
        if (litBuf.length() > 0) regex.append(Pattern.quote(litBuf.toString()));
        p.templateRegex = regex.toString();
        return p;
    }

    private String tokenToFieldType(String token) {
        if (TOK_AMOUNT_CR.equals(token)) return "amount";
        if (TOK_AMOUNT_DB.equals(token)) return "amount";
        if (TOK_BALANCE.equals(token))  return "balance";
        if (TOK_MERCHANT.equals(token)) return "merchant";
        if (TOK_CARD.equals(token))     return "card";
        if (TOK_ACNO.equals(token))     return "account";
        if (TOK_UPI.equals(token))      return "upi";
        if (TOK_DATE.equals(token))     return "date";
        if (TOK_TIME.equals(token))     return "time";
        if (TOK_NAME.equals(token))     return "name";
        if (TOK_MONTH.equals(token))    return "month";
        if (TOK_YEAR.equals(token))     return "year";
        return "text";
    }

    private void assignGroup(ExtractionPattern p, String token, int group) {
        if (TOK_AMOUNT_CR.equals(token)) { p.amountCrGroup = group; return; }
        if (TOK_AMOUNT_DB.equals(token)) { p.amountDbGroup = group; return; }
        if (TOK_BALANCE.equals(token))  { p.balanceGroup  = group; return; }
        if (TOK_MERCHANT.equals(token)) { p.merchantGroup = group; return; }
        if (TOK_CARD.equals(token))     { p.cardGroup     = group; return; }
        if (TOK_ACNO.equals(token))     { p.accountGroup  = group; return; }
        if (TOK_DATE.equals(token))     { p.dateGroup     = group; return; }
        if (TOK_TIME.equals(token))     { p.timeGroup     = group; return; }
        // name/month/year are structural — no group slot in ExtractionPattern
    }

    private String captureGroupFor(String fieldType) {
        if ("amount".equals(fieldType) || "balance".equals(fieldType))
            return "([\\d,]+\\.?\\d{0,2})";
        if ("card".equals(fieldType))    return "(\\d{4})";
        if ("upi".equals(fieldType))     return "([\\w@.\\-]+)";
        if ("account".equals(fieldType)) return "([X\\dx*]+)";
        if ("date".equals(fieldType))
            return "(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}|\\d{1,2}[\\- ][A-Za-z]{3}[\\- ]\\d{2,4})";
        if ("time".equals(fieldType))
            return "(\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s?[AP]M)?)";
        if ("name".equals(fieldType))    return "([A-Za-z][A-Za-z ]+)";
        if ("month".equals(fieldType))
            return "(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?|\\d{1,2})";
        if ("year".equals(fieldType))    return "(\\d{4})";
        return "([^\\n]+?)";
    }

    // ── Static listeners ──────────────────────────────────────────────────────

    static class BackListener implements View.OnClickListener {
        private final AddPdfPatternActivity mA;
        BackListener(AddPdfPatternActivity a) { mA = a; }
        public void onClick(View v) { mA.finish(); }
    }

    static class PickListener implements View.OnClickListener {
        private final AddPdfPatternActivity mA;
        PickListener(AddPdfPatternActivity a) { mA = a; }
        public void onClick(View v) { mA.pickFromStatement(); }
    }

    static class DeleteListener implements View.OnClickListener {
        private final AddPdfPatternActivity mA;
        DeleteListener(AddPdfPatternActivity a) { mA = a; }
        public void onClick(View v) { mA.confirmDeletePattern(); }
    }

    static class DeleteConfirmListener implements android.content.DialogInterface.OnClickListener {
        private final AddPdfPatternActivity mA;
        DeleteConfirmListener(AddPdfPatternActivity a) { mA = a; }
        public void onClick(android.content.DialogInterface d, int w) { mA.deletePattern(); }
    }

    static class SaveListener implements View.OnClickListener {
        private final AddPdfPatternActivity mA;
        SaveListener(AddPdfPatternActivity a) { mA = a; }
        public void onClick(View v) { mA.savePattern(); }
    }

    static class FieldChipListener implements View.OnClickListener {
        private final AddPdfPatternActivity mA;
        private final EditText mField;
        private final String   mToken;
        FieldChipListener(AddPdfPatternActivity a, EditText field, String token) {
            mA = a; mField = field; mToken = token;
        }
        public void onClick(View v) { mA.insertInto(mField, mToken); }
    }

    static class PickSourceDialogListener implements DialogInterface.OnClickListener {
        private final AddPdfPatternActivity mA;
        PickSourceDialogListener(AddPdfPatternActivity a) { mA = a; }
        public void onClick(DialogInterface d, int which) {
            if (which == 0) mA.openLocalPicker();
            else            mA.openDropboxPicker();
        }
    }

    static class TestListener implements View.OnClickListener {
        private final AddPdfPatternActivity mA;
        TestListener(AddPdfPatternActivity a) { mA = a; }
        public void onClick(View v) { mA.testPattern(); }
    }

    static class TestPickSourceDialogListener implements DialogInterface.OnClickListener {
        private final AddPdfPatternActivity mA;
        TestPickSourceDialogListener(AddPdfPatternActivity a) { mA = a; }
        public void onClick(DialogInterface d, int which) {
            if (which == 0) mA.openTestLocalPicker();
            else            mA.openTestDropboxPicker();
        }
    }

    static class TestResult {
        String       bank    = "";
        String       period  = "";
        List<String> matches = new ArrayList<String>();
    }

    static class TestRunnable implements Runnable {
        private final AddPdfPatternActivity mA;
        private final Handler               mH;
        private final Uri                   mUri;
        private final ExtractionPattern     mTransPat;
        private final String                mBankRegex;
        private final String                mPeriodRegex;
        private final String                mPassword;

        TestRunnable(AddPdfPatternActivity a, Handler h, Uri uri,
                     ExtractionPattern transPat, String bankRegex, String periodRegex,
                     String password) {
            mA = a; mH = h; mUri = uri; mTransPat = transPat;
            mBankRegex = bankRegex; mPeriodRegex = periodRegex; mPassword = password;
        }

        public void run() {
            String text;
            try {
                InputStream is = mA.getContentResolver().openInputStream(mUri);
                if (is == null) { post(null, "Could not open PDF"); return; }
                text = PdfTextExtractor.extract(is, mPassword);
                is.close();
            } catch (Exception e) {
                post(null, "Error reading PDF: " + e.getMessage());
                return;
            }
            if (PdfDecryptor.NEEDS_PASSWORD.equals(text)) {
                post(null, "PDF is password-protected; save the password in Bank Config first.");
                return;
            }
            if (text == null || text.trim().isEmpty()) {
                post(null, "No text extracted from PDF");
                return;
            }

            List<String> lines = PdfInboxActivity.splitLines(text);
            TestResult tr = new TestResult();
            tr.bank   = applyField(mBankRegex,   lines);
            tr.period = applyPeriodField(mPeriodRegex, lines);

            for (String line : lines) {
                if (!mTransPat.matches(line)) continue;
                String date   = mTransPat.dateGroup     > 0 ? mTransPat.extractGroup(line, mTransPat.dateGroup).trim()     : "";
                String amt    = "";
                if (mTransPat.amountCrGroup > 0) amt = mTransPat.extractGroup(line, mTransPat.amountCrGroup).trim();
                if (amt.isEmpty() && mTransPat.amountDbGroup > 0) amt = mTransPat.extractGroup(line, mTransPat.amountDbGroup).trim();
                if (amt.isEmpty() && mTransPat.amountGroup   > 0) amt = mTransPat.extractGroup(line, mTransPat.amountGroup).trim();
                String merch  = mTransPat.merchantGroup > 0 ? mTransPat.extractGroup(line, mTransPat.merchantGroup).trim() : "";
                StringBuilder row = new StringBuilder();
                if (!date.isEmpty())  { row.append(date);  row.append("  "); }
                if (!merch.isEmpty()) { row.append(merch); row.append("  "); }
                if (!amt.isEmpty())   { row.append("₹").append(amt); }
                if (row.length() == 0) row.append(line);
                tr.matches.add(row.toString());
                if (tr.matches.size() >= 20) break;
            }
            post(tr, null);
        }

        private static java.util.regex.Matcher findMatch(String regex, List<String> lines) {
            if (regex == null || regex.isEmpty()) return null;
            try {
                Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                for (String line : lines) {
                    java.util.regex.Matcher m = p.matcher(line.trim());
                    if (m.find()) return m;
                }
            } catch (Exception ignored) {}
            return null;
        }

        private static String applyField(String regex, List<String> lines) {
            java.util.regex.Matcher m = findMatch(regex, lines);
            if (m == null) return "";
            if (m.groupCount() > 0 && m.group(1) != null) return m.group(1).trim();
            return m.group(0).trim();
        }

        private static String applyPeriodField(String regex, List<String> lines) {
            java.util.regex.Matcher m = findMatch(regex, lines);
            if (m == null) return "";
            String month = null, year = null;
            for (int i = 1; i <= m.groupCount(); i++) {
                String g = m.group(i);
                if (g == null) continue;
                g = g.trim();
                if (year == null && g.matches("\\d{4}")) year = g;
                else if (month == null) month = normalizeMonth(g);
            }
            if (year != null && month != null) return year + "-" + month;
            if (year != null) return year;
            if (month != null) return month;
            return m.group(0).trim();
        }

        private static String normalizeMonth(String s) {
            if (s == null) return "";
            if (s.matches("\\d{1,2}")) {
                int n = Integer.parseInt(s);
                return n < 10 ? "0" + n : String.valueOf(n);
            }
            String lower = s.toLowerCase(java.util.Locale.US);
            String[] names = {"jan","feb","mar","apr","may","jun",
                              "jul","aug","sep","oct","nov","dec"};
            for (int i = 0; i < names.length; i++) {
                if (lower.startsWith(names[i])) {
                    int n = i + 1;
                    return n < 10 ? "0" + n : String.valueOf(n);
                }
            }
            return s;
        }

        private void post(TestResult tr, String error) {
            mH.post(new TestResultRunnable(mA, tr, error));
        }
    }

    static class TestResultRunnable implements Runnable {
        private final AddPdfPatternActivity mA;
        private final TestResult            mResult;
        private final String                mError;

        TestResultRunnable(AddPdfPatternActivity a, TestResult result, String error) {
            mA = a; mResult = result; mError = error;
        }

        public void run() {
            if (mA.isFinishing()) return;
            if (mError != null) {
                new AlertDialog.Builder(mA, R.style.RoundedDialog)
                    .setTitle("Test failed")
                    .setMessage(mError)
                    .setPositiveButton("Close", null)
                    .show();
                return;
            }
            StringBuilder body = new StringBuilder();
            body.append("Bank:   ")
                .append(mResult.bank.isEmpty()   ? "(not found)" : mResult.bank).append("\n");
            body.append("Period: ")
                .append(mResult.period.isEmpty() ? "(not found)" : mResult.period).append("\n\n");

            if (mResult.matches.isEmpty()) {
                body.append("No transactions matched.");
            } else {
                body.append("Matched ")
                    .append(mResult.matches.size())
                    .append(mResult.matches.size() == 20 ? "+" : "")
                    .append(" transactions:\n\n");
                for (String row : mResult.matches) body.append(row).append("\n");
            }

            new AlertDialog.Builder(mA, R.style.RoundedDialog)
                .setTitle("Test result")
                .setMessage(body.toString().trim())
                .setPositiveButton("Close", null)
                .show();
        }
    }

    static class ReApplyDialogListener implements android.content.DialogInterface.OnClickListener {
        private final AddPdfPatternActivity mA;
        private final ExtractionPattern     mP;
        ReApplyDialogListener(AddPdfPatternActivity a, ExtractionPattern p) { mA = a; mP = p; }
        public void onClick(android.content.DialogInterface d, int w) { mA.startReApply(mP); }
    }

    static class SkipDialogListener implements android.content.DialogInterface.OnClickListener {
        private final AddPdfPatternActivity mA;
        SkipDialogListener(AddPdfPatternActivity a) { mA = a; }
        public void onClick(android.content.DialogInterface d, int w) {
            mA.setResult(RESULT_OK);
            mA.finish();
        }
    }

    static class ReApplyThread extends Thread {
        private final AddPdfPatternActivity mA;
        private final ExtractionPattern     mP;
        private final ExpenseDatabase       mDb;
        private final android.os.Handler    mH;
        ReApplyThread(AddPdfPatternActivity a, ExtractionPattern p,
                      ExpenseDatabase db, android.os.Handler h) {
            mA = a; mP = p; mDb = db; mH = h;
        }
        public void run() {
            ExpenseDatabase.ReApplyResult r = mDb.reApplyPattern(mP);
            mH.post(new ReApplyDoneRunnable(mA, r.updated, r.orphaned, r.adopted));
        }
    }

    static class ReApplyDoneRunnable implements Runnable {
        private final AddPdfPatternActivity mA;
        private final int                   mUpdated;
        private final int                   mOrphaned;
        private final int                   mAdopted;
        ReApplyDoneRunnable(AddPdfPatternActivity a, int u, int o, int ad) {
            mA = a; mUpdated = u; mOrphaned = o; mAdopted = ad;
        }
        public void run() {
            if (!mA.isFinishing()) mA.onReApplyDone(mUpdated, mOrphaned, mAdopted);
        }
    }
}
