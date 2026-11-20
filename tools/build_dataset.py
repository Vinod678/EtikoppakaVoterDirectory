import sys, os, re, json, csv, hashlib, collections, datetime
sys.path.insert(0, sys.argv[1]); sys.stdout.reconfigure(encoding='utf-8')
import fitz, openpyxl
from openpyxl.styles import Font, PatternFill, Alignment
from openpyxl.utils import get_column_letter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ward_config
R = sys.argv[2]; xlsx_json = sys.argv[3]
WARD = int(sys.argv[4]) if len(sys.argv) > 4 else 11
W = ward_config.ward(WARD)
PDF = os.path.join(R, W['pdf'])
XLSX = os.path.join(R, W['xlsx'])
PHOTOS = os.path.join(R, W['photos'])
MAN = os.path.join(R, W['manifest'])
OUT_JSON = os.path.join(R, W['json'])
OUT_REV = os.path.join(R, W['review'])
FFFD = '�'


def sha(p):
    return hashlib.sha256(open(p, 'rb').read()).hexdigest()


before = {p: sha(p) for p in [PDF, XLSX] + [os.path.join(PHOTOS, f) for f in os.listdir(PHOTOS)]}

# ---------- Excel (raw strings, exactly as stored) ----------
xr = json.load(open(xlsx_json, encoding='utf-8'))[W['sheet']]['rows']
assert xr[0] == ['GP Voter Serial No.', 'Voter Name (English)', 'Relationship', 'Relative Name (English)',
                 'Voter Name (Telugu)', 'Relative Name (Telugu)', 'Review Status'], xr[0]
excel = {}
for r in xr[1:]:
    s = int(r[0]); assert s not in excel; excel[s] = r

# ---------- PDF cards ----------
doc = fitz.open(PDF)
REL = {'తం' + FFFD + 'డి పేరు': 'Father', 'భర' + FFFD + ' పేరు': 'Husband', 'తలి' + FFFD + ' పేరు': 'Mother'}
WATERMARK = {'SEC', 'ANDHRA', 'PRADESH', 'SH'}
pdf = {}
for pno, p in enumerate(doc):
    words = p.get_text('words')
    for w in ward_config.card_serials(words, W['first'], W['last']):
        s = int(w[4]); assert s not in pdf, s
        x0, y0 = w[0], w[1]; card = fitz.Rect(x0 - 62, y0 - 6, x0 + 122, y0 + 74)
        lines = []
        for b in p.get_text('dict', clip=card)['blocks']:
            for l in b.get('lines', []):
                t = ''.join(sp['text'] for sp in l['spans']).strip()
                if t:
                    lines.append((round(l['bbox'][1]), round(l['bbox'][0]), t))
        lines = [t for _, _, t in sorted(lines)]
        latin = [ww[4] for ww in words
                 if card.contains(fitz.Point((ww[0] + ww[2]) / 2, (ww[1] + ww[3]) / 2))
                 and re.search(r'[A-Za-z0-9]', ww[4]) and ww[4] not in WATERMARK]
        m = re.fullmatch(r'(\d{3,4}) \.?(\d+) (\d+) (\d+) (\d+) (\S+) ([A-Z]{3}\d{7})', ' '.join(latin))
        assert m and int(m.group(1)) == s, (s, latin)
        gl = [l for l in lines if l.startswith('లింగము')]; assert len(gl) == 1, (s, gl)
        g = gl[0].split(':', 1)[1].strip()
        # Female glyph (స్త్రీ) is unmapped in the PDF font and appears as three U+FFFD characters.
        gender = {'పు': 'Male', FFFD * 3: 'Female'}.get(g); assert gender, (s, g)
        rels = [REL[l] for l in lines if l in REL]; assert len(rels) <= 1
        values = [l[1:].strip() for l in lines if l.startswith(':') and not re.search(r'[0-9A-Za-z]', l)]
        pdf[s] = dict(page=pno + 1, assembly_serial='%s-%s-%s' % m.group(2, 3, 4), age=int(m.group(5)),
                      house_no=m.group(6), epic_no=m.group(7), gender=gender,
                      relation_label=rels[0] if rels else None, has_relative_text=len(values) >= 2)

# ---------- Photos ----------
man = {int(r['gp_serial_no']): r for r in csv.DictReader(open(MAN, encoding='utf-8'))}

EXPECTED = list(range(W['first'], W['last'] + 1))
assert sorted(excel) == EXPECTED and sorted(pdf) == EXPECTED and sorted(man) == EXPECTED


# ---------- Issue detection ----------
def issues_for(s):
    e = excel[s]; p = pdf[s]; ph = man[s]; out = []
    rel_x = e[2]; rel_name = e[3]
    if e[6] == 'Check original PDF':
        out.append(('High', 'EXCEL_FLAG_CHECK_PDF',
                    'The Excel file itself marks this record "Check original PDF". Compare the English and Telugu names with the PDF card.'))
    if not rel_name and p['has_relative_text']:
        if p['relation_label']:
            out.append(('High', 'RELATIVE_MISSING_IN_EXCEL',
                        'Excel has no relative name and relationship "%s", but the PDF card shows a %s name. '
                        'Enter the relative name (English + Telugu) and relationship from the PDF.' % (rel_x, p['relation_label'])))
        else:
            out.append(('High', 'RELATIVE_MISSING_RELATION_UNKNOWN',
                        'Excel has no relative name. The PDF card prints a relative name, but the label (Father/Husband/Mother) '
                        'is missing on the official card itself. Enter the name from the PDF; the relationship cannot be determined from the source.'))
    elif p['relation_label'] and rel_x != p['relation_label']:
        out.append(('High', 'RELATIONSHIP_MISMATCH',
                     'Excel relationship is "%s" but the PDF card label is "%s".' % (rel_x, p['relation_label'])))
    if rel_name and rel_name == e[1]:
        out.append(('Medium', 'RELATIVE_SAME_AS_VOTER',
                    "Relative name is identical to the voter's own name. The PDF card shows the same, so this is an error in "
                    'the official source. Leave unchanged unless verified with the official roll / voter.'))
    te = (e[4] or '') + '\n' + (e[5] or '')

    def words(t):
        return [w for w in re.split(r'[\s.]+', t or '') if w]
    for label, en, tel in (('voter name', e[1], e[4]), ('relative name', e[3], e[5])):
        if tel and len(words(tel)) > len(words(en)):
            out.append(('Medium', 'TELUGU_WORD_SPLIT_SUSPECTED',
                        'Telugu %s "%s" has %d words but the English "%s" has %d. A word may have been split by a stray space '
                        '(a known PDF text-extraction artefact, e.g. "సత్య వతి" for Satyavathi). Check against the PDF card by eye; '
                        'it may also be correct as written.' % (label, tel, len(words(tel)), en, len(words(en)))))
    if re.search('[‌‍]', te):
        out.append(('Medium', 'TELUGU_HIDDEN_CHARACTER',
                    'Telugu text contains an invisible zero-width character (U+200C/U+200D), which can break search. Check and retype if needed.'))
    if int(ph['width_px']) < 120:
        out.append(('Low', 'LOW_RESOLUTION_PHOTO',
                    'Original photo is only %sx%s px; it will look blurry when enlarged. No better source exists in the PDF.'
                    % (ph['width_px'], ph['height_px'])))
    return out


records = []; review = []
for s in EXPECTED:
    e = excel[s]; p = pdf[s]; ph = man[s]
    iss = issues_for(s)
    records.append(collections.OrderedDict([
        ('gp_serial_no', s), ('ward_no', WARD),
        ('name_en', e[1]), ('name_te', e[4]),
        ('relationship', e[2]), ('relative_name_en', e[3]), ('relative_name_te', e[5]),
        ('age', p['age']), ('gender', p['gender']), ('house_no', p['house_no']),
        ('assembly_serial', p['assembly_serial']), ('epic_no', p['epic_no']),
        ('photo_filename', ph['photo_filename']), ('photo_width_px', int(ph['width_px'])),
        ('photo_height_px', int(ph['height_px'])),
        ('pdf_page', p['page']), ('pdf_relation_label', p['relation_label']),
        ('excel_review_status', e[6]),
        ('needs_review', bool(iss)), ('review_issue_codes', [c for _, c, _ in iss]),
    ]))
    for sev, code, txt in iss:
        review.append((s, sev, code, txt))

# ---------- Validation ----------
V = []


def check(name, ok, detail=''):
    V.append((name, 'PASS' if ok else 'FAIL', detail))


ser = [r['gp_serial_no'] for r in records]
check('369 records', len(records) == 369, str(len(records)))
check('Serials %d-%d each exactly once' % (W['first'], W['last']), ser == EXPECTED and len(set(ser)) == 369)
check('Every photo file exists', all(os.path.isfile(os.path.join(PHOTOS, r['photo_filename'])) for r in records))
check('Photo filename = serial', all(r['photo_filename'].split('.')[0] == str(r['gp_serial_no']) for r in records))
check('Photo files match manifest checksums',
      all(sha(os.path.join(PHOTOS, man[s]['photo_filename'])) == man[s]['sha256'] for s in EXPECTED))
check('No extra files in voter_photos', len(os.listdir(PHOTOS)) == 369, str(len(os.listdir(PHOTOS))))
check('EPIC numbers unique', len({r['epic_no'] for r in records}) == 369)
check('Assembly serials unique', len({r['assembly_serial'] for r in records}) == 369)
check('Every record has age, gender, house no, EPIC',
      all(r['age'] and r['gender'] and r['house_no'] and r['epic_no'] for r in records))
check('Names/relationship copied verbatim from Excel',
      all(r['name_en'] == excel[r['gp_serial_no']][1] and r['name_te'] == excel[r['gp_serial_no']][4]
          and r['relative_name_en'] == excel[r['gp_serial_no']][3] and r['relative_name_te'] == excel[r['gp_serial_no']][5]
          and r['relationship'] == excel[r['gp_serial_no']][2] for r in records))
if 'official_counts' in W:
    check('Male/female counts match the PDF summary page %s' % (W['official_counts'],),
          (sum(r['gender'] == 'Male' for r in records), sum(r['gender'] == 'Female' for r in records)) == W['official_counts'])
check('No "Husband" relationship on a male voter',
      not [r for r in records if r['relationship'] == 'Husband' and r['gender'] == 'Male'])

meta = collections.OrderedDict([
    ('dataset', 'Etikoppaka Gram Panchayat - Ward %d voters' % WARD), ('ward_no', WARD), ('gram_panchayat', 'Etikoppaka'),
    ('mandal', 'Yelamanchili'), ('district', 'Anakapalli'), ('assembly_constituency', 'Yelamanchili'),
    # Roll dates are printed only in the Ward 11 PDF; other wards leave them empty.
    ('roll_qualifying_date', '2026-01-01' if WARD == 11 else None), ('roll_publication_date', '2026-04-15' if WARD == 11 else None),
    ('record_count', len(records)), ('records_needing_review', sum(r['needs_review'] for r in records)),
    ('photo_folder', W['photos']),
    ('sources', {'excel': W['xlsx'], 'pdf': W['pdf'],
                 'photo_manifest': W['manifest']}),
    ('field_sources', {
        'name_en, name_te, relationship, relative_name_en, relative_name_te, excel_review_status': 'Excel (verbatim, not yet verified)',
        'age, gender, house_no, assembly_serial, epic_no, pdf_page, pdf_relation_label': 'PDF card text',
        'photo_filename, photo_width_px, photo_height_px': 'Photo manifest'}),
    ('notes', ['No value has been corrected or filled in; missing values are null.',
               'The Excel file marks all names as automated normalization still needing spelling verification.',
               'pdf_relation_label is null where the PDF card shows no Father/Husband/Mother label.']),
    ('generated_at', datetime.datetime.now().isoformat(timespec='seconds')),
])
json.dump(collections.OrderedDict([('meta', meta), ('voters', records)]),
          open(OUT_JSON, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)

# ---------- Review workbook ----------
wb = openpyxl.Workbook()
hfont = Font(bold=True, color='FFFFFF'); hfill = PatternFill('solid', fgColor='305496')
sevfill = {'High': 'F8CBAD', 'Medium': 'FFE699', 'Low': 'DDEBF7'}
order = {'High': 0, 'Medium': 1, 'Low': 2}


def sheet(ws, headers, rows, widths):
    ws.append(headers)
    for c in ws[1]:
        c.font = hfont; c.fill = hfill; c.alignment = Alignment(wrap_text=True, vertical='center')
    for r in rows:
        ws.append(r)
    for i, w in enumerate(widths, 1):
        ws.column_dimensions[get_column_letter(i)].width = w
    for row in ws.iter_rows(min_row=2):
        for c in row:
            c.alignment = Alignment(wrap_text=True, vertical='top')
    ws.freeze_panes = 'B2'; ws.auto_filter.ref = ws.dimensions


ws = wb.active; ws.title = 'Records to review'
rows = []
for r in records:
    if not r['needs_review']:
        continue
    s = r['gp_serial_no']; its = [x for x in review if x[0] == s]
    top = min((x[1] for x in its), key=order.get)
    rows.append([s, top, len(its), ', '.join(x[2] for x in its), '\n'.join('- ' + x[3] for x in its),
                 r['name_en'], r['name_te'], r['relationship'], r['relative_name_en'], r['relative_name_te'],
                 r['pdf_relation_label'] or '(no label on card)', r['age'], r['gender'], r['house_no'], r['epic_no'],
                 r['photo_filename'], 'Page %d' % r['pdf_page'], r['excel_review_status'], '', '', ''])
rows.sort(key=lambda x: (order[x[1]], x[0]))
sheet(ws, ['GP Serial No.', 'Priority', 'Issue count', 'Issue codes', 'What to check', 'Voter Name (English)',
           'Voter Name (Telugu)', 'Relationship (Excel)', 'Relative Name (English)', 'Relative Name (Telugu)',
           'Relation label on PDF card', 'Age (PDF)', 'Gender (PDF)', 'House No. (PDF)', 'EPIC No. (PDF)', 'Photo file',
           'PDF location', 'Excel review status', 'Verified value / correction', 'Reviewer', 'Done (Y/N)'],
      rows, [9, 9, 7, 28, 70, 26, 26, 13, 24, 24, 14, 7, 8, 10, 13, 10, 9, 20, 30, 14, 9])
for row in ws.iter_rows(min_row=2):
    row[1].fill = PatternFill('solid', fgColor=sevfill[row[1].value])

ws2 = wb.create_sheet('All issues')
sheet(ws2, ['GP Serial No.', 'Priority', 'Issue code', 'Explanation'],
      sorted([list(x) for x in review], key=lambda x: (order[x[1]], x[2], x[0])), [9, 9, 34, 110])
for row in ws2.iter_rows(min_row=2):
    row[1].fill = PatternFill('solid', fgColor=sevfill[row[1].value])

ws3 = wb.create_sheet('Summary')
cnt = collections.Counter(x[2] for x in review); sv = {x[2]: x[1] for x in review}
desc = {'EXCEL_FLAG_CHECK_PDF': 'Excel already says "Check original PDF".',
        'RELATIVE_MISSING_IN_EXCEL': 'Excel blank; PDF shows relative name AND relationship label.',
        'RELATIVE_MISSING_RELATION_UNKNOWN': 'Excel blank; PDF shows relative name but no relationship label (source defect).',
        'RELATIONSHIP_MISMATCH': 'Excel relationship differs from PDF label.',
        'RELATIVE_SAME_AS_VOTER': 'Relative name equals voter name.',
        'TELUGU_WORD_SPLIT_SUSPECTED': 'Telugu name has more words than the English one - possible stray space splitting a word (automated, may include correct names).',
        'TELUGU_HIDDEN_CHARACTER': 'Invisible zero-width character in Telugu text.',
        'LOW_RESOLUTION_PHOTO': 'Photo smaller than 120 px wide.'}
srows = [[k, sv[k], v, len({x[0] for x in review if x[2] == k}), desc[k]]
         for k, v in sorted(cnt.items(), key=lambda kv: (order[sv[kv[0]]], kv[0]))]
sheet(ws3, ['Issue code', 'Priority', 'Issues', 'Records', 'Meaning'], srows, [34, 9, 8, 9, 80])
r0 = ws3.max_row + 2
summary = [('Total voters', 369), ('Records needing review', len(rows)),
           ('  High priority', sum(1 for x in rows if x[1] == 'High')),
           ('  Medium priority', sum(1 for x in rows if x[1] == 'Medium')),
           ('  Low priority', sum(1 for x in rows if x[1] == 'Low')),
           ('General note', 'Every Excel name is marked "automated normalization - verify spelling"; a full spelling check of all 369 is still advisable.'),
           ('Not corrected', 'No value has been changed or filled in. Record corrections in column "Verified value / correction" on the first sheet.')]
for i, (k, v) in enumerate(summary):
    ws3.cell(r0 + i, 1, k).font = Font(bold=True); ws3.cell(r0 + i, 2, v)

after = {p: sha(p) for p in before}
check('Source PDF, Excel and photos unchanged', after == before)
ws4 = wb.create_sheet('Validation')
sheet(ws4, ['Check', 'Result', 'Detail'], V, [50, 10, 30])
wb.save(OUT_REV)

for v in V:
    print('%-50s %s %s' % v)
print('records needing review', len(rows), '| High', sum(1 for x in rows if x[1] == 'High'),
      'Medium', sum(1 for x in rows if x[1] == 'Medium'), 'Low', sum(1 for x in rows if x[1] == 'Low'))
print('issues by code', dict(cnt))
print('records by code', {k: len({x[0] for x in review if x[2] == k}) for k in cnt})
