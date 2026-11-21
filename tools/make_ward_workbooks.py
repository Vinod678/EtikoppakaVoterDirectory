"""Corrected 7-column workbooks (Ward 11 format) for new wards, plus a per-ward analysis workbook.

Usage (project root, after tools/extract_telugu.py for all wards):
    python tools/make_ward_workbooks.py <pylib> <ward> [<ward> ...]
    e.g.  python tools/make_ward_workbooks.py tools/.pylib 1 5 6 7 8 9 10 12 13 14

Per ward N it writes
    input/Etikoppaka_Ward_N_Corrected_English.xlsx     the 7 columns (an existing draft there is first
                                                       copied once to output/ward_N/..._first_pass_backup.xlsx)
    output/ward_N/wardN_corrections_log.csv            every value that differs from the draft
    output/ward_N/wardN_analysis.xlsx                  indexing/audit, surname clusters, anomaly summary,
                                                       7-column table, record-by-record translation,
                                                       word-by-word breakdown, verification list

Sources and rules
  * Telugu (voter / relative) = the exact text printed on the PDF card (build/te_wardN.json, recovered
    glyph by glyph by tools/telugu_pdf_text.py). Draft Telugu is only compared, never used.
  * Relationship = the label printed on the card (Father / Husband / Mother), otherwise "Not specified".
  * English, per Telugu word, in this order:
      1. the spelling already used for that Telugu word in the corrected Wards 2, 11, 3, 4 (Ward 2 first);
      2. a spelling from a first-draft workbook of the ward (DRAFTS below), if it plausibly matches the
         Telugu letters (garbled draft words are not used); the most common draft spelling wins across wards;
      3. readable transliteration (tools/telugu_translit.py).
    X+రావు is written "X Rao", X+నాయుడు "X Naidu" (as in the corrected wards). Printed word order is kept.
  * Surname variants (same surname printed differently: nasal ం/న్/మ్, long/short vowel, ళ/ల, శ/ష/స,
    doubled consonants) get the English spelling of the cluster's canonical form; only identical
    skeletons are merged, near matches are listed for verification.
  * Review Status: "Check original PDF" when the card needs a look (damaged print, no relationship label,
    no relative name, English word printed in Telugu letters, possible surname variant), otherwise
    "Automated normalization — verify spelling" (as in every corrected ward).
"""
import collections, csv, difflib, itertools, json, os, re, shutil, sys
sys.path.insert(0, sys.argv[1]); sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import openpyxl
from openpyxl.styles import Alignment, Font, PatternFill
import ward_config
from telugu_translit import cap, split_word, translit_word

NEW = [int(x) for x in sys.argv[2:]]
ESTABLISHED = {2: 4, 11: 3, 3: 2, 4: 1}          # ward -> weight (Ward 2 first, then 11)
DL = os.path.expanduser('~/Downloads')
DRAFTS = {1: 'input/Etikoppaka_Ward_1_Corrected_English.xlsx', 5: 'input/Etikoppaka_Ward_5_Corrected_English.xlsx',
          6: DL + '/Etikoppaka_Ward_6_Corrected_Voters.xlsx', 7: DL + '/Etikoppaka_Ward_7_Corrected_Voters.xlsx'}
OK = 'Automated normalization — verify spelling'
CHECK = 'Check original PDF'
HEAD = ['GP Voter Serial No.', 'Voter Name (English)', 'Relationship', 'Relative Name (English)',
        'Voter Name (Telugu)', 'Relative Name (Telugu)', 'Review Status']
LABEL_TE = {'Father': 'తండ్రి పేరు', 'Husband': 'భర్త పేరు', 'Mother': 'తల్లి పేరు'}
INITIAL = {'వి': 'V', 'వీ': 'V', 'ఎ': 'A', 'ఏ': 'A', 'కె': 'K', 'కే': 'K', 'జి': 'G', 'జీ': 'G', 'ఎస్': 'S',
           'బి': 'B', 'బీ': 'B', 'పి': 'P', 'పీ': 'P', 'ఎం': 'M', 'ఎమ్': 'M', 'టి': 'T', 'టీ': 'T', 'డి': 'D',
           'డీ': 'D', 'ఆర్': 'R', 'ఎన్': 'N', 'సి': 'C', 'సీ': 'C', 'ఎల్': 'L', 'వై': 'Y', 'హెచ్': 'H', 'ఐ': 'I',
           'జె': 'J', 'జే': 'J', 'యు': 'U', 'ఒ': 'O', 'ఓ': 'O', 'ఇ': 'E', 'ఎఫ్': 'F', 'క్యూ': 'Q', 'ఎక్స్': 'X'}
# Letters spelled out as words ("యస్ వీ" = S. V.): initials only when next to another initial.
SPELLED = {'యస్': 'S', 'ఏస': 'S', 'ఏస్': 'S', 'ఎస': 'S', 'ఏన': 'N', 'ఏన్': 'N', 'ఎన': 'N', 'ఏమ్': 'M', 'ఈ': 'E',
           'హెచ్': 'H', 'ఆర్': 'R', 'ఎల్': 'L', 'వాయ్': 'Y'}
# Names the PDF prints broken across spaces (Telugu kept as printed, English joined), cf. the corrected
# Ward 11 ("నాగేశ్వ రరావు" = "Nageswara Rao"). Longest match first.
JOIN = {
    ('శ్రీ', 'ను'): 'Srinu', ('శ్రీ', 'నూ'): 'Srinu', ('దేవర్', 'పు'): 'Devarapu', ('మకర్', 'పు'): 'Makarapu',
    ('శ్రిగకోల్', 'పు'): 'Srigakolapu', ('వైరా', 'ల'): 'Vairala', ('కొత్త', 'ల'): 'Kottala',
    ('చ', 'క్కిరా', 'ల'): 'Chakkirala', ('నా', 'గీ', 'రేడ్డీ'): 'Nagireddi', ('సే', 'న్నా', 'మూ', 'సేట్టీ'): 'Sennamsetti',
    ('షేన్', 'ణంశెట్టి'): 'Sennamsetti', ('సేన్', 'నామ', 'శెట్టి'): 'Sennamsetti', ('శెన్నంశె', 'ట్టి'): 'Sennamsetti',
    ('సుబ్ర', 'మన్యా', 'మూ'): 'Subramanyam', ('ఉపరా', 'పల్లి'): 'Upparapalli', ('సర్', 'వేశ్వరరావు'): 'Sarveswara Rao',
    ('దే', 'మూ', 'లూ'): 'Demulu', ('దే', 'ముల్లు'): 'Demullu', ('గా', 'రా'): 'Gara', ('తంత', 'రా'): 'Thanthara',
    ('తంతా', 'రా'): 'Thanthara', ('కాసా', 'రపు'): 'Kasarapu', ('ఇంజి', 'రపు'): 'Injirapu', ('ము', 'సిలి'): 'Musili',
    ('సన్యాస', 'మ్మా'): 'Sanyasamma', ('కరే', 'డ్లా'): 'Karedla', ('నూకల', 'మ్మా'): 'Nukalamma',
    ('చిప్ప', 'లా'): 'Chippala', ('చీ', 'ప్పం'): 'Chippam', ('న', 'క్కా'): 'Nakka', ('పి', 'ల్లా'): 'Pilla',
    ('కా', 'సూలమ్మ'): 'Kasulamma', ('తా', 'తాలూ'): 'Tatalu', ('సిం', 'హచలం'): 'Simhachalam',
    ('పక్కూ', 'ర్ఠీ'): 'Pakkurthi', ('కట్', 'టా'): 'Katta', ('తాతబ్బా', 'యీ'): 'Tatabbayi', ('వడ్డా', 'ది'): 'Vaddadi',
    ('సాష్', 'యాంక్'): 'Sashank', ('అమా', 'డీ'): 'Amadi', ('శ్రీ', 'దేవి'): 'Sridevi',
}
# Established (corrected-ward) mappings that came from misaligned rows, and their right spelling.
FIXED = {'మూర్తి': 'Murthy', 'పాటి': 'Pati', 'శ్రీనువాస్': 'Srinivas', 'సుదర్శనరాజు': 'Sudarsana Raju',
         'సుర్యనారాయణ': 'Suryanarayana', 'లక్ష్మికలా': 'Lakshmikala', 'దోడ్డిపట్ల': 'Doddipatla', 'సేణం': 'Senam', 'బు': 'Bu'}
# English words printed in Telugu letters: translated, and the record flagged.
ENGLISH_WORDS = {'సిస్టర్': 'Sister', 'బ్రదర్': 'Brother', 'ఫాదర్': 'Father', 'మదర్': 'Mother', 'లేట్': '(Late)', 'లేటు': '(Late)'}
GLOSS = {'రావు': 'Rao — honorific name suffix', 'నాయుడు': 'Naidu — community title', 'రాజు': 'Raju — "king"',
         'లక్ష్మి': 'Lakshmi — goddess of wealth', 'లక్ష్మీ': 'Lakshmi — goddess of wealth', 'బాబు': 'Babu — "child / sir" (affectionate)',
         'శ్రీ': 'Sri — honorific "auspicious"', 'వెంకట': 'Venkata — Lord Venkateswara', 'సత్య': 'Satya — "truth"',
         'నారాయణ': 'Narayana — Lord Vishnu', 'చిన్న': 'Chinna — "small / younger"', 'పెద్ద': 'Pedda — "elder / big"',
         'కృష్ణ': 'Krishna — Lord Krishna', 'అప్పల': 'Appala — local deity Appanna', 'దుర్గ': 'Durga — goddess Durga',
         'రాము': 'Ramu — Lord Rama', 'సాయి': 'Sai — Sai Baba', 'నాగ': 'Naga — serpent deity', 'దేవి': 'Devi — "goddess"',
         'రమణ': 'Ramana — "delightful"', 'సూర్య': 'Surya — the Sun', 'గంగ': 'Ganga — the river goddess',
         'సీత': 'Sita — consort of Rama', 'పార్వతి': 'Parvathi — goddess Parvati', 'ఈశ్వర': 'Eswara — Lord Shiva',
         'శివ': 'Siva — Lord Shiva', 'కుమార్': 'Kumar — "son / prince"', 'కుమారి': 'Kumari — "daughter / maiden"'}
VS = 'ాిీుూృౄెేైొోౌ'


def load_rows(path):
    rows = [list(r) for r in openpyxl.load_workbook(path, data_only=True, read_only=True).worksheets[0].iter_rows(values_only=True)]
    hi = next(i for i, r in enumerate(rows) if r and any(str(c).strip() in ('GP Serial No.', 'GP Voter Serial No.') for c in r if c))
    h = [str(c).strip() if c else '' for c in rows[hi]]
    gp = h.index('GP Serial No.') if 'GP Serial No.' in h else h.index('GP Voter Serial No.')
    out = {}
    for r in rows[hi + 1:]:
        v = r[gp] if gp < len(r) else None
        if isinstance(v, (int, float)) or (isinstance(v, str) and v.strip().isdigit()):
            g = lambda k: (str(r[h.index(k)]).strip() if k in h and r[h.index(k)] not in (None, '') else None)
            out[int(v)] = dict(en=g('Voter Name (English)'), rel_en=g('Relative Name (English)'),
                               te=g('Voter Name (Telugu)'), rel_te=g('Relative Name (Telugu)'))
    return out


def te_units(word):
    """Spelling units of a Telugu word: [word] or [prefix, రావు / నాయుడు]."""
    return split_word(word)


def en_split(word):
    m = re.fullmatch(r'(.+?)(rao|naidu|nayudu)', word, re.I)
    if m and len(m.group(1)) > 2:
        return [m.group(1), 'Naidu' if m.group(2).lower() != 'rao' else 'Rao']
    return [word]


def align(te_text, en_text):
    """Telugu units aligned with English words, or None if the counts cannot be matched."""
    if not te_text or not en_text:
        return None
    tw, ew = te_text.split(), en_text.split()
    opts = [[[w]] + ([te_units(w)] if len(te_units(w)) > 1 else []) for w in tw]
    eopts = [[[w]] + ([en_split(w)] if len(en_split(w)) > 1 else []) for w in ew]
    for te_combo in itertools.product(*opts):
        tu = [(w, u) for w, units in zip(tw, te_combo) for u in units]
        for en_combo in itertools.islice(itertools.product(*eopts), 64):
            eu = [x for e in en_combo for x in e]
            if len(tu) == len(eu):
                if all(e in ('Rao', 'Naidu') for (w, u), e in zip(tu, eu) if u in ('రావు', 'రావ', 'నాయుడు', 'నాయిడు')):
                    return list(zip(tu, eu))
    return None


def letters(en):
    s = en.lower()
    for a, b in (('aa', 'a'), ('ee', 'i'), ('oo', 'u'), ('th', 't'), ('sh', 's'), ('w', 'v'), ('y', 'i'), ('dh', 'd'), ('bh', 'b')):
        s = s.replace(a, b)
    return re.sub(r'(.)\1+', r'\1', re.sub('[^a-z]', '', s))


def plausible(en, te_unit):
    """A draft spelling is used only if it has exactly the printed letters (drafts often drop the
    final vowel - "Annamm", "Tatayy" - or write the nasal as m - "Chamti", "Sumkar")."""
    return letters(en) == letters(translit_word(te_unit))


def skeleton(t):
    """Same surname printed differently -> same key (nasals, vowel length, ళ/ల, శ/ష/స, doubled consonants)."""
    s = re.sub('[\\s.‌‍]', '', t)
    s = re.sub('[ంణమన]్?(?=[క-హ])', 'N', s).replace('ం', 'N')
    for a, b in (('ీ', 'ి'), ('ూ', 'ు'), ('ే', 'ె'), ('ో', 'ొ'), ('ై', 'ె'), ('ళ', 'ల'), ('శ', 'స'), ('ష', 'స'), ('ఱ', 'ర'),
                 ('ణ', 'న'), ('ఠ', 'ట'), ('థ', 'త'), ('ఢ', 'డ'), ('ధ', 'ద'), ('భ', 'బ'), ('ఖ', 'క'), ('ఘ', 'గ'), ('ఛ', 'చ')):
        s = s.replace(a, b)
    s = re.sub('([క-హ])్\\1', '\\1', s)        # doubled consonant; other conjuncts / final ్ are kept
    return re.sub('[ాా]$', '', s)              # (చేక్ Shaik != చెక్క Chekka, గ్ర != గరా)


# ---------------------------------------------------------------- data
te = {n: {int(k): v for k, v in json.load(open('build/te_ward%d.json' % n, encoding='utf-8')).items()} for n in range(1, 15)}
# Card text the extractor cannot read as a name (checked on the rendered card image).
PRINT_FIX = {(14, 4958): ('relative', 'తాలుపులు', 'Father\'s name is printed "తాలుపులు25"; the stray "25" is left out.')}
for (_n, _s), (_f, _v, _why) in PRINT_FIX.items():
    te[_n][_s][_f] = _v
    te[_n][_s]['fix'] = _why

# 1. established spellings (unit -> English counts, weighted) and whole-word phrases
unit_en = collections.defaultdict(collections.Counter)
for n, wt in ESTABLISHED.items():
    for s, r in load_rows(ward_config.ward(n)['xlsx']).items():
        c = te[n][s]
        for te_text, en in ((c['name'], r['en']), (c['relative'], r['rel_en'])):
            for (w, u), e in align(te_text, en) or []:
                unit_en[u][e] += wt
established = {u: c.most_common(1)[0][0] for u, c in unit_en.items()}
established = {u: e for u, e in established.items()
               if not u.startswith('◌') and e.isascii() and (e.endswith('.') or e in ('Shaik', 'John', 'Rao', 'Naidu', '(Late)') or
               difflib.SequenceMatcher(None, letters(e), letters(translit_word(u))).ratio() >= 0.5)}
established.update(FIXED)


def clean_raw(u, e):
    """Some Ward 11 entries are raw transliterations ("Kondalaravu", "Govimdamma", "Kaasulu"): written
    for the new wards in the corrected style ("Kondala Rao", "Govindamma", "Kasulu")."""
    t = translit_word(u)
    raw = re.search(r'm[kgcjtd]|ravu$|nayudu$', e, re.I) or ('aa' in e.lower() and 'aa' not in t)
    if not raw or '.' in e:
        return e
    x = re.sub(r'm(?=[kgcjtd])', 'n', e)
    x = re.sub(r'(?i)(?<=[a-z])ravu$', ' Rao', x)
    x = re.sub(r'(?i)(?<=[a-z])nayudu$', ' Naidu', x)
    if 'aa' not in t:
        x = re.sub(r'(?i)aa', lambda m: m.group(0)[0], x)
    if re.search(r'm[kgcjtd]|ravu$|nayudu$', e, re.I):
        x = x.replace('oo', 'u')
    return x


established = {u: clean_raw(u, e) for u, e in established.items()}
est_variants = {u: sorted(c) for u, c in unit_en.items() if len(c) > 1}

# 2. draft spellings (plausible only), counted over all new wards
draft_en = collections.defaultdict(collections.Counter)
drafts = {}
for n in NEW:
    if n in DRAFTS and os.path.exists(DRAFTS[n]):
        bk = 'output/ward_%d/Etikoppaka_Ward_%d_first_pass_backup.xlsx' % (n, n)
        os.makedirs(os.path.dirname(bk), exist_ok=True)
        if not os.path.exists(bk):
            shutil.copyfile(DRAFTS[n], bk)
        drafts[n] = load_rows(bk)
        for s, r in drafts[n].items():
            c = te[n][s]
            for te_text, en in ((c['name'], r['en']), (c['relative'], r['rel_en'])):
                for (w, u), e in align(te_text, en) or []:
                    if u not in established and plausible(e, u):
                        draft_en[u][e] += 1
from_draft = {u: c.most_common(1)[0][0] for u, c in draft_en.items()}

# 3. surname clusters over every ward (last word of a 2+ word name is the family name in these rolls)
surname_occ = collections.defaultdict(list)    # telugu surname -> [(ward, serial, field, word#)]
for n in range(1, 15):
    for s, c in te[n].items():
        k0 = len((c['name'] or '').split())
        for field, txt, base in (('voter', c['name'], 0), ('relative', c['relative'], k0)):
            ws = (txt or '').split()
            if len(ws) >= 2 and len(ws[-1]) >= 3 and not ws[-1].endswith('.'):
                surname_occ[ws[-1]].append((n, s, field, base + len(ws)))
clusters = collections.defaultdict(list)
for t in surname_occ:
    clusters[skeleton(t)].append(t)


def unit_english(u):
    """English for one spelling unit (and where it came from)."""
    if u in ENGLISH_WORDS:
        return ENGLISH_WORDS[u], 'translation'
    base = u.rstrip('.')
    if base in INITIAL and (u.endswith('.') or len(base) <= 2):
        return INITIAL[base] + '.', 'initial'
    if u in established:
        return established[u], 'established'
    if u in ('రావు', 'రావ'):
        return 'Rao', 'established'
    if u in ('నాయుడు', 'నాయిడు'):
        return 'Naidu', 'established'
    if u in from_draft:
        return from_draft[u], 'draft'
    return cap(translit_word(u)), 'generated'


def canonical_surname(t):
    """(canonical Telugu form, its English) for a surname variant, or None if it is its own form."""
    group = clusters.get(skeleton(t), [t])
    if len(group) < 2:
        return None
    known = [g for g in group if g in established]
    best = max(known or group, key=lambda g: (g in established, len(surname_occ[g]), g))
    if best == t:
        return None
    return best, unit_english(best)[0]


def english_of(text):
    """English for a Telugu name (word order kept), with per-word provenance."""
    if not text:
        return None, []
    words, info = [], []
    tw = text.split()
    skip = 0
    for i, w in enumerate(tw):
        if skip:
            skip -= 1
            info.append(dict(te=w, en='(joined with previous word)', src='joined'))
            continue
        span = next((k for k in (4, 3, 2) if tuple(tw[i:i + k]) in JOIN), 0)
        if span:
            words.append(JOIN[tuple(tw[i:i + span])])
            info.append(dict(te=w, en=words[-1], src='joined'))
            skip = span - 1
            continue
        near = [x.rstrip('.') for x in tw[max(0, i - 1):i] + tw[i + 1:i + 2]]
        if w.rstrip('.') in SPELLED and any(x in SPELLED or x in INITIAL for x in near):
            words.append(SPELLED[w.rstrip('.')] + '.')
            info.append(dict(te=w, en=words[-1], src='initial'))
            continue
        units = te_units(w)
        if w in established:
            units = [w]
        en_units = [unit_english(u) for u in units]
        en = ' '.join(e for e, _ in en_units)
        src = '+'.join(sorted({s for _, s in en_units}))
        if i == len(tw) - 1 and len(tw) >= 2 and src in ('generated', 'draft'):
            cs = canonical_surname(w)
            if cs:
                en, src = cs[1], 'surname-variant of ' + cs[0]
        words.append(en)
        info.append(dict(te=w, en=en, src=src))
    return ' '.join(words), info


# ---------------------------------------------------------------- per ward
fill = PatternFill('solid', fgColor='305496')


def sheet(wb, title, header, rows, widths):
    ws = wb.create_sheet(title)
    ws.append(header)
    for c in ws[1]:
        c.font = Font(bold=True, color='FFFFFF'); c.fill = fill
        c.alignment = Alignment(wrap_text=True, vertical='center')
    for r in rows:
        ws.append(r)
    for i, w in enumerate(widths, 1):
        ws.column_dimensions[openpyxl.utils.get_column_letter(i)].width = w
    ws.freeze_panes = 'A2'
    return ws


def tags_for(variant, canon):
    t = []
    nas = lambda x: re.sub('[ంణమన]్?(?=[క-హ])', 'N', x).replace('ం', 'N')
    if nas(variant) == nas(canon) and variant != canon:
        t.append('nasal')
    if variant.startswith(canon) and len(variant) > len(canon):
        t.append('extra trailing')
    if canon.startswith(variant) and len(canon) > len(variant):
        t.append('dropped ending')
    if variant[-1:] in VS and canon[-1:] in VS and variant[-1] != canon[-1] and variant[:-1] == canon[:-1]:
        t.append('masculine/inflection')
    if not t:
        t.append('transliteration')    # vowel length / ళ-ల / శ-ష-స / doubled consonant
    return t


summary = []
for n in NEW:
    W = ward_config.ward(n)
    cards = te[n]
    draft = drafts.get(n, {})
    rows, log, verify, words_rows, trans_rows = [], [], [], [], []
    anomaly = collections.defaultdict(list)
    for s in range(W['first'], W['last'] + 1):
        c = cards[s]
        name_en, ninfo = english_of(c['name'])
        rel_en, rinfo = english_of(c['relative'])
        label = c['label'] or 'Not specified'
        reasons = [c['fix']] if c.get('fix') else []
        if c['check']:
            reasons.append('Printed name is damaged or unreadable on the card (orphan vowel sign / unreadable glyph).')
            anomaly['grapheme truncated'].append(s)
        if not c['label']:
            reasons.append('No relationship label printed on the card.')
        if not c['relative']:
            reasons.append('No relative name printed on the card.')
        for wi in ninfo + rinfo:
            if wi['src'] == 'translation':
                reasons.append('English word printed in Telugu letters: %s -> %s.' % (wi['te'], wi['en']))
                anomaly['translation'].append(s)
            if wi['src'].startswith('surname-variant'):
                anomaly['transliteration'].append(s)
        # surname dropped: relative carries a surname, voter name is a single word
        if c['name'] and c['relative'] and len(c['name'].split()) == 1 and len(c['relative'].split()) >= 2 and c['label'] in ('Father', 'Husband', 'Mother'):
            reasons.append('Voter name has no surname; relative is %s.' % c['relative'])
            anomaly['surname dropped'].append(s)
        # unspaced / splitting against known two-word forms
        for txt in (c['name'], c['relative']):
            for w in (txt or '').split():
                if len(te_units(w)) > 1 and w not in established:
                    anomaly['unspaced'].append(s)
        if any(wi['src'] == 'joined' for wi in ninfo + rinfo):
            anomaly['splitting'].append(s)
        for txt, info in ((c['name'], ninfo), (c['relative'], rinfo)):
            if info and len(info) >= 2 and info[-1]['src'] not in ('joined', 'initial') and \
                    re.fullmatch('[క-హ](?:్[క-హ])*[ా-ౌ]?', info[-1]['te']):
                reasons.append('Surname "%s" looks cut off in the print (%s).' % (info[-1]['te'], txt))
                anomaly['grapheme truncated'].append(s)
        status = CHECK if reasons else OK
        row = [s, name_en, label, rel_en, c['name'], c['relative'], status]
        rows.append(row)
        if reasons:
            verify.append([s, c['page'], c['name'], c['relative'], ' '.join(reasons)])
        d = draft.get(s)
        if d:
            for i, key in ((1, 'en'), (3, 'rel_en'), (4, 'te'), (5, 'rel_te')):
                if (d[key] or None) != (row[i] or None):
                    log.append([s, HEAD[i], d[key], row[i], 'Telugu = printed PDF text' if i >= 4 else 'English spelling rule'])
        # word-by-word breakdown (word index: voter name words 1..k, then relative name words k+1..)
        k = 0
        for field, info in (('Voter name', ninfo), ('Relative name', rinfo)):
            for j, wi in enumerate(info):
                k += 1
                is_surname = j == len(info) - 1 and len(info) >= 2
                tag = []
                if is_surname:
                    tag.append('surname')
                    cs = canonical_surname(wi['te'])
                    if cs:
                        tag += tags_for(wi['te'], cs[0])
                if len(te_units(wi['te'])) > 1:
                    tag.append('unspaced (written "%s")' % wi['en'])
                if wi['src'] == 'translation':
                    tag.append('translation')
                if wi['src'] == 'joined':
                    tag.append('splitting (one name printed in pieces)')
                if wi['te'] in est_variants:
                    tag.append('transliteration varies in corrected wards: ' + ' / '.join(est_variants[wi['te']]))
                meaning = GLOSS.get(wi['te']) or ('Initial' if wi['src'] == 'initial' else
                                                   'Surname (family name)' if is_surname else 'Personal name')
                words_rows.append([s, k, field, wi['te'], ', '.join(tag) or '-', translit_word(wi['te']), wi['en'], meaning, wi['src']])
        g_te = {'Male': 'పు', 'Female': 'స్త్రీ'}.get(c['gender'], '')
        card_te = ' | '.join(' '.join(x) for x in c['lines'])
        card_en = ('GP voter serial no. %s; assembly roll serial %s; voter name: %s; %s: %s; age: %s; gender: %s; '
                   'house no.: %s; voter ID (EPIC) card no.: %s' % (s, c['assembly'], name_en, label.lower() + "'s name" if c['label'] else 'relative', rel_en, c['age'], c['gender'], c['house'], c['epic']))
        trans_rows.append([s, c['page'], c['assembly'], c['name'], name_en, LABEL_TE.get(c['label'], '(none)'), label,
                           c['relative'], rel_en, c['age'], g_te, c['gender'], c['house'], c['epic'], card_te, card_en])

    # ---- 7-column workbook (Ward 11 format)
    if os.path.exists(W['xlsx']) and not os.path.exists('output/ward_%d/Etikoppaka_Ward_%d_first_pass_backup.xlsx' % (n, n)):
        os.makedirs('output/ward_%d' % n, exist_ok=True)
        shutil.copyfile(W['xlsx'], 'output/ward_%d/Etikoppaka_Ward_%d_first_pass_backup.xlsx' % (n, n))
    wb = openpyxl.Workbook(); wb.remove(wb.active)
    ws = sheet(wb, W['sheet'], HEAD, rows, [10, 30, 13, 30, 30, 30, 34])
    ws.freeze_panes = 'B2'; ws.auto_filter.ref = ws.dimensions
    wb.save(W['xlsx'])
    os.makedirs('output/ward_%d' % n, exist_ok=True)
    with open('output/ward_%d/ward%d_corrections_log.csv' % (n, n), 'w', newline='', encoding='utf-8-sig') as f:
        wr = csv.writer(f); wr.writerow(['GP Serial No.', 'Column', 'First-pass value', 'Corrected value', 'Note']); wr.writerows(log)

    # ---- analysis workbook
    serials = set(range(W['first'], W['last'] + 1))
    ward_surnames = {t for t, occ in surname_occ.items() if any(o[0] == n for o in occ)}
    cl_rows = []
    for key, group in sorted(clusters.items()):
        if len(group) < 2 or not (set(group) & ward_surnames):
            continue
        canon = max(group, key=lambda g: (g in established, len(surname_occ[g]), g))
        cen = unit_english(canon)[0]
        for v in sorted(group):
            occ = surname_occ[v]
            here = ['%s:w%d' % (s, wn) for (wd, s, f, wn) in occ if wd == n][:12]
            other = sorted({wd for (wd, s, f, wn) in occ if wd != n})
            expl = 'canonical form' if v == canon else (
                'printed variant of %s (%s); English unified to "%s"' % (canon, ', '.join(tags_for(v, canon)), cen))
            cl_rows.append([canon, cen, v, unit_english(v)[0] if v == canon else cen, len(occ), ' '.join(here),
                            ', '.join('W%d' % o for o in other), '-' if v == canon else ', '.join(tags_for(v, canon)), expl])
    # near matches (not merged): similar skeletons -> verify
    near = []
    sk = sorted({skeleton(t) for t in ward_surnames})
    allsk = sorted(clusters)
    for a in sk:
        for b in difflib.get_close_matches(a, allsk, n=4, cutoff=0.86):
            if b != a and a < b:
                near.append([' / '.join(clusters[a]), ' / '.join(clusters[b]), round(difflib.SequenceMatcher(None, a, b).ratio(), 2),
                             'Possibly the same surname - verify against the printed cards; not merged.'])
    for r in cl_rows:
        for t in r[7].split(', '):
            if t != '-':
                anomaly[t].extend(int(x.split(':')[0]) for x in r[5].split() if x)
    cats = ['nasal', 'unspaced', 'splitting', 'extra trailing', 'dropped ending', 'grapheme truncated',
            'masculine/inflection', 'transliteration', 'translation', 'surname dropped']
    an_rows = [[c, len(set(anomaly[c]) & serials), ', '.join(map(str, sorted(set(anomaly[c]) & serials)[:25]))] for c in cats]
    audit = [
        ['Word-indexing rule', 'Per record: whitespace-separated Telugu tokens of the voter name are words 1..k, the relative name continues at k+1. A trailing "." stays on its token (initials such as "వి.").'],
        ['Text source', 'Telugu recovered glyph by glyph from the PDF (tools/telugu_pdf_text.py): conjunct glyphs that the PDF text layer shows as U+FFFD are matched by outline to Nirmala UI and decoded with its substitution rules; letters put back into logical order.'],
        ['Accuracy check', 'Same method on the corrected Wards 2/3/4: 99.6% of name fields have identical letters (the rest are damaged prints).'],
        ['Cards in PDF', '%d of %d (serials %d-%d), each exactly once' % (len(cards), W['count'], W['first'], W['last'])],
        ['Damaged prints (flagged)', ', '.join(str(s) for s in sorted(cards) if cards[s]['check']) or 'none'],
        ['Cards without relationship label', ', '.join(str(s) for s in sorted(cards) if not cards[s]['label']) or 'none'],
        ['Cards without relative name', ', '.join(str(s) for s in sorted(cards) if not cards[s]['relative']) or 'none'],
        ['Male / female (PDF cards)', '%d / %d (official summary page: %d / %d)' % (sum(c['gender'] == 'Male' for c in cards.values()), sum(c['gender'] == 'Female' for c in cards.values()), *W['official_counts'])],
        ['First-draft workbook', DRAFTS.get(n, 'none (generated from the PDF)') if n in drafts else 'none (generated from the PDF)'],
        ['English sources (words)', ', '.join('%s %d' % (k, v) for k, v in collections.Counter(w[8] for w in words_rows).most_common())],
    ]
    wb = openpyxl.Workbook(); wb.remove(wb.active)
    sheet(wb, '1 Indexing & Audit', ['Item', 'Detail'], audit, [32, 140])
    sheet(wb, '2a Surname Clusters', ['Canonical (Telugu)', 'Canonical (English)', 'Observed variant', 'English used', 'Occurrences (all wards)',
                                      'In this ward (serial:word#)', 'Also in wards', 'Issue tags', 'Explanation'], cl_rows, [20, 18, 20, 18, 10, 40, 16, 22, 60])
    sheet(wb, '2b Near matches', ['Surname forms', 'Similar forms', 'Similarity', 'Note'], near, [40, 40, 10, 60])
    sheet(wb, '2c Anomaly Summary', ['Issue type', 'Records in this ward', 'Serials (first 25)'], an_rows, [22, 12, 120])
    sheet(wb, '3 Structured Table', HEAD, rows, [10, 30, 13, 30, 30, 30, 34])
    sheet(wb, '4 Record Translation', ['GP serial', 'PDF page', 'Assembly serial', 'Voter name (TE)', 'Voter name (EN)', 'Relation label (TE)',
                                       'Relationship (EN)', 'Relative (TE)', 'Relative (EN)', 'Age', 'Gender (TE)', 'Gender (EN)', 'House no.', 'EPIC',
                                       'Full card text (Telugu, as printed)', 'Full card text (English)'], trans_rows,
          [8, 7, 12, 26, 26, 12, 11, 26, 26, 5, 8, 8, 9, 12, 90, 90])
    sheet(wb, '5 Word Breakdown', ['GP serial', 'Word #', 'Field', 'Original Telugu', 'Surname / anomaly tag', 'Transliteration',
                                   'English used', 'Exact English meaning', 'Spelling source'], words_rows, [8, 7, 13, 20, 40, 18, 18, 36, 16])
    sheet(wb, '6 Verification', ['GP serial', 'PDF page', 'Voter (TE)', 'Relative (TE)', 'Why'], verify, [8, 8, 26, 26, 90])
    wb.save('output/ward_%d/ward%d_analysis.xlsx' % (n, n))
    src = collections.Counter(w[8] for w in words_rows)
    summary.append((n, len(rows), sum(r[6] == CHECK for r in rows), len(log), dict(src), len(cl_rows), len(near)))
    print('ward %d: %d records, %d "Check original PDF", %d draft changes logged, word sources %s, surname-cluster rows %d, near matches %d'
          % summary[-1])
