
# PDF Import Flow

Snapshot of the current PDF-ingest paths and a plan for streamlining before
Dropbox batch-import work resumes.

## What exists today

There are **two parallel ingest paths** in the code that overlap in what they
do but differ in what they persist.

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
| **Two UIs doing overlapping work.** Both `StatementImportActivity` and `SmsMapActivity` extract text, run patterns, show candidates. | Confusing which is canonical. Path A doesn't even create a statement row, so imports via it are untracked. |
| **`SmsMapActivity` is ~1500 lines of SMS + PDF logic braided together.** | High cognitive cost to change either flow without breaking the other. |
| **"Known template" case still needs clicks.** For a bank you've already configured, import *should* be: tap statement → "42 expenses imported." Today it's: open `SmsMapActivity` → Pick from PDF → Apply Regex. | Blocks the Dropbox batch-import ambition — N statements becomes N multi-screen sessions. |
| **Field patterns are global, not per-sender.** | Can't correctly auto-extract bank/month across more than one bank. |
| **Password cache is in-memory only.** | Every process restart re-prompts for every encrypted statement. |
| **No "already imported?" signal in Path A.** | Easy to double-import a statement. |
| **Expenses are not linked to the statement that produced them.** | No way to tally, undo an import, or show "N expenses from this file" on an inbox row. |

## Streamlining plan

### 1. Collapse to one ingest UI

Make `PdfInboxActivity` the single entry point. Each row is a `PdfStatement`
with a status:

- **unprocessed** → tap opens a *review* screen (what Path A currently is),
  prefilled with the sender's known patterns.
- **processed** → tap shows the expenses linked to that statement
  (read-only). Doubles as the Dropbox "re-tally" view.

Delete the standalone entry to `StatementImportActivity`; keep its screen as
the "review" screen under this umbrella. `SmsMapActivity`'s PDF mode becomes
template-authoring only, reached via "Edit template" from the review screen
when matches look wrong.

### 2. One-tap import when a template is known

In the review screen: if the sender has ≥1 `ExtractionPattern` and the scan
yields ≥1 candidate, auto-check all candidates and show a single "Import N"
button. User's only decision becomes yes / no. This is what unlocks bulk
Dropbox imports — N statements → N confirmation taps, not N authoring
sessions.

### 3. Link expenses to their source statement

Add `pdf_statement_id` (nullable) to the `expenses` table (schema bump,
`ALTER TABLE ADD COLUMN`). Both ingest paths stamp this on insert. Enables:

- "N expenses" count on each inbox row.
- Delete / re-import a statement without orphaning.
- Dropbox "re-tally" view (planned — see `dropbox-import.md`) falls out
  for free.

### 4. Per-sender field patterns

Add `sender_id` to `pdf_field_patterns` (schema bump). Existing rows become
sender-less defaults. Review screen auto-fills `bankName` / `month` on the
`PdfStatement` from the matching sender's field patterns.

### 5. Password durability

Add nullable `password` to `pdf_statements`. Private app storage, so leak
surface is small. Prompt once per statement, not once per process.

### 6. Factor PDF logic out of `SmsMapActivity`

Move the shared bits — `PdfLoadThread`, `BulkImportThread`,
`autoApplyFieldPatterns` — into a `pdf/PdfExtractionService` class.
`SmsMapActivity` returns to being SMS-only; review screen and authoring
screen both call the service. Roughly ~400 lines come out of
`SmsMapActivity`.

## Suggested order

Biggest UX win per diff, in order:

1. **(3) Link expenses to statement** — enables everything downstream and the
   Dropbox re-tally view; schema change only.
2. **(2) One-tap import** — unblocks Dropbox batch flow; small UI change in
   the review screen.
3. **(1) Collapse to one UI** — delete dead paths once (2) is in.
4. **(6) Refactor** — now safe because `SmsMapActivity` is only used for
   authoring.
5. **(4) Per-sender field patterns** — polish.
6. **(5) Password durability** — polish.
