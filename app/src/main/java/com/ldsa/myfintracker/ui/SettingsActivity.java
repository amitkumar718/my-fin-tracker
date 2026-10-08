package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;

import java.util.List;

public class SettingsActivity extends Activity {

    private static final String PREF_FILE    = "fin_prefs";
    private static final String PREF_LANDING = "landing_page";
    private static final String DBX_PREFS    = "dropbox";
    private static final String DBX_TOKEN    = "access_token";

    private Spinner         mSpinnerLanding;
    private TextView        mTvBanksCount;
    private TextView        mTvDropboxStatus;
    private ExpenseDatabase mDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mDb = ExpenseDatabase.getInstance(this);

        mSpinnerLanding   = (Spinner)      findViewById(R.id.spinnerLanding);
        mTvBanksCount     = (TextView)     findViewById(R.id.tvBanksCount);
        mTvDropboxStatus  = (TextView)     findViewById(R.id.tvDropboxStatus);
        LinearLayout rowBanks    = (LinearLayout) findViewById(R.id.rowBanks);
        LinearLayout rowDropbox  = (LinearLayout) findViewById(R.id.rowDropbox);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
            this, R.array.landing_page_labels, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerLanding.setAdapter(adapter);

        SharedPreferences prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        mSpinnerLanding.setSelection("sms".equals(prefs.getString(PREF_LANDING, "expenses")) ? 1 : 0);
        mSpinnerLanding.setOnItemSelectedListener(new LandingSelectedListener(this));

        rowBanks.setOnClickListener(new BanksRowClickListener(this));
        rowDropbox.setOnClickListener(new DropboxRowClickListener(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBanksCount();
        updateDropboxStatus();
    }

    void updateBanksCount() {
        int count = mDb.getAllSenders().size();
        mTvBanksCount.setText(count == 0 ? "No banks configured"
            : count + " bank" + (count == 1 ? "" : "s") + " configured");
    }

    void saveLandingPref(int pos) {
        getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit()
            .putString(PREF_LANDING, pos == 1 ? "sms" : "expenses")
            .apply();
    }

    void updateDropboxStatus() {
        String token = getSharedPreferences(DBX_PREFS, MODE_PRIVATE).getString(DBX_TOKEN, null);
        boolean hasToken = token != null && !token.isEmpty();
        mTvDropboxStatus.setText(hasToken ? R.string.dropbox_token_set : R.string.dropbox_token_not_set);
        mTvDropboxStatus.setTextColor(hasToken ? 0xFF388E3C : 0xFF9E9E9E);
    }

    void openDropboxSettings() {
        startActivity(new Intent(this, DropboxSettingsActivity.class));
    }

    void openBankList() {
        startActivity(new Intent(this, BankListActivity.class));
    }

    // ── Static listener classes ───────────────────────────────────────────────

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

    static class BanksRowClickListener implements View.OnClickListener {
        private final SettingsActivity mA;
        BanksRowClickListener(SettingsActivity a) { mA = a; }
        public void onClick(View v) { mA.openBankList(); }
    }

    static class DropboxRowClickListener implements View.OnClickListener {
        private final SettingsActivity mA;
        DropboxRowClickListener(SettingsActivity a) { mA = a; }
        public void onClick(View v) { mA.openDropboxSettings(); }
    }
}
