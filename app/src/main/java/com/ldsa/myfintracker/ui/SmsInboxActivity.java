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
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ldsa.myfintracker.R;
import com.ldsa.myfintracker.db.Expense;
import com.ldsa.myfintracker.db.ExpenseDatabase;
import com.ldsa.myfintracker.db.ExtractionPattern;
import com.ldsa.myfintracker.db.SenderConfig;
import com.ldsa.myfintracker.sms.SmsMessage;
import com.ldsa.myfintracker.sms.SmsReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsInboxActivity extends Activity {

    private static final int REQ_SMS_PERM = 10;

    private ListView mListView;
    private TextView mTvEmpty;
    private TextView mTvCount;
    Button   mBtnNewMatches;   // package-private for static inner access via method
    SmsAdapter mAdapter;
    private ExpenseDatabase mDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_inbox);

        mDb = ExpenseDatabase.getInstance(this);

        mListView      = (ListView) findViewById(R.id.listSms);
        mTvEmpty       = (TextView) findViewById(R.id.tvEmpty);
        mTvCount       = (TextView) findViewById(R.id.tvSmsCount);
        mBtnNewMatches = (Button)   findViewById(R.id.btnNewMatches);

        mAdapter = new SmsAdapter(this);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new SmsClickListener(this));
        mListView.setOnItemLongClickListener(new SmsLongClickListener(this));
        mBtnNewMatches.setOnClickListener(new NewMatchesClickListener(this));

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
        if (id == R.id.action_refresh)  { loadSms(); return true; }
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
        i.putExtra(SmsMapActivity.EXTRA_SENDER_ID,   item.senderConfig.id);
        startActivity(i);
    }

    void onItemsLoaded(List<SmsAdapter.ListItem> items) {
        mAdapter.setItems(items);
        int smsCount = 0;
        int newCount = 0;
        for (SmsAdapter.ListItem it : items) {
            if (it.type == SmsAdapter.ListItem.TYPE_SMS) {
                smsCount++;
                if (it.isNewMatch) newCount++;
            }
        }
        mTvCount.setText(smsCount + " message" + (smsCount == 1 ? "" : "s"));
        mTvEmpty.setVisibility(smsCount == 0 ? View.VISIBLE : View.GONE);
        mListView.setVisibility(smsCount == 0 ? View.GONE : View.VISIBLE);

        if (newCount > 0) {
            mBtnNewMatches.setVisibility(View.VISIBLE);
            mBtnNewMatches.setEnabled(true);
            mBtnNewMatches.setText("Found " + newCount + " new match"
                + (newCount == 1 ? "" : "es") + " — Create Expenses");
        } else {
            mBtnNewMatches.setVisibility(View.GONE);
        }
    }

    void autoCreateExpenses() {
        List<SmsAdapter.ListItem> toCreate = new ArrayList<SmsAdapter.ListItem>();
        for (int i = 0; i < mAdapter.getCount(); i++) {
            SmsAdapter.ListItem it = (SmsAdapter.ListItem) mAdapter.getItem(i);
            if (it.type == SmsAdapter.ListItem.TYPE_SMS && it.isNewMatch) toCreate.add(it);
        }
        if (toCreate.isEmpty()) return;
        mBtnNewMatches.setEnabled(false);
        mBtnNewMatches.setText("Creating expenses…");
        new AutoCreateThread(this, toCreate, mDb, new Handler(Looper.getMainLooper())).start();
    }

    void onAutoCreateDone(int count) {
        Toast.makeText(this,
            count + " expense" + (count == 1 ? "" : "s") + " created",
            Toast.LENGTH_SHORT).show();
        loadSms(); // reload so isNewMatch resets for created items
    }

    void loadSms() {
        List<SenderConfig>      configs  = mDb.getAllSenders();
        List<ExtractionPattern> patterns = mDb.getAllPatterns();
        Set<String>             applied  = mDb.getAppliedSmsBodies();
        new LoadSmsThread(this, configs, patterns, applied,
            new Handler(Looper.getMainLooper())).start();
    }

    private void checkPermissionAndLoad() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.READ_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{android.Manifest.permission.READ_SMS}, REQ_SMS_PERM);
                return;
            }
        }
        // Permission already granted — onResume will also call loadSms(), which is fine
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
        private final SmsInboxActivity        mActivity;
        private final List<SenderConfig>      mConfigs;
        private final List<ExtractionPattern> mPatterns;
        private final Set<String>             mApplied;
        private final Handler                 mHandler;

        LoadSmsThread(SmsInboxActivity a, List<SenderConfig> configs,
                      List<ExtractionPattern> patterns, Set<String> applied, Handler h) {
            mActivity = a; mConfigs = configs; mPatterns = patterns;
            mApplied = applied; mHandler = h;
        }

        public void run() {
            List<SmsMessage> allSms = SmsReader.readMatchingSms(mActivity, mConfigs);
            Map<Long, List<ExtractionPattern>> patternMap =
                new HashMap<Long, List<ExtractionPattern>>();
            for (ExtractionPattern p : mPatterns) {
                List<ExtractionPattern> list = patternMap.get(p.senderId);
                if (list == null) {
                    list = new ArrayList<ExtractionPattern>();
                    patternMap.put(p.senderId, list);
                }
                list.add(p);
            }
            List<SmsAdapter.ListItem> grouped = buildGrouped(allSms, patternMap);
            mHandler.post(new LoadResultRunnable(mActivity, grouped));
        }

        private List<SmsAdapter.ListItem> buildGrouped(
                List<SmsMessage> allSms,
                Map<Long, List<ExtractionPattern>> patternMap) {
            List<SmsAdapter.ListItem> result = new ArrayList<SmsAdapter.ListItem>();
            for (SenderConfig cfg : mConfigs) {
                List<ExtractionPattern> cfgPatterns = patternMap.get(cfg.id);
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
                        if (cfgPatterns != null && !cfgPatterns.isEmpty() && msg.body != null) {
                            tryExtract(item, msg.body, cfgPatterns);
                        }
                        if (item.hasExtraction && msg.body != null) {
                            item.isNewMatch = !mApplied.contains(msg.body);
                        }
                        result.add(item);
                    }
                }
            }
            return result;
        }

        private void tryExtract(SmsAdapter.ListItem item, String body,
                                List<ExtractionPattern> patterns) {
            for (ExtractionPattern p : patterns) {
                if (!p.matches(body)) continue;
                try {
                    Matcher m = Pattern.compile(p.templateRegex,
                        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(body);
                    if (!m.find()) continue;
                    item.hasExtraction     = true;
                    item.matchedPatternId  = p.id;
                    item.extractedAmount   = grp(m, p.amountGroup).replaceAll("[^0-9.,]", "");
                    item.extractedBalance  = grp(m, p.balanceGroup).replaceAll("[^0-9.,]", "");
                    item.extractedMerchant = grp(m, p.merchantGroup);
                    item.extractedCard     = grp(m, p.cardGroup);
                    item.extractedAccount  = grp(m, p.accountGroup);
                    item.transactionType   = p.transactionType != null ? p.transactionType : "";
                    return;
                } catch (Exception ignored) {}
            }
        }

        private static String grp(Matcher m, int group) {
            if (group < 0 || group > m.groupCount()) return "";
            String v = m.group(group);
            return v != null ? v : "";
        }
    }

    static class AutoCreateThread extends Thread {
        private final SmsInboxActivity          mActivity;
        private final List<SmsAdapter.ListItem> mItems;
        private final ExpenseDatabase           mDb;
        private final Handler                   mHandler;

        AutoCreateThread(SmsInboxActivity a, List<SmsAdapter.ListItem> items,
                         ExpenseDatabase db, Handler h) {
            mActivity = a; mItems = items; mDb = db; mHandler = h;
        }

        public void run() {
            int count = 0;
            for (SmsAdapter.ListItem item : mItems) {
                try {
                    String amtStr = item.extractedAmount
                        .replaceAll(",", "").replaceAll("[^0-9.]", "");
                    if (amtStr.isEmpty()) continue;
                    double amount = Double.parseDouble(amtStr);

                    double balance = 0;
                    if (item.extractedBalance != null && !item.extractedBalance.isEmpty()) {
                        try {
                            balance = Double.parseDouble(
                                item.extractedBalance.replaceAll(",", "")
                                    .replaceAll("[^0-9.]", ""));
                        } catch (NumberFormatException ignored) {}
                    }

                    Expense e        = new Expense();
                    e.amount         = amount;
                    e.dateMs         = item.sms.date;
                    e.merchant       = item.extractedMerchant;
                    e.card           = item.extractedCard;
                    e.accountNumber  = item.extractedAccount;
                    e.balance        = balance;
                    e.transactionType = item.transactionType;
                    e.isOnline       = isOnlineType(item.transactionType);
                    e.bank           = (item.senderConfig.displayName != null
                                        && !item.senderConfig.displayName.isEmpty())
                                       ? item.senderConfig.displayName
                                       : item.senderConfig.pattern;
                    e.originalSms    = item.sms.body;
                    e.patternId      = item.matchedPatternId;
                    e.createdAt      = System.currentTimeMillis();
                    mDb.insertExpense(e);
                    count++;
                } catch (Exception ignored) {}
            }
            final int finalCount = count;
            mHandler.post(new AutoCreateDoneRunnable(mActivity, finalCount));
        }

        private static boolean isOnlineType(String type) {
            return "UPI".equals(type) || "CARD_ONLINE".equals(type)
                || "NETBANKING_PURCHASE".equals(type) || "NETBANKING_TRANSFER".equals(type);
        }
    }

    static class AutoCreateDoneRunnable implements Runnable {
        private final SmsInboxActivity mActivity;
        private final int              mCount;
        AutoCreateDoneRunnable(SmsInboxActivity a, int count) { mActivity = a; mCount = count; }
        public void run() {
            if (!mActivity.isFinishing()) mActivity.onAutoCreateDone(mCount);
        }
    }

    static class NewMatchesClickListener implements View.OnClickListener {
        private final SmsInboxActivity mA;
        NewMatchesClickListener(SmsInboxActivity a) { mA = a; }
        public void onClick(View v) { mA.autoCreateExpenses(); }
    }

    static class LoadResultRunnable implements Runnable {
        private final SmsInboxActivity          mActivity;
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
                mActivity.openSmsMap(item);
                return true;
            }
            return false;
        }
    }
}
