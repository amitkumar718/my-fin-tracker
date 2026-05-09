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
    SmsAdapter mAdapter;
    private ExpenseDatabase mDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_inbox);

        mDb = ExpenseDatabase.getInstance(this);

        mListView = (ListView) findViewById(R.id.listSms);
        mTvEmpty  = (TextView) findViewById(R.id.tvEmpty);
        mTvCount  = (TextView) findViewById(R.id.tvSmsCount);

        mAdapter = new SmsAdapter(this);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new SmsClickListener(this));
        mListView.setOnItemLongClickListener(new SmsLongClickListener(this));

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
        if (id == R.id.action_refresh) { loadSms(); return true; }
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    void openSmsMap(SmsAdapter.ListItem item) {
        Intent i = new Intent(this, SmsMapActivity.class);
        i.putExtra(SmsMapActivity.EXTRA_SMS_ADDRESS, item.sms.address);
        i.putExtra(SmsMapActivity.EXTRA_SMS_BODY,    item.sms.body);
        i.putExtra(SmsMapActivity.EXTRA_SMS_DATE,    item.sms.date);
        startActivity(i);
    }

    void openExtractConfig(SmsAdapter.ListItem item) {
        Intent i = new Intent(this, SmsExtractConfigActivity.class);
        i.putExtra(SmsExtractConfigActivity.EXTRA_SENDER_ID, item.senderConfig.id);
        i.putExtra(SmsExtractConfigActivity.EXTRA_SMS_BODY,  item.sms.body);
        startActivity(i);
    }

    void onItemsLoaded(List<SmsAdapter.ListItem> items) {
        mAdapter.setItems(items);
        int smsCount = 0;
        for (SmsAdapter.ListItem it : items) {
            if (it.type == SmsAdapter.ListItem.TYPE_SMS) smsCount++;
        }
        mTvCount.setText(smsCount + " message" + (smsCount == 1 ? "" : "s"));
        mTvEmpty.setVisibility(smsCount == 0 ? View.VISIBLE : View.GONE);
        mListView.setVisibility(smsCount == 0 ? View.GONE : View.VISIBLE);
    }

    private void loadSms() {
        List<SenderConfig> configs = mDb.getAllSenders();
        new LoadSmsThread(this, configs, new Handler(Looper.getMainLooper())).start();
    }

    private void checkPermissionAndLoad() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.READ_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.READ_SMS}, REQ_SMS_PERM);
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
            mActivity = a; mConfigs = configs; mHandler = h;
        }

        public void run() {
            List<SmsMessage> allSms = SmsReader.readMatchingSms(mActivity, mConfigs);
            List<SmsAdapter.ListItem> grouped = buildGrouped(allSms, mConfigs);
            mHandler.post(new LoadResultRunnable(mActivity, grouped));
        }

        private List<SmsAdapter.ListItem> buildGrouped(List<SmsMessage> allSms, List<SenderConfig> configs) {
            List<SmsAdapter.ListItem> result = new ArrayList<SmsAdapter.ListItem>();
            for (SenderConfig cfg : configs) {
                List<SmsMessage> matching = new ArrayList<SmsMessage>();
                for (SmsMessage msg : allSms) {
                    if (cfg.matches(msg.address)) matching.add(msg);
                }
                if (!matching.isEmpty()) {
                    SmsAdapter.ListItem header = new SmsAdapter.ListItem();
                    header.type         = SmsAdapter.ListItem.TYPE_HEADER;
                    header.senderConfig = cfg;
                    result.add(header);
                    for (SmsMessage msg : matching) {
                        SmsAdapter.ListItem item = new SmsAdapter.ListItem();
                        item.type         = SmsAdapter.ListItem.TYPE_SMS;
                        item.senderConfig = cfg;
                        item.sms          = msg;
                        result.add(item);
                    }
                }
            }
            return result;
        }
    }

    static class LoadResultRunnable implements Runnable {
        private final SmsInboxActivity mActivity;
        private final List<SmsAdapter.ListItem> mItems;
        LoadResultRunnable(SmsInboxActivity a, List<SmsAdapter.ListItem> items) {
            mActivity = a; mItems = items;
        }
        public void run() {
            if (!mActivity.isFinishing()) mActivity.onItemsLoaded(mItems);
        }
    }

    static class SmsClickListener implements AdapterView.OnItemClickListener {
        private final SmsInboxActivity mActivity;
        SmsClickListener(SmsInboxActivity a) { mActivity = a; }
        public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
            SmsAdapter.ListItem item = (SmsAdapter.ListItem) mActivity.mAdapter.getItem(pos);
            if (item.type == SmsAdapter.ListItem.TYPE_SMS) mActivity.openSmsMap(item);
        }
    }

    static class SmsLongClickListener implements AdapterView.OnItemLongClickListener {
        private final SmsInboxActivity mActivity;
        SmsLongClickListener(SmsInboxActivity a) { mActivity = a; }
        public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
            SmsAdapter.ListItem item = (SmsAdapter.ListItem) mActivity.mAdapter.getItem(pos);
            if (item.type == SmsAdapter.ListItem.TYPE_SMS) {
                mActivity.openExtractConfig(item);
                return true;
            }
            return false;
        }
    }
}
