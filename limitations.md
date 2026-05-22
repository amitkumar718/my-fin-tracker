# Known Limitations

## PDF Text Extraction (`PdfTextExtractor`)

### Multi-line table cells — continuation row merging

After a position-aware extraction pass (tracking `Tm`/`Td`/`TD`/`T*` coordinates),
text chunks are grouped by Y coordinate into visual rows. When a table cell wraps
across multiple PDF lines, the wrapped fragments appear as separate Y-groups.

The extractor detects and merges these "continuation rows" back into the preceding
table row using two conditions:

1. The continuation row's leftmost chunk is indented more than 30 pt right of the
   page's leftmost column — meaning the date / first-column text is absent.
2. The continuation row has **≤ 2 chunks** — meaning at most 2 columns are
   simultaneously wrapping.

**Assumption:** Fewer than 3 columns wrap at the same time. In practice, bank
statements only wrap the narration / description column; date and amount columns
are always single-line.

**Known failures:**

| Scenario | Result |
|---|---|
| 3+ columns wrap simultaneously (≥3 chunks in continuation row) | Continuation row emitted as orphan line; not merged |
| Leftmost column (date) wraps | Continuation row not recognised; starts a new logical row |
| Table with no leftmost-column anchor (e.g. centred layout) | `contThresh` may be calibrated incorrectly; merges may be missed or spurious |

---

### Encrypted PDFs (`PdfDecryptor`)

- Supports Standard Security Handler **Revision 2, 3, and 4** only (covers the
  vast majority of bank-generated PDFs).
- **R5 / R6** (PDF 2.0, AES-256) are not supported — the file will be reported
  as needing a password even if the correct password is supplied.
- **128-bit AES** (R4 with `UseAES=true`) is supported via `javax.crypto`.
  **256-bit AES** is not.
- `/EncryptMetadata false` (metadata streams left unencrypted) is detected and
  handled correctly. The default (`EncryptMetadata=true`) is assumed when the key
  is absent from the encrypt dict.
- Owner-password-only files (no user password set) are tried with the derived
  owner key; if that fails the user sees a password prompt.

---

### Scanned / image-based PDFs

The extractor works on text-layer PDFs only. Scanned PDFs contain no text
operators (`BT`/`ET`) and will return an empty string. The UI surfaces the hint:
> "Text looks garbled — try `pdftotext statement.pdf statement.txt` in Termux,
> then import the .txt file."

---

### Non-standard font encodings

PDFs that use custom glyph-to-Unicode mappings (ToUnicode CMaps) outside the
standard Latin-1 range will produce garbled characters. The garbled-text
heuristic (< 40 % printable ASCII in the first 500 characters) detects this and
shows the `pdftotext` fallback hint.

---

### FlateDecode only

The extractor decompresses **FlateDecode** (zlib/deflate) streams, which covers
nearly all modern PDF generators. Other compression filters (`LZWDecode`,
`CCITTFaxDecode`, `JBIG2Decode`, etc.) are not supported and will be skipped.
