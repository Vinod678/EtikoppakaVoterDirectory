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


# Wards 1 and 5-14 (same summary table). Several wards share one PDF; the card finder only takes
# serials of the ward's own range, so a combined PDF works unchanged. Ward 14 has 373 voters.
_PDF = {1: 'input/ward1_voters.pdf', 5: 'input/ward_5_6_7_voters.pdf', 6: 'input/ward_5_6_7_voters.pdf',
        7: 'input/ward_5_6_7_voters.pdf', 8: 'input/ward8_9_10_voters.pdf', 9: 'input/ward8_9_10_voters.pdf',
        10: 'input/ward8_9_10_voters.pdf', 12: 'input/ward11,1,2,13,14_voters.pdf',
        13: 'input/ward11,1,2,13,14_voters.pdf', 14: 'input/ward11,1,2,13,14_voters.pdf'}
for _n, _first, _last, _counts in ((1, 1, 369, (180, 189)), (5, 1477, 1845, (176, 193)), (6, 1846, 2214, (183, 186)),
                                   (7, 2215, 2583, (176, 193)), (8, 2584, 2952, (183, 186)), (9, 2953, 3321, (182, 187)),
                                   (10, 3322, 3690, (170, 199)), (12, 4060, 4428, (186, 183)), (13, 4429, 4797, (171, 198)),
                                   (14, 4798, 5170, (188, 185))):
    WARDS[_n] = dict(first=_first, last=_last, pdf=_PDF[_n],
                     xlsx='input/Etikoppaka_Ward_%d_Corrected_English.xlsx' % _n, sheet='Ward %d - English' % _n,
                     photos='output/ward_%d/voter_photos' % _n, manifest='output/ward_%d/voter_photos_manifest.csv' % _n,
                     json='output/ward_%d/voters_ward_%d.json' % (_n, _n), review='output/ward_%d/voters_review_ward_%d.xlsx' % (_n, _n),
                     asset_json='wards/voters_ward_%d.json' % _n, asset_photos='voter_photos/ward_%d' % _n,
                     official_counts=_counts)
for _w in WARDS.values():
    _w['count'] = _w['last'] - _w['first'] + 1


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
        if not re.fullmatch(r'\d{1,4}', w[4]) or not first <= int(w[4]) <= last:
            continue
        if any(abs(l[1] - w[1]) < 3 and 0 < w[0] - l[0] < 75 for l in labels):
            out.append(w)
    return out
