"""Per-ward settings shared by the data-preparation tools (paths relative to the project root).

Ward 11 keeps its original locations so the existing dataset, photos and app assets are unchanged.
Later wards live in their own folders: output/ward_N/ and, in the app, assets/wards/voters_ward_N.json
plus assets/voter_photos/ward_N/ (photo file names are then plain "<serial>.jpg", the app adds the folder).
"""
import re

WARDS = {
    11: dict(first=3691, last=4059, pdf='input/ward11_voters.pdf',
             xlsx='input/Etikoppaka_Ward_11_Corrected_English.xlsx', sheet='Ward 11 - English',
             photos='output/voter_photos', manifest='output/voter_photos_manifest.csv',
             json='output/voters_data.json', review='output/voters_review.xlsx',
             asset_json='voters_data.json', asset_photos='voter_photos'),
    2: dict(first=370, last=738, pdf='input/ward2_voters.pdf',
            xlsx='input/Etikoppaka_Ward_2_Corrected_English.xlsx', sheet='Ward 2 - English',
            photos='output/ward_2/voter_photos', manifest='output/ward_2/voter_photos_manifest.csv',
            json='output/ward_2/voters_ward_2.json', review='output/ward_2/voters_review_ward_2.xlsx',
            asset_json='wards/voters_ward_2.json', asset_photos='voter_photos/ward_2',
            official_counts=(165, 204)),  # PDF page 2: Ward 2 = serials 370-738, 165 male, 204 female
}
# Wards 3 and 4 (summary table on page 2 of the Ward 2 PDF: male/female per ward).
for _n, _first, _counts in ((3, 739, (179, 190)), (4, 1108, (186, 183))):
    WARDS[_n] = dict(first=_first, last=_first + 368, pdf='input/ward%d_voters.pdf' % _n,
                     xlsx='input/Etikoppaka_Ward_%d_Corrected_English.xlsx' % _n, sheet='Ward %d - English' % _n,
                     photos='output/ward_%d/voter_photos' % _n, manifest='output/ward_%d/voter_photos_manifest.csv' % _n,
                     json='output/ward_%d/voters_ward_%d.json' % (_n, _n), review='output/ward_%d/voters_review_ward_%d.xlsx' % (_n, _n),
                     asset_json='wards/voters_ward_%d.json' % _n, asset_photos='voter_photos/ward_%d' % _n,
                     official_counts=_counts)


def ward(n):
    if n not in WARDS:
        raise SystemExit('Unknown ward %s; add it to tools/ward_config.py' % n)
    return WARDS[n]


def card_serials(words, first, last):
    """GP serial words on a page: the number printed right after the GP serial label
    ("గ్రా.పం.ఓ.జా.వ.సం."). Anchoring on the label avoids confusing 3-digit serials with
    assembly-serial parts or ages printed on the same card."""
    labels = [w for w in words if 'పం.ఓ.జా.వ.సం' in w[4]]
    out = []
    for w in words:
        if not re.fullmatch(r'\d{3,4}', w[4]) or not first <= int(w[4]) <= last:
            continue
        if any(abs(l[1] - w[1]) < 3 and 0 < w[0] - l[0] < 75 for l in labels):
            out.append(w)
    return out
