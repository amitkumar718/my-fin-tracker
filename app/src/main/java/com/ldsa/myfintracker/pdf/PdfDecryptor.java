package com.ldsa.myfintracker.pdf;

import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * PDF Standard Security Handler decryption — supports revisions 2, 3, 4 (RC4 and AES-128).
 * Uses only java.security and javax.crypto from the Android platform.
 *
 * Revision 5/6 (AES-256, PDF 1.7 ext 3) is not supported — very rare in bank statements.
 */
public class PdfDecryptor {

    public static final String NEEDS_PASSWORD = "PDF_NEEDS_PASSWORD";
    public static final String WRONG_PASSWORD = "PDF_WRONG_PASSWORD";

    // Standard 32-byte password padding (PDF spec §7.6.3.3)
    private static final byte[] PAD = {
        0x28, (byte)0xBF, 0x4E, 0x5E, 0x4E, 0x75, (byte)0x8A, 0x41,
        0x64, 0x00, 0x4E, 0x56, (byte)0xFF, (byte)0xFA, 0x01, 0x08,
        0x2E, 0x2E, 0x00, (byte)0xB6, (byte)0xD0, 0x68, 0x3E, (byte)0x80,
        0x2F, 0x0C, (byte)0xA9, (byte)0xFE, 0x64, 0x53, 0x69, 0x7A
    };

    // ── Public API ────────────────────────────────────────────────────────────

    /** Parsed values from the /Encrypt dictionary. */
    static class EncryptInfo {
        int    v      = 1;    // /V algorithm version
        int    r      = 2;    // /R revision
        byte[] o;             // /O owner verifier (32 bytes)
        byte[] u;             // /U user verifier (32 bytes)
        int    p;             // /P permissions
        int    keyLen = 5;    // encryption key length in bytes
        byte[] fileId;        // first element of trailer /ID
        boolean useAes;          // true = AES-128 (V4), false = RC4
        boolean encryptMetadata = true; // /EncryptMetadata — defaults to true per spec
    }

    /**
     * Detects encryption in the PDF (ISO-8859-1 string).
     * Returns an EncryptInfo if encrypted, or null if plain.
     */
    static EncryptInfo detect(String pdf) {
        // Search the last 20 KB for the /Encrypt entry (handles both classic and xref-stream PDFs)
        int searchFrom = Math.max(0, pdf.length() - 20000);
        String tail = pdf.substring(searchFrom);
        int encIdx = tail.lastIndexOf("/Encrypt");
        if (encIdx < 0) return null;

        int pos = encIdx + 8;
        while (pos < tail.length() && PdfTextExtractor.isWs(tail.charAt(pos))) pos++;
        if (pos >= tail.length()) return null;

        String dictStr;
        if (tail.charAt(pos) == '<' && pos + 1 < tail.length() && tail.charAt(pos + 1) == '<') {
            int end = findDictEnd(tail, pos);
            if (end < 0) return null;
            dictStr = tail.substring(pos, end);
        } else {
            // Indirect reference — find "N" before whitespace
            int numEnd = pos;
            while (numEnd < tail.length() && Character.isDigit(tail.charAt(numEnd))) numEnd++;
            if (numEnd == pos) return null;
            int objNum;
            try { objNum = Integer.parseInt(tail.substring(pos, numEnd)); }
            catch (NumberFormatException e) { return null; }
            dictStr = findObjDict(pdf, objNum);
            if (dictStr == null) return null;
        }

        // Must be Standard security handler
        if (!dictStr.contains("/Standard")) return null;

        EncryptInfo enc = new EncryptInfo();
        enc.v      = getDictInt(dictStr, "/V",         1);
        enc.r      = getDictInt(dictStr, "/R",         2);
        enc.p      = getDictInt(dictStr, "/P",         0);
        int keyBits = getDictInt(dictStr, "/KeyLength", enc.v >= 2 ? 128 : 40);
        enc.keyLen = keyBits / 8;
        enc.o      = getDictHexBytes(pdf, dictStr, "/O");
        enc.u      = getDictHexBytes(pdf, dictStr, "/U");

        if (enc.o == null || enc.u == null) return null;

        // AES = V4 with CFM=AESV2 in the /CF dictionary
        enc.useAes = (enc.v == 4) && dictStr.contains("AESV2");
        // /EncryptMetadata false means metadata streams are NOT encrypted.
        // Default (key absent or any other value) is true.
        enc.encryptMetadata = !dictStr.contains("/EncryptMetadata false");

        enc.fileId = findFileId(pdf);
        if (enc.fileId == null) enc.fileId = new byte[16];

        return enc;
    }

    /**
     * Tries the given string first as user password, then as owner password.
     * Returns the document encryption key on success, or null on failure.
     */
    static byte[] deriveKey(EncryptInfo enc, String password) {
        try {
            byte[] userPassPadded = padPassword(toBytes(password));
            byte[] key = computeKey(enc, userPassPadded);
            if (verifyUser(key, enc)) return key;

            // Try it as the owner password instead
            byte[] fromOwner = ownerToUserKey(enc, userPassPadded);
            if (fromOwner != null) return fromOwner;

            // Some bank PDFs have an empty user password — try that too
            if (!password.isEmpty()) {
                byte[] emptyPadded = padPassword(new byte[0]);
                byte[] emptyKey = computeKey(enc, emptyPadded);
                if (verifyUser(emptyKey, enc)) return emptyKey;
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Decrypts one stream's raw bytes using the document key.
     * objNum/genNum are from the PDF object header ("N G obj").
     */
    static byte[] decryptStream(byte[] data, byte[] encKey, int objNum, int genNum,
                                boolean useAes) {
        try {
            byte[] objKey = objectKey(encKey, objNum, genNum, useAes);
            if (useAes) {
                if (data.length < 16) return data;
                byte[] iv   = Arrays.copyOf(data, 16);
                byte[] body = Arrays.copyOfRange(data, 16, data.length);
                // Pad body to multiple of 16 if needed (malformed PDFs)
                if (body.length % 16 != 0) {
                    int padded = (body.length / 16 + 1) * 16;
                    body = Arrays.copyOf(body, padded);
                }
                Cipher c = Cipher.getInstance("AES/CBC/NoPadding");
                c.init(Cipher.DECRYPT_MODE,
                        new SecretKeySpec(objKey, "AES"), new IvParameterSpec(iv));
                byte[] out = c.doFinal(body);
                // Remove PKCS7 padding
                if (out.length > 0) {
                    int pad = out[out.length - 1] & 0xFF;
                    if (pad >= 1 && pad <= 16 && pad <= out.length) {
                        return Arrays.copyOf(out, out.length - pad);
                    }
                }
                return out;
            } else {
                return rc4(objKey, data);
            }
        } catch (Exception e) {
            return data;
        }
    }

    // ── Key derivation (PDF spec §7.6.3) ─────────────────────────────────────

    private static byte[] padPassword(byte[] pw) {
        byte[] out = new byte[32];
        int n = Math.min(pw.length, 32);
        System.arraycopy(pw, 0, out, 0, n);
        if (n < 32) System.arraycopy(PAD, 0, out, n, 32 - n);
        return out;
    }

    private static byte[] toBytes(String s) {
        try { return s.getBytes("ISO-8859-1"); }
        catch (Exception e) { return s.getBytes(); }
    }

    /** Algorithm 2 — compute the encryption key from a padded user password. */
    private static byte[] computeKey(EncryptInfo enc, byte[] paddedPass) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(paddedPass);
        md.update(enc.o);
        md.update((byte) (enc.p        & 0xFF));
        md.update((byte)((enc.p >>  8) & 0xFF));
        md.update((byte)((enc.p >> 16) & 0xFF));
        md.update((byte)((enc.p >> 24) & 0xFF));
        md.update(enc.fileId);
        // PDF spec §7.6.3.3 Algorithm 2 step (f): add 0xFF×4 only when
        // EncryptMetadata is explicitly false (metadata streams not encrypted).
        // The default is true, so this branch is taken only for unusual PDFs.
        if (enc.r >= 4 && !enc.encryptMetadata) {
            md.update(new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF});
        }
        byte[] hash = md.digest();
        if (enc.r >= 3) {
            for (int i = 0; i < 50; i++) {
                md.reset();
                md.update(hash, 0, enc.keyLen);
                hash = md.digest();
            }
        }
        return Arrays.copyOf(hash, enc.keyLen);
    }

    /** Algorithm 6/7 — verify user password. */
    private static boolean verifyUser(byte[] key, EncryptInfo enc) throws Exception {
        if (enc.r == 2) {
            byte[] result = rc4(key, PAD);
            return startsWith(enc.u, result, 32);
        }
        // R >= 3
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(PAD);
        md.update(enc.fileId);
        byte[] hash = md.digest(); // 16 bytes
        byte[] result = rc4(key, hash);
        for (int i = 1; i <= 19; i++) {
            byte[] tweaked = xorKey(key, i);
            result = rc4(tweaked, result);
        }
        return startsWith(enc.u, result, 16);
    }

    /** Algorithm 3 — recover the user password from the owner password. */
    private static byte[] ownerToUserKey(EncryptInfo enc, byte[] paddedOwnerPass)
            throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(paddedOwnerPass);
        byte[] hash = md.digest();
        if (enc.r >= 3) {
            for (int i = 0; i < 50; i++) {
                md.reset();
                md.update(hash, 0, enc.keyLen);
                hash = md.digest();
            }
        }
        byte[] ownerKey = Arrays.copyOf(hash, enc.keyLen);

        byte[] userPassPadded;
        if (enc.r == 2) {
            userPassPadded = rc4(ownerKey, enc.o);
        } else {
            byte[] cur = enc.o.clone();
            for (int i = 19; i >= 0; i--) {
                cur = rc4(xorKey(ownerKey, i), cur);
            }
            userPassPadded = cur;
        }

        byte[] key = computeKey(enc, userPassPadded);
        return verifyUser(key, enc) ? key : null;
    }

    /** Per-object key (PDF spec §7.6.2). */
    private static byte[] objectKey(byte[] encKey, int obj, int gen, boolean aes)
            throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(encKey);
        md.update((byte) (obj        & 0xFF));
        md.update((byte)((obj >>  8) & 0xFF));
        md.update((byte)((obj >> 16) & 0xFF));
        md.update((byte) (gen        & 0xFF));
        md.update((byte)((gen >>  8) & 0xFF));
        if (aes) md.update(new byte[]{0x73, 0x41, 0x6C, 0x54}); // "sAlT"
        byte[] hash = md.digest();
        int len = Math.min(encKey.length + 5, 16);
        return Arrays.copyOf(hash, len);
    }

    // ── RC4 stream cipher ─────────────────────────────────────────────────────

    static byte[] rc4(byte[] key, byte[] data) {
        byte[] S = new byte[256];
        for (int i = 0; i < 256; i++) S[i] = (byte) i;
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + (S[i] & 0xFF) + (key[i % key.length] & 0xFF)) & 0xFF;
            byte t = S[i]; S[i] = S[j]; S[j] = t;
        }
        byte[] out = new byte[data.length];
        int x = 0, y = 0;
        for (int i = 0; i < data.length; i++) {
            x = (x + 1) & 0xFF;
            y = (y + (S[x] & 0xFF)) & 0xFF;
            byte t = S[x]; S[x] = S[y]; S[y] = t;
            int k = ((S[x] & 0xFF) + (S[y] & 0xFF)) & 0xFF;
            out[i] = (byte) (data[i] ^ S[k]);
        }
        return out;
    }

    // ── Dictionary / structure parsers ────────────────────────────────────────

    private static String findObjDict(String pdf, int objNum) {
        // Find "N 0 obj" (generation 0 is universal for non-incremental PDFs)
        String marker = objNum + " 0 obj";
        int pos = pdf.indexOf(marker);
        if (pos < 0) return null;
        pos += marker.length();
        int dictStart = pdf.indexOf("<<", pos);
        if (dictStart < 0) return null;
        int dictEnd = findDictEnd(pdf, dictStart);
        if (dictEnd < 0) return null;
        return pdf.substring(dictStart, dictEnd);
    }

    /**
     * Finds the end position (exclusive) of a PDF dictionary starting at pos ('<' '<').
     */
    static int findDictEnd(String s, int start) {
        int depth = 0;
        boolean inStr = false;
        for (int i = start; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\') { i++; }
                else if (c == ')') inStr = false;
            } else {
                if (c == '(') inStr = true;
                else if (c == '<' && s.charAt(i + 1) == '<') { depth++; i++; }
                else if (c == '>' && s.charAt(i + 1) == '>') {
                    if (--depth == 0) return i + 2;
                    i++;
                }
            }
        }
        return -1;
    }

    private static int getDictInt(String dict, String key, int def) {
        int idx = dict.indexOf(key);
        if (idx < 0) return def;
        // Ensure key is not a prefix of a longer name (e.g. /V vs /Version)
        int after = idx + key.length();
        if (after < dict.length()) {
            char nc = dict.charAt(after);
            if (Character.isLetterOrDigit(nc)) return def;
        }
        int pos = after;
        while (pos < dict.length() && PdfTextExtractor.isWs(dict.charAt(pos))) pos++;
        int start = pos;
        if (pos < dict.length() && dict.charAt(pos) == '-') pos++;
        while (pos < dict.length() && Character.isDigit(dict.charAt(pos))) pos++;
        if (pos == start) return def;
        try { return Integer.parseInt(dict.substring(start, pos)); }
        catch (NumberFormatException e) { return def; }
    }

    /**
     * Extracts a hex or literal byte string for /O, /U etc.
     * We pass the full PDF because literal strings may need escape handling already
     * implemented in PdfTextExtractor.parseLiteralString.
     */
    private static byte[] getDictHexBytes(String fullPdf, String dict, String key) {
        int idx = 0;
        while (true) {
            idx = dict.indexOf(key, idx);
            if (idx < 0) return null;
            int after = idx + key.length();
            // Reject prefix matches (e.g. /OE, /Owner)
            if (after < dict.length() && Character.isLetterOrDigit(dict.charAt(after))) {
                idx++;
                continue;
            }
            break;
        }
        int pos = idx + key.length();
        while (pos < dict.length() && PdfTextExtractor.isWs(dict.charAt(pos))) pos++;
        if (pos >= dict.length()) return null;
        char c = dict.charAt(pos);
        if (c == '<' && (pos + 1 >= dict.length() || dict.charAt(pos + 1) != '<')) {
            int end = dict.indexOf('>', pos + 1);
            if (end < 0) return null;
            return hexToBytes(dict.substring(pos + 1, end).replaceAll("\\s", ""));
        }
        if (c == '(') {
            int[] end = {pos};
            String s = PdfTextExtractor.parseLiteralString(dict, pos, end);
            try { return s.getBytes("ISO-8859-1"); } catch (Exception e) { return null; }
        }
        return null;
    }

    private static byte[] findFileId(String pdf) {
        // Look for /ID in the last 8 KB (trailer region)
        int from = Math.max(0, pdf.length() - 8000);
        String tail = pdf.substring(from);
        int idx = tail.lastIndexOf("/ID");
        if (idx < 0) return new byte[16];
        int pos = idx + 3;
        while (pos < tail.length() && tail.charAt(pos) != '[') pos++;
        if (pos >= tail.length()) return new byte[16];
        pos++;
        while (pos < tail.length() && PdfTextExtractor.isWs(tail.charAt(pos))) pos++;
        if (pos >= tail.length() || tail.charAt(pos) != '<') return new byte[16];
        int end = tail.indexOf('>', pos + 1);
        if (end < 0) return new byte[16];
        byte[] id = hexToBytes(tail.substring(pos + 1, end).replaceAll("\\s", ""));
        return (id != null) ? id : new byte[16];
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static byte[] hexToBytes(String hex) {
        if (hex == null) return null;
        if (hex.length() % 2 != 0) hex = hex + "0";
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            try { out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16); }
            catch (NumberFormatException e) { return null; }
        }
        return out;
    }

    private static byte[] xorKey(byte[] key, int n) {
        byte[] out = new byte[key.length];
        for (int i = 0; i < key.length; i++) out[i] = (byte) (key[i] ^ n);
        return out;
    }

    private static boolean startsWith(byte[] container, byte[] prefix, int len) {
        if (container == null || prefix == null) return false;
        for (int i = 0; i < len; i++) {
            if (i >= container.length || i >= prefix.length) return false;
            if (container[i] != prefix[i]) return false;
        }
        return true;
    }
}
