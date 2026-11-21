"""Exact Telugu name / relative / relationship of every card of a ward, from the PDF.

Usage (project root):  python tools/extract_telugu.py <pylib> <fonttools dir or -> <ward> [<ward> ...]
Writes build/te_ward<N>.json: {serial: {name, relative, label, page, check}}.
See tools/telugu_pdf_text.py for how conjunct glyphs (U+FFFD in the text layer) are recovered.
"""
import json, os, sys
sys.path.insert(0, sys.argv[1]); sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ward_config
from telugu_pdf_text import TeluguPdf, card_fields, needs_check

ft = None if sys.argv[2] == '-' else sys.argv[2]
os.makedirs('build', exist_ok=True)
docs = {}
for n in map(int, sys.argv[3:]):
    W = ward_config.ward(n)
    tp = docs.get(W['pdf']) or docs.setdefault(W['pdf'], TeluguPdf(W['pdf'], ft))
    out = {}
    for pno, p in enumerate(tp.doc):
        for w in ward_config.card_serials(p.get_text('words'), W['first'], W['last']):
            c = card_fields(tp, p, w)
            out[int(w[4])] = dict(name=c['name'], relative=c['relative'], label=c['label'], page=pno + 1,
                                  age=c['age'], gender=c['gender'], house=c['house'], epic=c['epic'],
                                  assembly=c['assembly'], lines=c['lines'],
                                  check=needs_check(c['name']) or needs_check(c['relative']) or not c['name'])
    exp = list(range(W['first'], W['last'] + 1))
    assert sorted(out) == exp, 'ward %d: cards missing %s' % (n, sorted(set(exp) - set(out))[:10])
    with open('build/te_ward%d.json' % n, 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print('ward', n, 'cards', len(out), 'no relative', sum(1 for v in out.values() if not v['relative']),
          'no label', sum(1 for v in out.values() if not v['label']), 'flagged', sum(v['check'] for v in out.values()),
          'unresolved glyphs so far', tp.unresolved)
