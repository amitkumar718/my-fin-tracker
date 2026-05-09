package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.SenderConfig;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {

    private static final String PREF_FILE    = "fin_prefs";
    private static final String PREF_LANDING = "landing_page";

    private Spinner mSpinnerLanding;
    private ListView mListSenders;
    private TextView mTvNoSenders;
    private SenderConfigAdapter mSenderAdapter;
    private List<SenderConfig> mSenders = new ArrayList<SenderConfig>();
    private ExpenseDatabase mDb;
    private long mPendingDeleteId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mDb = ExpenseDatabase.getInstance(this);

        mSpinnerLanding = (Spinner)  findViewById(R.id.spinnerLanding);
        mListSenders    = (ListView) findViewById(R.id.listSenders);
        mTvNoSenders    = (TextView) findViewById(R.id.tvNoSenders);
        Button btnAdd   = (Button)   findViewById(R.id.btnAddSender);

        ArrayAdapter<CharSequence> landingAdapter = ArrayAdapter.createFromResource(
            this, R.array.landing_page_labels, android.R.layout.simple_spinner_item);
        landingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerLanding.setAdapter(landingAdapter);

        // Set current selection
        SharedPreferences prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        String landing = prefs.getString(PREF_LANDING, "expenses");
        mSpinnerLanding.setSelection("sms".equals(landing) ? 1 : 0);

        mSpinnerLanding.setOnItemSelectedListener(new LandingSelectedListener(this));

        mSenderAdapter = new SenderConfigAdapter(this, mSenders);
        mListSenders.setAdapter(mSenderAdapter);
        mListSenders.setOnItemClickListener(new SenderClickListener(this));
        mListSenders.setOnItemLongClickListener(new SenderLongClickListener(this));

        btnAdd.setOnClickListener(new AddSenderClickListener(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadSenders();
    }

    void saveLandingPref(int pos) {
        String val = pos == 1 ? "sms" : "expenses";
        getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit()
            .putString(PREF_LANDING, val)
            .apply();
    }

    void reloadSenders() {
        mSenders = mDb.getAllSenders();
        mSenderAdapter.setItems(mSenders);
        mTvNoSenders.setVisibility(mSenders.isEmpty() ? View.VISIBLE : View.GONE);
        mListSenders.setVisibility(mSenders.isEmpty() ? View.GONE : View.VISIBLE);
    }

    void openSenderTemplate(long senderId) {
        Intent i = new Intent(this, SenderTemplateActivity.class);
        i.putExtra(SenderTemplateActivity.EXTRA_SENDER_ID, senderId);
        startActivity(i);
    }

    void confirmDeleteSender(long id) {
        mPendingDeleteId = id;
        new AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete_sender)
            .setPositiveButton(android.R.string.ok, new DeleteSenderConfirmListener(this))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void deleteSender() {
        if (mPendingDeleteId >= 0) {
            mDb.deleteSender(mPendingDeleteId);
            mPendingDeleteId = -1;
            Toast.makeText(this, R.string.msg_sender_deleted, Toast.LENGTH_SHORT).show();
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
            if (mFirstCall) { mFirstCall = false; return; } // skip initial trigger
            mA.saveLandingPref(pos);
        }
        public void onNothingSelected(AdapterView<?> p) {}
    }

    static class AddSenderClickListener implements View.OnClickListener {
        private final SettingsActivity mA;
        AddSenderClickListener(SettingsActivity a) { mA = a; }
        public void onClick(View v) { mA.openSenderTemplate(-1L); }
    }

    static class SenderClickListener implements AdapterView.OnItemClickListener {
        private final SettingsActivity mA;
        SenderClickListener(SettingsActivity a) { mA = a; }
        public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            SenderConfig s = (SenderConfig) mA.mSenderAdapter.getItem(pos);
            mA.openSenderTemplate(s.id);
        }
    }

    static class SenderLongClickListener implements AdapterView.OnItemLongClickListener {
        private final SettingsActivity mA;
        SenderLongClickListener(SettingsActivity a) { mA = a; }
        public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
            SenderConfig s = (SenderConfig) mA.mSenderAdapter.getItem(pos);
            mA.confirmDeleteSender(s.id);
            return true;
        }
    }

    static class DeleteSenderConfirmListener implements DialogInterface.OnClickListener {
        private final SettingsActivity mA;
        DeleteSenderConfirmListener(SettingsActivity a) { mA = a; }
        public void onClick(DialogInterface d, int which) { mA.deleteSender(); }
    }
}
