package com.ldsa.myfintracker.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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

    private static String fromBytes(byte[] raw, String password) throws Exception {
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
        int pos = 0;
        int nStreams = 0, nFlate = 0, nInflated = 0, nBT = 0, nText = 0;
        int[] firstObjNumGen = null;

        while (pos < pdf.length()) {
            int kw = pdf.indexOf("stream", pos);
            if (kw < 0) break;

            // Confirm this is a real stream keyword: preceding non-whitespace must be '>'
            int pre = kw - 1;
            while (pre >= 0 && isWs(pdf.charAt(pre))) pre--;
            if (pre < 0 || pdf.charAt(pre) != '>') {
                pos = kw + 6;
                continue;
            }

            // Data starts after "stream" + optional \r + mandatory \n
            int dataStart = kw + 6;
            if (dataStart < pdf.length() && pdf.charAt(dataStart) == '\r') dataStart++;
            if (dataStart < pdf.length() && pdf.charAt(dataStart) == '\n') dataStart++;

            int endKw = pdf.indexOf("endstream", dataStart);
            if (endKw < 0) break;

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
                String lines = chunksToLines(chunks);
                if (!lines.isEmpty()) { nText++; out.append(lines); }
            }

            pos = endKw + 9;
        }

        String result = cleanText(out.toString());
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
        List<List<TextChunk>> merged = new ArrayList<List<TextChunk>>();
        for (int k = 0; k < rows.size(); k++) {
            List<TextChunk> row = rows.get(k);
            float minX = Float.MAX_VALUE;
            for (int j = 0; j < row.size(); j++) {
                if (row.get(j).x < minX) minX = row.get(j).x;
            }
            boolean isCont = !merged.isEmpty()
                    && row.size() <= 2          // ≤2 wrapping columns — see assumption above
                    && minX > contThresh;        // not in the leftmost (date) column
            if (isCont) {
                merged.get(merged.size() - 1).addAll(row);
            } else {
                merged.add(new ArrayList<TextChunk>(row));
            }
        }

        // Detect tabular columns across the whole page. Each column is tagged
        // with its alignment so right-aligned amount columns are matched by
        // right-edge (which is stable), not by X-start which shifts with
        // amount length. Empty cells become a "-" sentinel.
        List<Column> columns = detectColumns(merged);

        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < merged.size(); k++) {
            appendRow(sb, merged.get(k), columns);
        }
        return sb.toString();
    }

    /** A detected column: `pos` is the defining edge (either left or right
     *  depending on `rightAligned`). Chunks snap to the column whose defining
     *  edge is nearest. */
    private static class Column {
        float   pos;
        boolean rightAligned;
    }

    /** Comparator for sorting Columns by effective left edge. */
    static class ByColPos implements Comparator {
        public int compare(Object oa, Object ob) {
            float a = ((Column) oa).pos;
            float b = ((Column) ob).pos;
            if (a < b) return -1;
            if (a > b) return 1;
            return 0;
        }
    }

    /** Returns detected columns (sorted by `pos`), or empty if fewer than
     *  2 reliable columns are found. Clusters both X-starts (left-aligned)
     *  and X-ends (right-aligned) — a column is kept if either axis has
     *  content in ≥ 1/3 of the rows. */
    private static List<Column> detectColumns(List<List<TextChunk>> rows) {
        if (rows.size() < 3) return new ArrayList<Column>();

        List<Float> allLeft  = new ArrayList<Float>();
        List<Float> allRight = new ArrayList<Float>();
        for (int k = 0; k < rows.size(); k++) {
            for (int j = 0; j < rows.get(k).size(); j++) {
                TextChunk c = rows.get(k).get(j);
                allLeft.add(c.x);
                allRight.add(c.x + c.text.length() * 5.0f);
            }
        }
        Collections.sort(allLeft);
        Collections.sort(allRight);

        int threshold = Math.max(3, rows.size() / 3);
        List<Column> cols = new ArrayList<Column>();
        clusterInto(allLeft,  threshold, cols, false);
        clusterInto(allRight, threshold, cols, true);

        if (cols.size() < 2) return new ArrayList<Column>();

        // Sort by pos; dedupe columns that are within 10pt of each other
        // (prefer the right-aligned entry so amount columns win over any
        // stray left-edge cluster the amounts also produced).
        Collections.sort(cols, new ByColPos());
        List<Column> dedup = new ArrayList<Column>();
        for (int i = 0; i < cols.size(); i++) {
            Column c = cols.get(i);
            if (!dedup.isEmpty() && Math.abs(dedup.get(dedup.size() - 1).pos - c.pos) < 10f) {
                if (c.rightAligned) dedup.set(dedup.size() - 1, c);
                continue;
            }
            dedup.add(c);
        }
        return dedup.size() >= 2 ? dedup : new ArrayList<Column>();
    }

    /** Cluster a sorted list of X positions; emit a Column for each cluster
     *  whose size meets `threshold`. */
    private static void clusterInto(List<Float> sorted, int threshold,
                                    List<Column> out, boolean rightAligned) {
        float sum = 0;
        int   n   = 0;
        float last = -1000f;
        for (int i = 0; i < sorted.size(); i++) {
            float x = sorted.get(i);
            if (n > 0 && x - last > 10f) {
                if (n >= threshold) {
                    Column c = new Column();
                    c.pos = sum / n;
                    c.rightAligned = rightAligned;
                    out.add(c);
                }
                sum = 0; n = 0;
            }
            sum += x; n++; last = x;
        }
        if (n >= threshold) {
            Column c = new Column();
            c.pos = sum / n;
            c.rightAligned = rightAligned;
            out.add(c);
        }
    }

    private static void appendRow(StringBuilder sb, List<TextChunk> row, List<Column> columns) {
        if (row.isEmpty()) return;
        Collections.sort(row, new ByXAsc());

        if (columns.size() < 2) { appendRowSpaced(sb, row); return; }

        // Assign each chunk to the column whose defining edge is nearest.
        // Right-aligned columns compare chunk right-edge; left-aligned compare left-edge.
        int cols = columns.size();
        StringBuilder[] cells = new StringBuilder[cols];
        for (int i = 0; i < cols; i++) cells[i] = new StringBuilder();
        for (int k = 0; k < row.size(); k++) {
            TextChunk ch = row.get(k);
            float chLeft  = ch.x;
            float chRight = ch.x + ch.text.length() * 5.0f;
            int   best    = 0;
            float bestDist = Float.MAX_VALUE;
            for (int c = 0; c < cols; c++) {
                Column col = columns.get(c);
                float d = Math.abs((col.rightAligned ? chRight : chLeft) - col.pos);
                if (d < bestDist) { bestDist = d; best = c; }
            }
            if (cells[best].length() > 0) cells[best].append(' ');
            cells[best].append(ch.text);
        }

        StringBuilder line = new StringBuilder();
        for (int c = 0; c < cols; c++) {
            if (c > 0) line.append("  ");
            if (cells[c].length() == 0) line.append('-');
            else line.append(cells[c]);
        }
        String trimmed = line.toString().trim();
        if (!trimmed.isEmpty()) sb.append(trimmed).append('\n');
    }

    private static void appendRowSpaced(StringBuilder sb, List<TextChunk> row) {
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
