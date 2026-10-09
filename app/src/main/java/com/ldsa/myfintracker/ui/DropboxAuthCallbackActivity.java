package com.ldsa.myfintracker.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.ldsa.myfintracker.pdf.DropboxPdfHelper;

/**
 * Handles the Dropbox OAuth redirect: receives the authorization code via a
 * deep link (myfintracker://oauth?code=...), exchanges it for access +
 * refresh tokens via DropboxPdfHelper, then finishes.
 */
public class DropboxAuthCallbackActivity extends Activity {

    private static final String TAG = "myfin.dropbox";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || intent.getData() == null) {
            Log.w(TAG, "callback with no data");
            finish();
            return;
        }
        Uri uri = intent.getData();
        String code  = uri.getQueryParameter("code");
        String error = uri.getQueryParameter("error");
        if (error != null && !error.isEmpty()) {
            Log.w(TAG, "oauth error: " + error);
            Toast.makeText(this, "Dropbox: " + error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (code == null || code.isEmpty()) {
            Log.w(TAG, "callback with no code or error");
            finish();
            return;
        }
        Toast.makeText(this, "Connecting Dropbox…", Toast.LENGTH_SHORT).show();
        DropboxPdfHelper.exchangeCode(this, code, new ExchangeCallback(this));
    }

    void onAuthDone(boolean ok, String message) {
        if (isFinishing()) return;
        if (ok) {
            Toast.makeText(this, "Dropbox connected", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Dropbox: " + message, Toast.LENGTH_LONG).show();
        }
        finish();
    }

    static class ExchangeCallback implements DropboxPdfHelper.AuthCallback {
        private final DropboxAuthCallbackActivity mA;
        ExchangeCallback(DropboxAuthCallbackActivity a) { mA = a; }
        public void onSuccess() { mA.onAuthDone(true, null); }
        public void onError(String message) { mA.onAuthDone(false, message); }
    }
}
