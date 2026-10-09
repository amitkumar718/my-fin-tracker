# TODO

## AddPdfPatternActivity

- **Adapt `StatementScanActivity` for test results.** `AddPdfPatternActivity.testPattern()` currently uses a bespoke `TestRunnable` / `TestResult` / `TestResultRunnable` + `AlertDialog` to show matched transactions against an unsaved draft pattern. `StatementScanActivity` already runs the same extraction pipeline and renders a candidate list. Extend it to accept draft pattern + URI as extras (`EXTRA_TEMPLATE_REGEX`, `EXTRA_BANK_REGEX`, `EXTRA_PERIOD_REGEX`, `EXTRA_PDF_URI`, `EXTRA_PREVIEW_MODE`), bypass the DB lookup when those are present, and show results read-only. Then remove the three Test* classes and the dialog from `AddPdfPatternActivity`.

## Orphaned expenses review

- **`OrphanedExpensesActivity`** — review screen for expenses whose `patternId = -1 AND source != 'manual'`. Rows become orphaned when a pattern is edited and the saved expense's `originalSms` no longer matches the new regex (see `ExpenseDatabase.reApplyPattern`). For each orphan show the original line + currently-stored extracted values; actions per row: Delete, Re-map (opens `MapExpenseActivity` to pick a different pattern), Edit (opens `ExpenseDetailActivity`). Entry point: a row in Settings and/or a badge on the main screen when count > 0.

## General

- **Reduce duplicated code.** Pattern-matching logic, PDF-extraction wrapping, line splitting, and chip rendering appear in multiple activities. Factor shared pieces into helpers (e.g., a single `PatternScanner` service for `StatementScanActivity` and the test flow).
- **Optimize hot paths.** Line-by-line regex matching in the scan path recompiles `Pattern` inside `ExtractionPattern.extractGroup` / `matches` for every call. Compile once per pattern, cache on the `ExtractionPattern` instance (or in the scan loop). Same for `applyField` / `findMatch` — compile once, reuse across lines (already done inside `findMatch` but duplicated across the two apply* paths; verify no redundant recompilation at the call site).
