package com.ldsa.myfintracker.pdf;

import android.app.Activity;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Dropbox client for enumerating and downloading PDF statements under a given
 * folder in the user's App Folder (/Apps/myfintracker/). Folder may be nested
 * arbitrarily deep (e.g. fin/bank/year/month/stmt.pdf) — list_folder recurses.
 */
public class DropboxPdfHelper {

    public static final String PREF_FILE  = "dropbox";
    public static final String KEY_TOKEN  = "access_token";
    public static final String KEY_ROOT   = "root_path";

    /** App Folder root. Dropbox API uses empty string, not "/", for the root. */
    public static final String DEFAULT_ROOT = "";

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

    // ============================================================
    // Public API
    // ============================================================

    /** List all .pdf entries under rootPath (recursive). "" means App Folder root. */
    public static void listPdfs(Activity activity, String token, String rootPath,
                                ListCallback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        new ListThread(token, rootPath, callback, main).start();
    }

    /** Download pathLower into outFile. Overwrites existing file. */
    public static void downloadPdf(Activity activity, String token, String pathLower,
                                   File outFile, DownloadCallback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        new DownloadThread(token, pathLower, outFile, callback, main).start();
    }

    // ============================================================
    // Exceptions
    // ============================================================

    static class AuthException extends Exception {}

    // ============================================================
    // List thread (handles pagination)
    // ============================================================

    static class ListThread extends Thread {
        private final String mToken;
        private final String mRoot;
        private final ListCallback mCallback;
        private final Handler mMain;

        ListThread(String token, String root, ListCallback cb, Handler main) {
            mToken = token; mRoot = root; mCallback = cb; mMain = main;
        }

        public void run() {
            try {
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
        private final String mToken;
        private final String mPath;
        private final File mOut;
        private final DownloadCallback mCallback;
        private final Handler mMain;

        DownloadThread(String token, String path, File out, DownloadCallback cb, Handler main) {
            mToken = token; mPath = path; mOut = out; mCallback = cb; mMain = main;
        }

        public void run() {
            try {
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
