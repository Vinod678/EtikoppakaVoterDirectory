# App-Wide Responsive UI Audit — Etikoppaka Voter Directory 3.2

Date: 2026-11-20 · App version 3.2 (versionCode 5) · Database version 5 (unchanged)

## How it was tested

- **Device:** Android emulator `emulator-5554` (Pixel-class, 1080 × 2400 px, gesture navigation). Screen widths were
  simulated with `wm density` and font sizes with `settings put system font_scale`. Both were restored afterwards
  (density 420, font 1.0).
- **Widths and fonts tested:**

  | Width | Density | Font sizes |
  |---|---:|---|
  | 320 dp | 540 | 1.3×, 1.5× |
  | 360 dp | 480 | 1.0× |
  | 393 dp | 440 | 1.0× |
  | 411 dp | 420 (default) | 1.0×, 1.3×, 1.5× |

- **Landscape:** 411 dp default, 1.0×. The ward tabs switch to the side navigation rail.
- **Before and after:** every screen listed below was screenshotted at 320 dp / 1.3× before the changes. Problems
  were fixed and the screens re-checked.
- **Not tested:**
  - No physical phone was connected.
  - Three-button navigation was not tested; only gesture navigation was.
  - Tablets and widths of 600 dp and above were not tested.
  - Font scales other than those listed above were not tested.
  - Landscape was not tested for screens other than the Ward Voter List and Households.

## Shared fixes (they apply to every screen that uses them)

| Component | Problem | Fix |
|---|---|---|
| `SummaryTiles` (new, `ui/components/SummaryTiles.kt`) | Count tiles cut numbers off: "36" for 369 on the ward list, "1,47" for 1,476 on Home, "368/36" on Households. Labels split mid-word ("Househol·ds", "Unassign·ed"). | Each tile's needed width (padding, icon, the full number, the longest label word) is measured with the current font and font scale. The tiles then use one of three layouts: (1) side by side with the icon beside the number, (2) side by side with the icon above the number, (3) one tile per line. Numbers are never shrunk or cut, and words are never split. Used on Home, the Ward Voter List and Households. |
| `HeaderTitle` (`BrandChrome.kt`) | "Etikoppaka Voter Directory" was cut to "Etikoppaka Voter Dire…" next to the header buttons. | Up to 2 lines; steps down slightly (minimum 13 sp × font scale) instead of "…". |
| `ProgressLine` (`Widgets.kt`) | "Visited" and "Favourites" split into "Visit·ed" and "Favourite·s" in narrow cards. | Label and value sit in a `FlowRow`: the value moves under the label rather than splitting a word. |
| `VoterRow` (`Widgets.kt`) | The serial number beside the name squeezed long names ("Venkatalaksh·mi") in Household Details and the voter picker. | Serial on its own line (as on voter cards); name up to 3 lines, subtitle up to 3 lines. |
| `FitLabel` (bottom navigation) | "Households" lost its last letter at 320 dp / 1.5×. | Letter spacing 0, minimum step 6 sp × font scale. That minimum is only reached in that extreme configuration. |
| `rememberFloatingActionVisible` (`ScrollToTop.kt`, new) | Extended buttons ("New household", "New tag") covered cards and the rename/delete buttons of tag rows. | Each screen has a normal button at the top of the list. The floating copy appears only after that button scrolls away: it hides while scrolling down, returns when scrolling up, and is shown at the end of the list, where the bottom padding keeps the last item clear. |

## Per-screen results

| Screen | Issues Found | Fixes Applied | Tested Device/Size | Result |
|---|---|---|---|---|
| Home Page / All Wards | "1,476" cut to "1,47". Ward cards "Ward 1" lost the number at 320/1.3. | `SummaryTiles` (Large). Ward grid uses adaptive columns (one column when a card would be narrower than 132 dp × font scale). Full-width items use `maxLineSpan`. Wrapped ward text. Back-to-top padding. | 320/1.3, 320/1.5, 360/1.0, 393/1.0, 411/1.3, 411/1.5 | Pass |
| Ward Voter List | Counts cut ("36" for 369). Title ellipsised. Back-to-top button could cover the last card. | `SummaryTiles`, `HeaderTitle`, bottom list padding 80 dp. | Same as Home plus landscape 411 | Pass |
| Voter Details | Pager count cut to "1 /" instead of "1 / 369". "Relationship" split mid-word. | Previous/Next show arrows only when their words don't fit beside the full count (same accessible names). Info rows stack the label above the value when the label column can't fit "Relationship"; all rows switch together. | 320/1.3 | Pass |
| Households | "368/36"-style cut counts. Labels split. "Visit·ed" and "Favourite·s" on cards. "+ New household" covering cards. Title cut. At 320/1.5 the header buttons squeezed "Households" to "Household·s". | `SummaryTiles` with a clickable Unassigned tile. Responsive progress rows. Inline New household button plus scroll-aware floating button. Header buttons move to their own row when the titles would have less than 150 dp × font scale. Count line only while searching. | All sizes above plus landscape | Pass |
| Household Details and Members | Member names split by the serial column. Title single line. | `VoterRow` fix; title up to 2 lines. | 320/1.3 | Pass |
| Unassigned Voters (new screen) | — | Reuses the existing card and assignment sheet. | 411/1.0 (separate test user) | Pass |
| Assign to Household sheet | None found. | — | 411/1.0 | Pass |
| New household / Add members (voter picker) | Names squeezed by the serial. | `VoterRow` fix. | 320/1.3 | Pass |
| Move voter between households | Same sheet as Assign. | — | Not separately tested | Not tested |
| Blocks | Title cut to "Blo" by the "Manage assignments" text button in the top bar. | "Manage assignments" is now a full-width button at the top of the page. | 320/1.3 | Pass |
| Manage assignments sheet | "None" segment cut to "No". | The no-block segment shows ✕ (as on Voter Details) with the accessible name "No block (unassign)". | 320/1.3 | Pass |
| Tags | Floating "New tag" covered the rename/delete buttons of a row. | Inline "New tag" button under the search field plus scroll-aware floating button. Subtitle allowed 2 lines. | 320/1.3 | Pass |
| Settings (menu, Sign-in, About) | None found. | — | 320/1.3 | Pass |
| Backup & Restore | "Restore" button crushed into "Re·sto·re". | Buttons wrap with `FlowRow`. | 320/1.3 | Pass |
| Export PDF | None found; options wrap. | — | 320/1.3 | Pass |
| Filters sheet (Blocks & tags) | None found; chips already wrap. | — | 320/1.3 | Pass |
| Lock screen | None found. | — | 320/1.3 | Pass |
| Dialogs (Sign out, Remove from household, Delete tag) | Sign-out and Remove dialogs wrap correctly. | — | 320/1.3, 411/1.0 | Pass (Delete tag not opened) |
| Keyboard over the Private note field | The emulator keyboard was in a minimised state, so coverage could not be judged. The detail pager already uses `imePadding()`. | — | — | Not tested |

## Unresolved or accepted items

1. **Search placeholders end in "…" on narrow screens** (for example "Search across all war…"). These are hint texts:
   - the full hint is read by screen readers
   - typed text is never cut
   - they were left as they are rather than making them unreadably small
2. **The filter chip row on the Ward Voter List scrolls sideways.** This is the existing designed chip row, not a
   layout defect: "Blocks & tags" is reached by swiping the row. The full filter set is also in the Filters sheet.
3. **At 320 dp with 1.3–1.5× fonts the Households tiles stack one per line**, adding about 120 dp of height. This is
   intentional: the alternative was hiding digits.
4. **Physical devices, three-button navigation and tablets were not tested** (see "How it was tested").
5. **Cold start of the *debug* build measured 4–13 s on the busy emulator.** The release/shareable build measured
   1.6–3.6 s. The startup path (lock screen) was not changed.
