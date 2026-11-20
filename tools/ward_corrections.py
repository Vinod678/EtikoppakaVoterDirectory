"""Builds the corrected 7-column workbook for a ward from its first-pass draft (Wards 3, 4, ...).

Usage (project root):
    python tools/ward_corrections.py <pylib> <ward> audit   # review material only, writes nothing in input/
    python tools/ward_corrections.py <pylib> <ward> apply   # writes input/Etikoppaka_Ward_N_Corrected_English.xlsx

The draft (any layout whose header row contains "GP Serial No.", "Voter Name (English)", "Relationship",
"Relative Name (English)", "Voter Name (Telugu)", "Relative Name (Telugu)") is never modified: a byte
copy is kept as output/ward_N/Etikoppaka_Ward_N_first_pass_backup.xlsx and always used as the source,
so the script can be re-run. Ward-specific decisions live in tools/corrections/wardN.py:

    TOKENS     {english word: replacement}            applied word by word to both English names
    FIELDS     {(serial, 1 | 3): 'whole value'}        1 = voter English, 3 = relative English
    TE_FIELDS  {(serial, 4 | 5): 'Telugu as printed'}  only after checking the printed card
    UNCERTAIN  {serial: 'reason'}                      -> Review Status "Check original PDF"

Applied automatically (same rules as Ward 2, tools/ward2_corrections.py):
  * Telugu text-layer artefacts (duplicated vowel sign / anusvara / syllable after "ర్ర", stray ZWJ).
  * Relationship = label printed on the card (Father / Husband / Mother), "Not specified" if none.
  * Draft EPIC / house numbers are only compared with the PDF (the dataset always takes them from the PDF).
Outputs: output/ward_N/wardN_corrections_log.csv, output/ward_N/wardN_audit.txt (audit mode).
"""
import csv, difflib, importlib, json, os, re, shutil, sys
sys.path.insert(0, sys.argv[1]); sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fitz, openpyxl
from openpyxl.styles import Font, PatternFill, Alignment
import ward_config

N = int(sys.argv[2]); MODE = sys.argv[3]
W = ward_config.ward(N)
OUTDIR = 'output/ward_%d' % N
BACKUP = '%s/Etikoppaka_Ward_%d_first_pass_backup.xlsx' % (OUTDIR, N)
LOG = '%s/ward%d_corrections_log.csv' % (OUTDIR, N)
AUDIT = '%s/ward%d_audit.txt' % (OUTDIR, N)
OK = 'Automated normalization — verify spelling'
CHECK = 'Check original PDF'
HEAD = ['GP Voter Serial No.', 'Voter Name (English)', 'Relationship', 'Relative Name (English)',
        'Voter Name (Telugu)', 'Relative Name (Telugu)', 'Review Status']
FFFD = '�'
VS = 'ాిీుూెేొోౌంః'

rules = importlib.import_module('corrections.ward%d' % N) if os.path.exists(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), 'corrections', 'ward%d.py' % N)) else None
TOKENS = getattr(rules, 'TOKENS', {}); FIELDS = getattr(rules, 'FIELDS', {})
TE_FIELDS = getattr(rules, 'TE_FIELDS', {}); UNCERTAIN = getattr(rules, 'UNCERTAIN', {})


def fix_te(t):
    if not t:
        return t
    s = re.sub('ర్ర([%s]?)ర\\1' % VS, 'ర్ర\\1', t)
    s = re.sub('([%s])\\1' % VS, '\\1', s)
    s = s.replace('ంండ', 'ండ').replace('‍', '')
    return s


def fix_en(serial, idx, v):
    if (serial, idx) in FIELDS:
        return FIELDS[(serial, idx)]
    if not v:
        return v
    v = v.replace('‌', '').replace('‍', '')
    return ' '.join(TOKENS.get(w, w) for w in v.split())


# ---------------- draft ----------------
os.makedirs(OUTDIR, exist_ok=True)
if not os.path.exists(BACKUP):
    shutil.copyfile(W['xlsx'], BACKUP)
ws = openpyxl.load_workbook(BACKUP, data_only=True).worksheets[0]
rows = [[c for c in r] for r in ws.iter_rows(values_only=True)]
hi = next(i for i, r in enumerate(rows) if r and 'GP Serial No.' in [str(c).strip() if c else '' for c in r]
          or (r and r[0] == 'GP Voter Serial No.'))
hdr = [str(c).strip() if c else '' for c in rows[hi]]
col = {h: hdr.index(h) for h in hdr if h}
gp = col.get('GP Serial No.', col.get('GP Voter Serial No.'))
need = ['Voter Name (English)', 'Relationship', 'Relative Name (English)', 'Voter Name (Telugu)', 'Relative Name (Telugu)']
draft = {}
for r in rows[hi + 1:]:
    if not r or r[gp] in (None, ''):
        continue
    s = int(str(r[gp]).strip())
    assert s not in draft, 'duplicate serial in draft %d' % s
    draft[s] = dict(vals=[s] + [(str(r[col[h]]).strip() if r[col[h]] not in (None, '') else None) for h in need],
                    epic=r[col['EPIC ID']] if 'EPIC ID' in col else None,
                    house=r[col['House No.']] if 'House No.' in col else None)

# ---------------- PDF cards ----------------
REL = {'తం' + FFFD + 'డి పేరు': 'Father', 'భర' + FFFD + ' పేరు': 'Husband', 'తలి' + FFFD + ' పేరు': 'Mother'}
WATERMARK = {'SEC', 'ANDHRA', 'PRADESH', 'SH'}
cards = {}
for pno, p in enumerate(fitz.open(W['pdf'])):
    words = p.get_text('words')
    for w in ward_config.card_serials(words, W['first'], W['last']):
        s = int(w[4]); card = fitz.Rect(w[0] - 62, w[1] - 6, w[0] + 122, w[1] + 74)
        lines = []
        for b in p.get_text('dict', clip=card)['blocks']:
            for l in b.get('lines', []):
                t = ''.join(sp['text'] for sp in l['spans']).strip()
                if t:
                    lines.append((round(l['bbox'][1]), round(l['bbox'][0]), t))
        lines = [t for _, _, t in sorted(lines)]
        latin = [ww[4] for ww in words if card.contains(fitz.Point((ww[0] + ww[2]) / 2, (ww[1] + ww[3]) / 2))
                 and re.search(r'[A-Za-z0-9]', ww[4]) and ww[4] not in WATERMARK]
        m = re.fullmatch(r'(\d{3,4}) \.?(\d+) (\d+) (\d+) (\d+) (\S+) ([A-Z]{3}\d{7})', ' '.join(latin))
        found = [REL[l] for l in lines if l in REL]
        vals = [l[1:].strip() for l in lines if l.startswith(':') and not re.search(r'[0-9A-Za-z]', l)]
        cards[s] = dict(page=pno + 1, label=found[0] if found else None, vals=vals,
                        house=m.group(6) if m else None, epic=m.group(7) if m else None, ok=bool(m))

expected = list(range(W['first'], W['last'] + 1))
print('draft rows', len(draft), 'missing', sorted(set(expected) - set(draft)), 'extra', sorted(set(draft) - set(expected)))
print('pdf cards', len(cards), 'missing', sorted(set(expected) - set(cards)), 'unparsed', sorted(s for s, c in cards.items() if not c['ok']))


def sim(a, b):
    f = lambda t: re.sub(r'[\s�.‌‍]', '', t or '')
    return difflib.SequenceMatcher(None, f(a), f(b)).ratio()


def corrected(s):
    v = list(draft[s]['vals'])
    v[1] = fix_en(s, 1, v[1]); v[3] = fix_en(s, 3, v[3])
    v[4] = TE_FIELDS.get((s, 4), fix_te(v[4])); v[5] = TE_FIELDS.get((s, 5), fix_te(v[5]))
    v[2] = cards[s]['label'] or 'Not specified'
    return v + [CHECK if s in UNCERTAIN else OK]


if MODE == 'audit':
    # Known spellings from the corrected Ward 2 / Ward 11 workbooks (Telugu word -> English word).
    known = {}
    for path in ('input/Etikoppaka_Ward_2_Corrected_English.xlsx', 'input/Etikoppaka_Ward_11_Corrected_English.xlsx'):
        for r in list(openpyxl.load_workbook(path, data_only=True).worksheets[0].iter_rows(values_only=True))[1:]:
            for en, te in ((r[1], r[4]), (r[3], r[5])):
                if en and te:
                    e, t = en.split(), fix_te(te).split()
                    if len(e) == len(t):
                        for a, b in zip(t, e):
                            known.setdefault(a, set()).add(b)
    with open(AUDIT, 'w', encoding='utf-8') as f:
        f.write('serial | draft EN | rel | draft rel EN | TE (artefacts fixed) | rel TE | PDF label | sim name/rel | PDF text | draft EPIC/house vs PDF | known spellings\n')
        for s in expected:
            d = draft.get(s); c = cards.get(s)
            if not d or not c:
                f.write('%d MISSING draft=%s pdf=%s\n' % (s, bool(d), bool(c))); continue
            v = d['vals']; te4, te5 = fix_te(v[4]), fix_te(v[5])
            pv = c['vals']
            sn = sim(te4, pv[0] if pv else ''); sr = sim(te5, pv[1] if len(pv) > 1 else '')
            notes = []
            if d['epic'] and str(d['epic']).strip() != c['epic']: notes.append('EPIC draft %s pdf %s' % (d['epic'], c['epic']))
            if d['house'] and str(d['house']).strip() != c['house']: notes.append('HOUSE draft %s pdf %s' % (d['house'], c['house']))
            if v[2] != (c['label'] or 'Not specified'): notes.append('REL draft %s pdf %s' % (v[2], c['label']))
            if sn < 0.6 or (te5 and sr < 0.6) or (not te5 and len(pv) > 1): notes.append('TE-MISMATCH')
            kn = {t: sorted(known[t]) for t in (te4 or '').split() + (te5 or '').split() if t in known}
            f.write('%d | %s | %s | %s | %s | %s | %s | %.2f/%.2f | %s | %s | %s\n' % (
                s, v[1], v[2], v[3], te4, te5, c['label'], sn, sr, ' / '.join(pv), '; '.join(notes), kn))
    print('audit written', AUDIT)
    sys.exit(0)

# ---------------- apply ----------------
assert sorted(draft) == expected and sorted(cards) == expected
wb = openpyxl.Workbook(); out = wb.active; out.title = W['sheet']
out.append(HEAD)
for c in out[1]:
    c.font = Font(bold=True, color='FFFFFF'); c.fill = PatternFill('solid', fgColor='305496')
    c.alignment = Alignment(wrap_text=True, vertical='center')
log = []
for s in expected:
    new = corrected(s); old = draft[s]['vals'] + [None]
    out.append(new)
    for i in range(1, 6):
        if old[i] != new[i]:
            log.append([s, HEAD[i], old[i], new[i], ''])
    if s in UNCERTAIN:
        log.append([s, HEAD[6], '', CHECK, UNCERTAIN[s]])
for i, wdt in enumerate([10, 30, 13, 30, 30, 30, 34], 1):
    out.column_dimensions[openpyxl.utils.get_column_letter(i)].width = wdt
out.freeze_panes = 'B2'; out.auto_filter.ref = out.dimensions
wb.save(W['xlsx'])
with open(LOG, 'w', newline='', encoding='utf-8-sig') as f:
    wr = csv.writer(f); wr.writerow(['GP Serial No.', 'Column', 'First-pass value', 'Corrected value', 'Note']); wr.writerows(log)
from collections import Counter
print('wrote', W['xlsx'], '| changes by column', dict(Counter(x[1] for x in log)), '| flagged', len(UNCERTAIN))
print('cards without relationship label', sorted(s for s in expected if not cards[s]['label']))
mism = [s for s in expected if draft[s]['epic'] and str(draft[s]['epic']).strip() != cards[s]['epic']]
mish = [s for s in expected if draft[s]['house'] and str(draft[s]['house']).strip() != cards[s]['house']]
print('draft EPIC differs from PDF', mism, '| draft house differs from PDF', mish, '(dataset uses PDF values)')
