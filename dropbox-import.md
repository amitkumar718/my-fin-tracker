# Dropbox PDF Import

Design notes for the Dropbox statement-ingest flow. Current implementation
plus the planned refactor to drop the local copy once expenses are extracted.

## Dropbox app setup (one-time, per user)

1. https://www.dropbox.com/developers/apps → Create app
2. Scoped access, **App folder** type, name `myfintracker` → maps to
   `/Apps/myfintracker/` in the user's real Dropbox
3. Permissions tab: tick `files.metadata.read` + `files.content.read`
4. Settings tab → Generated access token → **Generate** → copy the `sl.xxx…`
   string. Long-lived, tied to this user's Dropbox, scoped to the App folder.

Statements can be dropped at any depth under `/Apps/myfintracker/`.
Convention: `<bank>/<year>/<month>/*.pdf` (e.g. `hdfc/2026/01/stmt.pdf`).
No `fin/` prefix level.

## Code layout

| File | Role |
|---|---|
| `pdf/DropboxPdfHelper.java` | Raw-HTTP client. `listPdfs()` (recursive `list_folder` + pagination, filtered to `*.pdf`). `downloadPdf()` streams to a `File`. D8-safe: static inner classes, no lambdas. |
| `ui/DropboxPdfInboxActivity.java` | Token dialog, checkbox-select UI over the entry list, sequential download, inserts `PdfStatement` rows, hands off to `SmsMapActivity`. |
| `res/layout/activity_dropbox_pdf_inbox.xml`, `res/layout/item_dropbox_pdf.xml` | UI. |

Token lives in `SharedPreferences("dropbox").getString("access_token", …)`.
Same prefs key as `mycontacts`'s DropboxHelper on purpose — one paste covers
both apps if the user wires the same token into both.

## Current flow (what ships today)

1. PDF Inbox → `+ Add` → chooser dialog → **Dropbox**.
2. `DropboxPdfInboxActivity` reads the token. First run → `AlertDialog` with
   `EditText`, user pastes `sl.xxx…`, saved to prefs.
3. Background thread → `POST /2/files/list_folder` with
   `{"path":"","recursive":true,"limit":2000}`. Pagination via
   `list_folder/continue` while `has_more`. Keeps only `.tag=="file"`
   entries whose name ends `.pdf`.
4. UI shows filename + parent folder + size, grey rows for already-imported
   (dedupe by `displayName` starting `"dbx:"` prefix — **to be replaced**).
5. Tap to select, "Download (N)" button downloads each selected entry
   sequentially via `POST /2/files/download` to
   `getFilesDir()/dropbox_pdfs/<pathLower with '/' → '_'>`.
6. For each download, inserts `PdfStatement` row with
   `uri = file://<cache path>` and `displayName = "dbx:<pathLower>"`.
7. Toast + `finish()` → PdfInboxActivity reloads in `onResume`, rows appear
   like locally-picked statements. Tap → `SmsMapActivity` → existing
   extraction pipeline.

### Errors

- `HTTP 401` or `missing_scope` 400 → `AuthException` → clear token → re-prompt.
- Any other HTTP / network failure → Toast and either finish (list) or skip to
  the next file (download loop).

### Internal storage note

Downloads today land in `getFilesDir()/dropbox_pdfs/` — private app storage
(`/data/data/com.ldsa.myfintracker/files/dropbox_pdfs/`). Not readable by
other apps (OS enforces at UID level). Survives until uninstall.

## Planned refactor (do after fixing the PDF flow)

Goal: don't preserve the PDF after extraction. The source of truth is Dropbox;
locally we only keep expense rows + a reference back to the Dropbox path so
the user can go verify a tally.

### Schema (DB_VERSION bump)

Add nullable column to `pdf_statements`:

```sql
ALTER TABLE pdf_statements ADD COLUMN source_ref TEXT;
```

- `source_ref = null` → locally-picked file; current `uri` is the SAF URI.
- `source_ref = "dbx:/hdfc/2026/01/stmt.pdf"` → came from Dropbox.
  `uri` points at the cache file *while it still exists*; it becomes stale
  (and ignored) after the file is deleted.

### Dropbox import flow (new)

1. Download selected PDFs to `getCacheDir()/dropbox_pdfs/` (not `filesDir` —
   let Android evict under pressure).
2. Insert `PdfStatement` row: `uri = file://<cache path>`,
   `source_ref = "dbx:/..."`, `displayName = <filename>`.
3. Launch `SmsMapActivity` as today — user walks through, confirms matches,
   `Expense` rows get inserted linked to `statement_id`.
4. On PdfInboxActivity reload: for any statement with `source_ref != null`
   that has ≥1 linked expense, delete the cached file. Row stays; only the
   local blob goes.

### Opening a Dropbox-sourced statement later

| State | Behavior |
|---|---|
| Cache file present (extraction not finished) | Open `SmsMapActivity` as today |
| Cache file gone (expenses already saved) | Open a read-only expenses-list view with the Dropbox path shown as subtitle. Long-press to delete the record. No re-download. |

### Dedupe

Switch from the `displayName.startsWith("dbx:")` hack to querying by
`source_ref` directly. Cleaner, and `displayName` reverts to being just a
display string.

## Open question (deferred)

The per-file `SmsMapActivity` confirm step is the same friction the local-file
path has. If we want bulk Dropbox import to be one-shot (auto-run the
sender's known template without the confirm screen), that's a separate
change on top of the above — out of scope for this refactor, but noted.
