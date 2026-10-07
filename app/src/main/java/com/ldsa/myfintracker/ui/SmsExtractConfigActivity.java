package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.ExtractionPattern;

public class SmsExtractConfigActivity extends Activity {

    public static final String EXTRA_SENDER_ID  = "sender_id";
    public static final String EXTRA_PATTERN_ID = "pattern_id";
    public static final String EXTRA_TRANS_LINE   = "sms_body";

    static final String[] TYPE_VALUES = {
        ExtractionPattern.TYPE_OTHER,
        ExtractionPattern.TYPE_UPI,
        ExtractionPattern.TYPE_CARD_ONLINE,
        ExtractionPattern.TYPE_CARD_POS,
        ExtractionPattern.TYPE_NETBANKING_PURCHASE,
        ExtractionPattern.TYPE_NETBANKING_TRANSFER,
        ExtractionPattern.TYPE_ATM
    };

    private String mSmsBody;
    private long   mSenderId;
    private long   mPatternId;
    private ExpenseDatabase mDb;
    private ExtractionPattern mPattern;

    private EditText     mEtPatternName;
    private Spinner      mSpinnerType;
    private TextView     mTvTemplateText;
    private TextView     mTvTemplateRegex;
    private LinearLayout mCardPreview;
    private TextView     mTvPreviewAmount;
    private TextView     mTvPreviewBalance;
    private TextView     mTvPreviewMerchant;
    private TextView     mTvPreviewCard;
    private TextView     mTvPreviewAccount;
    private TextView     mTvPreviewDate;
    private TextView     mTvPreviewTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_extract_config);

        mSenderId  = getIntent().getLongExtra(EXTRA_SENDER_ID,  -1L);
        mPatternId = getIntent().getLongExtra(EXTRA_PATTERN_ID, -1L);
        mSmsBody   = getIntent().getStringExtra(EXTRA_TRANS_LINE);
        mDb        = ExpenseDatabase.getInstance(this);

        LinearLayout cardSmsBody = (LinearLayout) findViewById(R.id.cardSmsBody);
        TextView     tvSmsBody   = (TextView)     findViewById(R.id.tvSmsBody);
        mEtPatternName           = (EditText)     findViewById(R.id.etPatternName);
        mSpinnerType             = (Spinner)      findViewById(R.id.spinnerTxnType);
        mTvTemplateText          = (TextView)     findViewById(R.id.tvTemplateText);
        mTvTemplateRegex         = (TextView)     findViewById(R.id.tvTemplateRegex);
        mCardPreview             = (LinearLayout) findViewById(R.id.cardExtractionPreview);
        mTvPreviewAmount         = (TextView)     findViewById(R.id.tvPreviewAmount);
        mTvPreviewBalance        = (TextView)     findViewById(R.id.tvPreviewBalance);
        mTvPreviewMerchant       = (TextView)     findViewById(R.id.tvPreviewMerchant);
        mTvPreviewCard           = (TextView)     findViewById(R.id.tvPreviewCard);
        mTvPreviewAccount        = (TextView)     findViewById(R.id.tvPreviewAccount);
        mTvPreviewDate           = (TextView)     findViewById(R.id.tvPreviewDate);
        mTvPreviewTime           = (TextView)     findViewById(R.id.tvPreviewTime);
        Button btnSave           = (Button)       findViewById(R.id.btnSaveExtraction);

        if (mSmsBody != null && !mSmsBody.isEmpty()) {
            cardSmsBody.setVisibility(View.VISIBLE);
            tvSmsBody.setText(mSmsBody);
        } else {
            cardSmsBody.setVisibility(View.GONE);
        }

        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(
            this, R.array.transaction_type_labels, android.R.layout.simple_spinner_item);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerType.setAdapter(typeAdapter);

        if (mPatternId >= 0) {
            mPattern = mDb.getPatternById(mPatternId);
            if (mPattern != null) populate(mPattern);
        }

        if (mPattern == null) mCardPreview.setVisibility(View.GONE);

        btnSave.setOnClickListener(new SaveClickListener(this));
    }

    private void populate(ExtractionPattern p) {
        if (p.name != null) mEtPatternName.setText(p.name);
        String type = p.transactionType != null ? p.transactionType : "";
        for (int i = 0; i < TYPE_VALUES.length; i++) {
            if (TYPE_VALUES[i].equals(type)) { mSpinnerType.setSelection(i); break; }
        }
        mTvTemplateText.setText(p.templateText != null ? p.templateText : "");
        mTvTemplateRegex.setText(p.templateRegex != null ? p.templateRegex : "");

        if (mSmsBody != null && !mSmsBody.isEmpty() && p.templateRegex != null) {
            showExtractionPreview(p);
        } else {
            mCardPreview.setVisibility(View.GONE);
        }
    }

    private void showExtractionPreview(ExtractionPattern p) {
        String amt  = p.extractGroup(mSmsBody, p.amountGroup);
        String bal  = p.extractGroup(mSmsBody, p.balanceGroup);
        String mer  = p.extractGroup(mSmsBody, p.merchantGroup);
        String card = p.extractGroup(mSmsBody, p.cardGroup);
        String acct = p.extractGroup(mSmsBody, p.accountGroup);
        String date = p.extractGroup(mSmsBody, p.dateGroup);
        String time = p.extractGroup(mSmsBody, p.timeGroup);

        boolean hasAny = !amt.isEmpty() || !bal.isEmpty() || !mer.isEmpty()
                      || !card.isEmpty() || !acct.isEmpty() || !date.isEmpty() || !time.isEmpty();
        if (!hasAny) { mCardPreview.setVisibility(View.GONE); return; }

        mCardPreview.setVisibility(View.VISIBLE);
        mTvPreviewAmount.setText(amt.isEmpty()   ? "(—)" : amt);
        mTvPreviewBalance.setText(bal.isEmpty()  ? "(—)" : bal);
        mTvPreviewMerchant.setText(mer.isEmpty() ? "(—)" : mer);
        mTvPreviewCard.setText(card.isEmpty()    ? "(—)" : card);
        mTvPreviewAccount.setText(acct.isEmpty() ? "(—)" : acct);
        mTvPreviewDate.setText(date.isEmpty()    ? "(—)" : date);
        mTvPreviewTime.setText(time.isEmpty()    ? "(—)" : time);
    }

    void save() {
        if (mPattern == null) { finish(); return; }
        mPattern.name            = mEtPatternName.getText().toString().trim();
        mPattern.transactionType = TYPE_VALUES[mSpinnerType.getSelectedItemPosition()];
        mDb.updatePattern(mPattern);
        Toast.makeText(this, R.string.msg_extraction_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    // ============================================================
    // Static classes — D8 constraints
    // ============================================================

    static class SaveClickListener implements View.OnClickListener {
        private final SmsExtractConfigActivity mA;
        SaveClickListener(SmsExtractConfigActivity a) { mA = a; }
        public void onClick(View v) { mA.save(); }
    }
}
