package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.ExtractionPattern;
import com.ldsa.myfintracker.db.SenderConfig;

import java.util.List;

public class BankListActivity extends Activity {

    private LinearLayout mContainer;
    private TextView     mTvEmpty;
    private ExpenseDatabase mDb;
    private long mPendingDeleteId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bank_list);
        getWindow().setStatusBarColor(0xFF1976D2);

        mDb        = ExpenseDatabase.getInstance(this);
        mContainer = (LinearLayout) findViewById(R.id.containerBanks);
        mTvEmpty   = (TextView)     findViewById(R.id.tvBanksEmpty);

        ((TextView) findViewById(R.id.btnBack)).setOnClickListener(new BackClickListener(this));
        ((Button)   findViewById(R.id.btnAddBank)).setOnClickListener(new AddBankClickListener(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    void reload() {
        List<SenderConfig> senders = mDb.getAllSenders();
        mContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (SenderConfig s : senders) {
            View row = inflater.inflate(R.layout.item_bank, mContainer, false);

            ((TextView) row.findViewById(R.id.tvBankName)).setText(
                s.displayName != null && !s.displayName.isEmpty() ? s.displayName : s.pattern);

            int smsCount = mDb.getPatternsBySender(s.id).size();
            int pdfCount = mDb.getPdfPatternsBySender(s.id).size();
            String meta  = s.pattern;
            if (smsCount > 0 || pdfCount > 0) {
                meta += "  ·  " + smsCount + " SMS  ·  " + pdfCount + " PDF";
            }
            ((TextView) row.findViewById(R.id.tvBankMeta)).setText(meta);

            row.setOnClickListener(new BankRowClickListener(this, s.id));
            row.setOnLongClickListener(new BankRowLongClickListener(this, s.id));
            mContainer.addView(row);
        }

        boolean empty = senders.isEmpty();
        mTvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        mContainer.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    void openBankConfig(long senderId) {
        Intent i = new Intent(this, BankConfigActivity.class);
        i.putExtra(BankConfigActivity.EXTRA_SENDER_ID, senderId);
        startActivity(i);
    }

    void addBank() {
        openBankConfig(-1L);
    }

    void confirmDelete(long id) {
        mPendingDeleteId = id;
        new AlertDialog.Builder(this, R.style.RoundedDialog)
            .setMessage(R.string.confirm_delete_bank)
            .setPositiveButton(android.R.string.ok, new DeleteConfirmListener(this))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void deleteBank() {
        if (mPendingDeleteId < 0) return;
        long id = mPendingDeleteId;
        mPendingDeleteId = -1L;
        List<ExtractionPattern> all = mDb.getPatternsBySender(id);
        for (ExtractionPattern p : all) mDb.deletePattern(p.id);
        List<ExtractionPattern> pdf = mDb.getPdfPatternsBySender(id);
        for (ExtractionPattern p : pdf) mDb.deletePattern(p.id);
        mDb.deleteSender(id);
        Toast.makeText(this, R.string.msg_bank_deleted, Toast.LENGTH_SHORT).show();
        reload();
    }

    // ── Static listener classes ───────────────────────────────────────────────

    static class BackClickListener implements View.OnClickListener {
        private final BankListActivity mA;
        BackClickListener(BankListActivity a) { mA = a; }
        public void onClick(View v) { mA.finish(); }
    }

    static class AddBankClickListener implements View.OnClickListener {
        private final BankListActivity mA;
        AddBankClickListener(BankListActivity a) { mA = a; }
        public void onClick(View v) { mA.addBank(); }
    }

    static class BankRowClickListener implements View.OnClickListener {
        private final BankListActivity mA;
        private final long mId;
        BankRowClickListener(BankListActivity a, long id) { mA = a; mId = id; }
        public void onClick(View v) { mA.openBankConfig(mId); }
    }

    static class BankRowLongClickListener implements View.OnLongClickListener {
        private final BankListActivity mA;
        private final long mId;
        BankRowLongClickListener(BankListActivity a, long id) { mA = a; mId = id; }
        public boolean onLongClick(View v) { mA.confirmDelete(mId); return true; }
    }

    static class DeleteConfirmListener implements DialogInterface.OnClickListener {
        private final BankListActivity mA;
        DeleteConfirmListener(BankListActivity a) { mA = a; }
        public void onClick(DialogInterface d, int w) { mA.deleteBank(); }
    }
}
