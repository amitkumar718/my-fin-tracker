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

    private Spinner         mSpinnerLanding;
    private TextView        mTvBanksCount;
    private ExpenseDatabase mDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mDb = ExpenseDatabase.getInstance(this);

        mSpinnerLanding = (Spinner)      findViewById(R.id.spinnerLanding);
        mTvBanksCount   = (TextView)     findViewById(R.id.tvBanksCount);
        LinearLayout rowBanks = (LinearLayout) findViewById(R.id.rowBanks);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
            this, R.array.landing_page_labels, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerLanding.setAdapter(adapter);

        SharedPreferences prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        mSpinnerLanding.setSelection("sms".equals(prefs.getString(PREF_LANDING, "expenses")) ? 1 : 0);
        mSpinnerLanding.setOnItemSelectedListener(new LandingSelectedListener(this));

        rowBanks.setOnClickListener(new BanksRowClickListener(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBanksCount();
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
}
