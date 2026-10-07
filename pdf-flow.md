# PDF Import Flow

Snapshot of the current PDF-ingest paths and a plan for streamlining before
Dropbox batch-import work resumes.

## What exists today

Two paths, **not redundant** — they do different jobs:

- **Path A — `StatementImportActivity`**: the lean *import* flow. Pick file,
  run known patterns, confirm candidates, insert expenses. One screen.
  Assumes the sender's `ExtractionPattern` rows already exist.
- **Path B — `PdfInboxActivity` → `SmsMapActivity`**: the *template-authoring*
  flow. Walks the user through building a regex from a sample line in the
  PDF. The "Apply Regex" button at the end also happens to do a bulk import,
  but that's a side-effect of the file being open — authoring is the point.

So they're complementary: Path B teaches the app a new bank's format once;
Path A then handles every subsequent statement from that bank.

### Path A — `StatementImportActivity` (self-contained, scans whole file)

1. "Pick PDF / TXT" button → SAF file picker.
2. `LoadFileThread` reads the stream → `PdfTextExtractor.extract(is, pw)`;
   `PdfDecryptor` may raise `NEEDS_PASSWORD` → prompt dialog.
3. On success: shows a text preview + bank spinner (optional filter: all
   banks vs a specific `SenderConfig`).
4. "Scan" → runs the sender's (or all) `ExtractionPattern` rows across the
   extracted text → list of `CandidateExpense` with checkboxes.
5. User unchecks noise, "Import" → inserts N `Expense` rows → `finish()`.
6. **Does not create a `PdfStatement` row.** The statement is ephemeral;
   after import there is no record of which file produced which expenses.

### Path B — `PdfInboxActivity` → `SmsMapActivity` in PDF mode

1. `+ Add` → SAF pick (or Dropbox).
2. Insert `PdfStatement` row (`uri`, `displayName`, …) → launch
   `SmsMapActivity` with `EXTRA_STATEMENT_ID` and `EXTRA_BLANK_TEMPLATE=true`.
3. `SmsMapActivity` is the regex-template-authoring screen originally built
   for SMS; PDF mode is bolted on — adds a "PDF section" with filename, month
   field, "Pick from PDF" button.
4. "Pick from PDF" → `PdfLoadThread` → extract text → split into lines →
   run existing patterns to pre-highlight matches → line-picker dialog.
5. User taps a line → that line text becomes the template in the top editor.
   User builds a regex by dropping `${amount}` / `${merchant}` / `${date}`
   tokens over the template. "Save" creates/updates an `ExtractionPattern`
   for the current sender.
6. Alt path: "Apply Regex" runs `BulkImportThread` — reloads the PDF,
   matches every line, inserts expenses in one shot.
7. Also persists **field patterns** (`pdf_field_patterns` table) — global,
   not per-sender — regex to extract bank name + month from arbitrary PDF
   text.

### Shared primitives

| File | Role |
|---|---|
| `pdf/PdfTextExtractor.java` | Hand-rolled extractor. FlateDecode only, position-aware (`Tm`/`Td`/`TD`/`T*`), heuristic multi-line table-row merging. Text-layer PDFs only. |
| `pdf/PdfDecryptor.java` | Standard Security Handler R2/R3/R4 (128-bit AES max). See `limitations.md`. |
| `db/ExtractionPattern.java` | Per-sender, per-txn-type regex with `amountGroup` / `merchantGroup` / `dateGroup` indexes. |
| `pdf_field_patterns` table | Global metadata regexes (bank-name, month). |
| `PdfInboxActivity.sCachedPasswords` | Static `HashMap<uri,pw>` for password caching — process-lifetime only. |

## Pain points

| Issue | Why it hurts |
|---|---|
| **Path A doesn't persist a `PdfStatement` row.** Imports are untracked — no "already imported?" check, no "N expenses from this file" summary, no way to undo. | Breaks the inbox model and blocks the Dropbox re-tally view (planned, `dropbox-import.md`). |
| **Expenses are not linked to the statement that produced them.** | No source traceability for any imported expense. |
| **Path A is not reachable from `PdfInboxActivity`.** It has its own standalone entry point. | Inbox rows have no "run patterns" action — only Path B's authoring screen opens on tap. |
| **`SmsMapActivity` is ~1500 lines of SMS + PDF logic braided together.** The "Apply Regex" bulk-import side-effect lives inside the authoring screen. | High cognitive cost to touch either flow. If Path A gains statement persistence, the Path B shortcut should go away. |
| **Field patterns are global, not per-sender.** | Can't correctly auto-extract bank/month across more than one bank. |
| **Password cache is in-memory only.** | Every process restart re-prompts for every encrypted statement. |

## Streamlining plan

Keep the two jobs and make each path do only its one job.

### Status of `StatementImportActivity` (Path A)

Path A is **orphaned dead code** — it has no `startActivity` call anywhere in
the source and is unreachable from the UI. It was superseded by Path B when
the inbox was built. The screen will be **deleted**; its internal scan+import
logic (`ScanThread`, `ImportThread`, `buildCandidate`, `parseDate`) will be
absorbed inline into the inbox tap handler so none of that work is lost.

### 1. Link expenses to their source statement

Add `pdf_statement_id` (nullable) to `expenses` (schema bump,
`ALTER TABLE ADD COLUMN`). Every import path stamps it on insert. Enables:

- "N expenses" count on each inbox row.
- Delete / re-import a statement without orphaning.
- Dropbox "re-tally" view (planned, `dropbox-import.md`) falls out for free.

### 2. Add a one-tap import path inside the inbox

Tap behavior on `PdfInboxActivity`:

- **unprocessed row** (no linked expenses) → new `StatementRunActivity` (or
  inline within the inbox): load the PDF, run all patterns, show candidate
  list with checkboxes, import selected → link to `pdf_statement_id`. No
  authoring, no detour through `SmsMapActivity`.
- **processed row** → read-only expenses summary (same screen as Dropbox
  re-tally).

Long-press "Edit template" keeps the Path B authoring flow for when matches
look wrong.

`StatementImportActivity` is deleted once this is in place.

### 3. Delete Path B's bulk-import side-effect

The "Apply Regex" bulk-import inside `SmsMapActivity` becomes redundant once
step 2 is live — remove it. `SmsMapActivity` reverts to pure template
authoring; `BulkImportThread` moves out or is deleted with it. Removes a
significant chunk of the SMS/PDF braiding.

### 4. Per-sender field patterns

Add `sender_id` to `pdf_field_patterns`. Existing rows become sender-less
defaults. The import path auto-fills `bankName` / `month` on the
`PdfStatement` from the matching sender's field patterns.

### 5. Password durability

Add nullable `password` to `pdf_statements`. Private app storage, small leak
surface. Prompt once per statement, not once per process.

## Suggested order

1. **(1) Link expenses to statement** — schema change; unblocks everything.
2. **(2) One-tap import in inbox** — absorbs Path A logic; delete `StatementImportActivity`.
3. **(3) Remove Path B's bulk-import** — safe once (2) is in.
4. **(4) Per-sender field patterns** — polish.
5. **(5) Password durability** — polish.
