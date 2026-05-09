package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
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
    private LinearLayout mContainerSenders;
    private TextView mTvNoSenders;
    private List<SenderConfig> mSenders = new ArrayList<SenderConfig>();
    private ExpenseDatabase mDb;
    private long mPendingDeleteId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mDb = ExpenseDatabase.getInstance(this);

        mSpinnerLanding    = (Spinner)       findViewById(R.id.spinnerLanding);
        mContainerSenders  = (LinearLayout)  findViewById(R.id.containerSenders);
        mTvNoSenders       = (TextView)      findViewById(R.id.tvNoSenders);
        Button btnAdd      = (Button)        findViewById(R.id.btnAddSender);

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
        String val = pos == 1 ? "sms" : "expenses";
        getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit()
            .putString(PREF_LANDING, val)
            .apply();
    }

    void reloadSenders() {
        mSenders = mDb.getAllSenders();
        mContainerSenders.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < mSenders.size(); i++) {
            final SenderConfig s = mSenders.get(i);
            View row = inflater.inflate(R.layout.item_sender, mContainerSenders, false);
            ((TextView) row.findViewById(R.id.tvSenderName)).setText(s.getLabel());
            ((TextView) row.findViewById(R.id.tvSenderPattern)).setText(s.pattern);
            row.findViewById(R.id.tvRegexBadge).setVisibility(s.isRegex ? View.VISIBLE : View.GONE);
            if (i > 0) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
                lp.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
                row.setLayoutParams(lp);
            }
            row.setOnClickListener(new SenderRowClickListener(this, s.id));
            row.setOnLongClickListener(new SenderRowLongClickListener(this, s.id));
            mContainerSenders.addView(row);
        }
        mTvNoSenders.setVisibility(mSenders.isEmpty() ? View.VISIBLE : View.GONE);
        mContainerSenders.setVisibility(mSenders.isEmpty() ? View.GONE : View.VISIBLE);
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
}
