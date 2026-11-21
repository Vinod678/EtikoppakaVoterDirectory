"""Readable Telugu -> English transliteration in the spelling style of the corrected wards.

Not a scholarly (ISO 15919) scheme: it follows the conventions of the corrected Ward 2 / 11
workbooks - "Lakshmi", "Nageswara Rao", "Appala Naidu", "Ramana", "Ratnam", "Sri". Known words are
taken from a dictionary built from the corrected wards (see build_dictionary); this module only
spells words that are not in it.
"""
import re

V = {'అ': 'a', 'ఆ': 'a', 'ఇ': 'i', 'ఈ': 'ee', 'ఉ': 'u', 'ఊ': 'oo', 'ఋ': 'ru', 'ౠ': 'roo', 'ఎ': 'e', 'ఏ': 'e',
     'ఐ': 'ai', 'ఒ': 'o', 'ఓ': 'o', 'ఔ': 'ou'}
M = {'ా': 'a', 'ి': 'i', 'ీ': 'ee', 'ు': 'u', 'ూ': 'oo', 'ృ': 'ru', 'ౄ': 'roo', 'ె': 'e', 'ే': 'e', 'ై': 'ai',
     'ొ': 'o', 'ో': 'o', 'ౌ': 'ou'}
C = {'క': 'k', 'ఖ': 'kh', 'గ': 'g', 'ఘ': 'gh', 'ఙ': 'n', 'చ': 'ch', 'ఛ': 'chh', 'జ': 'j', 'ఝ': 'jh', 'ఞ': 'n',
     'ట': 't', 'ఠ': 'th', 'డ': 'd', 'ఢ': 'dh', 'ణ': 'n', 'త': 't', 'థ': 'th', 'ద': 'd', 'ధ': 'dh', 'న': 'n',
     'ప': 'p', 'ఫ': 'ph', 'బ': 'b', 'భ': 'bh', 'మ': 'm', 'య': 'y', 'ర': 'r', 'ఱ': 'r', 'ల': 'l', 'ళ': 'l',
     'వ': 'v', 'శ': 's', 'ష': 'sh', 'స': 's', 'హ': 'h'}
VIRAMA, ANUSVARA, VISARGA = '్', 'ం', 'ః'
LABIAL = set('పఫబభమ')


NASAL_N = set('కఖగఘఙచఛజఝఞటఠడఢణతథదధన')   # anusvara before these sounds is "n", otherwise "m"


def translit_word(w):
    """One Telugu word -> lower-case English letters."""
    out = []
    i, n = 0, len(w)
    while i < n:
        ch = w[i]
        if ch in V:
            # PDF quirk: a vowel sign printed as an independent vowel mid-word (చీకటఅలా = చీకటాలా)
            if ch in 'అఆ' and i > 0 and out and out[-1] == 'a' and w[i - 1] in C:
                i += 1; continue
            out.append(V[ch]); i += 1
        elif ch in C:
            # consonant cluster: C (్ C)*
            cl = [ch]; j = i + 1
            while j + 1 < n and w[j] == VIRAMA and w[j + 1] in C:
                cl.append(w[j + 1]); j += 2
            final_dead = j < n and w[j] == VIRAMA and j + 1 == n
            if len(cl) == 2 and cl[0] == cl[1]:
                sound = C[cl[0]] * (2 if len(C[cl[0]]) == 1 else 1) if cl[0] not in 'చ' else 'chch'
            elif cl == ['క', 'ష']:
                sound = 'ksh'
            elif cl[:2] == ['జ', 'ఞ']:
                sound = 'gn'
            elif cl[:2] == ['శ', 'వ']:
                sound = 'sw' + ''.join(C[c] for c in cl[2:])
            elif cl[:2] == ['శ', 'ర']:
                sound = 'sr' + ''.join(C[c] for c in cl[2:])
            elif len(cl) == 1 and ch == 'త' and not final_dead:
                sound = 'th'          # Senapathi, Jyothi, Thota (single త before a vowel)
            elif len(cl) == 1 and ch in 'శష' and final_dead:
                sound = 'sh'          # Mahesh, Ganesh, Satish
            else:
                sound = ''.join(C[c] for c in cl)
            out.append(sound)
            if j < n and w[j] == VIRAMA:                  # dead consonant (halant)
                i = j + 1
            elif j < n and w[j] in M:
                m = M[w[j]]
                if m == 'ee' and (j + 1 < n or cl[:2] == ['శ', 'ర']):
                    m = 'i'           # Srinivasa, Sattibabu, Satish
                if m == 'oo':
                    m = 'u'           # Nukaraju, Suribabu, Kunduru
                if m == 'ru':
                    m = 'ri'          # Krishna
                out.append(m); i = j + 1
            else:
                out.append('a'); i = j
        elif ch == ANUSVARA:
            nxt = w[i + 1] if i + 1 < n else ''
            out.append('n' if nxt in NASAL_N else 'm'); i += 1
        elif ch == VISARGA:
            out.append('h'); i += 1
        else:
            i += 1
    s = ''.join(out)
    s = re.sub(r'ee$', 'i', s)       # లక్ష్మీ -> Lakshmi
    s = re.sub(r'oo$', 'u', s)       # బాబూ -> Babu
    return s


RAO = ('రావు', 'రావ')
NAIDU = ('నాయుడు', 'నాయిడు', 'నాయుడూ')


def split_word(w):
    """Splits the compound endings the corrected wards write separately: X+రావు -> "X Rao",
    X+నాయుడు -> "X Naidu"."""
    for suf, en in ((RAO, 'Rao'), (NAIDU, 'Naidu')):
        for s in suf:
            if w.endswith(s) and len(w) > len(s):
                return [w[: -len(s)], s]
    return [w]


def cap(s):
    return s[:1].upper() + s[1:] if s else s
