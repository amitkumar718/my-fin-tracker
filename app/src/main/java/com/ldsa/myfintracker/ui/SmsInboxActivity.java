package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.SenderConfig;
import com.ldsa.myfintracker.sms.SmsMessage;
import com.ldsa.myfintracker.sms.SmsReader;

import java.util.ArrayList;
import java.util.List;

public class SmsInboxActivity extends Activity {

    private static final int REQ_SMS_PERM = 10;

    private ListView mListView;
    private TextView mTvEmpty;
    private TextView mTvCount;
    private SmsAdapter mAdapter;
    private List<SmsMessage> mMessages = new ArrayList<SmsMessage>();
    private ExpenseDatabase mDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_inbox);

        mDb = ExpenseDatabase.getInstance(this);

        mListView = (ListView) findViewById(R.id.listSms);
        mTvEmpty  = (TextView) findViewById(R.id.tvEmpty);
        mTvCount  = (TextView) findViewById(R.id.tvSmsCount);

        mAdapter = new SmsAdapter(this, mMessages);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new SmsItemClickListener(this));

        checkPermissionAndLoad();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasSmsPermission()) loadSms();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_sms, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            loadSms();
            return true;
        }
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    void openSmsMap(SmsMessage msg) {
        Intent i = new Intent(this, SmsMapActivity.class);
        i.putExtra(SmsMapActivity.EXTRA_SMS_ADDRESS, msg.address);
        i.putExtra(SmsMapActivity.EXTRA_SMS_BODY,    msg.body);
        i.putExtra(SmsMapActivity.EXTRA_SMS_DATE,    msg.date);
        startActivity(i);
    }

    void onMessagesLoaded(List<SmsMessage> messages) {
        mMessages = messages;
        mAdapter.setItems(mMessages);
        int count = mMessages.size();
        mTvCount.setText(count + " message" + (count == 1 ? "" : "s"));
        mTvEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        mListView.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
    }

    private void loadSms() {
        List<SenderConfig> configs = mDb.getAllSenders();
        Handler handler = new Handler(Looper.getMainLooper());
        new LoadSmsThread(this, configs, handler).start();
    }

    private void checkPermissionAndLoad() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.READ_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{android.Manifest.permission.READ_SMS},
                    REQ_SMS_PERM);
                return;
            }
        }
        loadSms();
    }

    private boolean hasSmsPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            return checkSelfPermission(android.Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        if (req == REQ_SMS_PERM) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                loadSms();
            } else {
                Toast.makeText(this, R.string.perm_sms_required, Toast.LENGTH_LONG).show();
            }
        }
    }

    // ============================================================
    // Static classes — D8 constraints
    // ============================================================

    static class LoadSmsThread extends Thread {
        private final SmsInboxActivity mActivity;
        private final List<SenderConfig> mConfigs;
        private final Handler mHandler;

        LoadSmsThread(SmsInboxActivity a, List<SenderConfig> configs, Handler h) {
            mActivity = a;
            mConfigs  = configs;
            mHandler  = h;
        }

        public void run() {
            List<SmsMessage> msgs = SmsReader.readMatchingSms(mActivity, mConfigs);
            mHandler.post(new LoadSmsResultRunnable(mActivity, msgs));
        }
    }

    static class LoadSmsResultRunnable implements Runnable {
        private final SmsInboxActivity mActivity;
        private final List<SmsMessage> mMessages;

        LoadSmsResultRunnable(SmsInboxActivity a, List<SmsMessage> msgs) {
            mActivity = a;
            mMessages = msgs;
        }

        public void run() {
            if (!mActivity.isFinishing()) mActivity.onMessagesLoaded(mMessages);
        }
    }

    static class SmsItemClickListener implements AdapterView.OnItemClickListener {
        private final SmsInboxActivity mActivity;
        SmsItemClickListener(SmsInboxActivity a) { mActivity = a; }
        public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            SmsMessage msg = (SmsMessage) mActivity.mAdapter.getItem(pos);
            mActivity.openSmsMap(msg);
        }
    }
}
