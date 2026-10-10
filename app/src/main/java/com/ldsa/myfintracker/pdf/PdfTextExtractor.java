package com.ldsa.myfintracker.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Minimal PDF text extractor for bank-generated (not scanned) PDFs.
 * Handles FlateDecode-compressed and uncompressed content streams.
 * Does not require any external libraries — uses only java.util.zip.
 */
public class PdfTextExtractor {

    private static final int MAX_BYTES = 25 * 1024 * 1024; // 25 MB limit

    // Returned by extract() when no text could be produced; contains diagnostic counts.
    public static final String EXTRACT_EMPTY = "EXTRACT_EMPTY";

    /**
     * Extracts text from a plain or password-protected PDF.
     * Returns {@link PdfDecryptor#NEEDS_PASSWORD} if encrypted and no password given.
     * Returns {@link PdfDecryptor#WRONG_PASSWORD} if the password is incorrect.
     * Returns empty string on other failures.
     */
    public static String extract(InputStream is, String password) {
        try {
            byte[] raw = readFully(is);
            return fromBytes(raw, password);
        } catch (Exception e) {
            return "";
        }
    }

    /** Convenience overload — tries extraction without a password first. */
    public static String extract(InputStream is) {
        return extract(is, null);
    }

    /**
     * Debug / diagnostic entry point: returns the raw TextChunk list for every
     * content stream, one chunk per line as TSV: {@code streamIdx\ty\tx\ttext}.
     * Y-row grouping, continuation-row merging, and line emission are skipped —
     * this exposes exactly what {@link #chunksFromStream} saw, so callers can
     * inspect PDF text positioning directly. Used by the pdf-dump helper tool.
     */
    public static String extractChunkDump(InputStream is, String password) {
        try {
            byte[] raw = readFully(is);
            return fromBytes(raw, password, true);
        } catch (Exception e) {
            return "";
        }
    }

    private static String fromBytes(byte[] raw, String password) throws Exception {
        return fromBytes(raw, password, false);
    }

    private static String fromBytes(byte[] raw, String password, boolean dumpChunks) throws Exception {
        // ISO-8859-1 maps each byte 0–255 to the same char value, preserving binary data.
        String pdf = new String(raw, "ISO-8859-1");

        // ── Encryption check ──────────────────────────────────────────────────
        PdfDecryptor.EncryptInfo enc = PdfDecryptor.detect(pdf);
        byte[] encKey = null;
        if (enc != null) {
            if (password == null) return PdfDecryptor.NEEDS_PASSWORD;
            encKey = PdfDecryptor.deriveKey(enc, password);
            if (encKey == null) {
                return PdfDecryptor.WRONG_PASSWORD
                        + "\nV=" + enc.v + " R=" + enc.r
                        + " keyLen=" + (enc.keyLen * 8) + "bit"
                        + " AES=" + enc.useAes
                        + " encMeta=" + enc.encryptMetadata
                        + "\nO=" + (enc.o != null ? enc.o.length : 0) + "B"
                        + " U=" + (enc.u != null ? enc.u.length : 0) + "B"
                        + " ID=" + (enc.fileId != null ? enc.fileId.length : 0) + "B";
            }
        }

        StringBuilder out = new StringBuilder();
        int nStreams = 0, nFlate = 0, nInflated = 0, nBT = 0, nText = 0;
        int[] firstObjNumGen = null;

        // Pass 1: collect every content-stream keyword in file order.
        List<Integer> allStreamKws = findAllStreamKws(pdf);

        // Pass 2: reorder them to /Pages tree traversal order so page N's content
        // comes out before page N+1's. PDF object numbers are not laid out in page
        // order, so file-order processing scrambles pages. The reorder returns
        // null (and emits a diagnostic marker) if the catalog/pages tree can't be
        // resolved — then the fallback preserves today's file-order behaviour.
        StreamOrder order = tryPageOrder(pdf, allStreamKws);
        List<Integer> orderedKws;
        int pageTreeCount;
        if (order == null) {
            out.append("[######### PDF /Pages tree could not be resolved — using file order #########]\n");
            orderedKws = allStreamKws;
            pageTreeCount = 0;
        } else {
            orderedKws = order.kws;
            pageTreeCount = order.pageCount;
        }

        // Pass 3: process each stream in resolved order.
        for (int streamIdx = 0; streamIdx < orderedKws.size(); streamIdx++) {
            int kw = orderedKws.get(streamIdx);
            // When page-tree resolution succeeded, streams past pageTreeCount
            // were not reached by walking /Pages — flag them so output readers can tell.
            boolean notInPageTree = (order != null) && (streamIdx >= pageTreeCount);

            // Data starts after "stream" + optional \r + mandatory \n
            int dataStart = kw + 6;
            if (dataStart < pdf.length() && pdf.charAt(dataStart) == '\r') dataStart++;
            if (dataStart < pdf.length() && pdf.charAt(dataStart) == '\n') dataStart++;

            int endKw = pdf.indexOf("endstream", dataStart);
            if (endKw < 0) continue;

            nStreams++;

            // Look at the dict region before this stream for the /Filter entry
            String dict = pdf.substring(Math.max(0, kw - 600), kw);
            boolean flate = dict.contains("/FlateDecode")
                    || dict.contains("/Fl\n") || dict.contains("/Fl\r")
                    || dict.contains("/Fl ") || dict.contains("/Fl/")
                    || dict.contains("/Fl>");
            if (flate) nFlate++;

            byte[] data = pdf.substring(dataStart, endKw).getBytes("ISO-8859-1");

            // ── Decrypt stream if the document is encrypted ───────────────────
            if (encKey != null) {
                int[] og = objNumGen(pdf, kw);
                if (firstObjNumGen == null) firstObjNumGen = og;
                data = PdfDecryptor.decryptStream(data, encKey, og[0], og[1], enc.useAes);
            }

            String content = null;
            if (flate) {
                content = tryInflate(data);
                if (content != null) nInflated++;
            }
            if (content == null) content = new String(data, "ISO-8859-1");

            // Only bother parsing content streams (they have BT text operators)
            if (content.contains("BT")) {
                nBT++;
                List<TextChunk> chunks = chunksFromStream(content);
                int streamLabel = streamIdx + 1;
                if (dumpChunks) {
                    if (!chunks.isEmpty()) {
                        nText++;
                        out.append("=== stream ").append(streamLabel).append(" ===\n");
                        if (notInPageTree) {
                            out.append("[######### Stream ").append(streamLabel)
                               .append(" has text but was not referenced from /Pages tree #########]\n");
                        }
                        for (int i = 0; i < chunks.size(); i++) {
                            TextChunk ch = chunks.get(i);
                            out.append(streamLabel).append('\t')
                               .append(ch.y).append('\t')
                               .append(ch.x).append('\t')
                               .append(ch.text).append('\n');
                        }
                    }
                } else {
                    String lines = chunksToLines(chunks);
                    if (!lines.isEmpty()) {
                        nText++;
                        if (notInPageTree) {
                            out.append("[######### Stream ").append(streamLabel)
                               .append(" has text but was not referenced from /Pages tree #########]\n");
                        }
                        out.append(lines);
                    }
                }
            }
        }

        // In dump mode, skip cleanText (which collapses whitespace & trims lines);
        // chunk dump output is already one-chunk-per-line TSV and should pass through verbatim.
        String result = dumpChunks ? out.toString() : cleanText(out.toString());
        if (result.isEmpty()) {
            String obj0 = (firstObjNumGen != null)
                    ? firstObjNumGen[0] + "g" + firstObjNumGen[1] : "none";
            return EXTRACT_EMPTY
                    + "|streams=" + nStreams
                    + " flate=" + nFlate
                    + " inflated=" + nInflated
                    + " BT=" + nBT
                    + " text=" + nText
                    + (enc != null ? " enc=true" : " enc=false")
                    + " obj0=" + obj0;
        }
        return result;
    }

    /**
     * Scans backwards from {@code beforePos} to find the enclosing PDF object header
     * ("N G obj") and returns {objNum, genNum}.
     * Uses regex so newline-separated tokens ("7\n0\nobj") are handled correctly.
     */
    private static final java.util.regex.Pattern OBJ_PATTERN =
            java.util.regex.Pattern.compile("(\\d+)\\s+(\\d+)\\s+obj");

    private static int[] objNumGen(String pdf, int beforePos) {
        int scanFrom = Math.max(0, beforePos - 8000);
        String region = pdf.substring(scanFrom, beforePos);
        java.util.regex.Matcher m = OBJ_PATTERN.matcher(region);
        int objNum = 0, genNum = 0;
        boolean found = false;
        // Iterate to the last match — that's the object header immediately enclosing this stream
        while (m.find()) {
            try {
                objNum = Integer.parseInt(m.group(1));
                genNum = Integer.parseInt(m.group(2));
                found = true;
            } catch (NumberFormatException ignored) {}
        }
        return found ? new int[]{objNum, genNum} : new int[]{0, 0};
    }

    // ── Page-order resolution ────────────────────────────────────────────────
    //
    // PDF objects are not stored in page order; the catalog's /Pages tree is
    // the authoritative source for reading order. These helpers resolve that
    // tree so content streams can be processed page-by-page instead of in
    // file-object order (which scrambles pages for most banks' statements).

    private static final java.util.regex.Pattern REF_PATTERN =
            java.util.regex.Pattern.compile("(\\d+)\\s+(\\d+)\\s+R");

    /** Result of a successful page-tree walk. First {@code pageCount} entries
     *  of {@code kws} are stream offsets in /Pages document order; the
     *  remainder are streams found elsewhere in the file (fonts, metadata,
     *  xobjects) appended so no text is dropped. */
    private static class StreamOrder {
        List<Integer> kws;
        int pageCount;
    }

    /** First pass: find every content-stream keyword that follows an obj dict's
     *  closing '>'. Returns offsets in file order. */
    private static List<Integer> findAllStreamKws(String pdf) {
        List<Integer> kws = new ArrayList<Integer>();
        int pos = 0;
        while (pos < pdf.length()) {
            int kw = pdf.indexOf("stream", pos);
            if (kw < 0) break;
            int pre = kw - 1;
            while (pre >= 0 && isWs(pdf.charAt(pre))) pre--;
            if (pre < 0 || pdf.charAt(pre) != '>') { pos = kw + 6; continue; }
            kws.add(kw);
            int ds = kw + 6;
            if (ds < pdf.length() && pdf.charAt(ds) == '\r') ds++;
            if (ds < pdf.length() && pdf.charAt(ds) == '\n') ds++;
            int end = pdf.indexOf("endstream", ds);
            if (end < 0) break;
            pos = end + 9;
        }
        return kws;
    }

    /** Attempts to reorder streamKws to match /Pages tree traversal order.
     *  Returns null if the catalog or pages tree can't be resolved; the caller
     *  then emits a diagnostic marker and falls back to file-order. */
    private static StreamOrder tryPageOrder(String pdf, List<Integer> allStreamKws) {
        if (allStreamKws.isEmpty()) return null;

        // Map (objNum, genNum) → streamKw by scanning backward from each stream
        // to its enclosing "N G obj" header.
        Map<Long, Integer> objToStreamKw = new HashMap<Long, Integer>();
        for (int i = 0; i < allStreamKws.size(); i++) {
            int kw = allStreamKws.get(i);
            int[] og = objNumGen(pdf, kw);
            if (og[0] <= 0) continue;
            long key = ((long) og[0] << 32) | (og[1] & 0xFFFFFFFFL);
            objToStreamKw.put(key, kw);
        }

        // Index every top-level "N G obj" header → body range.
        Map<Long, int[]> objIdx = indexObjects(pdf, allStreamKws);
        if (objIdx.isEmpty()) return null;

        // Resolve /Root (catalog) → /Pages → walk the pages tree.
        int[] rootRef = findRootRef(pdf);
        if (rootRef == null) return null;
        long catKey = ((long) rootRef[0] << 32) | (rootRef[1] & 0xFFFFFFFFL);
        int[] catRange = objIdx.get(catKey);
        if (catRange == null) return null;
        int[] pagesRef = parseIndirectRef(pdf.substring(catRange[0], catRange[1]), "/Pages");
        if (pagesRef == null) return null;

        List<long[]> contentRefs = new ArrayList<long[]>();
        collectPageContents(pdf, pagesRef[0], pagesRef[1], objIdx, contentRefs, 0);
        if (contentRefs.isEmpty()) return null;

        // Convert content refs to streamKws in order; append any streams not reached by the tree.
        List<Integer> ordered = new ArrayList<Integer>();
        Set<Integer> used = new HashSet<Integer>();
        for (int i = 0; i < contentRefs.size(); i++) {
            long[] ref = contentRefs.get(i);
            long key = (ref[0] << 32) | (ref[1] & 0xFFFFFFFFL);
            Integer kw = objToStreamKw.get(key);
            if (kw != null) { ordered.add(kw); used.add(kw); }
        }
        int pageCount = ordered.size();
        for (int i = 0; i < allStreamKws.size(); i++) {
            Integer kw = allStreamKws.get(i);
            if (!used.contains(kw)) ordered.add(kw);
        }
        StreamOrder so = new StreamOrder();
        so.kws = ordered;
        so.pageCount = pageCount;
        return so;
    }

    /** Builds (objNum,genNum) → (bodyStart, endobjOffset) for every "N G obj"
     *  header that lies outside a stream's data region. */
    private static Map<Long, int[]> indexObjects(String pdf, List<Integer> streamKws) {
        Map<Long, int[]> idx = new HashMap<Long, int[]>();
        // Compute stream data ranges (so we can skip "N G obj" matches that
        // happen to land inside compressed stream bytes).
        List<int[]> streamRanges = new ArrayList<int[]>();
        for (int i = 0; i < streamKws.size(); i++) {
            int kw = streamKws.get(i);
            int ds = kw + 6;
            if (ds < pdf.length() && pdf.charAt(ds) == '\r') ds++;
            if (ds < pdf.length() && pdf.charAt(ds) == '\n') ds++;
            int end = pdf.indexOf("endstream", ds);
            if (end > 0) streamRanges.add(new int[]{ds, end});
        }
        java.util.regex.Matcher m = OBJ_PATTERN.matcher(pdf);
        int rangeIdx = 0;
        while (m.find()) {
            int pos = m.start();
            while (rangeIdx < streamRanges.size() && streamRanges.get(rangeIdx)[1] <= pos) rangeIdx++;
            if (rangeIdx < streamRanges.size()
                    && pos >= streamRanges.get(rangeIdx)[0]
                    && pos <  streamRanges.get(rangeIdx)[1]) continue;
            try {
                int n = Integer.parseInt(m.group(1));
                int g = Integer.parseInt(m.group(2));
                long k = ((long) n << 32) | (g & 0xFFFFFFFFL);
                int bodyStart = m.end();
                int endPos = pdf.indexOf("endobj", bodyStart);
                if (endPos < 0) continue;
                idx.put(k, new int[]{bodyStart, endPos});
            } catch (NumberFormatException ignored) {}
        }
        return idx;
    }

    /** Resolves the /Root ref from the trailer dict, falling back to any
     *  "/Root N G R" appearance in the file (handles cross-reference-stream
     *  layouts where the dict lives inside an xref stream object). */
    private static int[] findRootRef(String pdf) {
        int trailerKw = pdf.lastIndexOf("trailer");
        if (trailerKw >= 0) {
            int ds = pdf.indexOf("<<", trailerKw);
            if (ds >= 0) {
                int de = findMatchingDictEnd(pdf, ds);
                if (de > 0) {
                    int[] r = parseIndirectRef(pdf.substring(ds, de), "/Root");
                    if (r != null) return r;
                }
            }
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "/Root\\s+(\\d+)\\s+(\\d+)\\s+R").matcher(pdf);
        int lastN = -1, lastG = -1;
        while (m.find()) {
            try {
                lastN = Integer.parseInt(m.group(1));
                lastG = Integer.parseInt(m.group(2));
            } catch (NumberFormatException ignored) {}
        }
        return lastN >= 0 ? new int[]{lastN, lastG} : null;
    }

    /** Returns the offset just past the matching ">>" for a dict starting at ds. */
    private static int findMatchingDictEnd(String pdf, int ds) {
        int depth = 1;
        int i = ds + 2;
        while (i + 1 < pdf.length()) {
            char c = pdf.charAt(i);
            char n = pdf.charAt(i + 1);
            if (c == '<' && n == '<') { depth++; i += 2; continue; }
            if (c == '>' && n == '>') { depth--; if (depth == 0) return i + 2; i += 2; continue; }
            i++;
        }
        return -1;
    }

    /** Finds "/KeyName N G R" in the given dict text; returns {N, G} or null. */
    private static int[] parseIndirectRef(String dict, String keyName) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                java.util.regex.Pattern.quote(keyName) + "\\s+(\\d+)\\s+(\\d+)\\s+R").matcher(dict);
        if (m.find()) {
            try {
                return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /** Walks the /Pages tree depth-first starting at (objNum, genNum),
     *  pushing each leaf page's content-stream refs to out in document order.
     *  A depth cap prevents infinite loops on malformed PDFs with cyclic /Kids. */
    private static void collectPageContents(String pdf, int objNum, int genNum,
                                            Map<Long, int[]> objIdx,
                                            List<long[]> out, int depth) {
        if (depth > 32) return;
        long key = ((long) objNum << 32) | (genNum & 0xFFFFFFFFL);
        int[] range = objIdx.get(key);
        if (range == null) return;
        String body = pdf.substring(range[0], range[1]);

        // Branch node: /Kids [ N G R ... ] — recurse on each kid in order.
        int kidsIdx = body.indexOf("/Kids");
        if (kidsIdx >= 0) {
            int bo = body.indexOf('[', kidsIdx);
            if (bo >= 0) {
                int bc = body.indexOf(']', bo);
                if (bc > bo) {
                    java.util.regex.Matcher km = REF_PATTERN.matcher(body.substring(bo + 1, bc));
                    while (km.find()) {
                        try {
                            int kn = Integer.parseInt(km.group(1));
                            int kg = Integer.parseInt(km.group(2));
                            collectPageContents(pdf, kn, kg, objIdx, out, depth + 1);
                        } catch (NumberFormatException ignored) {}
                    }
                    return;
                }
            }
        }

        // Leaf page: /Contents is a single ref or an array of refs.
        int contIdx = body.indexOf("/Contents");
        if (contIdx < 0) return;
        int vp = contIdx + "/Contents".length();
        while (vp < body.length() && isWs(body.charAt(vp))) vp++;
        if (vp >= body.length()) return;
        if (body.charAt(vp) == '[') {
            int bc = body.indexOf(']', vp);
            if (bc < 0) return;
            java.util.regex.Matcher am = REF_PATTERN.matcher(body.substring(vp + 1, bc));
            while (am.find()) {
                try {
                    out.add(new long[]{Integer.parseInt(am.group(1)), Integer.parseInt(am.group(2))});
                } catch (NumberFormatException ignored) {}
            }
        } else {
            // Expect "N G R" immediately after /Contents.
            java.util.regex.Matcher sm = REF_PATTERN.matcher(body);
            if (sm.find(vp) && sm.start() <= vp + 2) {
                try {
                    out.add(new long[]{Integer.parseInt(sm.group(1)), Integer.parseInt(sm.group(2))});
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    // ── Decompression ─────────────────────────────────────────────────────────

    private static String tryInflate(byte[] data) {
        // Try with zlib wrapper (the normal case for PDF FlateDecode)
        try {
            InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(data));
            return new String(readFully(iis), "ISO-8859-1");
        } catch (Exception ignored) {}
        // Try raw deflate without zlib header (some PDF generators omit it)
        try {
            Inflater inf = new Inflater(true);
            inf.setInput(data);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            while (!inf.finished() && !inf.needsInput()) {
                int n = inf.inflate(buf);
                if (n > 0) baos.write(buf, 0, n);
            }
            inf.end();
            if (baos.size() > 0) return baos.toString("ISO-8859-1");
        } catch (Exception ignored) {}
        return null;
    }

    // ── Position-aware text chunk extraction ─────────────────────────────────

    private static class TextChunk {
        String text;
        float  x;
        float  y;
        TextChunk(String t, float x, float y) { text = t; this.x = x; this.y = y; }
    }

    /** Sort: highest Y first (PDF Y=0 is bottom), then left-to-right by X. */
    private static class ByYDescXAsc implements Comparator {
        public int compare(Object oa, Object ob) {
            TextChunk a = (TextChunk) oa;
            TextChunk b = (TextChunk) ob;
            if (Math.abs(a.y - b.y) > 2.0f) return Float.compare(b.y, a.y);
            return Float.compare(a.x, b.x);
        }
    }

    private static class ByXAsc implements Comparator {
        public int compare(Object oa, Object ob) {
            TextChunk a = (TextChunk) oa;
            TextChunk b = (TextChunk) ob;
            return Float.compare(a.x, b.x);
        }
    }

    /**
     * Parses a PDF content stream and returns a list of positioned text chunks.
     * Tracks Tm/Td/TD/T* coordinates; Tj/TJ emit a TextChunk at the current position.
     */
    private static List<TextChunk> chunksFromStream(String s) {
        List<TextChunk> out = new ArrayList<TextChunk>();

        // Operand stack (numbers parsed before the operator)
        float[] ns = new float[8];
        int     nc = 0;

        StringBuilder pend   = new StringBuilder();
        boolean inBT  = false;
        float   curX  = 0, curY = 0;   // current text position
        float   lineX = 0, lineY = 0;  // start of text line (set by BT / Tm / Td)
        float   TL    = 12f;           // text leading (default 12 pt)

        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);

            if (isWs(c)) { i++; continue; }

            // Literal string  (...)
            if (c == '(') {
                int[] end = {i};
                String text = parseLiteralString(s, i, end);
                if (inBT) pend.append(text);
                i = end[0];
                continue;
            }

            // Hex string  <hh...>
            if (c == '<' && i + 1 < s.length() && s.charAt(i + 1) != '<') {
                int close = s.indexOf('>', i + 1);
                if (close < 0) { i++; continue; }
                if (inBT) {
                    String hex = s.substring(i + 1, close).replaceAll("[ \t\r\n]", "");
                    pend.append(decodeHex(hex));
                }
                i = close + 1;
                continue;
            }

            // TJ array  [...]
            if (c == '[') {
                int close = closingBracket(s, i);
                if (close < 0) { i++; continue; }
                if (inBT) pend.append(parseTJArray(s, i + 1, close));
                i = close + 1;
                continue;
            }

            // Dictionary  <<...>>  — skip entirely
            if (c == '<' && i + 1 < s.length() && s.charAt(i + 1) == '<') {
                int close = s.indexOf(">>", i + 2);
                i = (close < 0) ? i + 2 : close + 2;
                continue;
            }

            // PDF name  /Name
            if (c == '/') {
                int j = i + 1;
                while (j < s.length() && !isWs(s.charAt(j))
                        && s.charAt(j) != '/' && s.charAt(j) != '('
                        && s.charAt(j) != '[' && s.charAt(j) != '<') j++;
                i = j;
                nc = 0; // names reset operand stack
                continue;
            }

            // Number token
            if (c == '-' || c == '+' || c == '.' || Character.isDigit(c)) {
                int j = i + 1;
                while (j < s.length() && (Character.isDigit(s.charAt(j))
                        || s.charAt(j) == '.' || s.charAt(j) == 'e' || s.charAt(j) == 'E'
                        || ((s.charAt(j) == '-' || s.charAt(j) == '+')
                            && j > 0 && (s.charAt(j-1) == 'e' || s.charAt(j-1) == 'E')))) j++;
                try { ns[nc < 8 ? nc++ : 7] = Float.parseFloat(s.substring(i, j)); }
                catch (NumberFormatException ignored) { nc = 0; }
                i = j;
                continue;
            }

            // Keyword token
            if (Character.isLetter(c) || c == '\'' || c == '"' || c == '*') {
                int j = i;
                while (j < s.length() && !isWs(s.charAt(j))
                        && s.charAt(j) != '(' && s.charAt(j) != '['
                        && s.charAt(j) != '<' && s.charAt(j) != '/') j++;
                String tok = s.substring(i, j);
                i = j;

                if ("BT".equals(tok)) {
                    inBT = true;
                    pend.setLength(0);
                    curX = 0; curY = 0; lineX = 0; lineY = 0;

                } else if ("ET".equals(tok)) {
                    if (inBT) {
                        flushChunk(out, pend, curX, curY);
                        inBT = false;
                    }

                } else if (inBT) {

                    if ("Tj".equals(tok) || "'".equals(tok)) {
                        flushChunk(out, pend, curX, curY);
                        if ("'".equals(tok)) {
                            // next-line-and-show: move down one leading first
                            lineY -= TL; curX = lineX; curY = lineY;
                        }
                        nc = 0;

                    } else if ("TJ".equals(tok)) {
                        flushChunk(out, pend, curX, curY);
                        nc = 0;

                    } else if ("\"".equals(tok)) {
                        // Tw Tc " — next line and show
                        flushChunk(out, pend, curX, curY);
                        lineY -= TL; curX = lineX; curY = lineY;
                        nc = 0;

                    } else if ("Tm".equals(tok)) {
                        // a b c d e f Tm — sets text matrix; e=x, f=y
                        flushChunk(out, pend, curX, curY);
                        if (nc >= 6) {
                            curX = ns[nc - 2]; curY = ns[nc - 1];
                            lineX = curX;      lineY = curY;
                        }
                        nc = 0;

                    } else if ("Td".equals(tok)) {
                        // tx ty Td — move text position
                        flushChunk(out, pend, curX, curY);
                        if (nc >= 2) {
                            lineX += ns[nc - 2]; lineY += ns[nc - 1];
                            curX = lineX; curY = lineY;
                        }
                        nc = 0;

                    } else if ("TD".equals(tok)) {
                        // tx ty TD — move and set leading = -ty
                        flushChunk(out, pend, curX, curY);
                        if (nc >= 2) {
                            TL = -ns[nc - 1];
                            lineX += ns[nc - 2]; lineY += ns[nc - 1];
                            curX = lineX; curY = lineY;
                        }
                        nc = 0;

                    } else if ("T*".equals(tok)) {
                        flushChunk(out, pend, curX, curY);
                        lineY -= TL; curX = lineX; curY = lineY;
                        nc = 0;

                    } else if ("TL".equals(tok)) {
                        if (nc >= 1) TL = ns[nc - 1];
                        nc = 0;

                    } else {
                        nc = 0; // consume numeric operands for any other operator
                    }
                } else {
                    nc = 0;
                }
                continue;
            }

            i++;
        }

        return out;
    }

    private static void flushChunk(List<TextChunk> out, StringBuilder pend, float x, float y) {
        if (pend.length() == 0) return;
        String t = pend.toString().trim();
        if (!t.isEmpty()) out.add(new TextChunk(t, x, y));
        pend.setLength(0);
    }

    /**
     * Groups TextChunks by Y coordinate (same row = Y within 2 units),
     * merges wrapped continuation rows (indented, few chunks, no date/amount column),
     * sorts each row left-to-right, and joins with spaces proportional to the gap.
     */
    private static String chunksToLines(List<TextChunk> chunks) {
        if (chunks.isEmpty()) return "";
        Collections.sort(chunks, new ByYDescXAsc());

        // Find leftmost X across all chunks — marks the first-column boundary
        float leftmostX = Float.MAX_VALUE;
        for (int k = 0; k < chunks.size(); k++) {
            float x = chunks.get(k).x;
            if (x < leftmostX) leftmostX = x;
        }

        // Group into Y-rows
        List<List<TextChunk>> rows = new ArrayList<List<TextChunk>>();
        List<TextChunk> cur = new ArrayList<TextChunk>();
        float curY = chunks.get(0).y;

        for (int k = 0; k < chunks.size(); k++) {
            TextChunk c = chunks.get(k);
            if (Math.abs(c.y - curY) > 2.0f) {
                if (!cur.isEmpty()) { rows.add(new ArrayList<TextChunk>(cur)); cur.clear(); }
                curY = c.y;
            }
            cur.add(c);
        }
        if (!cur.isEmpty()) rows.add(cur);

        // Merge continuation rows into the preceding table row.
        //
        // ASSUMPTION: fewer than 3 columns wrap simultaneously (i.e. at most 2
        // PDF Y-groups carry continuation text for the same logical table row).
        // This holds for typical bank statements where only the narration/description
        // column wraps; date and amount columns are always single-line.
        //
        // KNOWN LIMITATION: if 3 or more columns wrap at once (row.size() > 2), or
        // if the leftmost column (date) itself wraps (minX ≤ contThresh), the
        // continuation row is NOT merged and will appear as a separate orphan line.
        // See limitations.md for the full list of PDF extraction constraints.
        float contThresh = leftmostX + 30f;
        float contUpperX = leftmostX + 100f;   // Details column usually starts within here.
        List<List<TextChunk>> merged = new ArrayList<List<TextChunk>>();
        for (int k = 0; k < rows.size(); k++) {
            List<TextChunk> row = rows.get(k);
            float minX = Float.MAX_VALUE;
            boolean hasNumeric = false;
            for (int j = 0; j < row.size(); j++) {
                TextChunk ch = row.get(j);
                if (ch.x < minX) minX = ch.x;
                if (isNumericCell(ch.text)) hasNumeric = true;
            }
            // A continuation should (a) start near the Details column, not
            // further right (which would indicate centred footer text like
            // "Closing Balance"), and (b) contain no numeric chunks (so section
            // totals or standalone amounts don't get swallowed into a tx row).
            boolean isCont = !merged.isEmpty()
                    && row.size() <= 2          // ≤2 wrapping columns — see assumption above
                    && minX > contThresh         // not in the leftmost (date) column
                    && minX < contUpperX         // not centred-footer text
                    && !hasNumeric;              // no amount-looking tokens
            if (isCont) {
                merged.get(merged.size() - 1).addAll(row);
            } else {
                merged.add(new ArrayList<TextChunk>(row));
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < merged.size(); k++) {
            appendRow(sb, merged.get(k));
        }
        return sb.toString();
    }

    /** Chunk looks like an amount / balance: has digits, no spaces, and at
     *  least 60% of its characters are digits/commas/dots. Used by the
     *  continuation-row merge guard to avoid swallowing section totals or
     *  stray numeric footers into a transaction row. */
    private static boolean isNumericCell(String s) {
        if (s == null || s.isEmpty()) return false;
        int digits = 0, len = s.length();
        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);
            if (ch == ' ') return false;
            if ((ch >= '0' && ch <= '9') || ch == ',' || ch == '.') digits++;
        }
        return digits > 0 && digits * 10 >= len * 6;
    }

    private static void appendRow(StringBuilder sb, List<TextChunk> row) {
        if (row.isEmpty()) return;
        Collections.sort(row, new ByXAsc());
        StringBuilder line = new StringBuilder();
        float prevRight = -1f;
        for (TextChunk chunk : row) {
            if (prevRight >= 0) {
                float gap = chunk.x - prevRight;
                // ~5 pt per character is a reasonable default character width
                int spaces = Math.max(1, Math.round(gap / 5.0f));
                if (spaces > 20) spaces = 4; // cap run-away gaps (e.g. page margins)
                for (int k = 0; k < spaces; k++) line.append(' ');
            }
            line.append(chunk.text);
            // estimate right edge: 5pt per character
            prevRight = chunk.x + chunk.text.length() * 5.0f;
        }
        String trimmed = line.toString().trim();
        if (!trimmed.isEmpty()) sb.append(trimmed).append('\n');
    }

    // ── String / array parsers ────────────────────────────────────────────────

    /**
     * Parses a PDF literal string starting at s[start] = '('.
     * Sets end[0] to the position immediately after the closing ')'.
     */
    static String parseLiteralString(String s, int start, int[] end) {
        StringBuilder sb = new StringBuilder();
        int depth = 0, i = start;
        if (i < s.length() && s.charAt(i) == '(') { depth = 1; i++; }

        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char esc = s.charAt(i + 1);
                switch (esc) {
                    case 'n':  sb.append('\n'); i += 2; break;
                    case 'r':  sb.append('\r'); i += 2; break;
                    case 't':  sb.append('\t'); i += 2; break;
                    case '(':  sb.append('(');  i += 2; break;
                    case ')':  sb.append(')');  i += 2; break;
                    case '\\': sb.append('\\'); i += 2; break;
                    default:
                        if (esc >= '0' && esc <= '7') {
                            int oct = esc - '0'; i += 2;
                            for (int k = 0; k < 2 && i < s.length(); k++) {
                                char nc = s.charAt(i);
                                if (nc < '0' || nc > '7') break;
                                oct = oct * 8 + (nc - '0'); i++;
                            }
                            if (oct > 0) sb.append((char) oct);
                        } else {
                            sb.append(esc); i += 2;
                        }
                }
            } else if (c == '(') {
                depth++; sb.append(c); i++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) { i++; break; }
                sb.append(c); i++;
            } else {
                if (c > 0) sb.append(c); // skip null bytes
                i++;
            }
        }
        end[0] = i;
        return sb.toString();
    }

    /** Extracts text from a TJ array body (content between '[' and ']'). */
    private static String parseTJArray(String s, int start, int end) {
        StringBuilder sb = new StringBuilder();
        int i = start;
        while (i < end) {
            char c = s.charAt(i);
            if (c == '(') {
                int[] e = {i};
                sb.append(parseLiteralString(s, i, e));
                i = e[0];
            } else if (c == '<' && i + 1 < end && s.charAt(i + 1) != '<') {
                int close = s.indexOf('>', i + 1);
                if (close < 0 || close >= end) { i++; continue; }
                String hex = s.substring(i + 1, close).replaceAll("[ \t\r\n]", "");
                sb.append(decodeHex(hex));
                i = close + 1;
            } else if (c == '-' || Character.isDigit(c)) {
                // Kerning number — large negative gap means word space
                int j = i;
                if (c == '-') j++;
                while (j < end && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) j++;
                try {
                    double kern = Double.parseDouble(s.substring(i, j));
                    if (kern < -100) sb.append(' ');
                } catch (NumberFormatException ignored) {}
                i = j;
            } else {
                i++;
            }
        }
        return sb.toString();
    }

    private static String decodeHex(String hex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < hex.length(); i += 2) {
            try {
                int b = Integer.parseInt(hex.substring(i, i + 2), 16);
                if (b > 0) sb.append((char) b);
            } catch (NumberFormatException ignored) {}
        }
        return sb.toString();
    }

    /** Finds the matching ']' for a '[' at position start. */
    private static int closingBracket(String s, int start) {
        int depth = 0;
        boolean inStr = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\') { i++; }
                else if (c == ')') inStr = false;
            } else {
                if (c == '(') inStr = true;
                else if (c == '[') depth++;
                else if (c == ']') { if (--depth == 0) return i; }
            }
        }
        return -1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String cleanText(String s) {
        String[] lines = s.split("\n");
        StringBuilder sb = new StringBuilder();
        int blanks = 0;
        for (String line : lines) {
            // Normalize internal whitespace
            String t = line.replaceAll("[ \t]+", " ").trim();
            if (t.isEmpty()) {
                if (++blanks <= 1) sb.append('\n');
            } else {
                blanks = 0;
                sb.append(t).append('\n');
            }
        }
        return sb.toString().trim();
    }

    static boolean isWs(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\f';
    }

    private static byte[] readFully(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n, total = 0;
        while ((n = is.read(buf)) >= 0) {
            baos.write(buf, 0, n);
            if ((total += n) > MAX_BYTES) break;
        }
        return baos.toByteArray();
    }
}
