"""Recovers the exact Telugu text of the GP voter-roll PDFs.

The PDFs embed subsets of the Windows font "Nirmala UI" whose ToUnicode table has no entry for
conjunct glyphs (్వ, ్డ, ్ప, ...), so the normal text layer shows U+FFFD there
("నాగేశ� రి నగిరెడి�" instead of "నాగేశ్వరి నగిరెడ్డి").

How the text is recovered (nothing is guessed):
  1. Every glyph drawn on the page is read with its glyph id (PyMuPDF text trace).
  2. Glyphs with a Unicode value keep it.
  3. A U+FFFD glyph is matched by its outline to the same glyph of the installed Nirmala UI
     (C:/Windows/Fonts/Nirmala.ttc - the PDF was made with another version of the font, so the
     glyph ids differ, the outlines do not).
  4. That glyph is turned back into letters with the installed font's own substitution rules
     (OpenType GSUB: ligature and single substitutions, applied in reverse), recursively.
A glyph that cannot be resolved stays U+FFFD, so it is visible and gets flagged.

Use: TeluguPdf(pdf_path, fonttools_dir).card_text(page, rect) -> list of lines (top-to-bottom).
"""
import hashlib, io, re, sys

NIRMALA = 'C:/Windows/Fonts/Nirmala.ttc'
FFFD = 0xFFFD


def _outline_key(glyf, name):
    g = glyf[name]
    try:
        coords, ends, flags = g.getCoordinates(glyf)
    except Exception:
        return None
    if not len(coords):
        return None
    pts = [(int(x), int(y)) for x, y in coords]
    return hashlib.sha1(repr((pts, list(ends), [f & 1 for f in flags])).encode()).hexdigest()


MATRA = 'ాిీుూృౄెేైొోౌౕౖ'
CONS = 'కఖగఘఙచఛజఝఞటఠడఢణతథదధనపఫబభమయరఱలళవశషసహ'
VIRAMA = '్'


def logical_order(text):
    """Puts glyphs back into Unicode (logical) order. Nirmala UI draws a subscript consonant after
    the vowel sign of its syllable ("డి్డ" for డ్డి, "రు్డ" for ర్డు) and the subscript ra before its
    base ("తం్రడి" for తండ్రి)."""
    # 1. subscript ra drawn before its base: "్ర" + base (+ subscripts) -> base (+ subscripts) + "్ర"
    s = re.sub('%sర([%s](?:%s[%s])*)' % (VIRAMA, CONS, VIRAMA, CONS), r'\1' + VIRAMA + 'ర', text)
    # 2. subscript drawn after the vowel sign: vowel sign + subscript(s) -> subscript(s) + vowel sign
    prev = None
    while prev != s:
        prev = s
        s = re.sub('([%s])((?:%s[%s])+)' % (MATRA, VIRAMA, CONS), r'\2\1', s)
    return s


class _SystemFont:
    """Installed Nirmala UI face: outline -> glyph name, glyph name -> letters."""

    def __init__(self, index):
        from fontTools.ttLib import TTFont
        f = TTFont(NIRMALA, fontNumber=index)
        self.name = f['name'].getDebugName(4)
        glyf = f['glyf']
        self.by_outline = {}
        for n in f.getGlyphOrder():
            k = _outline_key(glyf, n)
            if k and k not in self.by_outline:
                self.by_outline[k] = n
        rev_cmap = {}
        for u, n in sorted(f.getBestCmap().items()):
            rev_cmap.setdefault(n, chr(u))
        # Reverse substitutions: produced glyph -> list of source glyph sequences.
        made = {}
        for lookup in f['GSUB'].table.LookupList.Lookup:
            for st in lookup.SubTable:
                t = lookup.LookupType
                if t == 7:
                    t, st = st.ExtSubTable.LookupType, st.ExtSubTable
                if t == 1:
                    for src, dst in st.mapping.items():
                        made.setdefault(dst, []).append([src])
                elif t == 4:
                    for first, ligs in st.ligatures.items():
                        for lig in ligs:
                            made.setdefault(lig.LigGlyph, []).append([first] + list(lig.Component))
        self.rev_cmap, self.made, self.cache = rev_cmap, made, {}

    def letters(self, name, depth=0):
        if name in self.rev_cmap:
            return self.rev_cmap[name]
        if name in self.cache:
            return self.cache[name]
        if depth > 8 or name not in self.made:
            return None
        best = None
        for seq in self.made[name]:
            parts = [self.letters(s, depth + 1) for s in seq]
            if all(p is not None for p in parts):
                txt = ''.join(parts)
                if best is None or len(txt) < len(best):
                    best = txt
        self.cache[name] = best
        return best


class TeluguPdf:
    def __init__(self, pdf_path, fonttools_dir=None):
        if fonttools_dir:
            sys.path.insert(0, fonttools_dir)
        import fitz
        from fontTools.ttLib import TTFont
        self.fitz = fitz
        self.doc = fitz.open(pdf_path)
        self.system = {}      # 'regular' / 'bold' -> _SystemFont
        self.glyph_text = {}  # (font name, glyph id) -> letters or None
        self.embedded = {}    # font name -> (TTFont, kind)
        for pno in range(self.doc.page_count):
            for xref, ext, typ, base, ref, enc, *_ in self.doc.get_page_fonts(pno, full=True):
                if 'Nirmala' in base and base not in self.embedded:
                    name, e, t, buf = self.doc.extract_font(xref)
                    kind = 'bold' if 'Bold' in base else 'regular'
                    self.embedded[base.split('+')[-1]] = (TTFont(io.BytesIO(buf)), kind)
        self.unresolved = 0

    def _sys(self, kind):
        if kind not in self.system:
            idx = {'regular': 0, 'bold': 1}[kind]
            self.system[kind] = _SystemFont(idx)
        return self.system[kind]

    def _decode_glyph(self, font, gid):
        key = (font, gid)
        if key not in self.glyph_text:
            txt = None
            emb = next((v for k, v in self.embedded.items() if k in font), None)
            if emb and gid > 0:
                tt, kind = emb
                order = tt.getGlyphOrder()
                if gid < len(order):
                    k = _outline_key(tt['glyf'], order[gid])
                    sysf = self._sys(kind)
                    name = sysf.by_outline.get(k)
                    if name:
                        txt = sysf.letters(name)
            self.glyph_text[key] = txt
        return self.glyph_text[key]

    def chars(self, page, rect=None):
        """(x, y, text) for every drawn character, with U+FFFD glyphs decoded where possible."""
        out = []
        for sp in page.get_texttrace():
            for c in sp['chars']:
                u, gid, origin = c[0], c[1], c[2]
                if rect is not None and not rect.contains(self.fitz.Point(origin)):
                    continue
                if u == FFFD:
                    t = self._decode_glyph(sp['font'], gid)
                    if t is None:
                        self.unresolved += 1
                        t = chr(FFFD)
                else:
                    t = chr(u)
                out.append((origin[0], origin[1], t, sp['size']))
        return out

    def lines(self, page, rect=None, tol=2.5):
        """Characters grouped into lines (top to bottom), in drawing order within a line."""
        rows = []
        for x, y, t, size in self.chars(page, rect):
            for r in rows:
                if abs(r['y'] - y) <= tol:
                    r['c'].append((x, t)); break
            else:
                rows.append(dict(y=y, c=[(x, t)]))
        rows.sort(key=lambda r: r['y'])
        return [''.join(t for _, t in r['c']) for r in rows]


LABELS = {'తండ్రి పేరు': 'Father', 'భర్త పేరు': 'Husband', 'తల్లి పేరు': 'Mother'}


def card_fields(tp, page, serial_word):
    """Name, relative, relationship label (and raw lines) of the card whose GP serial is
    [serial_word] (a PyMuPDF word tuple from ward_config.card_serials)."""
    fitz = tp.fitz
    w = serial_word
    rect = fitz.Rect(w[0] - 62, w[1] - 6, w[0] + 122, w[1] + 74)
    rows = []
    for x, y, t, size in tp.chars(page, rect):
        for r in rows:
            if abs(r['y'] - y) <= 2.5:
                r['c'].append((x, t)); break
        else:
            rows.append(dict(y=y, c=[(x, t)]))
    rows.sort(key=lambda r: r['y'])
    lines = []
    for r in rows:
        segs, cur, last = [], '', None
        for x, t in r['c']:
            if last is not None and abs(x - last) > 14:
                segs.append(cur); cur = ''
            cur += t; last = x
        segs.append(cur)
        lines.append([logical_order(s).strip() for s in segs if s.strip()])
    # Values start with ":" (first = voter, second = relative); a long name continues on the next
    # line without ":". Single letters are margin / watermark marks; numeric lines are other fields.
    values, label, last = [], None, None
    for segs in lines:
        for seg in segs:
            if seg in LABELS:
                label = LABELS[seg]; last = None
            elif seg.startswith(':') and not re.search(r'[0-9A-Za-z]', seg):
                values.append(_clean(seg[1:])); last = len(values) - 1
            elif re.search(r'[0-9A-Za-z]', seg) or ':' in seg or seg in ('ఓటరు పేరు', 'వయస్సు') or len(seg) <= 2:
                last = None if re.search(r'[0-9]', seg) or ':' in seg else last
            elif last is not None:
                values[last] = (values[last] + ' ' + _clean(seg)).strip()
    name = values[0] if values else None
    rel = values[1] if len(values) > 1 else None
    flat = ' '.join(' '.join(x) for x in lines)
    g = re.search('లింగము\s*:\s*(పు|స్త్రీ|స్ర్తీ)', flat)
    age = re.search('వయస్సు\s*:?\s*(\d{2,3})', flat) or re.search(':\s*(\d{2,3})\s*లింగము', flat)
    house = re.search('ఇంటి సంఖ్య\s*:\s*(\S+)', flat)
    epic = re.search('([A-Z]{3}\d{7})', flat)
    asm = re.search('అ\.ఓ\.జా\.వ\.సం\s*\.\s*(\d+\s*-\s*\d+\s*-\s*\d+)', flat)
    return dict(name=name or None, relative=rel or None, label=label, lines=lines,
                gender=None if not g else ('Male' if g.group(1) == 'పు' else 'Female'),
                age=int(age.group(1)) if age else None, house=house.group(1) if house else None,
                epic=epic.group(1) if epic else None,
                assembly=re.sub(r'\s+', '', asm.group(1)) if asm else None)


MARGIN = ('ఓ', 'త', 'భ', 'వ', 'ఇ')


def _clean(v):
    v = re.sub(r'\s+', ' ', v).strip()
    while len(v.split()) > 1 and v.split()[-1] in MARGIN:  # a margin mark caught on the line
        v = v.rsplit(' ', 1)[0]
    v = v.replace('ళ్శ', 'ళ్ళ')  # subscript ళ shares its outline with subscript శ
    v = v.replace('౦', 'ం')      # Telugu digit zero and anusvara share an outline
    # A word-final halant drawn before its consonant ("ఆదర్్శ" for ఆదర్శ్).
    v = re.sub('%s%s([%s])' % (VIRAMA, VIRAMA, CONS), VIRAMA + r'\1' + VIRAMA, v)
    # Symbols that only come from outline look-alikes (e.g. "శ్⌍వేతా"); ◌ / U+FFFD are kept so the
    # row is flagged for a check of the printed card.
    return ''.join(ch for ch in v if 'ఀ' <= ch <= '౿' or ch in ' .‌◌�')


def needs_check(text):
    """True when the printed text itself is damaged (orphan vowel sign / unreadable glyph)."""
    return bool(text) and ('◌' in text or '�' in text or text.startswith(tuple(MATRA)))
