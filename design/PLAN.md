# new-look branch — plan

Living document tracking modernisation work. Updated as decisions land / phases
ship. See `README.md` for the SVG mockups referenced below.

## Design decisions (confirmed)

- **Palette** per `00_palette.svg`. Primary `#1976D2`, surface `#FFFFFF`,
  background `#F5F7FA`, debit `#C62828`, credit `#2E7D32`, orphan/destructive
  `#D32F2F`, amount-auto `#6A1B9A`.
- **Typography scale** — Title 20sp/500, Section 11sp/700 +0.06 letter-spacing,
  Body 15sp, Caption 12sp, Mono 13sp.
- **Shape** — card radius 12dp, button radius 8dp, chip radius 14dp.
- **Spacing** — 4 / 8 / 12 / 16 / 24.
- **Ripple feedback** on every tap target (API 21+ built-in, no deps).

## Landing design decisions (confirmed)

- **Hero shows SPENT ONLY** — no income chip, no net math. Big spent total,
  status chip, optional clock badge, caption with `N transactions · bank list`.
- **No sparkline** on hero.
- **Month pager** with status dot per month.
- **Status taxonomy** per month:
  - `LIVE` — current month (always provisional).
  - `NO STATEMENT` — past month, no PDF ever imported.
  - `READY` — past month, PDF imported, not reconciled.
  - `RECONCILED` — past month, reconcile pass completed.
- **Attention row pills** (hidden when zero):
  - `N SMS to map`, `N PDFs to scan`, `N orphans`,
    `N months awaiting statement`, `N unmatched SMS · <month>`.
- **Quick actions**: Add expense · Import PDF · Scan SMS · New trip.
- **Recent**: top 5 in selected month, `SMS` / `PDF` / `MAN` tag per row.
- **Footer links**: All expenses · Trips · Banks (+ version tag).

## Reconciliation decisions (confirmed, implementation deferred)

- **Trigger**: per-month "Reconcile" button on hero (shown only when state is
  `READY`). No auto-reconcile on PDF import.
- **Match tolerance**: same `bank`, same `amount` (exact to 2dp), `dateMs`
  within ±1 day. Narration ignored.
- **Unmatched SMS** after reconcile: kept in DB, surfaced with red `UNMATCHED`
  pill in list + a count pill in the attention row. User resolves manually
  (delete or confirm).
- **Data model**: new column `superseded_by_expense_id` on `expenses`,
  `DB_VERSION` → 19. Reconcile marks each matched SMS with the PDF's id.
  Past-month queries hide rows with `superseded_by_expense_id != -1`.

## Phases

### Phase 1 — `LandingActivity` as a new screen (NOT the launcher yet)

- **New activity + layout** matching `04_landing.svg` / `04b_landing_past.svg`
  (sparkline removed).
- **Hero** computes spent total for `selected month`. Status chip = `LIVE` for
  current month, `NO STATEMENT` vs `RECONCILED` approximation for past months
  (based on `PdfStatement` presence) until Phase 3 wires real state.
- **Month pager** flips a `Calendar` field. Status dot from PDF presence.
- **Attention row counts** — real values for `SMS to map`, `PDFs to scan`,
  `orphan expenses`, `months awaiting statement`. The `unmatched SMS` pill is
  omitted (Phase 3).
- **Quick actions** → `AddExpenseActivity`, `PdfInboxActivity`,
  `SmsInboxActivity`, `EditTripActivity`.
- **Recent list** — top 5 by `dateMs DESC` within selected month, with source
  tag per row.
- **Footer links** → current `MainActivity`, `TripListActivity`, `BankListActivity`.
- **Entry point**: a new row in `SettingsActivity` ("Try new landing") routing
  to `LandingActivity`. Launcher stays on `MainActivity`.
- **Palette + typography XML** introduced here (`colors.xml`, text-appearance
  styles) so every new widget references semantic names from day one. No
  changes to existing activities' XML — they can migrate later.
- **Files touched**:
  - new: `ui/LandingActivity.java`, `res/layout/activity_landing.xml`,
    `res/layout/item_landing_recent.xml`, `res/layout/view_landing_hero.xml`,
    `res/values/colors.xml` (if not present), `res/values/text_appearances.xml`
  - edit: `ui/SettingsActivity.java` + `res/layout/activity_settings.xml`
    (new row), `strings.xml` (new strings), `AndroidManifest.xml` (register
    `LandingActivity`).
- **D8 safety**: adapters as static inner classes with `Object getItem`, raw
  `Comparator` where needed, no lambdas.

### Phase 2 — Make `LandingActivity` the launcher

- Rename current `MainActivity` → `ExpenseListActivity` (file + class + manifest
  entry); the file is 336 lines, class rename + imports only.
- Move the `MAIN`/`LAUNCHER` intent-filter from `MainActivity` to
  `LandingActivity`.
- Footer link "All expenses" routes to `ExpenseListActivity`.
- Retire the `landing_page` SharedPref (`fin_prefs` key) — the toggle is no
  longer meaningful. Leave pref value in storage; just stop reading it.
- Remove the "Try new landing" settings row added in Phase 1.
- Smoke test every entry path that used `MainActivity.class` (grep: menu, back
  nav, SMS-landing shortcut).

### Phase 3 — Reconciliation feature

- DB migration — add `superseded_by_expense_id` column (default -1), bump
  `DB_VERSION` to 19.
- `ExpenseDatabase.reconcileMonth(long startMs, long endMs, long senderId?)`
  — matches SMS rows against PDF rows by bank+amount+date tolerance, writes
  `superseded_by_expense_id` on matches. Returns `{matched, unmatchedSms,
  pdfOnly}`.
- Hero status chip switches to the full taxonomy
  (`LIVE` / `NO STATEMENT` / `READY` / `RECONCILED`).
- "Reconcile with statement" action in the hero bottom-right, visible only when
  state is `READY`. On tap: runs reconcile for the selected month, toasts the
  result, refreshes the hero + list.
- Attention row gains the `N unmatched SMS · <month>` pill (new DB method
  `countUnmatchedSmsForMonth`).
- Recent-list rows for past reconciled months hide `superseded` rows; SMS rows
  that didn't match show the red `UNMATCHED` pill.

## Open items

- Dark mode (`values-night/colors.xml`) — deferred until Phase 2 lands. Easy
  once everything references `@color/*`.
- Shared `view_app_bar.xml` include for a single top-app-bar look across all
  activities — in scope for Phase 1 or deferred? Decision pending.
- Launcher icon refresh — not planned; current `ic_launcher` stays.

## Status

- [ ] Phase 1
- [ ] Phase 2
- [ ] Phase 3
