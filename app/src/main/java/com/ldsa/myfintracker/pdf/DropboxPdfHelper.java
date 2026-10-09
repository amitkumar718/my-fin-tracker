package com.ldsa.myfintracker.pdf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Dropbox client for enumerating and downloading PDF statements under a given
 * folder in the user's App Folder (/Apps/myfintracker/). Folder may be nested
 * arbitrarily deep (e.g. fin/bank/year/month/stmt.pdf) — list_folder recurses.
 */
public class DropboxPdfHelper {

    public static final String PREF_FILE          = "dropbox";
    public static final String KEY_TOKEN          = "access_token";
    public static final String KEY_ROOT           = "root_path";
    public static final String KEY_APP_KEY           = "app_key";
    public static final String KEY_APP_SECRET        = "app_secret";
    public static final String KEY_REFRESH_TOKEN     = "refresh_token";
    public static final String KEY_EXPIRES_AT        = "expires_at";
    public static final String KEY_PENDING_VERIFIER  = "pending_verifier";
    public static final String KEY_LAST_AUTH_STATUS  = "last_auth_status"; // "ok" / "auth_fail"

    /** App Folder root. Dropbox API uses empty string, not "/", for the root. */
    public static final String DEFAULT_ROOT = "";

    public static final String REDIRECT_URI = "myfintracker://oauth";

    private static final String AUTH_URL          = "https://www.dropbox.com/oauth2/authorize";
    private static final String TOKEN_URL         = "https://api.dropboxapi.com/oauth2/token";
    private static final String LIST_URL          = "https://api.dropboxapi.com/2/files/list_folder";
    private static final String LIST_CONTINUE_URL = "https://api.dropboxapi.com/2/files/list_folder/continue";
    private static final String DOWNLOAD_URL      = "https://content.dropboxapi.com/2/files/download";

    private static final String TAG = "myfin.dropbox";

    // ============================================================
    // Data model
    // ============================================================

    public static class PdfEntry {
        public String pathDisplay;   // "/fin/hdfc/2026/01/stmt.pdf"
        public String pathLower;     // for API calls (download by path)
        public String name;
        public long   size;
        public String serverModified;
    }

    // ============================================================
    // Callbacks
    // ============================================================

    public interface ListCallback {
        void onSuccess(List<PdfEntry> entries);
        void onError(String message);
        void onAuthFailed();
    }

    public interface DownloadCallback {
        void onSuccess(File outFile);
        void onError(String message);
        void onAuthFailed();
    }

    public interface AuthCallback {
        void onSuccess();
        void onError(String message);
    }

    // ============================================================
    // Public API
    // ============================================================

    /** List all .pdf entries under rootPath (recursive). "" means App Folder root. */
    public static void listPdfs(Activity activity, String token, String rootPath,
                                ListCallback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        new ListThread(activity.getApplicationContext(), token, rootPath, callback, main).start();
    }

    /** Download pathLower into outFile. Overwrites existing file. */
    public static void downloadPdf(Activity activity, String token, String pathLower,
                                   File outFile, DownloadCallback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        new DownloadThread(activity.getApplicationContext(), token, pathLower, outFile,
            callback, main).start();
    }

    // ============================================================
    // OAuth 2.0 (app key + secret, offline access with refresh token)
    // ============================================================

    /** Launch the system browser to the Dropbox authorize URL (PKCE S256). */
    public static boolean startAuth(Activity activity) {
        SharedPreferences p = activity.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        String appKey = p.getString(KEY_APP_KEY, "");
        if (appKey == null || appKey.isEmpty()) return false;

        String verifier  = generateCodeVerifier();
        String challenge = codeChallengeS256(verifier);
        p.edit().putString(KEY_PENDING_VERIFIER, verifier).apply();

        String url = AUTH_URL
            + "?client_id=" + urlEncode(appKey)
            + "&response_type=code"
            + "&token_access_type=offline"
            + "&code_challenge=" + urlEncode(challenge)
            + "&code_challenge_method=S256"
            + "&redirect_uri=" + urlEncode(REDIRECT_URI);
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(i);
        return true;
    }

    static String generateCodeVerifier() {
        char[] alphabet =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~".toCharArray();
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(64);
        for (int i = 0; i < 64; i++) sb.append(alphabet[rnd.nextInt(alphabet.length)]);
        return sb.toString();
    }

    static String codeChallengeS256(String verifier) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes("US-ASCII"));
            return android.util.Base64.encodeToString(hash,
                android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING
                    | android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Exchange an authorization code for access + refresh tokens. */
    public static void exchangeCode(Context ctx, String code, AuthCallback cb) {
        new ExchangeCodeThread(ctx.getApplicationContext(), code, cb,
            new Handler(Looper.getMainLooper())).start();
    }

    /**
     * Returns the stored access token, refreshing first if it's expired or
     * near-expiry. Returns null if there is no token at all. Must be called
     * from a background thread (performs network IO on refresh).
     */
    static synchronized String getValidAccessToken(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        String token = p.getString(KEY_TOKEN, null);
        long expiresAt = p.getLong(KEY_EXPIRES_AT, 0L);
        if (token != null && !token.isEmpty()
                && (expiresAt == 0L || System.currentTimeMillis() < expiresAt - 60_000L)) {
            return token;
        }
        String refreshToken = p.getString(KEY_REFRESH_TOKEN, null);
        if (refreshToken == null || refreshToken.isEmpty()) return token; // stale
        String appKey    = p.getString(KEY_APP_KEY,    "");
        String appSecret = p.getString(KEY_APP_SECRET, "");
        if (appKey.isEmpty() || appSecret.isEmpty()) return token;
        try {
            String body = "grant_type=refresh_token"
                + "&refresh_token=" + urlEncode(refreshToken)
                + "&client_id="     + urlEncode(appKey)
                + "&client_secret=" + urlEncode(appSecret);
            String resp = postForm(TOKEN_URL, body);
            JSONObject j = new JSONObject(resp);
            String newAccess = j.getString("access_token");
            long   expIn     = j.optLong("expires_in", 14400L);
            String newRefresh = j.optString("refresh_token", "");
            SharedPreferences.Editor ed = p.edit();
            ed.putString(KEY_TOKEN, newAccess);
            ed.putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expIn * 1000L);
            if (!newRefresh.isEmpty()) ed.putString(KEY_REFRESH_TOKEN, newRefresh);
            ed.apply();
            Log.d(TAG, "refresh ok, new expiry in " + expIn + "s");
            return newAccess;
        } catch (Exception e) {
            Log.e(TAG, "refresh failed: " + e.getMessage(), e);
            return token;
        }
    }

    // ============================================================
    // OAuth thread + helpers
    // ============================================================

    static class ExchangeCodeThread extends Thread {
        private final Context mCtx;
        private final String  mCode;
        private final AuthCallback mCb;
        private final Handler mMain;
        ExchangeCodeThread(Context ctx, String code, AuthCallback cb, Handler main) {
            mCtx = ctx; mCode = code; mCb = cb; mMain = main;
        }
        public void run() {
            SharedPreferences p = mCtx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
            String appKey    = p.getString(KEY_APP_KEY,    "");
            String appSecret = p.getString(KEY_APP_SECRET, "");
            String verifier  = p.getString(KEY_PENDING_VERIFIER, "");
            if (appKey.isEmpty() || appSecret.isEmpty()) {
                mMain.post(new AuthErrorRunnable(mCb, "App key or secret not set"));
                return;
            }
            if (verifier.isEmpty()) {
                mMain.post(new AuthErrorRunnable(mCb, "Missing PKCE verifier — start auth again"));
                return;
            }
            try {
                String body = "grant_type=authorization_code"
                    + "&code="          + urlEncode(mCode)
                    + "&client_id="     + urlEncode(appKey)
                    + "&client_secret=" + urlEncode(appSecret)
                    + "&code_verifier=" + urlEncode(verifier)
                    + "&redirect_uri="  + urlEncode(REDIRECT_URI);
                String resp = postForm(TOKEN_URL, body);
                JSONObject j = new JSONObject(resp);
                String access  = j.getString("access_token");
                String refresh = j.optString("refresh_token", "");
                long   expIn   = j.optLong("expires_in", 14400L);
                SharedPreferences.Editor ed = p.edit();
                ed.putString(KEY_TOKEN, access);
                if (!refresh.isEmpty()) ed.putString(KEY_REFRESH_TOKEN, refresh);
                ed.putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expIn * 1000L);
                ed.remove(KEY_PENDING_VERIFIER);
                ed.apply();
                Log.d(TAG, "exchange ok, refresh=" + (refresh.isEmpty() ? "n" : "y")
                    + " expiry in " + expIn + "s");
                mMain.post(new AuthSuccessRunnable(mCb));
            } catch (Exception e) {
                Log.e(TAG, "exchange failed: " + e.getMessage(), e);
                mMain.post(new AuthErrorRunnable(mCb, e.getMessage()));
            }
        }
    }

    static class AuthSuccessRunnable implements Runnable {
        private final AuthCallback mCb;
        AuthSuccessRunnable(AuthCallback cb) { mCb = cb; }
        public void run() { mCb.onSuccess(); }
    }

    static class AuthErrorRunnable implements Runnable {
        private final AuthCallback mCb;
        private final String       mMsg;
        AuthErrorRunnable(AuthCallback cb, String msg) { mCb = cb; mMsg = msg; }
        public void run() { mCb.onError(mMsg != null ? mMsg : "unknown error"); }
    }

    static String postForm(String url, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        byte[] b = body.getBytes("UTF-8");
        OutputStream os = conn.getOutputStream();
        os.write(b);
        os.close();
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + sb);
        return sb.toString();
    }

    static String urlEncode(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    // ============================================================
    // Exceptions
    // ============================================================

    static class AuthException extends Exception {}

    // ============================================================
    // List thread (handles pagination)
    // ============================================================

    static class ListThread extends Thread {
        private final Context mCtx;
        private String        mToken;
        private final String  mRoot;
        private final ListCallback mCallback;
        private final Handler mMain;

        ListThread(Context ctx, String token, String root, ListCallback cb, Handler main) {
            mCtx = ctx; mToken = token; mRoot = root; mCallback = cb; mMain = main;
        }

        public void run() {
            try {
                // Prefer a freshly refreshed token when the stored one is near-expiry.
                String refreshed = getValidAccessToken(mCtx);
                if (refreshed != null && !refreshed.isEmpty()) mToken = refreshed;

                List<PdfEntry> out = new ArrayList<PdfEntry>();

                String body = "{\"path\":\"" + mRoot
                        + "\",\"recursive\":true,\"include_media_info\":false"
                        + ",\"include_deleted\":false,\"limit\":2000}";
                String resp = postJson(LIST_URL, body);
                String cursor = parsePage(resp, out);

                while (cursor != null) {
                    String contBody = "{\"cursor\":\"" + cursor + "\"}";
                    resp = postJson(LIST_CONTINUE_URL, contBody);
                    cursor = parsePage(resp, out);
                }

                Log.d(TAG, "list: " + out.size() + " pdf entries under '" + mRoot + "'");
                mMain.post(new ListSuccessRunnable(mCallback, out));

            } catch (AuthException e) {
                mMain.post(new ListAuthRunnable(mCallback));
            } catch (Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : "List failed";
                Log.e(TAG, "list exception for path '" + mRoot + "': " + msg, e);
                mMain.post(new ListErrorRunnable(mCallback, "path '" + mRoot + "': " + msg));
            }
        }

        private String postJson(String url, String jsonBody) throws Exception {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + mToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);

            byte[] b = jsonBody.getBytes("UTF-8");
            OutputStream os = conn.getOutputStream();
            os.write(b);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            if (code == 401) throw new AuthException();
            if (code < 200 || code >= 300) {
                String err = readStream(conn.getErrorStream());
                Log.e(TAG, "list HTTP " + code + " body: " + err);
                if (code == 400 && (err.contains("missing_scope") || err.contains("required scope"))) throw new AuthException();
                throw new Exception("HTTP " + code + (err.isEmpty() ? "" : ": " + err));
            }
            return readStream(conn.getInputStream());
        }

        /** Append pdf entries from this page's response. Returns next cursor, or null. */
        private static String parsePage(String resp, List<PdfEntry> out) throws Exception {
            JSONObject obj = new JSONObject(resp);
            JSONArray entries = obj.getJSONArray("entries");
            for (int i = 0; i < entries.length(); i++) {
                JSONObject e = entries.getJSONObject(i);
                String tag = e.optString(".tag", "");
                if (!"file".equals(tag)) continue;
                String name = e.optString("name", "");
                if (!name.toLowerCase().endsWith(".pdf")) continue;
                PdfEntry pe = new PdfEntry();
                pe.name           = name;
                pe.pathDisplay    = e.optString("path_display", "");
                pe.pathLower      = e.optString("path_lower", "");
                pe.size           = e.optLong("size", 0L);
                pe.serverModified = e.optString("server_modified", "");
                out.add(pe);
            }
            boolean hasMore = obj.optBoolean("has_more", false);
            if (!hasMore) return null;
            return obj.optString("cursor", null);
        }
    }

    // ============================================================
    // Download thread
    // ============================================================

    static class DownloadThread extends Thread {
        private final Context mCtx;
        private String        mToken;
        private final String  mPath;
        private final File    mOut;
        private final DownloadCallback mCallback;
        private final Handler mMain;

        DownloadThread(Context ctx, String token, String path, File out,
                       DownloadCallback cb, Handler main) {
            mCtx = ctx; mToken = token; mPath = path; mOut = out; mCallback = cb; mMain = main;
        }

        public void run() {
            try {
                String refreshed = getValidAccessToken(mCtx);
                if (refreshed != null && !refreshed.isEmpty()) mToken = refreshed;

                HttpURLConnection conn = (HttpURLConnection) new URL(DOWNLOAD_URL).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Authorization", "Bearer " + mToken);
                conn.setRequestProperty("Dropbox-API-Arg", "{\"path\":\"" + mPath + "\"}");
                // Download requires empty Content-Type
                conn.setRequestProperty("Content-Type", "");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(120000);
                conn.getOutputStream().close();

                int code = conn.getResponseCode();
                if (code == 401) throw new AuthException();
                if (code < 200 || code >= 300) {
                    String err = readStream(conn.getErrorStream());
                    Log.e(TAG, "download HTTP " + code + " body: " + err);
                    if (code == 400 && (err.contains("missing_scope") || err.contains("required scope"))) throw new AuthException();
                    throw new Exception("HTTP " + code + (err.isEmpty() ? "" : ": " + err));
                }

                InputStream in = conn.getInputStream();
                File parent = mOut.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                FileOutputStream fos = new FileOutputStream(mOut);
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                fos.flush();
                fos.close();
                in.close();

                Log.d(TAG, "downloaded " + mPath + " → " + mOut.getAbsolutePath()
                    + " (" + mOut.length() + " bytes)");
                mMain.post(new DownloadSuccessRunnable(mCallback, mOut));

            } catch (AuthException e) {
                mMain.post(new DownloadAuthRunnable(mCallback));
            } catch (Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : "Download failed";
                Log.e(TAG, "download exception: " + msg, e);
                mMain.post(new DownloadErrorRunnable(mCallback, msg));
            }
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    static String readStream(InputStream is) {
        if (is == null) return "";
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ============================================================
    // Runnables (D8-safe, no lambdas)
    // ============================================================

    static class ListSuccessRunnable implements Runnable {
        private final ListCallback mCb;
        private final List<PdfEntry> mEntries;
        ListSuccessRunnable(ListCallback cb, List<PdfEntry> e) { mCb = cb; mEntries = e; }
        public void run() { mCb.onSuccess(mEntries); }
    }

    static class ListErrorRunnable implements Runnable {
        private final ListCallback mCb;
        private final String mMsg;
        ListErrorRunnable(ListCallback cb, String m) { mCb = cb; mMsg = m; }
        public void run() { mCb.onError(mMsg); }
    }

    static class ListAuthRunnable implements Runnable {
        private final ListCallback mCb;
        ListAuthRunnable(ListCallback cb) { mCb = cb; }
        public void run() { mCb.onAuthFailed(); }
    }

    static class DownloadSuccessRunnable implements Runnable {
        private final DownloadCallback mCb;
        private final File mFile;
        DownloadSuccessRunnable(DownloadCallback cb, File f) { mCb = cb; mFile = f; }
        public void run() { mCb.onSuccess(mFile); }
    }

    static class DownloadErrorRunnable implements Runnable {
        private final DownloadCallback mCb;
        private final String mMsg;
        DownloadErrorRunnable(DownloadCallback cb, String m) { mCb = cb; mMsg = m; }
        public void run() { mCb.onError(mMsg); }
    }

    static class DownloadAuthRunnable implements Runnable {
        private final DownloadCallback mCb;
        DownloadAuthRunnable(DownloadCallback cb) { mCb = cb; }
        public void run() { mCb.onAuthFailed(); }
    }
}
