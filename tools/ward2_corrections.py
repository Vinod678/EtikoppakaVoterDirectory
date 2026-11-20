"""Ward 2: builds input/Etikoppaka_Ward_2_Corrected_English.xlsx from the first-pass workbook.

Usage (project root):  python tools/ward2_corrections.py <pylib> <first-pass.xlsx>

The first-pass workbook (same 7 columns as Ward 11) is never modified; a byte copy is kept in
output/ward_2/. Every change is written to output/ward_2/ward2_corrections_log.csv.

Corrections, all checked against the printed PDF cards:
  * Telugu: text-layer artefacts that duplicate a vowel sign / anusvara or a syllable after "ర్ర"
    (e.g. గుర్రంరం -> గుర్రం, కర్రిరి -> కర్రి, నాగరత్నంం -> నాగరత్నం, ఉద్దంండం -> ఉద్దండం).
    Nothing else in the Telugu text is changed.
  * Relationship: taken from the card label (తండ్రి/భర్త/తల్లి పేరు) when the card has one;
    "Not specified" (Ward 11 wording) when the card prints no label.
  * English: readable spellings in the Ward 11 style (e.g. "Nageswara Rao", "Tatabbayi",
    "Yerramsetti", "-setti"), one spelling per surname, names split/joined as printed.
  * Review Status: Ward 11 vocabulary - "Automated normalization — verify spelling", or
    "Check original PDF" for records that remain genuinely uncertain.
"""
import csv, os, re, shutil, sys, json
sys.path.insert(0, sys.argv[1]); sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fitz, openpyxl, ward_config

SRC = sys.argv[2]
W = ward_config.ward(2)
OUT = W['xlsx']
BACKUP = 'output/ward_2/Etikoppaka_Ward_2_first_pass_backup.xlsx'
LOG = 'output/ward_2/ward2_corrections_log.csv'
OK = 'Automated normalization — verify spelling'
CHECK = 'Check original PDF'
FFFD = '�'

# ---------------- Telugu artefacts ----------------
VS = 'ాిీుూెేొోౌంః'


def fix_te(t):
    if not t:
        return t
    s = t
    s = re.sub('ర్ర([%s]?)ర\\1' % VS, 'ర్ర\\1', s)          # గుర్రంరం, కర్రిరి, గుర్రరయ్య, బుర్రేరే
    s = re.sub('([%s])\\1' % VS, '\\1', s)                     # ంం, ీీ, ాా, ుు, ోో
    s = s.replace('ంండ', 'ండ')                                  # ఉద్దంండం
    s = s.replace('‍', '')                                 # stray ZWJ (దేముళ్‍ళు, ప్రకాశ్‍)
    s = s.replace('వీరమళ్శు', 'వీరమళ్ళు')                       # card prints వీరమళ్ళు
    return s


# ---------------- English ----------------
TOKENS = {
    # Rao / Naidu compounds (Ward 11: "Nageswara Rao")
    'SanyasiRao': 'Sanyasi Rao', 'SanyaashiRao': 'Sanyasi Rao', 'PrabhakaraRao': 'Prabhakara Rao',
    'DurgaaRao': 'Durga Rao', 'SubbaaRao': 'Subba Rao', 'EeshvaraRao': 'Eswara Rao', 'ChinnaaRao': 'Chinna Rao',
    'JagadheeshvaraRao': 'Jagadeeswara Rao', 'NageshvaraRao': 'Nageswara Rao', 'Narayanaravu': 'Narayana Rao',
    'Jogaravu': 'Joga Rao', 'Baburavu': 'Babu Rao', 'Babooravu': 'Babu Rao', 'Shankararaava': 'Shankara Rao',
    'Bhaaskararaava': 'Bhaskara Rao', 'Narasimgaraava': 'Narasinga Rao', 'Srinivasaraava': 'Srinivasa Rao',
    'Sanyasiraava': 'Sanyasi Rao', 'Ramachamdraraava': 'Ramachandra Rao', 'Narasimhraaaavu': 'Narasimha Rao',
    'Eshvarroro': 'Eswara Rao', 'Naageshvar': 'Nageswara', 'Eeshvara': 'Eswara', 'Bhaaskara': 'Bhaskara',
    'SomuNaidu': 'Somu Naidu', 'Ramunayudu': 'Ramu Naidu', 'Rajunaayadu': 'Raju Naidu', 'MaathiNaidu': 'Mathi Naidu',
    'Raminaaydu': 'Rami Naidu',
    # given names
    'NagaRatnam': 'Nagaratnam', 'NookaRatnam': 'Nookaratnam', 'NookaRatnama': 'Nookaratnam', 'Suryaprakaash': 'Suryaprakash',
    'Rambaabu': 'Rambabu', 'RamanaMurthy': 'Ramanamurthy', 'AppalaKonda': 'Appalakonda', 'Bhaargava': 'Bhargava',
    'AnAnthaLakshmi': 'Ananthalakshmi', 'Nagaiyaamma': 'Nagaiyamma', 'SeethaaRamayya': 'Seetharamayya',
    'Mamgayamma': 'Mangayamma', 'Leelaanjaali': 'Leelanjali', 'JayaSurya': 'Jayasurya', 'VenkatApparao': 'Venkatapparao',
    'Suryakumaari': 'Suryakumari', 'Raajeshvari': 'Rajeswari', 'Simhaachalam': 'Simhachalam', 'AppalaRaju': 'Appalaraju',
    'SuryaNarayana': 'Suryanarayana', 'KaAntham': 'Kantham', 'KaAnthamu': 'Kanthamu', 'Vemkayamma': 'Venkayamma',
    'DhanaLakshmi': 'Dhanalakshmi', 'AadiNarayana': 'Adinarayana', 'Appalaanarsa': 'Appalanarsa', 'Myoosiri': 'Myusiri',
    'Jhaagannadhaam': 'Jagannadham', 'Aachchibaabu': 'Achchibabu', 'Achchibaabu': 'Achchibabu', 'Eeshvaramma': 'Eswaramma',
    'Aneel': 'Anil', 'Chinaabaabu': 'Chinababu', 'Bhavanee': 'Bhavani', 'Maanikyamu': 'Manikyamu', 'Maanikyamm': 'Manikyam',
    'Samudraaaalu': 'Samudralu', 'Veeraasvaami': 'Veeraswamy', 'Paarvathee': 'Parvathi', 'Prabhaakar': 'Prabhakar',
    'Mauli': 'Mouli', 'Meresoo': 'Meresu', 'Abbaai': 'Abbai', 'Gangabhavaani': 'Gangabhavani', 'Sathyaprasaadu': 'Satyaprasadu',
    'Vaara': 'Vara', 'Musilee': 'Musili', 'AchchiRaju': 'Achchiraju', 'PedaApparao': 'Peda Apparao', 'Maheshvari': 'Maheswari',
    'Durgaprasaad': 'Durgaprasad', 'ShivaRama': 'Sivarama', 'Thaathaabbayi': 'Tatabbayi', 'Thaathabbaaya': 'Tatabbayi',
    'Taataabbaaee': 'Tatabbayi', 'Thaathaabbaayi': 'Tatabbayi', 'Aadiyya': 'Adiyya', 'DharmaRaju': 'Dharmaraju',
    'Saudhaamani': 'Soudhamani', 'Naagesha': 'Nagesha', 'Naagu': 'Nagu', 'Naagesh': 'Nagesh', 'Ramoolamma': 'Ramulamma',
    'Baabji': 'Babji', 'SathyaKrishna': 'Satyakrishna', 'SathyammKrishna': 'Satyakrishna', 'Kalaavathee': 'Kalavathi',
    'Sannibaabu': 'Sannibabu', 'Kondabbaayi': 'Kondabbayi', 'Govindua': 'Govinda', 'Saavithrii': 'Savithri',
    'Naagemdra': 'Nagendra', 'Veeraachaari': 'Veerachari', 'Bamdiya': 'Bandiya', 'Laavanya': 'Lavanya',
    'Thaathayyalu': 'Tatayyalu', 'Thaathaylu': 'Tatayyalu', 'Thaathylu': 'Tatayyalu', 'Lovacharee': 'Lovachari',
    'Bamdithalli': 'Banditalli', 'VenkataLakshmi': 'Venkatalakshmi', 'Kaavya': 'Kavya', 'Shrii': 'Sri',
    'Kumarabaabu': 'Kumarababu', 'MahaRaju': 'Maharaju', 'MahaaRaju': 'Maharaju', 'ChinaKonda': 'Chinakonda',
    'DaanaaLakshmi': 'Danalakshmi', 'Lovabheemadu': 'Lovabheemudu', 'Imdala': 'Indala', 'Imdali': 'Indali', 'Imdla': 'Indla',
    'Svaami': 'Swamy', 'VaraLakshmi': 'Varalakshmi', 'Satheesh': 'Satish', 'Prakaash': 'Prakash', 'Raayee': 'Rayi', 'Raaye': 'Rayi', 'Raayi': 'Rayi',
    'Raaya': 'Raya', 'Raay': 'Raya', 'Arjuna': 'Arjuna', 'Ammaaji': 'Ammaji', 'Aadibaabu': 'Adibabu', 'Aadilakshmi': 'Adilakshmi',
    'Kishtamma': 'Kishtamma', 'Chinnabbaayi': 'Chinnabbayi', 'Pedapothuraju': 'Pedapothuraju', 'Kalyaanam': 'Kalyanam',
    'Durgaalakshmi': 'Durgalakshmi', 'Harinee': 'Harini', 'Satyavathee': 'Satyavathi', 'Bandee': 'Bandi',
    'Chinapaapa': 'Chinapapa', 'Thaatha': 'Tata', 'Abbaayi': 'Abbayi', 'Prasaada': 'Prasada',
    # surnames (one spelling each)
    'Bandaaru': 'Bandaru', 'Vaasadula': 'Vasadula', 'Vaasaadula': 'Vasadula', 'GomthuMurthy': 'Gonthumurthy',
    'Gontumoorthee': 'Gonthumurthy', 'Sheshetti': 'Sesetti', 'Shesetti': 'Sesetti', 'Korameelee': 'Korameeli',
    'Kaalla': 'Kalla', 'Kaashepoo': 'Kasepu', 'Karriri': 'Karri', 'Ulaampakama': 'Ulampakama', 'Gurramm': 'Gurram',
    'Momdi': 'Mondi', 'Koththadamtla': 'Kothadantla', 'AppiKonda': 'Appikonda', 'Kolamti': 'Kolanti', 'Bamgaari': 'Bangari',
    'Shenaapathee': 'Senapathi', 'Saanapaththi': 'Senapathi', 'Yarramramshetti': 'Yerramsetti', 'Erramramshetti': 'Yerramsetti',
    'Yaramshettee': 'Yerramsetti', 'Yarramramsetti': 'Yerramsetti', 'Gamji': 'Ganji', 'Remji': 'Renji', 'Gemji': 'Genji',
    'Edla': 'Yedla', 'Vennammshettee': 'Vennamsetti', 'Veeramalshu': 'Veeramallu', 'Maagamti': 'Maganti',
    'Amjuree': 'Anjuri', 'Anjuree': 'Anjuri', 'Amjoori': 'Anjuri', 'Amjuri': 'Anjuri', 'Mogamti': 'Moganti',
    'Naanishetti': 'Nanisetti', 'Naaneesettee': 'Nanisetti', 'Pamdoori': 'Panduri', 'Maanepalli': 'Manepalli',
    'Kupina': 'Kuppina', 'Bomda': 'Bonda', 'Pothanasettee': 'Pothanasetti', 'Gurrarama': 'Gurram', 'Gurraramu': 'Gurram',
    'Shennammshetti': 'Sennamsetti', 'Senaaemesititiai': 'Sennamsetti', 'Sennaamsethee': 'Sennamsetti',
    'Seenamaseetti': 'Sennamsetti', 'Patnaala': 'Patnala', 'Pathanaala': 'Patnala', 'Jagoo': 'Jagu', 'Jaagu': 'Jagu',
    'Burrere': 'Burre', 'Boorare': 'Burare', 'Uddandamu': 'Uddandam', 'Karanamu': 'Karanam', 'Koththala': 'Kottala',
    'Kottali': 'Kottali', 'Kothvaal': 'Kotwal', 'Shilaparashetti': 'Silaparasetti', 'Thoorubatla': 'Thurubatla',
    'Thruubatla': 'Thurubatla', 'Mailaarapoo': 'Mylarapu', 'Pamchaadaarla': 'Panchadarla', 'Kotaavaala': 'Kotavala',
    'Budha': 'Buddha', 'Bheesetti': 'Bheesetti',
}

# Whole-field English values where token mapping is not enough (index: 1 = voter, 3 = relative).
FIELDS = {
    (382, 3): 'Rama Rao', (385, 3): 'Pedapothuraju', (390, 1): 'Ayyappa Gupta Garuda', (393, 3): 'Narasimha Murthy',
    (397, 3): 'Narayana Rao', (414, 3): 'Govind Budda', (417, 1): 'Yallapu Raju Naidu', (418, 1): 'Yallapu Adilakshmi',
    (418, 3): 'Raju Naidu Yallapu', (429, 1): 'Bandayya Buddha', (434, 3): 'Eswara Rao Kalla', (436, 3): 'Ramu Naidu Kalla',
    (440, 1): 'Nookaratnam Kasepu', (441, 1): 'Karri Santosh Kumari Kumari', (441, 3): 'Husband Apparao',
    (444, 3): 'Somi Naidu', (448, 3): 'Srinu Balla', (454, 3): 'Appanna (Late)', (457, 1): 'Nageswara Rao Senapathi',
    (459, 1): 'Govinda Rao Senapathi', (460, 1): 'Govinda Rajulu Vegi', (461, 3): 'Govinda Rajulu Vegi',
    (462, 3): 'Govindarajulu Vegi Vegi', (463, 3): 'Govinda Rajulu Vegi', (464, 1): 'Nageswara Rao Senapathi',
    (465, 1): 'Varalakshmi Senapathi', (466, 3): 'Nageswara Rao Senapathi', (472, 1): 'Kumarababu Bheemisetti',
    (472, 3): 'Jagadeeswara Rao Bheemisetti', (473, 3): 'Suribabu Senapathi', (478, 1): 'Appala Ratnam Karri',
    (480, 1): 'Venkata Lakshmi Annam', (486, 1): 'Kavya Sri Pilli', (487, 3): 'Venkata Rao', (488, 1): 'Venkata Rao Kasireddy',
    (495, 1): 'Chinapal Narsa Thanuku', (498, 1): 'Lakshmi Gurram', (498, 3): 'Chiranjeevi Gurram',
    (503, 3): 'Appalakonda', (513, 1): 'Dhanalakshmi Kandrakota', (551, 3): 'Appala Naidu', (552, 3): 'Appala Naidu',
    (555, 1): 'Prabhu Kiran Yerramsetti', (555, 3): 'Srinivasu Yerramsetti', (556, 3): 'Srinivasu Yerramsetti',
    (557, 1): 'Mohana Bhoomika Yerramsetti', (557, 3): 'Srinivasu Yerramsetti', (554, 3): 'Srinivasu',
    (561, 1): 'Adilamma Genji', (562, 3): 'Apparao Yedla', (566, 3): 'Samudralu', (567, 1): 'Prasada Setti',
    (567, 3): 'Samudralu Setti', (571, 1): 'Surya Rao Nakkina', (572, 3): 'Surya Rao', (573, 3): 'Surya Rao',
    (575, 3): 'Appala Krishna Surya Narayana Rayi', (577, 1): 'Meresu Vennamsetti', (577, 3): 'China Abbai',
    (579, 3): 'Varaha Satyaprasadu', (581, 1): 'Satya Vara Prasad Veeramalla', (582, 3): 'Babu Rao',
    (585, 3): 'Subba Rao Moganti', (589, 3): 'Musili Naidu Rayi', (590, 1): 'Musili Rayi', (590, 3): 'Gurrayya',
    (596, 3): 'Gurrayya Raya', (601, 3): 'Gurrayya', (603, 1): 'Appalakrishna Suryanarayana Rayi', (604, 3): 'Appala Krishna',
    (605, 1): 'Apparao Nanisetti', (607, 1): 'Sattibabu Nanisetti', (615, 3): 'Gurram Appalanarasa',
    (619, 1): 'Tatabbayi Rayi', (622, 1): 'Narasimha Rao Rayi', (622, 3): 'Tatabbayi Rayi', (623, 3): 'Konda',
    (629, 1): 'Apparao Gurram', (630, 1): 'Appalanarsa Gurram', (631, 1): 'Bhaskara Rao Senapathi',
    (639, 1): 'Shyamala Sennamsetti', (639, 3): 'Nageswara Rao Sennamsetti', (640, 1): 'Sennamsetti Vasu',
    (640, 3): 'Sennamsetti', (642, 1): 'Sennamsetti Govindu', (642, 3): 'Sennamsetti Kondayya',
    (644, 3): 'Babuji Sennamsetti', (645, 1): 'Narasinga Rao Senapathi', (649, 1): 'Ramulamma Sennamsetti',
    (651, 1): 'Vara Lakshmi Sennamsetti', (653, 3): 'Ganesh Yerramsetti', (654, 1): 'Indala Bhavani Shankar',
    (654, 3): 'Indala Srinu', (657, 1): 'Babji Sennamsetti', (659, 3): 'Satyakrishna', (665, 1): 'Naripireddi Srinu',
    (665, 3): 'Naripireddi Varahalu', (666, 3): 'Sanyasi Rao Patnala', (667, 1): 'Satyanarayana Patnala',
    (668, 3): 'Kondabbayi Yerramsetti', (669, 3): 'Kondabbayi Yerramsetti', (672, 3): 'Rami Naidu Vommi',
    (683, 1): 'Ramachandra Rao Anjuri', (687, 1): 'Tatabbayi Yerramsetti', (689, 1): 'Yerramsetti Ganesh',
    (689, 3): 'Tatabbayi', (694, 3): 'Rayi Nagendra', (695, 1): 'Vijayalakshmi Senapathi', (695, 3): 'Kannayya Senapathi',
    (697, 1): 'Satyavathi Kottali', (698, 1): 'Kottala Nageswara Rao', (699, 1): 'Kottala Mahalakshmi',
    (700, 1): 'Eswara Rao Karri', (700, 3): 'Tatabbayi', (701, 3): 'Bandiya Rayi', (706, 1): 'Nagendra Rayi',
    (706, 3): 'Bandiya Rayi', (710, 1): 'Tatayyalu Thurubatla', (710, 3): 'Lova Chari Thurubatla',
    (713, 1): 'Tatayyalu Thurubatla', (715, 1): 'Babu Rao Karanam', (717, 3): 'Babu Rao', (721, 3): 'Venkata Apparao',
    (726, 3): 'Venkata Prakasa Rao', (728, 1): 'Burare Swamy', (728, 3): 'Burare Nukaraju',
    (737, 1): 'Sennamsetti Pushpalatha', (737, 3): 'Sennamsetti Srinu', (738, 1): 'Babuji Sennamsetti',
    (382, 1): 'Nagaratnam Katakam', (384, 1): 'Nookaratnam Thota', (411, 3): 'Mahesh Bheesetti',
    (430, 1): 'Satyavathi Buddha', (449, 1): 'Naga Lakshmi Kalla', (520, 3): 'Bandayya',
}

# Card-verified relative name missing from the first pass (575: printed over two lines).
FILL_TE = {575: 'అప్పల కృష్ణ సూర్య నారాయణ రాయి'}

# Records that stay uncertain -> "Check original PDF" (reason goes to the log).
UNCERTAIN = {
    372: 'Name printed "అపల్ నర్స తళా" (unusual); spelling kept as printed',
    441: 'Relative printed "హస్బెండ్ అప్పారావు" (the word "husband" is part of the printed name); "కుమారి" printed twice',
    414: 'Surname printed బుడ్డ (Budda); may be the same family as Buddha',
    429: 'Surname printed బుద్ద; written Buddha like the other బుద్ధ records',
    430: 'Surname printed బుధ; written Buddha like the other బుద్ధ records',
    434: 'Relative printed "ఏశ్వర్రోరో" (garbled); written Eswara Rao',
    454: 'Relative printed "అప్పన్న (లేటు)" = Appanna (late)',
    462: 'Relative printed with the surname twice ("వేగి వేగి")',
    517: 'Card prints no relationship label; relative "Appalanarsa" is usually a woman\'s name',
    560: 'Surname printed రెంజి; father Manikyam suggests the Ganji family (559)',
    561: 'Card prints no relationship label; surname printed గేంజి (Ganji family?)',
    567: 'Surname printed "సేట్టీ"; father Samudralu suggests the Sesetti family (565/566)',
    575: 'Relative name was missing; copied from the card (printed over two lines)',
    578: 'Surname printed వీరమళ్ళు; husband is likely Prabhakara Rao Veeramalla (581)',
    579: 'Surname printed వీరమళ్ళు; husband is likely Varaha Satya Prasad Veeramalla (576)',
    623: 'Card prints no relationship label',
    505: 'Card prints no relationship label (relative Appalanarsa Thanuku)',
    635: 'Card prints no relationship label (relative Nukaraju)',
    625: 'Surname printed బదిరెడ్డి; may be Buddireddi (624, same father name Apparao)',
    629: 'Surname printed గుర్రమ; written Gurram',
    639: 'Surname printed "సేణం శేటి"; written Sennamsetti', 640: 'Surname printed "షేన్ణంశెట్టి"; written Sennamsetti',
    642: 'Surname printed "సేనం శెట్టి"; written Sennamsetti',
    643: 'Surname printed in English letters "సెనాఎమ్ఎస్ఇటిటిఐ" (S-E-N-A-M-S-E-T-T-I); written Sennamsetti',
    644: 'Surname printed "సేన్నామ్సేతీ"; written Sennamsetti', 649: 'Surname printed "శేనాం సేట్టీ"; written Sennamsetti',
    651: 'Surname printed "శేనాం సేట్టీ"; written Sennamsetti', 737: 'Surname printed "సీనమసీట్టి" / "సీనామేశ్ఎట్టి"; written Sennamsetti',
    667: 'Surname printed పతనాలా; wife 662 is Patnala',
    678: 'Card prints no relationship label', 689: 'Surname printed "య రం శేట్టీ"; written Yerramsetti',
    695: 'Surname printed సానపత్తి; written Senapathi',
    710: 'Name printed "తాతయ్లు"; written Tatayyalu', 713: 'Name printed "తాత్య్లు త్రుబట్ల"; written Tatayyalu Thurubatla',
    720: 'Surname printed మైలారపూ (Mylarapu); may be the Mylaru family', 728: 'Surname printed బూరరే (Burare)',
    731: 'Card prints no relationship label',
}


def fix_en(serial, idx, v):
    if (serial, idx) in FIELDS:
        return FIELDS[(serial, idx)]
    if not v:
        return v
    v = v.replace('‌', '').replace('‍', '')
    return ' '.join(TOKENS.get(w, w) for w in v.split())


# ---------------- PDF relationship labels ----------------
REL = {'తం' + FFFD + 'డి పేరు': 'Father', 'భర' + FFFD + ' పేరు': 'Husband', 'తలి' + FFFD + ' పేరు': 'Mother'}
labels = {}
for p in fitz.open(W['pdf']):
    for w in ward_config.card_serials(p.get_text('words'), W['first'], W['last']):
        card = fitz.Rect(w[0] - 62, w[1] - 6, w[0] + 122, w[1] + 74)
        lines = [''.join(s['text'] for s in l['spans']).strip()
                 for b in p.get_text('dict', clip=card)['blocks'] for l in b.get('lines', [])]
        found = [REL[l] for l in lines if l in REL]
        labels[int(w[4])] = found[0] if found else None

os.makedirs(os.path.dirname(BACKUP), exist_ok=True)
if not os.path.exists(BACKUP):
    shutil.copyfile(SRC, BACKUP)
wb = openpyxl.load_workbook(BACKUP)  # always start from the untouched first pass (re-runnable)
ws = wb.active
head = [c.value for c in ws[1]]
assert head == ['GP Voter Serial No.', 'Voter Name (English)', 'Relationship', 'Relative Name (English)',
                'Voter Name (Telugu)', 'Relative Name (Telugu)', 'Review Status'], head
log = []
for row in ws.iter_rows(min_row=2):
    s = int(row[0].value)
    new = [c.value for c in row]
    new[1] = fix_en(s, 1, new[1]); new[3] = fix_en(s, 3, new[3])
    new[4] = fix_te(new[4]); new[5] = fix_te(new[5])
    if s in FILL_TE and not new[5]:
        new[5] = FILL_TE[s]
    lab = labels[s]
    new[2] = lab if lab else 'Not specified'
    new[6] = CHECK if s in UNCERTAIN else OK
    for i, c in enumerate(row):
        if c.value != new[i]:
            log.append([s, head[i], c.value, new[i], UNCERTAIN.get(s, '') if i == 6 else ''])
            c.value = new[i]
wb.save(OUT)
with open(LOG, 'w', newline='', encoding='utf-8-sig') as f:
    wr = csv.writer(f); wr.writerow(['GP Serial No.', 'Column', 'First-pass value', 'Corrected value', 'Note'])
    wr.writerows(log)
print('changes', len(log), 'records changed', len({x[0] for x in log}), 'flagged', len(UNCERTAIN))
print('labels: none on card', sorted(k for k, v in labels.items() if not v))
