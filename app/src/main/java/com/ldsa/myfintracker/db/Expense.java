package com.ldsa.myfintracker.db;

import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

public class Expense {
    public long id;
    public double amount;
    public boolean isCredit;   // true = income/refund; false = debit/expense
    public long dateMs;
    public String merchant;
    public String reason;
    public String card;
    public boolean isOnline;
    public String bank;
    public String originalSms;
    public String labelsJson;
    public long   createdAt;
    public double balance;
    public String transactionType;
    public String accountNumber;
    public String remarks;
    public long   patternId = -1;
    public long   pdfStatementId = -1;
    public String source;   // "sms", "pdf", or "manual"

    public List<String> getLabels() {
        List<String> labels = new ArrayList<String>();
        if (labelsJson == null || labelsJson.isEmpty()) return labels;
        try {
            JSONArray arr = new JSONArray(labelsJson);
            for (int i = 0; i < arr.length(); i++) labels.add(arr.getString(i));
        } catch (Exception ignored) {}
        return labels;
    }

    public void setLabels(List<String> labels) {
        JSONArray arr = new JSONArray();
        for (String l : labels) arr.put(l);
        labelsJson = arr.toString();
    }
}
