# Design preview

SVG mockups for the proposed modernisation. Open each in a browser — they're
side-by-side **today vs proposed** so comparison is explicit. All visuals are
achievable within the current Termux/D8 build (no AndroidX, no Material
Components library, no Kotlin).

| File | What it shows |
|---|---|
| `00_palette.svg` | Full palette, typography scale, button/badge/chip styles, card row example, empty-state, spacing/elevation reference. |
| `01_settings.svg` | Settings screen — grouped sections, leading icon chips, 12dp cards, 15sp body + 12sp caption, white top-app-bar instead of blue action bar. |
| `02_debug_patterns.svg` | Debug → All Patterns — pill badges (`PDF` / `SMS` / `ORPHAN`), optional filter chips row, pink-tinted card for orphan rows. |
| `03_delete_dialog.svg` | Delete-pattern dialog — before (bare `setItems` list) vs after (rows with sub-text, destructive action in red, Cancel segregated). |
| `04_landing.svg` | Landing — current month (`LIVE`). Spent-only hero + clock badge, SMS-tagged recent rows. |
| `04b_landing_past.svg` | Landing — past month (`RECONCILED`). PDF-tagged rows + an UNMATCHED SMS row surfaced in the recent list and attention row. |
| `04c_landing_stacked_ctas.svg` | Variant C — stacked bottom CTAs **replace** the quick-action tile row. Two full-width CTAs (Add expense / Import PDF) + a text link to the rest. |
| `04d_landing_allocation_bar.svg` | Variant D — hero gains a 3-segment bank-split allocation bar. Quick-action tiles kept. |
| `04e_landing_kitchen_sink.svg` | Variant E — both: allocation bar in hero AND stacked bottom CTAs replacing tiles. |
| `05_landing_bottomnav.svg` | Landing with a 5-slot pinned bottom nav bar — `Home · Expenses · [+] · Trips · More`. `+` is a bigger filled blue square in the centre. |
| `06_add_sheet.svg` | Bottom sheet opened by the `+` slot — primary CTA (Add expense), 2-grid (Import PDF / Scan SMS), Recent uploads list. |
| `07_more_sheet.svg` | Bottom sheet opened by the `More` slot — Statements · Banks · Settings · Debug. |

## Nothing is final

These are directional sketches. Iterate before any XML changes land. If the
direction is right, the first implementation step is **palette + typography**
(see the main-chat proposal) which costs no visual change but prepares the
ground for everything else.
