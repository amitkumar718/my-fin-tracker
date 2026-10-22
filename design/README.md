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

## Nothing is final

These are directional sketches. Iterate before any XML changes land. If the
direction is right, the first implementation step is **palette + typography**
(see the main-chat proposal) which costs no visual change but prepares the
ground for everything else.
