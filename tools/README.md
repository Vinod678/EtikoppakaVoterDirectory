# Data-preparation tools

These scripts turn the source files in `input/` (official Telugu PDF + English workbook per ward) into the
datasets and photos the app ships. They never modify `input/` PDFs or the extracted originals in `output/`;
values are copied verbatim, and problems are only *flagged* for manual review.

**Full step-by-step guide (adding a new ward, signing, upgrades):**
[`docs/PROJECT_CLEANUP_AND_FUTURE_IMPORT_GUIDE.md`](../docs/PROJECT_CLEANUP_AND_FUTURE_IMPORT_GUIDE.md)

| Script | What it does | Output |
|---|---|---|
| `ward_config.py` | Per-ward settings: serial range, file locations, official male/female counts | – |
| `ward_corrections.py` | Draft workbook → corrected 7-column workbook (`audit` / `apply`), using `corrections/wardN.py` | `input/Etikoppaka_Ward_N_Corrected_English.xlsx`, `output/ward_N/wardN_corrections_log.csv`, `wardN_audit.txt` |
| `extract_telugu.py` | Exact printed Telugu + card fields of every card (conjunct glyphs recovered via `telugu_pdf_text.py`; needs fontTools in `.pylib`) | `build/te_wardN.json` |
| `make_ward_workbooks.py` | Wards without a corrected workbook (1, 5-10, 12-14): Ward-11-format 7-column workbook from the PDF Telugu, English in the established spellings, plus an analysis workbook (surname clusters, anomalies, word breakdown, verification list) | `input/Etikoppaka_Ward_N_Corrected_English.xlsx`, `output/ward_N/wardN_analysis.xlsx` |
| `ward2_corrections.py` | The one-off Ward 2 correction run (kept for reproducibility) | as above, Ward 2 |
| `surname_fixes.py` | One spelling per family surname (English and Telugu), so the app groups each family into one household. Hand-checked rules per ward (same house / matching father, husband or mother); `preview` prints every change, `apply` edits the workbooks. Idempotent: run it again after any workbook is regenerated | `input/Etikoppaka_Ward_N_Corrected_English.xlsx`, `output/surname_fixes_log.csv` |
| `xlsx_dump.py` | Reads a workbook (standard library only) into a JSON dump | e.g. `build/xlsx_ward5.json` |
| `extract_photos.py` | Extracts each voter's original embedded photo from the PDF, matched to its card | `output/…/voter_photos/`, `voter_photos_manifest.csv` |
| `build_dataset.py` | Combines workbook + PDF card fields + photo manifest, validates, writes review workbook | `output/…/voters_*.json`, `voters_review*.xlsx` |
| `sync_assets.py` | Copies a ward's dataset and photos into the app assets (resizes only photos > 800 px; `--no-optimize` for exact copies) | `app/src/main/assets/` |

## One-time setup

```
python -m pip install --target tools/.pylib -r tools/requirements.txt
```

## Typical run for ward N (from the project root)

```
python tools/ward_corrections.py tools/.pylib N audit        # only for a first-draft workbook
python tools/ward_corrections.py tools/.pylib N apply
python tools/surname_fixes.py tools/.pylib apply N             # after every workbook (re)generation
python tools/xlsx_dump.py input/Etikoppaka_Ward_N_Corrected_English.xlsx build/xlsx_wardN.json
python tools/extract_photos.py tools/.pylib input/wardN_voters.pdf build/xlsx_wardN.json output/ward_N/voter_photos output/ward_N/voter_photos_manifest.csv - N
python tools/build_dataset.py tools/.pylib . build/xlsx_wardN.json N
python tools/sync_assets.py N
```

Ward 11 (the original dataset) uses the defaults: `extract_photos.py … output/voter_photos output/voter_photos_manifest.csv output/test_photo/3691.jpg`,
`build_dataset.py tools/.pylib . build/xlsx_ward11.json`, `sync_assets.py`.

Then run the app's tests (`gradlew.bat testDebugUnitTest`); `DatasetIntegrityTest` checks the bundled JSON and
photos against `output/`.

## Privacy

`input/`, `output/` and `app/src/main/assets/` contain personal data (names, photos, EPIC numbers).
Never publish them in a public repository.
