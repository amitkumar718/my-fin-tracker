package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Card;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.ExtractionPattern;
import com.ldsa.myfintracker.db.SenderConfig;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {

    private static final String PREF_FILE    = "fin_prefs";
    private static final String PREF_LANDING = "landing_page";

    private Spinner       mSpinnerLanding;
    private LinearLayout  mContainerSenders;
    private TextView      mTvNoSenders;
    private ExpenseDatabase mDb;
    private long mPendingDeleteSenderId  = -1;
    private long mPendingDeletePatternId = -1;
    private long mPendingDeleteCardId    = -1;

    // card edit dialog state
    long   mEditCardSenderId = -1;
    long   mEditCardId       = -1;
    EditText  mCardNameEdit;
    RadioGroup mCardTypeGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mDb = ExpenseDatabase.getInstance(this);

        mSpinnerLanding   = (Spinner)      findViewById(R.id.spinnerLanding);
        mContainerSenders = (LinearLayout) findViewById(R.id.containerSenders);
        mTvNoSenders      = (TextView)     findViewById(R.id.tvNoSenders);
        Button btnAdd     = (Button)       findViewById(R.id.btnAddSender);

        ArrayAdapter<CharSequence> landingAdapter = ArrayAdapter.createFromResource(
            this, R.array.landing_page_labels, android.R.layout.simple_spinner_item);
        landingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerLanding.setAdapter(landingAdapter);

        SharedPreferences prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        String landing = prefs.getString(PREF_LANDING, "expenses");
        mSpinnerLanding.setSelection("sms".equals(landing) ? 1 : 0);
        mSpinnerLanding.setOnItemSelectedListener(new LandingSelectedListener(this));

        btnAdd.setOnClickListener(new AddSenderClickListener(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadSenders();
    }

    void saveLandingPref(int pos) {
        getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit()
            .putString(PREF_LANDING, pos == 1 ? "sms" : "expenses")
            .apply();
    }

    void reloadSenders() {
        List<SenderConfig> senders = mDb.getAllSenders();
        mContainerSenders.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        float dp = getResources().getDisplayMetrics().density;

        for (int i = 0; i < senders.size(); i++) {
            SenderConfig s = senders.get(i);

            if (i > 0) addVerticalSpace(dp * 12);

            // ── sender header row ──────────────────────────────────
            View senderRow = inflater.inflate(R.layout.item_sender, mContainerSenders, false);
            ((TextView) senderRow.findViewById(R.id.tvSenderName)).setText(s.getLabel());
            ((TextView) senderRow.findViewById(R.id.tvSenderPattern)).setText(s.pattern);
            senderRow.findViewById(R.id.tvRegexBadge)
                .setVisibility(s.isRegex ? View.VISIBLE : View.GONE);
            senderRow.setOnClickListener(new SenderRowClickListener(this, s.id));
            senderRow.setOnLongClickListener(new SenderRowLongClickListener(this, s.id));
            mContainerSenders.addView(senderRow);

            // ── extraction patterns section ────────────────────────
            List<ExtractionPattern> patterns = mDb.getPatternsBySender(s.id);
            TextView tvPatternsLabel = new TextView(this);
            tvPatternsLabel.setText(R.string.label_extraction_patterns);
            tvPatternsLabel.setTextColor(0xFF757575);
            tvPatternsLabel.setTextSize(11);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin    = (int)(dp * 6);
            lp.leftMargin   = (int)(dp * 4);
            lp.bottomMargin = (int)(dp * 2);
            tvPatternsLabel.setLayoutParams(lp);
            mContainerSenders.addView(tvPatternsLabel);

            for (ExtractionPattern p : patterns) {
                View pRow = inflater.inflate(R.layout.item_extraction_pattern,
                    mContainerSenders, false);
                String label = (p.name != null && !p.name.isEmpty()) ? p.name : "Pattern #" + p.id;
                ((TextView) pRow.findViewById(R.id.tvPatternName)).setText(label);
                TextView badge = (TextView) pRow.findViewById(R.id.tvPatternTypeBadge);
                if (p.transactionType != null && !p.transactionType.isEmpty()) {
                    badge.setVisibility(View.VISIBLE);
                    badge.setText(p.getTypeLabel());
                } else {
                    badge.setVisibility(View.GONE);
                }
                LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                plp.leftMargin   = (int)(dp * 8);
                plp.bottomMargin = (int)(dp * 4);
                pRow.setLayoutParams(plp);
                pRow.setOnClickListener(new PatternRowClickListener(this, p.id, s.id));
                pRow.setOnLongClickListener(new PatternRowLongClickListener(this, p.id));
                mContainerSenders.addView(pRow);
            }

            // ── cards section ──────────────────────────────────────
            List<Card> cards = mDb.getCardsBySender(s.id);
            TextView tvCardsLabel = new TextView(this);
            tvCardsLabel.setText(R.string.label_cards);
            tvCardsLabel.setTextColor(0xFF757575);
            tvCardsLabel.setTextSize(11);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.leftMargin   = (int)(dp * 4);
            clp.bottomMargin = (int)(dp * 2);
            tvCardsLabel.setLayoutParams(clp);
            mContainerSenders.addView(tvCardsLabel);

            for (Card card : cards) {
                View cRow = inflater.inflate(R.layout.item_card, mContainerSenders, false);
                ((TextView) cRow.findViewById(R.id.tvCardLabel)).setText(card.getLabel());
                ((TextView) cRow.findViewById(R.id.tvCardLast4)).setText(
                    card.last4 != null ? "**" + card.last4 : "");
                ((TextView) cRow.findViewById(R.id.tvCardTypeBadge)).setText(card.getTypeLabel());
                LinearLayout.LayoutParams rclp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rclp.leftMargin   = (int)(dp * 8);
                rclp.bottomMargin = (int)(dp * 4);
                cRow.setLayoutParams(rclp);
                cRow.setOnClickListener(new CardRowClickListener(this, card.id, s.id));
                cRow.setOnLongClickListener(new CardRowLongClickListener(this, card.id));
                mContainerSenders.addView(cRow);
            }

            Button btnAddCard = new Button(this);
            btnAddCard.setText(R.string.btn_add_card);
            btnAddCard.setTextSize(11);
            btnAddCard.setTextColor(0xFF1976D2);
            btnAddCard.setBackground(getResources().getDrawable(R.drawable.bg_button_secondary));
            LinearLayout.LayoutParams bclp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, (int)(dp * 32));
            bclp.leftMargin   = (int)(dp * 8);
            bclp.topMargin    = (int)(dp * 2);
            bclp.bottomMargin = (int)(dp * 4);
            btnAddCard.setLayoutParams(bclp);
            btnAddCard.setOnClickListener(new AddCardClickListener(this, s.id));
            mContainerSenders.addView(btnAddCard);
        }

        mTvNoSenders.setVisibility(senders.isEmpty() ? View.VISIBLE : View.GONE);
        mContainerSenders.setVisibility(senders.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void addVerticalSpace(float heightPx) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (int) heightPx));
        mContainerSenders.addView(spacer);
    }

    void openSenderTemplate(long senderId) {
        Intent i = new Intent(this, SenderTemplateActivity.class);
        i.putExtra(SenderTemplateActivity.EXTRA_SENDER_ID, senderId);
        startActivity(i);
    }

    void openExtractConfig(long patternId, long senderId) {
        Intent i = new Intent(this, SmsExtractConfigActivity.class);
        i.putExtra(SmsExtractConfigActivity.EXTRA_PATTERN_ID, patternId);
        i.putExtra(SmsExtractConfigActivity.EXTRA_SENDER_ID, senderId);
        startActivity(i);
    }

    void confirmDeleteSender(long id) {
        mPendingDeleteSenderId = id;
        new AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete_sender)
            .setPositiveButton(android.R.string.ok, new DeleteSenderConfirmListener(this))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void deleteSender() {
        if (mPendingDeleteSenderId >= 0) {
            mDb.deleteSender(mPendingDeleteSenderId);
            mPendingDeleteSenderId = -1;
            Toast.makeText(this, R.string.msg_sender_deleted, Toast.LENGTH_SHORT).show();
            reloadSenders();
        }
    }

    void confirmDeletePattern(long id) {
        mPendingDeletePatternId = id;
        new AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete_pattern)
            .setPositiveButton(android.R.string.ok, new DeletePatternConfirmListener(this))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void deletePattern() {
        if (mPendingDeletePatternId >= 0) {
            mDb.deletePattern(mPendingDeletePatternId);
            mPendingDeletePatternId = -1;
            Toast.makeText(this, R.string.msg_pattern_deleted, Toast.LENGTH_SHORT).show();
            reloadSenders();
        }
    }

    void showCardDialog(long cardId, long senderId) {
        mEditCardId       = cardId;
        mEditCardSenderId = senderId;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_card, null);
        mCardNameEdit  = (EditText)   dialogView.findViewById(R.id.etCardName);
        mCardTypeGroup = (RadioGroup) dialogView.findViewById(R.id.rgCardType);
        EditText etLast4 = (EditText) dialogView.findViewById(R.id.etCardLast4);

        if (cardId >= 0) {
            Card existing = mDb.getCardById(cardId);
            if (existing != null) {
                if (existing.displayName != null) mCardNameEdit.setText(existing.displayName);
                if (existing.last4 != null)       etLast4.setText(existing.last4);
                mCardTypeGroup.check(existing.cardType == Card.TYPE_CREDIT
                    ? R.id.rbCredit : R.id.rbDebit);
            }
        }

        new AlertDialog.Builder(this)
            .setTitle(cardId >= 0 ? "Edit Card" : "Add Card")
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok,
                new SaveCardDialogListener(this, etLast4))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void saveCard(EditText etLast4) {
        Card card;
        if (mEditCardId >= 0) {
            card = mDb.getCardById(mEditCardId);
            if (card == null) card = new Card();
        } else {
            card = new Card();
        }
        card.senderId    = mEditCardSenderId;
        card.last4       = etLast4.getText().toString().trim();
        card.displayName = mCardNameEdit.getText().toString().trim();
        card.cardType    = (mCardTypeGroup.getCheckedRadioButtonId() == R.id.rbCredit)
            ? Card.TYPE_CREDIT : Card.TYPE_DEBIT;

        if (mEditCardId >= 0 && card.id > 0) {
            mDb.updateCard(card);
        } else {
            mDb.insertCard(card);
        }
        Toast.makeText(this, R.string.msg_card_saved, Toast.LENGTH_SHORT).show();
        reloadSenders();
    }

    void confirmDeleteCard(long id) {
        mPendingDeleteCardId = id;
        new AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete_card)
            .setPositiveButton(android.R.string.ok, new DeleteCardConfirmListener(this))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void deleteCard() {
        if (mPendingDeleteCardId >= 0) {
            mDb.deleteCard(mPendingDeleteCardId);
            mPendingDeleteCardId = -1;
            Toast.makeText(this, R.string.msg_card_deleted, Toast.LENGTH_SHORT).show();
            reloadSenders();
        }
    }

    // ============================================================
    // Static listener classes — D8 constraints
    // ============================================================

    static class LandingSelectedListener implements AdapterView.OnItemSelectedListener {
        private final SettingsActivity mA;
        private boolean mFirstCall = true;
        LandingSelectedListener(SettingsActivity a) { mA = a; }
        public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            if (mFirstCall) { mFirstCall = false; return; }
            mA.saveLandingPref(pos);
        }
        public void onNothingSelected(AdapterView<?> p) {}
    }

    static class AddSenderClickListener implements View.OnClickListener {
        private final SettingsActivity mA;
        AddSenderClickListener(SettingsActivity a) { mA = a; }
        public void onClick(View v) { mA.openSenderTemplate(-1L); }
    }

    static class SenderRowClickListener implements View.OnClickListener {
        private final SettingsActivity mA;
        private final long mId;
        SenderRowClickListener(SettingsActivity a, long id) { mA = a; mId = id; }
        public void onClick(View v) { mA.openSenderTemplate(mId); }
    }

    static class SenderRowLongClickListener implements View.OnLongClickListener {
        private final SettingsActivity mA;
        private final long mId;
        SenderRowLongClickListener(SettingsActivity a, long id) { mA = a; mId = id; }
        public boolean onLongClick(View v) { mA.confirmDeleteSender(mId); return true; }
    }

    static class DeleteSenderConfirmListener implements DialogInterface.OnClickListener {
        private final SettingsActivity mA;
        DeleteSenderConfirmListener(SettingsActivity a) { mA = a; }
        public void onClick(DialogInterface d, int which) { mA.deleteSender(); }
    }

    static class PatternRowClickListener implements View.OnClickListener {
        private final SettingsActivity mA;
        private final long mPatternId;
        private final long mSenderId;
        PatternRowClickListener(SettingsActivity a, long pid, long sid) {
            mA = a; mPatternId = pid; mSenderId = sid;
        }
        public void onClick(View v) { mA.openExtractConfig(mPatternId, mSenderId); }
    }

    static class PatternRowLongClickListener implements View.OnLongClickListener {
        private final SettingsActivity mA;
        private final long mId;
        PatternRowLongClickListener(SettingsActivity a, long id) { mA = a; mId = id; }
        public boolean onLongClick(View v) { mA.confirmDeletePattern(mId); return true; }
    }

    static class DeletePatternConfirmListener implements DialogInterface.OnClickListener {
        private final SettingsActivity mA;
        DeletePatternConfirmListener(SettingsActivity a) { mA = a; }
        public void onClick(DialogInterface d, int which) { mA.deletePattern(); }
    }

    static class CardRowClickListener implements View.OnClickListener {
        private final SettingsActivity mA;
        private final long mCardId;
        private final long mSenderId;
        CardRowClickListener(SettingsActivity a, long cid, long sid) {
            mA = a; mCardId = cid; mSenderId = sid;
        }
        public void onClick(View v) { mA.showCardDialog(mCardId, mSenderId); }
    }

    static class CardRowLongClickListener implements View.OnLongClickListener {
        private final SettingsActivity mA;
        private final long mId;
        CardRowLongClickListener(SettingsActivity a, long id) { mA = a; mId = id; }
        public boolean onLongClick(View v) { mA.confirmDeleteCard(mId); return true; }
    }

    static class AddCardClickListener implements View.OnClickListener {
        private final SettingsActivity mA;
        private final long mSenderId;
        AddCardClickListener(SettingsActivity a, long sid) { mA = a; mSenderId = sid; }
        public void onClick(View v) { mA.showCardDialog(-1L, mSenderId); }
    }

    static class DeleteCardConfirmListener implements DialogInterface.OnClickListener {
        private final SettingsActivity mA;
        DeleteCardConfirmListener(SettingsActivity a) { mA = a; }
        public void onClick(DialogInterface d, int which) { mA.deleteCard(); }
    }

    static class SaveCardDialogListener implements DialogInterface.OnClickListener {
        private final SettingsActivity mA;
        private final EditText mEtLast4;
        SaveCardDialogListener(SettingsActivity a, EditText et) { mA = a; mEtLast4 = et; }
        public void onClick(DialogInterface d, int which) { mA.saveCard(mEtLast4); }
    }
}
