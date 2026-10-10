import com.ldsa.myfintracker.pdf.PdfTextExtractor;

import java.io.FileInputStream;
import java.io.InputStream;

public class Main {

    public static void main(String[] args) throws Exception {
        String path = null;
        String password = null;
        boolean mask = true;
        boolean dump = false;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--password".equals(a) && i + 1 < args.length) {
                password = args[++i];
            } else if ("--no-mask".equals(a)) {
                mask = false;
            } else if ("--dump".equals(a)) {
                dump = true;
            } else if ("-h".equals(a) || "--help".equals(a)) {
                usage(0);
            } else if (path == null && !a.startsWith("--")) {
                path = a;
            } else {
                System.err.println("Unknown arg: " + a);
                usage(2);
            }
        }
        if (path == null) usage(2);

        String out;
        InputStream is = new FileInputStream(path);
        try {
            out = dump
                    ? PdfTextExtractor.extractChunkDump(is, password)
                    : PdfTextExtractor.extract(is, password);
        } finally {
            is.close();
        }

        if (mask) out = dump ? maskDumpTextOnly(out) : maskAll(out);
        System.out.println(out);
    }

    private static void usage(int code) {
        System.err.println("Usage: java Main <pdf-path> [--password <pw>] [--dump] [--no-mask]");
        System.err.println();
        System.err.println("  <pdf-path>       path to PDF file to extract");
        System.err.println("  --password <pw>  password for encrypted PDF (omit = unencrypted)");
        System.err.println("  --dump           print raw TextChunk list (stream, y, x, text) TSV");
        System.err.println("                   instead of final extract() output");
        System.err.println("  --no-mask        print raw output (default masks letters -> x, digits -> 9)");
        System.exit(code);
    }

    /** Masks letters -> x/X and digits -> 9 across the entire string.
     *  Used for the normal extract() output where every character is PII-adjacent. */
    private static String maskAll(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) sb.append(maskChar(s.charAt(i)));
        return sb.toString();
    }

    /** Masks the dump output's text field only. Dump lines are TSV:
     *  {@code streamIdx\ty\tx\ttext} — mask only after the 3rd tab so coordinate
     *  floats stay readable. {@code === stream N ===} headers pass through unchanged. */
    private static String maskDumpTextOnly(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int lineStart = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i == s.length() || s.charAt(i) == '\n') {
                maskDumpLine(out, s, lineStart, i);
                if (i < s.length()) out.append('\n');
                lineStart = i + 1;
            }
        }
        return out.toString();
    }

    private static void maskDumpLine(StringBuilder out, String s, int from, int to) {
        // Pass-through for stream-header lines and anything that isn't the TSV shape.
        if (to - from >= 3 && s.charAt(from) == '=' && s.charAt(from + 1) == '='
                && s.charAt(from + 2) == '=') {
            out.append(s, from, to);
            return;
        }
        int tabs = 0;
        int maskStart = -1;
        for (int i = from; i < to; i++) {
            if (s.charAt(i) == '\t') {
                tabs++;
                if (tabs == 3) { maskStart = i + 1; break; }
            }
        }
        if (maskStart < 0) {
            // Not a TSV line (e.g. the EXTRACT_EMPTY diagnostic) — mask everything to be safe.
            for (int i = from; i < to; i++) out.append(maskChar(s.charAt(i)));
            return;
        }
        out.append(s, from, maskStart);
        for (int i = maskStart; i < to; i++) out.append(maskChar(s.charAt(i)));
    }

    private static char maskChar(char c) {
        if (Character.isLetter(c)) return Character.isUpperCase(c) ? 'X' : 'x';
        if (c >= '0' && c <= '9') return '9';
        return c;
    }
}
