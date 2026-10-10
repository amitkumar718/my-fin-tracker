package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.ExtractionPattern;
import com.ldsa.myfintracker.db.PdfStatement;
import com.ldsa.myfintracker.db.SenderConfig;
import com.ldsa.myfintracker.pdf.PdfDecryptor;
import com.ldsa.myfintracker.pdf.PdfTextExtractor;
import com.ldsa.myfintracker.sms.SmsReader;

import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MapExpenseActivity extends Activity {

    public static final String EXTRA_SENDER_ADDRESS   = "sms_address";
    public static final String EXTRA_TRANS_LINE      = "sms_body";
    public static final String EXTRA_SOURCE_DATE      = "sms_date";
    public static final String EXTRA_SENDER_ID     = "sender_id";
    /** When true the template EditText starts blank instead of pre-filled with the body text. */
    public static final String EXTRA_BLANK_TEMPLATE = "blank_template";
    /** When true the saved pattern is tagged as a PDF pattern (is_pdf=1). */
    public static final String EXTRA_IS_PDF = "is_pdf";
    /** PDF statement ID — when set, the PDF picker section is shown. */
    public static final String EXTRA_STATEMENT_ID = "statement_id";

    // token labels inserted into the SMS template
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

    static final String[] ALL_TOKENS = {
        TOK_AMOUNT_CR, TOK_AMOUNT_DB, TOK_MERCHANT, TOK_CARD, TOK_ACNO, TOK_UPI,
        TOK_DATE, TOK_TIME, TOK_BALANCE, TOK_IGNORE
    };

    static final String[] TYPE_VALUES = {
        "", "UPI", "CARD_ONLINE", "CARD_POS",
        "NETBANKING_PURCHASE", "NETBANKING_TRANSFER", "ATM"
    };

    private TextView mTvTransLine;
    private TextView mTvExtractPreview;
    private TextView mTvPatternHint;
    private EditText mEtTemplate;
    private EditText mEtDate;
    private EditText mEtTime;
    private EditText mEtReason;
    private EditText mEtBank;
    private EditText mEtRemarks;
    private Spinner  mSpinnerTxnType;
    private CheckBox mCbOnline;

    long   mSelectedDateMs;
    String mTxnType = "";
    String mTransLine;
    private String mSenderAddress;
    private long   mSenderId = -1L;
    private ExpenseDatabase    mDb;
    private ExtractionPattern  mCurrentPattern; // non-null when autoExtract found a saved pattern

    // ── PDF inbox mode (set when EXTRA_STATEMENT_ID is provided) ─────────────
    private long         mStatementId = -1L;
    private PdfStatement mPdfStatement;
    private List<PdfLineAdapter.PdfLine> mPdfItems = new ArrayList<PdfLineAdapter.PdfLine>();
    private boolean      mPdfLoaded;
    // Pending bulk import triggered when Apply Regex pressed before PDF was loaded
    private ExtractionPattern mPendingBulkPattern   = null;
    private long              mPendingBulkPatternId = -1;
    private boolean           mPendingBulkReApply   = false;
    private View         mLayoutTab1;
    private View         mLayoutTab2;
    private View         mLayoutTabBar;
    private Button       mBtnTabStatement;
    private Button       mBtnTabTransaction;
    private TextView     mTvPdfFileName;
    private EditText     mEtPdfMonth;
    private Button       mBtnPickFromPdf;
    // Bank line section (always visible in PDF mode; blank until a line is selected)
    private TextView mTvBankOrigLine;
    EditText         mEtBankPattern;
    private TextView mTvBankPatternPreview;
    String           mBankOrigLine = "";

    // Month line section (always visible in PDF mode; blank until a line is selected)
    private TextView mTvMonthOrigLine;
    EditText         mEtMonthPattern;
    private TextView mTvMonthPatternPreview;
    String           mMonthOrigLine = "";

    // Live during dialog only — accessed by static inner listener classes
    PdfPickerAdapter     mPdfPickerAdapter;
    Button               mPdfModeBank;
    Button               mPdfModeMonth;
    Button               mPdfModeTrans;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_map);
        getWindow().setStatusBarColor(0xFF1976D2);

        mSenderAddress     = getIntent().getStringExtra(EXTRA_SENDER_ADDRESS);
        mTransLine        = getIntent().getStringExtra(EXTRA_TRANS_LINE);
        mSelectedDateMs = getIntent().getLongExtra(EXTRA_SOURCE_DATE, System.currentTimeMillis());
        mSenderId       = getIntent().getLongExtra(EXTRA_SENDER_ID, -1L);

        mDb = ExpenseDatabase.getInstance(this);

        mTvTransLine        = (TextView) findViewById(R.id.tvSmsBody);
        mTvExtractPreview = (TextView) findViewById(R.id.tvExtractPreview);
        mTvPatternHint    = (TextView) findViewById(R.id.tvPatternHint);
        mEtTemplate       = (EditText) findViewById(R.id.etTemplate);
        mEtDate           = (EditText) findViewById(R.id.etDate);
        mEtTime           = (EditText) findViewById(R.id.etTime);
        mEtReason         = (EditText) findViewById(R.id.etReason);
        mEtBank           = (EditText) findViewById(R.id.etBank);
        mEtRemarks        = (EditText) findViewById(R.id.etRemarks);
        mSpinnerTxnType   = (Spinner)  findViewById(R.id.spinnerTxnType);
        mCbOnline         = (CheckBox) findViewById(R.id.cbOnline);
        Button btnSave    = (Button)   findViewById(R.id.btnSave);

        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(
            this, R.array.transaction_type_labels, android.R.layout.simple_spinner_item);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerTxnType.setAdapter(typeAdapter);
        mSpinnerTxnType.setOnItemSelectedListener(new TxnTypeSelectedListener(this));

        String[] btnIds_tokens = {
            TOK_AMOUNT_DB, TOK_AMOUNT_CR, TOK_MERCHANT, TOK_CARD,
            TOK_ACNO,      TOK_UPI,       TOK_DATE,
            TOK_TIME,      TOK_BALANCE,   TOK_IGNORE
        };
        int[] btnIds = {
            R.id.btnTokAmountDb, R.id.btnTokAmountCr, R.id.btnTokMerchant, R.id.btnTokCard,
            R.id.btnTokAcNo,     R.id.btnTokUpi,      R.id.btnTokDate,
            R.id.btnTokTime,     R.id.btnTokBalance,  R.id.btnTokIgnore
        };
        for (int i = 0; i < btnIds.length; i++) {
            Button b = (Button) findViewById(btnIds[i]);
            b.setFocusable(false);
            b.setOnClickListener(new TokenButtonListener(this, btnIds_tokens[i]));
        }
        Button btnClearTags = (Button) findViewById(R.id.btnClearTags);
        if (btnClearTags != null) {
            btnClearTags.setOnClickListener(new ClearTagsListener(this));
        }

        // Tab layout views (always bound; tab bar + tab1 stay GONE unless PDF mode)
        mLayoutTab1        = (View)   findViewById(R.id.layoutTab1);
        mLayoutTab2        = (View)   findViewById(R.id.layoutTab2);
        mLayoutTabBar      = (View)   findViewById(R.id.layoutTabBar);
        mBtnTabStatement   = (Button) findViewById(R.id.btnTabStatement);
        mBtnTabTransaction = (Button) findViewById(R.id.btnTabTransaction);
        mTvPdfFileName     = (TextView) findViewById(R.id.tvPdfSectionFileName);
        mEtPdfMonth        = (EditText) findViewById(R.id.etPdfMonth);
        mBtnPickFromPdf    = (Button)   findViewById(R.id.btnPickFromPdf);

        if (mBtnTabStatement   != null) mBtnTabStatement.setOnClickListener(new TabListener(this, 0));
        if (mBtnTabTransaction != null) mBtnTabTransaction.setOnClickListener(new TabListener(this, 1));

        mTvTransLine.setOnTouchListener(new SaveSelectionListener(this));
        mTvTransLine.setText(mTransLine != null ? mTransLine : "");
        mEtBank.setText(mSenderAddress != null ? mSenderAddress : "");
        updateDateTimeDisplay();

        mStatementId = getIntent().getLongExtra(EXTRA_STATEMENT_ID, -1L);
        if (mStatementId >= 0) initPdfMode();

        boolean blankTemplate = getIntent().getBooleanExtra(EXTRA_BLANK_TEMPLATE, false);
        // PDF mode: if initPdfMode() already restored a trans line, don't wipe the template
        if (blankTemplate && mStatementId >= 0 && mTransLine != null && !mTransLine.isEmpty()) {
            blankTemplate = false;
        }
        mEtTemplate.setText((!blankTemplate && mTransLine != null) ? mTransLine : "");
        autoExtract();
        if (mTransLine != null) {
            mTaggedSpans.clear();
            mTaggedSpans.addAll(parseSpansFromTemplate(mTransLine, mEtTemplate.getText().toString()));
            renderTaggedSpans();
        }
        updatePatternHint();
        updateRegexPreview();

        mEtTemplate.addTextChangedListener(new TemplateWatcher(this));
        mEtDate.setOnClickListener(new DateClickListener(this));
        mEtTime.setOnClickListener(new TimeClickListener(this));
        mTvPatternHint.setOnClickListener(new PatternHintClickListener(this));
        btnSave.setOnClickListener(new SaveClickListener(this));

        if (mStatementId >= 0 && mPdfStatement != null && !mPdfLoaded) {
            startPdfLoad(null);
        }
    }

    private void autoExtract() {
        if (mTransLine == null || mTransLine.isEmpty()) return;

        List<ExtractionPattern> patterns = null;

        SenderConfig cfg = null;
        if (mSenderId >= 0) cfg = mDb.getSenderById(mSenderId);
        if (cfg == null && mSenderAddress != null)
            cfg = SmsReader.findConfig(mSenderAddress, mDb.getAllSenders());

        if (cfg != null) {
            patterns = mDb.getPatternsBySender(cfg.id);
        }
        if ((patterns == null || patterns.isEmpty()) && mStatementId >= 0) {
            // PDF mode fallback: try is_pdf=1 or senderId=-1 patterns
            patterns = mDb.getAllPdfPatterns();
        }

        if (patterns == null) return;
        for (ExtractionPattern p : patterns) {
            if (!p.matches(mTransLine)) continue;
            if (p.templateText != null && !p.templateText.isEmpty()) {
                mEtTemplate.setText(p.templateText);
                mTaggedSpans.clear();
                mTaggedSpans.addAll(parseSpansFromTemplate(mTransLine, p.templateText));
                renderTaggedSpans();
            }
            mCurrentPattern = p;
            mTxnType = p.transactionType != null ? p.transactionType : "";
            for (int i = 0; i < TYPE_VALUES.length; i++) {
                if (TYPE_VALUES[i].equals(mTxnType)) { mSpinnerTxnType.setSelection(i); break; }
            }
            String date = p.extractGroup(mTransLine, p.dateGroup).trim();
            if (!date.isEmpty()) tryApplyExtractedDate(date);
            String time = p.extractGroup(mTransLine, p.timeGroup).trim();
            if (!time.isEmpty()) tryApplyExtractedTime(time);
            return;
        }
    }

    void updatePatternHint() {
        if (mCurrentPattern != null && mCurrentPattern.id > 0) {
            mTvPatternHint.setVisibility(View.VISIBLE);
            String name = (mCurrentPattern.name != null && !mCurrentPattern.name.isEmpty())
                ? mCurrentPattern.name : "existing pattern";
            mTvPatternHint.setText("Modifying: " + name + "  ·  tap to save as new instead");
        } else {
            mTvPatternHint.setVisibility(View.GONE);
        }
    }

    void clearCurrentPattern() {
        mCurrentPattern = null;
        updatePatternHint();
    }

    // ── Point-and-label tagging (replaces regex template editing) ────────────

    static class TagSpan {
        int start, end;
        String token;
        TagSpan(int s, int e, String t) { start = s; end = e; token = t; }
    }

    private final List<TagSpan> mTaggedSpans = new ArrayList<TagSpan>();
    int mLastSelStart = -1;
    int mLastSelEnd   = -1;

    /** Called when user selects a span in tvSmsBody and taps a label chip. */
    void tagSelection(String token) {
        if (mTransLine == null || mTransLine.isEmpty()) return;
        // Prefer live selection; fall back to saved if focus was already lost
        int s = mTvTransLine.getSelectionStart();
        int e = mTvTransLine.getSelectionEnd();
        if (s < 0 || e < 0 || s == e) { s = mLastSelStart; e = mLastSelEnd; }
        if (s < 0 || e < 0 || s == e) {
            Toast.makeText(this, R.string.msg_select_text_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if (s > e) { int tmp = s; s = e; e = tmp; }
        if (e > mTransLine.length()) e = mTransLine.length();

        // Remove any existing spans that overlap the new one
        List<TagSpan> kept = new ArrayList<TagSpan>();
        for (TagSpan ts : mTaggedSpans) {
            if (ts.end <= s || ts.start >= e) kept.add(ts);
        }
        kept.add(new TagSpan(s, e, token));
        // Sort by start
        for (int i = 1; i < kept.size(); i++) {
            TagSpan k = kept.get(i); int j = i - 1;
            while (j >= 0 && kept.get(j).start > k.start) { kept.set(j + 1, kept.get(j)); j--; }
            kept.set(j + 1, k);
        }
        mTaggedSpans.clear();
        mTaggedSpans.addAll(kept);
        mLastSelStart = -1;
        mLastSelEnd   = -1;
        renderTaggedSpans();
    }

    void clearAllTags() {
        mTaggedSpans.clear();
        renderTaggedSpans();
    }

    /** Rebuilds the inline-highlighted sms body and the derived template. */
    void renderTaggedSpans() {
        if (mTransLine == null) return;
        SpannableString ss = new SpannableString(mTransLine);
        for (TagSpan ts : mTaggedSpans) {
            int color = tokenColor(ts.token);
            ss.setSpan(new BackgroundColorSpan(color), ts.start, ts.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new ForegroundColorSpan(0xFFFFFFFF), ts.start, ts.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        mTvTransLine.setText(ss);
        // Remove selection highlight so chip colors are immediately visible
        CharSequence cur = mTvTransLine.getText();
        if (cur instanceof android.text.Spannable) {
            android.text.Selection.removeSelection((android.text.Spannable) cur);
        }
        mEtTemplate.setText(deriveTemplate());
    }

    private String deriveTemplate() {
        if (mTransLine == null) return "";
        if (mTaggedSpans.isEmpty()) return mTransLine;
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        for (TagSpan ts : mTaggedSpans) {
            if (ts.start > pos) sb.append(mTransLine, pos, ts.start);
            sb.append(ts.token);
            pos = ts.end;
        }
        if (pos < mTransLine.length()) sb.append(mTransLine, pos, mTransLine.length());
        return sb.toString();
    }

    /** Reverse-engineer tag spans from an existing template + original body. */
    private List<TagSpan> parseSpansFromTemplate(String body, String template) {
        List<TagSpan> out = new ArrayList<TagSpan>();
        if (body == null || template == null) return out;
        // buildPatternFromTemplate() trims the template; mirror that here so literal
        // lookups don't miss on trailing whitespace in the body.
        template = template.trim();
        int tmplPos = 0, bodyPos = 0;
        while (tmplPos < template.length()) {
            int tokStart = -1;
            String foundTok = null;
            for (String tok : ALL_TOKENS) {
                int idx = template.indexOf(tok, tmplPos);
                if (idx >= 0 && (tokStart < 0 || idx < tokStart)) {
                    tokStart = idx; foundTok = tok;
                }
            }
            if (foundTok == null) break;
            String literal = template.substring(tmplPos, tokStart);
            int litInBody = body.indexOf(literal, bodyPos);
            if (litInBody < 0) return new ArrayList<TagSpan>();
            bodyPos = litInBody + literal.length();

            int nextTokStart = -1;
            for (String tok : ALL_TOKENS) {
                int idx = template.indexOf(tok, tokStart + foundTok.length());
                if (idx >= 0 && (nextTokStart < 0 || idx < nextTokStart)) nextTokStart = idx;
            }
            String nextLit = (nextTokStart < 0)
                ? template.substring(tokStart + foundTok.length())
                : template.substring(tokStart + foundTok.length(), nextTokStart);

            int tagEnd;
            if (nextLit.isEmpty()) {
                tagEnd = body.length();
            } else {
                tagEnd = body.indexOf(nextLit, bodyPos);
                if (tagEnd < 0) return new ArrayList<TagSpan>();
            }
            out.add(new TagSpan(bodyPos, tagEnd, foundTok));
            bodyPos = tagEnd;
            tmplPos = tokStart + foundTok.length();
        }
        return out;
    }

    private static int tokenColor(String token) {
        // Okabe-Ito colorblind-safe palette
        if (TOK_AMOUNT_CR.equals(token)) return 0xFF2E7D32; // deep green = credit
        if (TOK_AMOUNT_DB.equals(token)) return 0xFFC62828; // red = debit
        if (TOK_BALANCE.equals(token))  return 0xFF56B4E9; // sky blue
        if (TOK_MERCHANT.equals(token)) return 0xFF0072B2; // blue
        if (TOK_CARD.equals(token))     return 0xFF0072B2;
        if (TOK_ACNO.equals(token))     return 0xFF0072B2;
        if (TOK_UPI.equals(token))      return 0xFF0072B2;
        if (TOK_DATE.equals(token))     return 0xFFD55E00; // vermilion
        if (TOK_TIME.equals(token))     return 0xFFCC79A7; // reddish purple
        if (TOK_IGNORE.equals(token))   return 0xFF999999; // grey
        return 0xFF999999;
    }

    // Legacy entry point (template-text insert). Routes to tagSelection.
    void insertToken(String token) {
        tagSelection(token);
    }

    void save() {
        String template = mEtTemplate.getText().toString();
        ExtractionPattern pattern = buildPatternFromTemplate(template);

        if (mStatementId >= 0) {
            if (mEtBankPattern  != null) pattern.bankNamePat = mEtBankPattern.getText().toString().trim();
            if (mEtMonthPattern != null) pattern.periodPat   = mEtMonthPattern.getText().toString().trim();
        }

        if (pattern.amountGroup < 0 && pattern.amountCrGroup < 0 && pattern.amountDbGroup < 0) {
            Toast.makeText(this, R.string.error_template_needs_amount, Toast.LENGTH_SHORT).show();
            return;
        }

        Matcher matcher = null;
        if (mTransLine != null && pattern.templateRegex != null) {
            try {
                matcher = Pattern.compile(pattern.templateRegex,
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(mTransLine);
                if (!matcher.find()) matcher = null;
            } catch (Exception ignored) {}
        }

        // Credit column takes precedence; fall back to debit, then legacy amount.
        String amtStr    = "";
        boolean isCredit = false;
        if (pattern.amountCrGroup >= 0) {
            amtStr = groupStr(matcher, pattern.amountCrGroup).replaceAll("[^0-9.]", "");
            if (!amtStr.isEmpty()) isCredit = true;
        }
        if (amtStr.isEmpty() && pattern.amountDbGroup >= 0) {
            amtStr = groupStr(matcher, pattern.amountDbGroup).replaceAll("[^0-9.]", "");
        }
        if (amtStr.isEmpty() && pattern.amountGroup >= 0) {
            amtStr = groupStr(matcher, pattern.amountGroup).replaceAll("[^0-9.]", "");
        }
        String balStr   = groupStr(matcher, pattern.balanceGroup)
                            .replaceAll("[^0-9.,]", "").replaceAll(",", "");
        String merchant = groupStr(matcher, pattern.merchantGroup).trim();
        String card     = groupStr(matcher, pattern.cardGroup).trim();
        String account  = groupStr(matcher, pattern.accountGroup).trim();
        String dateStr  = groupStr(matcher, pattern.dateGroup).trim();
        String timeStr  = groupStr(matcher, pattern.timeGroup).trim();

        if (amtStr.isEmpty()) {
            Toast.makeText(this, R.string.error_amount_required, Toast.LENGTH_SHORT).show();
            return;
        }
        double amount;
        try { amount = Double.parseDouble(amtStr); }
        catch (NumberFormatException e) {
            Toast.makeText(this, R.string.error_invalid_amount, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!dateStr.isEmpty()) tryApplyExtractedDate(dateStr);
        if (!timeStr.isEmpty()) tryApplyExtractedTime(timeStr);

        double balance = 0;
        if (!balStr.isEmpty()) {
            try { balance = Double.parseDouble(balStr); } catch (NumberFormatException ignored) {}
        }

        // ── Persist pattern (update or insert) ──────────────────────
        long patternId = -1;
        boolean isPdfMode = (mStatementId >= 0);
        boolean isUpdate = (mCurrentPattern != null
                            && mCurrentPattern.id > 0
                            && (mSenderId >= 0 || isPdfMode));

        // Persist the current trans line so Edit/re-setup can restore it
        if (isPdfMode && mPdfStatement != null && mTransLine != null && !mTransLine.isEmpty()) {
            if (!mTransLine.equals(mPdfStatement.sampleTransLine)) {
                mPdfStatement.sampleTransLine = mTransLine;
                mDb.updatePdfStatement(mPdfStatement);
            }
        }

        // Auto-create a SenderConfig for a new PDF bank so the pattern isn't
        // orphaned. Runs for both insert (brand-new pattern) and update of an
        // orphan pattern — the latter promotes a previously-orphan PDF pattern
        // to a real bank once the user types the name on save.
        if (isPdfMode && mSenderId < 0) {
            String bankName = mEtBank.getText().toString().trim();
            if (!bankName.isEmpty()) {
                SenderConfig newSender = new SenderConfig();
                newSender.pattern     = bankName;
                newSender.displayName = bankName;
                newSender.isRegex     = false;
                mSenderId = mDb.insertSender(newSender);
                pattern.senderId = mSenderId;
                if (mPdfStatement != null) {
                    mPdfStatement.senderId = mSenderId;
                    mDb.updatePdfStatement(mPdfStatement);
                }
            }
        }

        // Gate: a PDF pattern must have a sender, otherwise it saves with
        // senderId = -1 and is invisible to BankConfigActivity's per-bank list.
        if (isPdfMode && mSenderId < 0) {
            Toast.makeText(this, R.string.error_bank_name_required, Toast.LENGTH_SHORT).show();
            if (mEtBank != null) mEtBank.requestFocus();
            return;
        }

        if (mSenderId >= 0 || isPdfMode) {
            if (isUpdate) {
                pattern.id = mCurrentPattern.id;
                // Prefer the current mSenderId (may have been just auto-created
                // from the bank-name field when rescuing an orphan pattern);
                // fall back to the stored senderId for ordinary in-place edits.
                pattern.senderId = (mSenderId > 0) ? mSenderId : mCurrentPattern.senderId;
                mDb.updatePattern(pattern);
                patternId = mCurrentPattern.id;
            } else {
                patternId = mDb.insertPattern(pattern);
            }
        }

        // Count BEFORE inserting the new expense so the dialog count reflects prior entries only
        int priorCount = isUpdate ? mDb.countExpensesByPattern(patternId) : 0;

        // ── PDF mode: bulk-import all matching lines ──────────────────
        if (isPdfMode) {
            if (mPdfItems.isEmpty()) {
                // PDF not loaded yet — load it first; bulk import runs in onPdfLinesLoaded
                mPendingBulkPattern   = pattern;
                mPendingBulkPatternId = patternId;
                mPendingBulkReApply   = isUpdate && priorCount > 0;
                startPdfLoad(null);
                return;
            }
            Toast.makeText(this, "Scanning " + mPdfItems.size() + " lines…", Toast.LENGTH_SHORT).show();
            new BulkImportThread(this, new ArrayList<PdfLineAdapter.PdfLine>(mPdfItems),
                    pattern, patternId, mStatementId,
                    mEtBank.getText().toString().trim(),
                    mEtReason.getText().toString().trim(),
                    mEtRemarks.getText().toString().trim(),
                    mCbOnline.isChecked(), mTxnType, mDb,
                    isUpdate && priorCount > 0,
                    new Handler(Looper.getMainLooper())).start();
            return;
        }

        // ── SMS mode: single expense ─────────────────────────────────
        Expense expense = new Expense();
        expense.amount          = amount;
        expense.isCredit        = isCredit;
        expense.dateMs          = mSelectedDateMs;
        expense.merchant        = merchant;
        expense.reason          = mEtReason.getText().toString().trim();
        expense.card            = card;
        expense.accountNumber   = account;
        expense.isOnline        = mCbOnline.isChecked();
        expense.bank            = mEtBank.getText().toString().trim();
        expense.originalSms     = mTransLine;
        expense.balance         = balance;
        expense.transactionType = mTxnType;
        expense.remarks         = mEtRemarks.getText().toString().trim();
        expense.patternId       = patternId;
        expense.pdfStatementId  = isPdfMode ? mStatementId : -1L;
        expense.createdAt       = System.currentTimeMillis();
        expense.source          = isPdfMode ? "pdf" : "sms";
        mDb.insertExpense(expense);

        // ── Offer re-apply if updating an existing pattern ───────────
        if (isUpdate && priorCount > 0) {
            showReApplyDialog(pattern, priorCount);
            return;
        }

        finishWithSuccess(mSenderId >= 0 || isPdfMode);
    }

    private void showReApplyDialog(ExtractionPattern pattern, int count) {
        new AlertDialog.Builder(this, R.style.RoundedDialog)
            .setTitle("Pattern updated")
            .setMessage("Re-apply to " + count + " existing expense"
                + (count == 1 ? "" : "s") + "?\n\nAmount, merchant, card and balance will be "
                + "recalculated. Date, reason and notes are kept as-is.")
            .setPositiveButton("Re-apply", new PatternUpdateDialogListener(this, pattern, true))
            .setNegativeButton("Skip",     new PatternUpdateDialogListener(this, pattern, false))
            .setCancelable(false)
            .show();
    }

    void startReApply(ExtractionPattern pattern) {
        Toast.makeText(this, "Updating expenses…", Toast.LENGTH_SHORT).show();
        new ReApplyThread(this, pattern, mDb, new Handler(Looper.getMainLooper())).start();
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

    void onBulkImportDone(int imported, int reApplied, int scanned) {
        StringBuilder sb = new StringBuilder();
        sb.append("Scanned ").append(scanned).append(" lines — ");
        if (imported == 0) sb.append("no new expenses found");
        else sb.append("imported ").append(imported).append(" expense").append(imported == 1 ? "" : "s");
        if (reApplied > 0) sb.append(", updated ").append(reApplied).append(" existing");
        Toast.makeText(this, sb.toString(), Toast.LENGTH_LONG).show();
        finishWithSuccess(true);
    }

    static long parseDateMs(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        String[] fmts = {"dd/MM/yyyy", "dd-MM-yyyy", "dd/MM/yy", "dd-MM-yy",
                         "yyyy-MM-dd", "dd MMM yyyy", "dd MMM yy",
                         "dd-MMM-yyyy", "dd-MMM-yy"};
        for (String fmt : fmts) {
            try {
                java.util.Date d = new SimpleDateFormat(fmt, Locale.US).parse(raw);
                if (d != null) return d.getTime();
            } catch (ParseException ignored) {}
        }
        return 0;
    }

    static long applyTimeToMs(long baseMs, String raw) {
        if (raw == null || raw.isEmpty()) return baseMs;
        String[] fmts = {"HH:mm:ss", "HH:mm", "hh:mm:ss a", "hh:mm a"};
        for (String fmt : fmts) {
            try {
                java.util.Date d = new SimpleDateFormat(fmt, Locale.US).parse(raw.trim());
                if (d == null) continue;
                Calendar tc = Calendar.getInstance();
                tc.setTime(d);
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(baseMs);
                cal.set(Calendar.HOUR_OF_DAY, tc.get(Calendar.HOUR_OF_DAY));
                cal.set(Calendar.MINUTE,      tc.get(Calendar.MINUTE));
                cal.set(Calendar.SECOND,      0);
                return cal.getTimeInMillis();
            } catch (ParseException ignored) {}
        }
        return baseMs;
    }

    // ── PDF inbox mode ──────────────────────────────────────────────────────

    void initPdfMode() {
        mPdfStatement = mDb.getPdfStatementById(mStatementId);
        if (mPdfStatement == null) return;

        if (mPdfStatement.senderId > 0) mSenderId = mPdfStatement.senderId;

        mLayoutTabBar.setVisibility(View.VISIBLE);
        switchToTab(0);
        mTvPdfFileName.setText(mPdfStatement.displayName != null
                ? mPdfStatement.displayName : mPdfStatement.uri);

        if (mPdfStatement.bankName != null) mEtBank.setText(mPdfStatement.bankName);
        if (mPdfStatement.statementPeriod    != null) mEtPdfMonth.setText(mPdfStatement.statementPeriod);

        // Relabel the transaction box header in PDF mode
        TextView tvTransHeader = (TextView) findViewById(R.id.tvTransHeader);
        if (tvTransHeader != null) tvTransHeader.setText("Transaction Line");

        // Restore last selected transaction line + template
        if (mPdfStatement.sampleTransLine != null && !mPdfStatement.sampleTransLine.isEmpty()) {
            mTransLine = mPdfStatement.sampleTransLine;
            mTvTransLine.setText(mTransLine);
            mEtTemplate.setText(mTransLine);
            mTaggedSpans.clear();
            renderTaggedSpans();
        }

        mTvBankOrigLine        = (TextView) findViewById(R.id.tvBankOrigLine);
        mEtBankPattern         = (EditText) findViewById(R.id.etBankPattern);
        mTvBankPatternPreview  = (TextView) findViewById(R.id.tvBankPatternPreview);

        mTvMonthOrigLine       = (TextView) findViewById(R.id.tvMonthOrigLine);
        mEtMonthPattern        = (EditText) findViewById(R.id.etMonthPattern);
        mTvMonthPatternPreview = (TextView) findViewById(R.id.tvMonthPatternPreview);

        // Pre-fill stored patterns — prefer per-pattern values, fall back to global store
        String storedBankPat  = (mCurrentPattern != null && mCurrentPattern.bankNamePat != null
                                  && !mCurrentPattern.bankNamePat.isEmpty())
                                ? mCurrentPattern.bankNamePat
                                : mDb.getPdfFieldPattern("bank");
        String storedMonthPat = (mCurrentPattern != null && mCurrentPattern.periodPat != null
                                  && !mCurrentPattern.periodPat.isEmpty())
                                ? mCurrentPattern.periodPat
                                : mDb.getPdfFieldPattern("month");
        if (storedBankPat  != null) mEtBankPattern.setText(storedBankPat);
        if (storedMonthPat != null) mEtMonthPattern.setText(storedMonthPat);

        mEtBankPattern.addTextChangedListener(new BankPatternWatcher(this));
        mEtMonthPattern.addTextChangedListener(new MonthPatternWatcher(this));

        // Restore saved orig lines (so re-edit shows highlighted bank/month text up-front)
        if (mPdfStatement.bankOrigLine != null && !mPdfStatement.bankOrigLine.isEmpty()) {
            showBankLine(mPdfStatement.bankOrigLine, storedBankPat != null ? storedBankPat : "");
        }
        if (mPdfStatement.monthOrigLine != null && !mPdfStatement.monthOrigLine.isEmpty()) {
            showMonthLine(mPdfStatement.monthOrigLine, storedMonthPat != null ? storedMonthPat : "");
        }

        // Bank pattern snippet buttons
        int[] bankBtnIds = {
            R.id.btnBankPatAny, R.id.btnBankPatWord, R.id.btnBankPatWords, R.id.btnBankPatSkip
        };
        String[] bankSnippets = { "(.+?)", "(\\w+)", "([\\w ]+?)", ".*?" };
        for (int i = 0; i < bankBtnIds.length; i++) {
            Button b = (Button) findViewById(bankBtnIds[i]);
            if (b != null) b.setOnClickListener(new PatternSnippetListener(mEtBankPattern, bankSnippets[i]));
        }

        // Month pattern snippet buttons
        int[] monthBtnIds = {
            R.id.btnMonthPatMonYYYY, R.id.btnMonthPatYYYYMM,
            R.id.btnMonthPatMMYYYY, R.id.btnMonthPatAny, R.id.btnMonthPatSkip
        };
        String[] monthSnippets = {
            "(\\w{3,} \\d{4})", "(\\d{4}-\\d{2})", "(\\d{2}[-/]\\d{4})", "(.+?)", ".*?"
        };
        for (int i = 0; i < monthBtnIds.length; i++) {
            Button b = (Button) findViewById(monthBtnIds[i]);
            if (b != null) b.setOnClickListener(new PatternSnippetListener(mEtMonthPattern, monthSnippets[i]));
        }

        Button btnMarkBank = (Button) findViewById(R.id.btnMarkBank);
        if (btnMarkBank != null) btnMarkBank.setOnClickListener(new MarkBankListener(this));
        Button btnMarkMonth = (Button) findViewById(R.id.btnMarkMonth);
        if (btnMarkMonth != null) btnMarkMonth.setOnClickListener(new MarkMonthListener(this));

        mBtnPickFromPdf.setOnClickListener(new PdfPickBtnClickListener(this));
        mBtnPickFromPdf.setEnabled(true);
    }

    void startPdfLoad(String password) {
        mPdfLoaded = false;
        mBtnPickFromPdf.setEnabled(false);
        TextView tvStatus = (TextView) findViewById(R.id.tvPdfLoadStatus);
        if (tvStatus != null) { tvStatus.setVisibility(android.view.View.VISIBLE); tvStatus.setText(R.string.status_reading_file); }
        if (password == null && mPdfStatement.senderId > 0) {
            String stored = getSharedPreferences("fin_prefs", MODE_PRIVATE)
                .getString("pdf_pass_" + mPdfStatement.senderId, null);
            if (stored != null && !stored.isEmpty()) password = stored;
        }
        Uri uri = Uri.parse(mPdfStatement.uri);
        new PdfLoadThread(this, uri, mPdfStatement.isPdf, password,
                mPdfStatement.senderId, new Handler(Looper.getMainLooper()), mDb).start();
    }

    void onPdfLinesLoaded(List<String> lines, List<PdfLineAdapter.PdfLine> items, String status) {
        TextView tvStatus = (TextView) findViewById(R.id.tvPdfLoadStatus);
        if (PdfDecryptor.NEEDS_PASSWORD.equals(status)) {
            showPdfPasswordDialog(false);
            return;
        }
        if (status.startsWith(PdfDecryptor.WRONG_PASSWORD)) {
            String diag = status.substring(PdfDecryptor.WRONG_PASSWORD.length()).trim();
            if (!diag.isEmpty()) Toast.makeText(this, diag, Toast.LENGTH_LONG).show();
            showPdfPasswordDialog(true);
            return;
        }
        if (status.startsWith(PdfTextExtractor.EXTRACT_EMPTY)) {
            String diag = status.substring(PdfTextExtractor.EXTRACT_EMPTY.length()).trim();
            if (tvStatus != null) { tvStatus.setVisibility(android.view.View.VISIBLE); tvStatus.setText(getString(R.string.status_extract_failed) + (diag.isEmpty() ? "" : ": " + diag)); }
            mBtnPickFromPdf.setEnabled(true);
            return;
        }
        mPdfItems = items;
        if (tvStatus != null) { tvStatus.setVisibility(android.view.View.VISIBLE); tvStatus.setText(status); }
        mPdfLoaded = !items.isEmpty();
        mBtnPickFromPdf.setEnabled(true);

        // Recovery: no stored trans line yet. Iterate saved patterns to pick first match.
        if ((mTransLine == null || mTransLine.isEmpty()) && mPdfLoaded) {
            List<ExtractionPattern> candidates = null;
            if (mSenderId > 0) candidates = mDb.getPatternsBySender(mSenderId);
            if (candidates == null || candidates.isEmpty())
                candidates = mDb.getAllPdfPatterns();
            if (candidates != null) {
                for (ExtractionPattern p : candidates) {
                    boolean matched = false;
                    for (PdfLineAdapter.PdfLine item : mPdfItems) {
                        if (p.matches(item.text)) {
                            mTransLine = item.text;
                            mCurrentPattern = p;
                            mTvTransLine.setText(mTransLine);
                            mEtTemplate.setText(p.templateText != null
                                ? p.templateText : mTransLine);
                            mTaggedSpans.clear();
                            mTaggedSpans.addAll(parseSpansFromTemplate(mTransLine,
                                mEtTemplate.getText().toString()));
                            renderTaggedSpans();
                            matched = true;
                            break;
                        }
                    }
                    if (matched) break;
                }
            }
        }

        // If Apply Regex triggered the load, run bulk import now instead of showing picker
        if (mPendingBulkPattern != null && mPdfLoaded) {
            ExtractionPattern p  = mPendingBulkPattern;
            long             pid = mPendingBulkPatternId;
            boolean      reApply = mPendingBulkReApply;
            mPendingBulkPattern   = null;
            mPendingBulkPatternId = -1;
            mPendingBulkReApply   = false;
            Toast.makeText(this, "Scanning " + mPdfItems.size() + " lines…", Toast.LENGTH_SHORT).show();
            new BulkImportThread(this, new ArrayList<PdfLineAdapter.PdfLine>(mPdfItems),
                    p, pid, mStatementId,
                    mEtBank.getText().toString().trim(),
                    mEtReason.getText().toString().trim(),
                    mEtRemarks.getText().toString().trim(),
                    mCbOnline.isChecked(), mTxnType, mDb, reApply,
                    new Handler(Looper.getMainLooper())).start();
            return;
        }

        if (mPdfLoaded) {
            autoApplyFieldPatterns();
            if (mTransLine == null || mTransLine.isEmpty()) {
                showPdfPicker();
            }
        }
    }

    void showPdfPasswordDialog(boolean wrongPassword) {
        android.view.View v = getLayoutInflater().inflate(R.layout.dialog_password, null);
        EditText etPass = (EditText) v.findViewById(R.id.etDialogPassword);
        etPass.setHint(R.string.hint_pdf_password);
        new AlertDialog.Builder(this, R.style.RoundedDialog)
            .setTitle(R.string.pdf_password_title)
            .setMessage(wrongPassword ? R.string.pdf_password_wrong : R.string.pdf_password_msg)
            .setView(v)
            .setPositiveButton(android.R.string.ok, new PdfPasswordOkListener(this, etPass))
            .setNegativeButton(android.R.string.cancel, new PdfPasswordCancelListener(this))
            .show();
    }

    void showPdfPicker() {
        if (!mPdfLoaded || mPdfItems.isEmpty()) return;

        mPdfPickerAdapter = new PdfPickerAdapter(this, mPdfItems);
        float dp  = getResources().getDisplayMetrics().density;
        int   pad = (int)(8 * dp);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // Mode buttons row
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setPadding(pad, pad, pad, pad / 2);

        mPdfModeBank = new Button(this);
        mPdfModeBank.setText(R.string.pdf_mode_bank);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        blp.setMargins(0, 0, pad / 2, 0);
        mPdfModeBank.setLayoutParams(blp);
        mPdfModeBank.setOnClickListener(new PdfModeButtonListener(this, PdfPickerAdapter.MODE_BANK));
        modeRow.addView(mPdfModeBank);

        mPdfModeMonth = new Button(this);
        mPdfModeMonth.setText(R.string.pdf_mode_month);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        mlp.setMargins(pad / 2, 0, pad / 2, 0);
        mPdfModeMonth.setLayoutParams(mlp);
        mPdfModeMonth.setOnClickListener(new PdfModeButtonListener(this, PdfPickerAdapter.MODE_MONTH));
        modeRow.addView(mPdfModeMonth);

        mPdfModeTrans = new Button(this);
        mPdfModeTrans.setText(R.string.pdf_mode_trans);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMargins(pad / 2, 0, 0, 0);
        mPdfModeTrans.setLayoutParams(tlp);
        mPdfModeTrans.setOnClickListener(new PdfModeButtonListener(this, PdfPickerAdapter.MODE_TRANS));
        modeRow.addView(mPdfModeTrans);

        root.addView(modeRow);

        // Line list
        ListView lv = new ListView(this);
        lv.setAdapter(mPdfPickerAdapter);
        lv.setOnItemClickListener(new PdfPickerLineListener(this));
        root.addView(lv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(400 * dp)));

        // Auto-select matching lines so user can submit without extra taps
        String bankPat  = mEtBankPattern  != null ? mEtBankPattern.getText().toString().trim()  : "";
        String monthPat = mEtMonthPattern != null ? mEtMonthPattern.getText().toString().trim() : "";
        List<ExtractionPattern> pdfPats = mDb.getAllPdfPatterns();

        int autoBank = -1, autoMonth = -1, autoTrans = -1;
        for (int i = 0; i < mPdfItems.size(); i++) {
            String text = mPdfItems.get(i).text;
            if (autoBank  < 0 && !bankPat.isEmpty()  && !applyFieldPattern(bankPat,  text).isEmpty()) autoBank  = i;
            if (autoMonth < 0 && !monthPat.isEmpty() && !applyFieldPattern(monthPat, text).isEmpty()) autoMonth = i;
            if (autoTrans < 0) {
                for (ExtractionPattern p : pdfPats) {
                    if (p.matches(text)) { autoTrans = i; break; }
                }
            }
        }

        if (autoBank  >= 0) { mPdfPickerAdapter.setMode(PdfPickerAdapter.MODE_BANK);  mPdfPickerAdapter.onItemClick(autoBank);  }
        if (autoMonth >= 0) { mPdfPickerAdapter.setMode(PdfPickerAdapter.MODE_MONTH); mPdfPickerAdapter.onItemClick(autoMonth); }
        if (autoTrans >= 0) { mPdfPickerAdapter.setMode(PdfPickerAdapter.MODE_TRANS); mPdfPickerAdapter.onItemClick(autoTrans); }

        // Default active mode = TRANS
        setPdfPickerMode(PdfPickerAdapter.MODE_TRANS);
        if (autoTrans >= 0) lv.setSelection(autoTrans);

        new AlertDialog.Builder(this, R.style.RoundedDialog)
            .setTitle(R.string.pdf_picker_dialog_title)
            .setView(root)
            .setPositiveButton(R.string.pdf_picker_submit, new PdfSubmitListener(this))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void setPdfPickerMode(int mode) {
        if (mPdfPickerAdapter != null) mPdfPickerAdapter.setMode(mode);
        if (mPdfModeBank == null) return;
        int[] activeBg   = { 0xFF1565C0, 0xFF2E7D32, 0xFFE65100 };
        int[] inactiveBg = { 0xFFBBDEFB, 0xFFC8E6C9, 0xFFFFE0B2 };
        int[] inactiveFg = { 0xFF1565C0, 0xFF2E7D32, 0xFFE65100 };
        Button[] btns    = { mPdfModeBank, mPdfModeMonth, mPdfModeTrans };
        for (int i = 0; i < btns.length; i++) {
            if (i == mode) {
                btns[i].setBackgroundColor(activeBg[i]);
                btns[i].setTextColor(0xFFFFFFFF);
            } else {
                btns[i].setBackgroundColor(inactiveBg[i]);
                btns[i].setTextColor(inactiveFg[i]);
            }
        }
    }

    void onPdfPickerSubmit(int bankIdx, int monthIdx, int transIdx) {
        String bankPat  = mEtBankPattern  != null ? mEtBankPattern.getText().toString().trim()  : "";
        String monthPat = mEtMonthPattern != null ? mEtMonthPattern.getText().toString().trim() : "";
        String bankOrigLine = null, monthOrigLine = null;
        if (bankIdx >= 0 && bankIdx < mPdfItems.size()) {
            String line     = mPdfItems.get(bankIdx).text;
            String fallback = line.length() > 60 ? line.substring(0, 60).trim() : line.trim();
            if (!bankPat.isEmpty()) {
                String extracted = applyFieldPattern(bankPat, line);
                mEtBank.setText(extracted.isEmpty() ? fallback : extracted);
            } else {
                mEtBank.setText(fallback);
            }
            bankOrigLine = line;
            showBankLine(line, bankPat);
        }
        if (monthIdx >= 0 && monthIdx < mPdfItems.size()) {
            String line = mPdfItems.get(monthIdx).text;
            if (!monthPat.isEmpty()) {
                String extracted = applyFieldPattern(monthPat, line);
                if (!extracted.isEmpty()) mEtPdfMonth.setText(extracted);
            } else {
                String month = extractPdfMonth(line);
                if (!month.isEmpty()) mEtPdfMonth.setText(month);
            }
            monthOrigLine = line;
            showMonthLine(line, monthPat);
        }
        if (transIdx >= 0 && transIdx < mPdfItems.size()) {
            String line = mPdfItems.get(transIdx).text;
            mTransLine = line;
            mTvTransLine.setText(line);
            mEtTemplate.setText(line);
            mTaggedSpans.clear();
            renderTaggedSpans();
            updateRegexPreview();
        }
        // Persist patterns for auto-apply on next file open
        mDb.setPdfFieldPattern("bank",  bankPat.isEmpty()  ? null : bankPat);
        mDb.setPdfFieldPattern("month", monthPat.isEmpty() ? null : monthPat);
        // Persist bank/month/trans back to the statement record
        if (mPdfStatement != null) {
            String bank  = mEtBank.getText().toString().trim();
            String month = mEtPdfMonth.getText().toString().trim();
            mPdfStatement.bankName     = bank.isEmpty()  ? null : bank;
            mPdfStatement.statementPeriod = month.isEmpty() ? null : month;
            mPdfStatement.sampleTransLine = mTransLine;
            if (bankOrigLine  != null) mPdfStatement.bankOrigLine  = bankOrigLine;
            if (monthOrigLine != null) mPdfStatement.monthOrigLine = monthOrigLine;
            mDb.updatePdfStatement(mPdfStatement);
        }
    }

    void autoApplyFieldPatterns() {
        if (mPdfItems.isEmpty()) return;
        String bankPat  = mDb.getPdfFieldPattern("bank");
        String monthPat = mDb.getPdfFieldPattern("month");
        if (bankPat != null && !bankPat.isEmpty()) {
            for (PdfLineAdapter.PdfLine item : mPdfItems) {
                String val = applyFieldPattern(bankPat, item.text);
                if (!val.isEmpty()) {
                    showBankLine(item.text, bankPat);
                    if (mEtBank.getText().toString().trim().isEmpty()) mEtBank.setText(val);
                    break;
                }
            }
        }
        if (monthPat != null && !monthPat.isEmpty()) {
            for (PdfLineAdapter.PdfLine item : mPdfItems) {
                String val = applyFieldPattern(monthPat, item.text);
                if (!val.isEmpty()) {
                    showMonthLine(item.text, monthPat);
                    if (mEtPdfMonth.getText().toString().trim().isEmpty()) mEtPdfMonth.setText(val);
                    break;
                }
            }
        }
    }

    void showBankLine(String origLine, String pattern) {
        mBankOrigLine = origLine;
        if (mTvBankOrigLine == null) return;
        mTvBankOrigLine.setText(highlightMatch(origLine, pattern, 0xFF0072B2));
        boolean hasLine = origLine != null && !origLine.isEmpty();
        mTvBankOrigLine.setVisibility(hasLine ? View.VISIBLE : View.GONE);
        Button btnMark = (Button) findViewById(R.id.btnMarkBank);
        if (btnMark != null) btnMark.setVisibility(hasLine ? View.VISIBLE : View.GONE);
        if (mEtBankPattern != null && pattern != null) mEtBankPattern.setText(pattern);
        updateBankPatternPreview();
    }

    static CharSequence highlightMatch(String line, String pattern, int bgColor) {
        if (line == null) return "";
        if (pattern == null || pattern.isEmpty()) return line;
        try {
            Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(line);
            if (!m.find()) return line;
            int s = m.groupCount() > 0 ? m.start(1) : m.start();
            int e = m.groupCount() > 0 ? m.end(1)   : m.end();
            if (s < 0 || e <= s) return line;
            android.text.SpannableString ss = new android.text.SpannableString(line);
            ss.setSpan(new android.text.style.BackgroundColorSpan(bgColor), s, e, 0);
            ss.setSpan(new android.text.style.ForegroundColorSpan(0xFFFFFFFF), s, e, 0);
            return ss;
        } catch (Exception ex) {
            return line;
        }
    }

    void updateBankPatternPreview() {
        if (mEtBankPattern == null || mTvBankPatternPreview == null) return;
        String pattern = mEtBankPattern.getText().toString().trim();
        if (mTvBankOrigLine != null && mBankOrigLine != null && !mBankOrigLine.isEmpty()) {
            mTvBankOrigLine.setText(highlightMatch(mBankOrigLine, pattern, 0xFF0072B2));
        }
        if (pattern.isEmpty() || mBankOrigLine.isEmpty()) {
            mTvBankPatternPreview.setVisibility(View.GONE);
            return;
        }
        String result = applyFieldPattern(pattern, mBankOrigLine);
        if (!result.isEmpty()) {
            mTvBankPatternPreview.setText("→ " + result);
            mTvBankPatternPreview.setVisibility(View.VISIBLE);
            mEtBank.setText(result);
        } else {
            mTvBankPatternPreview.setText("(no match)");
            mTvBankPatternPreview.setVisibility(View.VISIBLE);
        }
    }

    void showMonthLine(String origLine, String pattern) {
        mMonthOrigLine = origLine;
        if (mTvMonthOrigLine == null) return;
        mTvMonthOrigLine.setText(highlightMatch(origLine, pattern, 0xFF009E73));
        boolean hasLine = origLine != null && !origLine.isEmpty();
        mTvMonthOrigLine.setVisibility(hasLine ? View.VISIBLE : View.GONE);
        Button btnMark = (Button) findViewById(R.id.btnMarkMonth);
        if (btnMark != null) btnMark.setVisibility(hasLine ? View.VISIBLE : View.GONE);
        if (mEtMonthPattern != null && pattern != null) mEtMonthPattern.setText(pattern);
        updateMonthPatternPreview();
    }

    void updateMonthPatternPreview() {
        if (mEtMonthPattern == null || mTvMonthPatternPreview == null) return;
        String pattern = mEtMonthPattern.getText().toString().trim();
        if (mTvMonthOrigLine != null && mMonthOrigLine != null && !mMonthOrigLine.isEmpty()) {
            mTvMonthOrigLine.setText(highlightMatch(mMonthOrigLine, pattern, 0xFF009E73));
        }
        if (pattern.isEmpty() || mMonthOrigLine.isEmpty()) {
            mTvMonthPatternPreview.setVisibility(View.GONE);
            return;
        }
        String result = applyFieldPattern(pattern, mMonthOrigLine);
        if (!result.isEmpty()) {
            mTvMonthPatternPreview.setText("→ " + result);
            mTvMonthPatternPreview.setVisibility(View.VISIBLE);
            mEtPdfMonth.setText(result);
        } else {
            mTvMonthPatternPreview.setText("(no match)");
            mTvMonthPatternPreview.setVisibility(View.VISIBLE);
        }
    }

    static String applyFieldPattern(String pattern, String line) {
        try {
            Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(line);
            if (!m.find()) return "";
            return m.groupCount() > 0 ? m.group(1) : m.group(0);
        } catch (Exception e) {
            return "";
        }
    }

    private static String extractPdfMonth(String line) {
        Matcher m1 = Pattern.compile("\\b(20\\d\\d)[\\-/](0[1-9]|1[0-2])\\b").matcher(line);
        if (m1.find()) return m1.group(1) + "-" + m1.group(2);
        Matcher m3 = Pattern.compile("\\b(0[1-9]|1[0-2])[\\-/](20\\d\\d)\\b").matcher(line);
        if (m3.find()) return m3.group(2) + "-" + m3.group(1);
        String[] months = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
        String[] nums   = {"01","02","03","04","05","06","07","08","09","10","11","12"};
        for (int i = 0; i < months.length; i++) {
            Matcher m2 = Pattern.compile(
                    "\\b" + months[i] + "[a-z]*[\\s,\\-]+(20\\d\\d)\\b",
                    Pattern.CASE_INSENSITIVE).matcher(line);
            if (m2.find()) return m2.group(1) + "-" + nums[i];
        }
        return "";
    }

    void finishWithSuccess(boolean patternSaved) {
        String msg = patternSaved
            ? getString(R.string.msg_expense_and_pattern_saved)
            : getString(R.string.msg_expense_saved);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    // ── Pattern building from template ──────────────────────────────────────

    private ExtractionPattern buildPatternFromTemplate(String template) {
        ExtractionPattern p = new ExtractionPattern();
        p.senderId        = mSenderId;
        p.templateText    = template;
        p.transactionType = mTxnType;
        p.isPdf           = getIntent().getBooleanExtra(EXTRA_IS_PDF, false);

        ExtractionPattern tmp = new ExtractionPattern();
        tmp.transactionType = mTxnType;
        p.name = tmp.getTypeLabel() + " "
            + new SimpleDateFormat("dd MMM", Locale.getDefault())
                  .format(new java.util.Date(mSelectedDateMs));

        template = template.trim(); // strip trailing whitespace to avoid literal mismatch

        StringBuilder regex   = new StringBuilder();
        int pos      = 0;
        int groupNum = 0;
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
        return "text";
    }

    private void assignGroup(ExtractionPattern p, String token, int group) {
        if (TOK_AMOUNT_CR.equals(token)) { p.amountCrGroup = group; return; }
        if (TOK_AMOUNT_DB.equals(token)) { p.amountDbGroup = group; return; }
        if (TOK_BALANCE.equals(token))  { p.balanceGroup  = group; return; }
        if (TOK_MERCHANT.equals(token)) { p.merchantGroup = group; return; }
        if (TOK_CARD.equals(token))     { p.cardGroup     = group; return; }
        if (TOK_ACNO.equals(token))     { p.accountGroup  = group; return; }
        if (TOK_UPI.equals(token))      { return; } // structural anchor — not stored
        if (TOK_DATE.equals(token))     { p.dateGroup     = group; return; }
        if (TOK_TIME.equals(token))     { p.timeGroup     = group; return; }
    }

    private String captureGroupFor(String fieldType) {
        switch (fieldType) {
            case "amount":
            case "balance": return "([\\d,]+\\.?\\d{0,2})";
            case "card":    return "(\\d{4})";
            case "upi":     return "([\\w@.\\-]+)";
            case "account": return "([X\\dx*]+)";
            case "date":    return "(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}|\\d{1,2}[\\- ][A-Za-z]{3}[\\- ]\\d{2,4})";
            case "time":    return "(\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s?[AP]M)?)";
            default:        return "([^\\n]+?)";
        }
    }

    private String groupStr(Matcher m, int group) {
        if (m == null || group < 0 || group > m.groupCount()) return "";
        String v = m.group(group);
        return v != null ? v : "";
    }

    // ── Date / time helpers ─────────────────────────────────────────────────

    private void tryApplyExtractedDate(String raw) {
        String[] formats = {"dd/MM/yyyy", "dd-MM-yyyy", "dd/MM/yy", "dd-MM-yy",
                            "yyyy-MM-dd", "dd MMM yyyy", "dd MMM yy",
                            "dd-MMM-yyyy", "dd-MMM-yy"};
        for (String fmt : formats) {
            try {
                java.util.Date d = new SimpleDateFormat(fmt, Locale.US).parse(raw);
                if (d != null) { mSelectedDateMs = d.getTime(); updateDateTimeDisplay(); return; }
            } catch (ParseException ignored) {}
        }
    }

    private void tryApplyExtractedTime(String raw) {
        String[] formats = {"HH:mm:ss", "HH:mm", "hh:mm:ss a", "hh:mm a"};
        for (String fmt : formats) {
            try {
                java.util.Date d = new SimpleDateFormat(fmt, Locale.US).parse(raw.trim());
                if (d == null) continue;
                Calendar timeCal = Calendar.getInstance();
                timeCal.setTime(d);
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(mSelectedDateMs);
                cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
                cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
                cal.set(Calendar.SECOND, 0);
                mSelectedDateMs = cal.getTimeInMillis();
                updateDateTimeDisplay();
                return;
            } catch (ParseException ignored) {}
        }
    }

    void onDateSet(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mSelectedDateMs);
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, day);
        mSelectedDateMs = cal.getTimeInMillis();
        updateDateTimeDisplay();
    }

    void onTimeSet(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mSelectedDateMs);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        mSelectedDateMs = cal.getTimeInMillis();
        updateDateTimeDisplay();
    }

    void updateDateTimeDisplay() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mSelectedDateMs);
        mEtDate.setText(new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.getTime()));
        mEtTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.getTime()));
    }

    // ── Extraction preview ──────────────────────────────────────────────────

    void updateRegexPreview() {
        String template = mEtTemplate.getText().toString();
        if (mTransLine == null || mTransLine.isEmpty() || template.isEmpty()) {
            mTvExtractPreview.setVisibility(View.GONE);
            return;
        }
        ExtractionPattern p = buildPatternFromTemplate(template);
        StringBuilder sb = new StringBuilder();
        Matcher m = null;
        try {
            m = Pattern.compile(p.templateRegex,
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(mTransLine);
            if (!m.find()) m = null;
        } catch (Exception e) {
            mTvExtractPreview.setText("Regex error: " + e.getMessage());
            mTvExtractPreview.setVisibility(View.VISIBLE);
            return;
        }
        if (m == null) {
            sb.append("(no match)\n\n");
        } else {
            addPreviewLine(sb, "Amount",   groupStr(m, p.amountGroup));
            addPreviewLine(sb, "Balance",  groupStr(m, p.balanceGroup));
            addPreviewLine(sb, "Merchant", groupStr(m, p.merchantGroup));
            addPreviewLine(sb, "Card",     groupStr(m, p.cardGroup));
            addPreviewLine(sb, "Account",  groupStr(m, p.accountGroup));
            addPreviewLine(sb, "Date",     groupStr(m, p.dateGroup));
            addPreviewLine(sb, "Time",     groupStr(m, p.timeGroup));
            if (sb.length() > 0) sb.append("\n");
        }
        sb.append(p.templateRegex);
        mTvExtractPreview.setText(sb.toString());
        mTvExtractPreview.setVisibility(View.VISIBLE);
    }

    private void addPreviewLine(StringBuilder sb, String label, String value) {
        if (!value.isEmpty()) sb.append(label).append(": ").append(value).append("\n");
    }

    void switchToTab(int tab) {
        if (mLayoutTab1 != null)
            mLayoutTab1.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        if (mLayoutTab2 != null)
            mLayoutTab2.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        if (mBtnTabStatement != null)
            mBtnTabStatement.setBackgroundResource(
                tab == 0 ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        if (mBtnTabTransaction != null)
            mBtnTabTransaction.setBackgroundResource(
                tab == 1 ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
    }

    // ============================================================
    // Static listener classes — D8 constraints
    // ============================================================

    static class TemplateWatcher implements TextWatcher {
        private final MapExpenseActivity mA;
        TemplateWatcher(MapExpenseActivity a) { mA = a; }
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
        public void afterTextChanged(Editable s) { mA.updateRegexPreview(); }
    }

    static class TokenButtonListener implements View.OnClickListener {
        private final MapExpenseActivity mA;
        private final String mToken;
        TokenButtonListener(MapExpenseActivity a, String token) { mA = a; mToken = token; }
        public void onClick(View v) { mA.insertToken(mToken); }
    }

    static class ClearTagsListener implements View.OnClickListener {
        private final MapExpenseActivity mA;
        ClearTagsListener(MapExpenseActivity a) { mA = a; }
        public void onClick(View v) { mA.clearAllTags(); }
    }

    static class TabListener implements View.OnClickListener {
        private final MapExpenseActivity mA;
        private final int mTab;
        TabListener(MapExpenseActivity a, int tab) { mA = a; mTab = tab; }
        public void onClick(View v) { mA.switchToTab(mTab); }
    }

    static class SaveSelectionListener implements View.OnTouchListener {
        private final MapExpenseActivity mA;
        SaveSelectionListener(MapExpenseActivity a) { mA = a; }
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                mA.mLastSelStart = mA.mTvTransLine.getSelectionStart();
                mA.mLastSelEnd   = mA.mTvTransLine.getSelectionEnd();
            }
            return false;
        }
    }

    static class PatternHintClickListener implements View.OnClickListener {
        private final MapExpenseActivity mA;
        PatternHintClickListener(MapExpenseActivity a) { mA = a; }
        public void onClick(View v) { mA.clearCurrentPattern(); }
    }

    static class PatternUpdateDialogListener implements DialogInterface.OnClickListener {
        private final MapExpenseActivity    mA;
        private final ExtractionPattern mPattern;
        private final boolean           mDoReApply;

        PatternUpdateDialogListener(MapExpenseActivity a, ExtractionPattern p, boolean doReApply) {
            mA = a; mPattern = p; mDoReApply = doReApply;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (mDoReApply) {
                mA.startReApply(mPattern);
            } else {
                mA.finishWithSuccess(true);
            }
        }
    }

    static class ReApplyThread extends Thread {
        private final MapExpenseActivity    mA;
        private final ExtractionPattern mPattern;
        private final ExpenseDatabase   mDb;
        private final Handler           mHandler;

        ReApplyThread(MapExpenseActivity a, ExtractionPattern p, ExpenseDatabase db, Handler h) {
            mA = a; mPattern = p; mDb = db; mHandler = h;
        }

        public void run() {
            ExpenseDatabase.ReApplyResult r = mDb.reApplyPattern(mPattern);
            mHandler.post(new ReApplyDoneRunnable(mA, r.updated, r.orphaned, r.adopted));
        }
    }

    static class ReApplyDoneRunnable implements Runnable {
        private final MapExpenseActivity mA;
        private final int            mUpdated;
        private final int            mOrphaned;
        private final int            mAdopted;
        ReApplyDoneRunnable(MapExpenseActivity a, int updated, int orphaned, int adopted) {
            mA = a; mUpdated = updated; mOrphaned = orphaned; mAdopted = adopted;
        }
        public void run() {
            if (!mA.isFinishing()) mA.onReApplyDone(mUpdated, mOrphaned, mAdopted);
        }
    }

    static class TxnTypeSelectedListener implements AdapterView.OnItemSelectedListener {
        private final MapExpenseActivity mA;
        TxnTypeSelectedListener(MapExpenseActivity a) { mA = a; }
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            mA.mTxnType = TYPE_VALUES[pos];
            ExtractionPattern tmp = new ExtractionPattern();
            tmp.transactionType = mA.mTxnType;
            mA.mCbOnline.setChecked(tmp.isOnlineType());
        }
        public void onNothingSelected(AdapterView<?> parent) {}
    }

    static class DateClickListener implements View.OnClickListener {
        private final MapExpenseActivity mA;
        DateClickListener(MapExpenseActivity a) { mA = a; }
        public void onClick(View v) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(mA.mSelectedDateMs);
            new DatePickerDialog(mA, new DateSetListener(mA),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show();
        }
    }

    static class TimeClickListener implements View.OnClickListener {
        private final MapExpenseActivity mA;
        TimeClickListener(MapExpenseActivity a) { mA = a; }
        public void onClick(View v) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(mA.mSelectedDateMs);
            new TimePickerDialog(mA, new TimeSetListener(mA),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        }
    }

    static class DateSetListener implements DatePickerDialog.OnDateSetListener {
        private final MapExpenseActivity mA;
        DateSetListener(MapExpenseActivity a) { mA = a; }
        public void onDateSet(DatePicker v, int year, int month, int day) {
            mA.onDateSet(year, month, day);
        }
    }

    static class TimeSetListener implements TimePickerDialog.OnTimeSetListener {
        private final MapExpenseActivity mA;
        TimeSetListener(MapExpenseActivity a) { mA = a; }
        public void onTimeSet(TimePicker v, int hour, int minute) { mA.onTimeSet(hour, minute); }
    }

    static class SaveClickListener implements View.OnClickListener {
        private final MapExpenseActivity mA;
        SaveClickListener(MapExpenseActivity a) { mA = a; }
        public void onClick(View v) { mA.save(); }
    }

    // ── PDF picker static listener classes ────────────────────────────────────

    static class MarkBankListener implements View.OnClickListener {
        private final MapExpenseActivity mA;
        MarkBankListener(MapExpenseActivity a) { mA = a; }
        public void onClick(View v) { mA.markBankSelection(); }
    }

    static class MarkMonthListener implements View.OnClickListener {
        private final MapExpenseActivity mA;
        MarkMonthListener(MapExpenseActivity a) { mA = a; }
        public void onClick(View v) { mA.markMonthSelection(); }
    }

    static class PatternSnippetListener implements View.OnClickListener {
        private final EditText mTarget;
        private final String   mSnippet;
        PatternSnippetListener(EditText target, String snippet) { mTarget = target; mSnippet = snippet; }
        public void onClick(View v) {
            int cursor = mTarget.getSelectionStart();
            if (cursor < 0) cursor = mTarget.getText().length();
            mTarget.getText().insert(cursor, mSnippet);
        }
    }

    void markBankSelection() {
        if (mBankOrigLine == null || mBankOrigLine.isEmpty() || mTvBankOrigLine == null) return;
        int s = mTvBankOrigLine.getSelectionStart();
        int e = mTvBankOrigLine.getSelectionEnd();
        if (s < 0 || e < 0 || s == e) {
            Toast.makeText(this, R.string.msg_select_text_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if (s > e) { int tmp = s; s = e; e = tmp; }
        String prefix = java.util.regex.Pattern.quote(mBankOrigLine.substring(0, s));
        String suffix = java.util.regex.Pattern.quote(mBankOrigLine.substring(e));
        mEtBankPattern.setText(prefix + "(.+?)" + suffix);
    }

    void markMonthSelection() {
        if (mMonthOrigLine == null || mMonthOrigLine.isEmpty() || mTvMonthOrigLine == null) return;
        int s = mTvMonthOrigLine.getSelectionStart();
        int e = mTvMonthOrigLine.getSelectionEnd();
        if (s < 0 || e < 0 || s == e) {
            Toast.makeText(this, R.string.msg_select_text_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if (s > e) { int tmp = s; s = e; e = tmp; }
        String prefix = java.util.regex.Pattern.quote(mMonthOrigLine.substring(0, s));
        String suffix = java.util.regex.Pattern.quote(mMonthOrigLine.substring(e));
        mEtMonthPattern.setText(prefix + "(.+?)" + suffix);
    }

    static class BankPatternWatcher implements TextWatcher {
        private final MapExpenseActivity mA;
        BankPatternWatcher(MapExpenseActivity a) { mA = a; }
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
        public void afterTextChanged(Editable s) {
            mA.updateBankPatternPreview();
            String pat = s.toString().trim();
            mA.mDb.setPdfFieldPattern("bank", pat.isEmpty() ? null : pat);
        }
    }

    static class MonthPatternWatcher implements TextWatcher {
        private final MapExpenseActivity mA;
        MonthPatternWatcher(MapExpenseActivity a) { mA = a; }
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
        public void afterTextChanged(Editable s) {
            mA.updateMonthPatternPreview();
            String pat = s.toString().trim();
            mA.mDb.setPdfFieldPattern("month", pat.isEmpty() ? null : pat);
        }
    }

    static class PdfPickBtnClickListener implements View.OnClickListener {
        private final MapExpenseActivity mA;
        PdfPickBtnClickListener(MapExpenseActivity a) { mA = a; }
        public void onClick(View v) {
            if (mA.mPdfLoaded) {
                mA.showPdfPicker();
            } else {
                mA.startPdfLoad(null);
            }
        }
    }

    static class PdfModeButtonListener implements View.OnClickListener {
        private final MapExpenseActivity mA;
        private final int            mMode;
        PdfModeButtonListener(MapExpenseActivity a, int mode) { mA = a; mMode = mode; }
        public void onClick(View v) { mA.setPdfPickerMode(mMode); }
    }

    static class PdfPickerLineListener implements AdapterView.OnItemClickListener {
        private final MapExpenseActivity mA;
        PdfPickerLineListener(MapExpenseActivity a) { mA = a; }
        public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            if (mA.mPdfPickerAdapter == null) return;
            int mode = mA.mPdfPickerAdapter.getCurrentMode();
            // Check if this tap is a selection (not a deselect)
            boolean wasSelected = (mode == PdfPickerAdapter.MODE_BANK  && mA.mPdfPickerAdapter.getBankIdx()  == pos)
                                || (mode == PdfPickerAdapter.MODE_MONTH && mA.mPdfPickerAdapter.getMonthIdx() == pos);
            mA.mPdfPickerAdapter.onItemClick(pos);
            if (!wasSelected && pos >= 0 && pos < mA.mPdfItems.size()) {
                String lineText = mA.mPdfItems.get(pos).text;
                if (mode == PdfPickerAdapter.MODE_BANK && mA.mEtBankPattern != null
                        && mA.mEtBankPattern.getText().toString().trim().isEmpty()) {
                    mA.mEtBankPattern.setText(lineText);
                } else if (mode == PdfPickerAdapter.MODE_MONTH && mA.mEtMonthPattern != null
                        && mA.mEtMonthPattern.getText().toString().trim().isEmpty()) {
                    mA.mEtMonthPattern.setText(lineText);
                }
            }
        }
    }

    static class PdfSubmitListener implements DialogInterface.OnClickListener {
        private final MapExpenseActivity mA;
        PdfSubmitListener(MapExpenseActivity a) { mA = a; }
        public void onClick(DialogInterface d, int which) {
            if (mA.mPdfPickerAdapter == null) return;
            mA.onPdfPickerSubmit(
                    mA.mPdfPickerAdapter.getBankIdx(),
                    mA.mPdfPickerAdapter.getMonthIdx(),
                    mA.mPdfPickerAdapter.getTransIdx());
        }
    }

    static class PdfPasswordOkListener implements DialogInterface.OnClickListener {
        private final MapExpenseActivity mA;
        private final EditText       mEt;
        PdfPasswordOkListener(MapExpenseActivity a, EditText et) { mA = a; mEt = et; }
        public void onClick(DialogInterface dialog, int which) {
            mA.startPdfLoad(mEt.getText().toString());
        }
    }

    static class PdfPasswordCancelListener implements DialogInterface.OnClickListener {
        private final MapExpenseActivity mA;
        PdfPasswordCancelListener(MapExpenseActivity a) { mA = a; }
        public void onClick(DialogInterface dialog, int which) {
            mA.mBtnPickFromPdf.setEnabled(true);
            mA.mPendingBulkPattern   = null;
            mA.mPendingBulkPatternId = -1;
            mA.mPendingBulkReApply   = false;
        }
    }

    static class BulkImportThread extends Thread {
        private final MapExpenseActivity               mA;
        private final List<PdfLineAdapter.PdfLine> mItems;
        private final ExtractionPattern            mPattern;
        private final long                         mPatternId;
        private final long                         mStatementId;
        private final String                       mBank;
        private final String                       mReason;
        private final String                       mRemarks;
        private final boolean                      mIsOnline;
        private final String                       mTxnType;
        private final ExpenseDatabase              mDb;
        private final boolean                      mDoReApply;
        private final Handler                      mHandler;

        BulkImportThread(MapExpenseActivity a, List<PdfLineAdapter.PdfLine> items,
                         ExtractionPattern pattern, long patternId, long statementId,
                         String bank, String reason, String remarks,
                         boolean isOnline, String txnType, ExpenseDatabase db,
                         boolean doReApply, Handler h) {
            mA = a; mItems = items; mPattern = pattern; mPatternId = patternId;
            mStatementId = statementId;
            mBank = bank; mReason = reason; mRemarks = remarks;
            mIsOnline = isOnline; mTxnType = txnType; mDb = db;
            mDoReApply = doReApply; mHandler = h;
        }

        public void run() {
            Set<String> already = mDb.getAppliedSmsBodies();
            int imported = 0;
            int scanned  = mItems.size();
            boolean autoCredit = mPattern.senderId > 0
                    && mDb.getSenderPdfAutoCredit(mPattern.senderId);
            com.ldsa.myfintracker.pdf.PdfCreditClassifier classifier =
                    new com.ldsa.myfintracker.pdf.PdfCreditClassifier(autoCredit);

            for (PdfLineAdapter.PdfLine item : mItems) {
                if (!mPattern.matches(item.text)) continue;
                if (already.contains(item.text)) continue;
                String amtStr    = "";
                boolean amountCrMatched = false;
                if (mPattern.amountCrGroup >= 0) {
                    amtStr = mPattern.extractGroup(item.text, mPattern.amountCrGroup)
                                     .replaceAll("[^0-9.]", "");
                    if (!amtStr.isEmpty()) amountCrMatched = true;
                }
                if (amtStr.isEmpty() && mPattern.amountDbGroup >= 0) {
                    amtStr = mPattern.extractGroup(item.text, mPattern.amountDbGroup)
                                     .replaceAll("[^0-9.]", "");
                }
                if (amtStr.isEmpty() && mPattern.amountGroup >= 0) {
                    amtStr = mPattern.extractGroup(item.text, mPattern.amountGroup)
                                     .replaceAll("[^0-9.]", "");
                }
                if (amtStr.isEmpty()) continue;
                double amount;
                try { amount = Double.parseDouble(amtStr); }
                catch (NumberFormatException ex) { continue; }
                String balStr = mPattern.extractGroup(item.text, mPattern.balanceGroup)
                                        .replaceAll("[^0-9.,]", "").replace(",", "");
                boolean hasBalance = !balStr.isEmpty();
                double balance = 0;
                if (hasBalance) {
                    try { balance = Double.parseDouble(balStr); }
                    catch (NumberFormatException ignored) { hasBalance = false; }
                }

                com.ldsa.myfintracker.pdf.PdfCreditClassifier.Decision cd =
                        classifier.classify(hasBalance, balance, amountCrMatched);
                if (cd.skip) continue;  // zero balance delta — drop

                String dateStr = mPattern.extractGroup(item.text, mPattern.dateGroup).trim();
                String timeStr = mPattern.extractGroup(item.text, mPattern.timeGroup).trim();
                long dateMs = parseDateMs(dateStr);
                if (dateMs == 0) dateMs = System.currentTimeMillis();
                dateMs = applyTimeToMs(dateMs, timeStr);

                Expense e = new Expense();
                e.amount          = amount;
                e.isCredit        = cd.isCredit;
                e.dateMs          = dateMs;
                e.merchant        = mPattern.extractGroup(item.text, mPattern.merchantGroup).trim();
                e.card            = mPattern.extractGroup(item.text, mPattern.cardGroup).trim();
                e.accountNumber   = mPattern.extractGroup(item.text, mPattern.accountGroup).trim();
                e.balance         = balance;
                e.reason          = mReason;
                e.isOnline        = mIsOnline;
                e.bank            = mBank;
                e.originalSms     = item.text;
                e.transactionType = mTxnType;
                e.remarks         = mRemarks;
                e.patternId       = mPatternId;
                e.createdAt       = System.currentTimeMillis();
                e.source          = "pdf";
                e.pdfStatementId  = mStatementId;
                mDb.insertExpense(e);
                imported++;
            }
            int reApplied = 0;
            if (mDoReApply) {
                ExpenseDatabase.ReApplyResult r = mDb.reApplyPattern(mPattern);
                // Fold adopted into the "updated existing" counter for the bulk-import
                // toast — both represent rows that now point at this pattern.
                reApplied = r.updated + r.adopted;
            }
            mHandler.post(new BulkImportDoneRunnable(mA, imported, reApplied, scanned));
        }
    }

    static class BulkImportDoneRunnable implements Runnable {
        private final MapExpenseActivity mA;
        private final int mImported;
        private final int mReApplied;
        private final int mScanned;
        BulkImportDoneRunnable(MapExpenseActivity a, int imported, int reApplied, int scanned) {
            mA = a; mImported = imported; mReApplied = reApplied; mScanned = scanned;
        }
        public void run() { if (!mA.isFinishing()) mA.onBulkImportDone(mImported, mReApplied, mScanned); }
    }

    static class PdfLoadThread extends Thread {
        private final MapExpenseActivity   mA;
        private final Uri              mUri;
        private final boolean          mIsPdf;
        private final String           mPassword;
        private final long             mSenderId;
        private final Handler          mHandler;
        private final ExpenseDatabase  mDb;

        PdfLoadThread(MapExpenseActivity a, Uri uri, boolean isPdf, String password,
                      long senderId, Handler h, ExpenseDatabase db) {
            mA = a; mUri = uri; mIsPdf = isPdf; mPassword = password;
            mSenderId = senderId; mHandler = h; mDb = db;
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
                mHandler.post(new PdfLoadDoneRunnable(mA,
                        new ArrayList<String>(), new ArrayList<PdfLineAdapter.PdfLine>(), raw));
                return;
            }

            List<String> lines = PdfInboxActivity.splitLines(raw);
            List<ExtractionPattern> patterns = PdfInboxActivity.loadPatterns(mDb, mSenderId);
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
            mHandler.post(new PdfLoadDoneRunnable(mA, lines, items, statusMsg));
        }
    }

    static class PdfLoadDoneRunnable implements Runnable {
        private final MapExpenseActivity               mA;
        private final List<String>                 mLines;
        private final List<PdfLineAdapter.PdfLine> mItems;
        private final String                       mStatus;
        PdfLoadDoneRunnable(MapExpenseActivity a, List<String> lines,
                            List<PdfLineAdapter.PdfLine> items, String status) {
            mA = a; mLines = lines; mItems = items; mStatus = status;
        }
        public void run() { if (!mA.isFinishing()) mA.onPdfLinesLoaded(mLines, mItems, mStatus); }
    }

    // ── PDF picker list adapter ───────────────────────────────────────────────

    static class PdfPickerAdapter extends BaseAdapter {
        static final int MODE_BANK  = 0;
        static final int MODE_MONTH = 1;
        static final int MODE_TRANS = 2;

        private static final int[] BG_COLORS = { 0xFFBBDEFB, 0xFFC8E6C9, 0xFFFFE0B2 };

        private final LayoutInflater              mInflater;
        private final List<PdfLineAdapter.PdfLine> mItems;
        private final int[]                        mSelModes;
        private int mCurrentMode = MODE_TRANS;

        PdfPickerAdapter(android.content.Context ctx, List<PdfLineAdapter.PdfLine> items) {
            mInflater = LayoutInflater.from(ctx);
            mItems    = items;
            mSelModes = new int[items.size()];
            for (int i = 0; i < mSelModes.length; i++) mSelModes[i] = -1;
        }

        void setMode(int mode) { mCurrentMode = mode; }
        int  getCurrentMode() { return mCurrentMode; }

        void onItemClick(int pos) {
            if (pos < 0 || pos >= mItems.size()) return;
            if (mSelModes[pos] == mCurrentMode) {
                mSelModes[pos] = -1;
            } else {
                for (int i = 0; i < mSelModes.length; i++) {
                    if (mSelModes[i] == mCurrentMode) mSelModes[i] = -1;
                }
                mSelModes[pos] = mCurrentMode;
            }
            notifyDataSetChanged();
        }

        private int getSelected(int mode) {
            for (int i = 0; i < mSelModes.length; i++) {
                if (mSelModes[i] == mode) return i;
            }
            return -1;
        }

        int getBankIdx()  { return getSelected(MODE_BANK);  }
        int getMonthIdx() { return getSelected(MODE_MONTH); }
        int getTransIdx() { return getSelected(MODE_TRANS); }

        @Override public int getCount()          { return mItems.size(); }
        @Override public Object getItem(int pos) { return mItems.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.item_pdf_line, parent, false);
            }
            PdfLineAdapter.PdfLine line = mItems.get(pos);
            TextView tvText  = (TextView) convertView.findViewById(R.id.tvPdfLineText);
            TextView tvMatch = (TextView) convertView.findViewById(R.id.tvPdfLineMatch);

            tvText.setText(line.text);
            if (line.matchSummary != null && !line.matchSummary.isEmpty()) {
                tvMatch.setText(line.matchSummary);
                tvMatch.setVisibility(View.VISIBLE);
            } else {
                tvMatch.setVisibility(View.GONE);
            }

            int selMode = mSelModes[pos];
            convertView.setBackgroundColor(
                    selMode >= 0 && selMode < BG_COLORS.length ? BG_COLORS[selMode] : 0xFFFFFFFF);
            return convertView;
        }
    }
}
