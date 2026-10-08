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

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.ExtractionPattern;
import com.ldsa.myfintracker.db.PdfSource;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AddPdfPatternActivity extends Activity {

    public static final String EXTRA_SENDER_ID  = "sender_id";
    public static final String EXTRA_PATTERN_ID = "pattern_id";

    // Transaction tokens (shared with MapExpenseActivity)
    static final String TOK_AMOUNT   = "(/amount/)";
    static final String TOK_MERCHANT = "(/merchant/)";
    static final String TOK_CARD     = "(/card/)";
    static final String TOK_ACNO     = "(/ac_no/)";
    static final String TOK_UPI      = "(/upi/)";
    static final String TOK_DATE     = "(/date/)";
    static final String TOK_TIME     = "(/time/)";
    static final String TOK_BALANCE  = "(/balance/)";
    static final String TOK_IGNORE   = "(/ignore/)";

    // PDF-specific tokens
    static final String TOK_NAME     = "(/name/)";
    static final String TOK_MONTH    = "(/month/)";
    static final String TOK_YEAR     = "(/year/)";

    static final String[] ALL_TOKENS = {
        TOK_AMOUNT, TOK_MERCHANT, TOK_CARD, TOK_ACNO, TOK_UPI,
        TOK_DATE, TOK_TIME, TOK_BALANCE, TOK_IGNORE,
        TOK_NAME, TOK_MONTH, TOK_YEAR
    };

    private static final int REQ_PICK_LOCAL   = 601;
    private static final int REQ_PICK_DROPBOX = 602;
    private static final int REQ_PICK_LINES   = 603;

    long             mSenderId  = -1L;
    long             mPatternId = -1L;
    ExpenseDatabase  mDb;

    EditText mEtPatternName;
    EditText mEtBankPat;
    EditText mEtPeriodPat;
    EditText mEtTransPat;

    String mRawBankPat   = "";
    String mRawPeriodPat = "";
    String mRawTransPat  = "";

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

        ((TextView) findViewById(R.id.btnBack)).setOnClickListener(new BackListener(this));
        ((Button) findViewById(R.id.btnPickFromStatement)).setOnClickListener(new PickListener(this));
        ((Button) findViewById(R.id.btnSavePattern)).setOnClickListener(new SaveListener(this));

        // Bank chips
        wireFieldChip(R.id.btnTokNameBank,    mEtBankPat,   TOK_NAME);

        // Period chips
        wireFieldChip(R.id.btnTokMonthPeriod, mEtPeriodPat, TOK_MONTH);
        wireFieldChip(R.id.btnTokYearPeriod,  mEtPeriodPat, TOK_YEAR);
        wireFieldChip(R.id.btnTokSkipPeriod,  mEtPeriodPat, TOK_IGNORE);

        if (mPatternId >= 0) populateFromPattern(mPatternId);

        // Trans chips
        wireFieldChip(R.id.btnTokAmount,   mEtTransPat, TOK_AMOUNT);
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
        String raw = getRaw(field);
        int start = Math.max(field.getSelectionStart(), 0);
        int end   = Math.max(field.getSelectionEnd(), 0);
        if (start > end) { int t = start; start = end; end = t; }
        if (end > raw.length()) end = raw.length();
        raw = raw.substring(0, start) + token + raw.substring(end);
        setRaw(field, raw);
        renderField(field);
    }

    String getRaw(EditText field) {
        if (field == mEtBankPat)   return mRawBankPat;
        if (field == mEtPeriodPat) return mRawPeriodPat;
        return mRawTransPat;
    }

    void setRaw(EditText field, String raw) {
        if (field == mEtBankPat)        mRawBankPat   = raw;
        else if (field == mEtPeriodPat) mRawPeriodPat = raw;
        else                            mRawTransPat  = raw;
    }

    void renderField(EditText field) {
        if (field == mEtBankPat)
            field.setText(renderPatternAsChips(mRawBankPat,   "NAME",   0xFF0072B2));
        else if (field == mEtPeriodPat)
            field.setText(renderPatternAsChips(mRawPeriodPat, "PERIOD", 0xFFD55E00));
        else
            field.setText(renderTokenTemplate(mRawTransPat));
    }

    static CharSequence renderPatternAsChips(String pat, String label, int color) {
        if (pat == null || pat.isEmpty()) return "";
        for (String tok : ALL_TOKENS) {
            if (pat.contains(tok)) return renderTokenTemplate(pat);
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "\\\\Q(.*?)\\\\E|\\(([^)]+)\\)").matcher(pat);
        StringBuilder display = new StringBuilder();
        List<int[]> chips = new ArrayList<int[]>();
        while (m.find()) {
            if (m.group(1) != null) {
                display.append(m.group(1));
            } else {
                int s = display.length();
                display.append(label);
                chips.add(new int[]{s, display.length()});
            }
        }
        if (display.length() == 0) return pat;
        android.text.SpannableString ss = new android.text.SpannableString(display.toString());
        for (int[] c : chips) {
            ss.setSpan(new android.text.style.BackgroundColorSpan(color), c[0], c[1],
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new android.text.style.ForegroundColorSpan(0xFFFFFFFF), c[0], c[1],
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ss;
    }

    static CharSequence renderTokenTemplate(String pat) {
        if (pat == null || pat.isEmpty()) return "";
        StringBuilder display = new StringBuilder();
        List<int[]> chips = new ArrayList<int[]>();
        List<Integer> colors = new ArrayList<Integer>();
        int pos = 0;
        while (pos < pat.length()) {
            String found = null;
            for (String tok : ALL_TOKENS) {
                if (pat.startsWith(tok, pos)) { found = tok; break; }
            }
            if (found != null) {
                int s = display.length();
                display.append(tokenLabel(found));
                chips.add(new int[]{s, display.length()});
                colors.add(tokenColor(found));
                pos += found.length();
            } else {
                display.append(pat.charAt(pos++));
            }
        }
        android.text.SpannableString ss = new android.text.SpannableString(display.toString());
        for (int i = 0; i < chips.size(); i++) {
            int[] c = chips.get(i);
            ss.setSpan(new android.text.style.BackgroundColorSpan(colors.get(i)), c[0], c[1],
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new android.text.style.ForegroundColorSpan(0xFFFFFFFF), c[0], c[1],
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ss;
    }

    void populateFromPattern(long id) {
        ExtractionPattern p = mDb.getPatternById(id);
        if (p == null) return;
        mEtPatternName.setText(p.name != null ? p.name : "");

        mRawBankPat   = (p.bankNamePat != null && !p.bankNamePat.isEmpty())
                        ? p.bankNamePat : nullToEmpty(mDb.getPdfFieldPattern("bank"));
        mRawPeriodPat = (p.periodPat   != null && !p.periodPat.isEmpty())
                        ? p.periodPat  : nullToEmpty(mDb.getPdfFieldPattern("month"));
        mRawTransPat  = p.templateText != null ? p.templateText : "";
        renderField(mEtBankPat);
        renderField(mEtPeriodPat);
        renderField(mEtTransPat);
    }


    static String tokenLabel(String token) {
        if (TOK_AMOUNT.equals(token))   return "AMT";
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
        if (TOK_AMOUNT.equals(token))   return 0xFF009E73;
        if (TOK_BALANCE.equals(token))  return 0xFF56B4E9;
        if (TOK_MERCHANT.equals(token)) return 0xFF0072B2;
        if (TOK_CARD.equals(token))     return 0xFF0072B2;
        if (TOK_ACNO.equals(token))     return 0xFF0072B2;
        if (TOK_UPI.equals(token))      return 0xFF0072B2;
        if (TOK_DATE.equals(token))     return 0xFFD55E00;
        if (TOK_TIME.equals(token))     return 0xFFCC79A7;
        if (TOK_IGNORE.equals(token))   return 0xFF999999;
        return 0xFF999999;
    }

    static String nullToEmpty(String s) { return s != null ? s : ""; }

    void pickFromStatement() {
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
                }, new PickSourceDialogListener(this))
                .show();
        } else {
            openLocalPicker();
        }
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
            if (bankLine   != null) { mRawBankPat   = bankLine;   renderField(mEtBankPat); }
            if (periodLine != null) { mRawPeriodPat = periodLine; renderField(mEtPeriodPat); }
            if (transLine  != null) { mRawTransPat  = transLine;  renderField(mEtTransPat); }
        }
    }

    void savePattern() {
        if (mRawTransPat.trim().isEmpty()) {
            Toast.makeText(this, R.string.msg_trans_pat_required, Toast.LENGTH_SHORT).show();
            return;
        }

        ExtractionPattern p = buildPatternFromTemplate(mRawTransPat.trim());
        p.senderId    = mSenderId;
        p.isPdf       = true;
        p.bankNamePat = mRawBankPat.trim();
        p.periodPat   = mRawPeriodPat.trim();

        String name = mEtPatternName.getText().toString().trim();
        if (name.isEmpty()) {
            name = "PDF Pattern " + new java.text.SimpleDateFormat("dd MMM",
                java.util.Locale.getDefault()).format(new java.util.Date());
        }
        p.name = name;

        if (mPatternId >= 0) {
            p.id = mPatternId;
            mDb.updatePattern(p);
        } else {
            mDb.insertPattern(p);
        }
        Toast.makeText(this, R.string.msg_pattern_saved, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
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
        if (TOK_AMOUNT.equals(token))   return "amount";
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
        if (TOK_AMOUNT.equals(token))   { p.amountGroup   = group; return; }
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
}
