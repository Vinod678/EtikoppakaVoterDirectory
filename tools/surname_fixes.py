"""Family-surname corrections: one spelling per family, so the app groups the family into one household.

Usage (project root):
    python tools/surname_fixes.py <pylib> preview [<ward> ...]     print every change, write nothing
    python tools/surname_fixes.py <pylib> apply   [<ward> ...]     edit input/Etikoppaka_Ward_N_Corrected_English.xlsx
then, per changed ward: xlsx_dump -> build_dataset -> sync_assets (see tools/README.md).

Run it again after make_ward_workbooks.py / ward2_corrections.py regenerate a workbook; it is idempotent
(already-corrected records are left alone).

Why: the app's household grouping (HouseholdGrouper) treats "same surname = same family". The printed roll
often spells one family's surname differently from card to card ("మొగంటి" / "మాగంటి", "గంజి" / "గెంజి" /
"రెంజి", "బూరరే" / "బుర్రె"), and the English then differed too, so those voters ended up in a
"Household - House x-y" group or alone. Each rule below was checked by hand against the other members of the
family: same house (or the next house), and a matching father / husband / mother name or age pattern.

A rule: (canonical English, canonical Telugu, English variants, Telugu variants, serials, reason).
For each listed serial, every variant word in the voter name and in the relative name is replaced by the
canonical spelling (English and Telugu separately); given names are never touched. A listed serial that
contains none of the variants (and is not already canonical) stops the run, so a rule cannot silently
change the wrong record. Every change is logged to output/surname_fixes_log.csv.
"""
import csv, os, re, sys
sys.path.insert(0, sys.argv[1]); sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import openpyxl
import ward_config

LOG = 'output/surname_fixes_log.csv'
HEAD = ['GP Voter Serial No.', 'Voter Name (English)', 'Relationship', 'Relative Name (English)',
        'Voter Name (Telugu)', 'Relative Name (Telugu)', 'Review Status']

R = {
    1: [
        ('Seeram', 'సీరం', ['Sheeram'], [], [161], 'house 1-44: Kumari and her father Ramana; spelling confirmed by the user (as in Ward 2)'),
        ('Kasinkota', 'కసింకోట', ['Kosimkota'], ['కొసిమ్కొటా'], [4], 'same house 1-1 as Suribabu Kasinkota; Kasimkota spelt with o'),
        ('Appikonda', 'అప్పికొండ', ['Apekonda'], ['అపేకోంద'], [127], 'wife of Ramu Appikonda (daughter Anita Appikonda, father Ramu Appikonda)'),
        ('Korimilli', 'కోరిమిల్లి', ['Korimila', 'Korumilli', 'Korameeli', 'Koremili', 'Karumilli', 'Korimoli'],
         ['కోరిమీల', 'కోరుమిల్లి', 'కోరమీలీ', 'కొరేమీలీ', 'కరుమిల్లి', 'కొరిమోలి'],
         [61, 129, 155, 173, 181, 182, 190, 191, 240, 242, 367],
         'one family spelt five ways: brothers Avinash Korameeli / Ramakishor Korimilli (father Prabhakara Rao), '
         'Maheshu Korameeli / Varahalamma Korimilli (Peda Appalakonda), Kalavati Korumilli / Chinna Ramana Koremili '
         '(China Abbayi), Jagadisvara Rao Karumilli = "Jagadeeswara Rao Korameeli" on card 191'),
        ('Yallapu', 'యల్లపు', ['Ellapu', 'Yelapu'], ['ఏల్లపు', 'యేలాపూ'], [47, 163, 167, 232, 336],
         'siblings Jayalakshmi Ellapu / Naga Nuka Mahesh Yallapu (Chandra Rao), Manikantha Ellapu / Koumalika Yallapu '
         '(Nageswara Rao, house 1-45), Janaki Ellapu wife of Veeru Naidu Yallapu'),
        ('Buddha', 'బుద్ద', ['Buddu'], ['బుద్దు'], [150], 'same house 1-41 as Ramana Buddha'),
        ('Vijjapu', 'విజ్జపు', ['Veejjupu'], ['వీజ్జుపూ', 'విజ్జుపు'], [263, 264, 265, 328, 329, 330],
         'one Vijjapu family (Vani, wife of Shankar Naresh; Varaprasad; Mahesh; Lakshmi, wife of Srinu), printed '
         'Veejjupu / Vijjapu; spelling Vijjapu confirmed by the user, 2026-11-22'),
        ('Boddeti', 'బొడ్డేటి', ['Boddati'], ['బొద్దటి'], [291], 'son of Ramana Boddeti (same house 1-76)'),
        ('Mulampaka', 'ములంపాక', ['Malampaka'], ['మలంపాక'], [225], 'son of Gangayyamma Mulampaka (husband Demullu), house 1-57'),
        ('Uddandam', 'ఉద్దండం', ['Udandama', 'Udanda', 'Uddandamu'], ['ఉడందమ', 'ఉడండా', 'ఉద్దండాము', 'ఉడంద'], [140, 156, 157],
         'Bujji Kondababu Udanda and his wife Durgadevi Uddandamu (house 1-43); Uddandam family'),
        ('Panchadarla', 'పంచదార్ల', ['Panchdarla', 'Panchadala', 'Panchadarala'], ['పంచ్దర్లా', 'పంచదల', 'పంచదరల', 'పంచదారల', 'పంచాదరాల', 'పంచాదారల'],
         None, 'Panchadarla family (Nukaraju Panchadarla is the father on cards 105 and 107)'),
        ('Addanam', 'అడ్డాణం', ['Addanamu'], ['అడ్డానము'], [306], 'same house 1-81 as the Addanam family'),
        ('Annam', None, ['Annao'], ['అన్నఒ'], [363], 'same house 1-98 as the Annam family ("అన్నఒ" is a misprint)'),
        ('Kurandasu', None, ['Kuraradasu'], ['కూరరదాసూ'], [268], 'same house 1-73 as the Kurandasu family'),
    ],
    2: [
        ('Vijjapu', 'విజ్జపు', ['Veejjupu'], ['వీజ్జుపూ', 'విజ్జుపు'], [391, 392, 393],
         'Narasimha Murthy Vijjapu, his wife Satyavathi and son Srinu (the Ward 1 Vijjapu family); spelling confirmed by the user, 2026-11-22'),
        ('Veeramallu', 'వీరమళ్ళు', ['Veeramalla'], ['వీరమల్ల'], [576, 581, 578, 579],
         'houses 2-12/1, 2-13: one family (Prabhakara Rao, Varaha Satya Prasad); spelling confirmed by the user'),
        ('Kothwala', 'కొత్వాల', ['Kotavala', 'Kotwal', 'Kothvala'], ['కోటావాల', 'కొత్వాల్'], [467, 705], 'spelling confirmed by the user'),
        ('Seeram', 'సీరం', ['Sheeram'], ['శీరం'], [421, 422], 'house 1-125: Ramana and his wife Lakshmi; spelling confirmed by the user'),
        ('Buddireddi', 'బుద్దిరెడ్డి', ['Badireddi'], ['బదిరెడ్డి'], [625], 'house 2-27: son of Lakshmi Buddireddi; spelling confirmed by the user'),
        ('Maganti', 'మాగంటి', ['Moganti'], ['మొగంటి'], [585, 586], 'house 2-14: Lakshmi Maganti, her husband Babu Rao and daughter Sudha'),
        ('Genji', 'గెంజి', ['Ganji', 'Renji'], ['గంజి', 'రెంజి', 'గేంజి'], [559, 560, 561],
         'house 2-5: Lakshmi (husband Manikyam), son Srinivasa Rao (father Manikyam), Adilamma (relative Lakshmi Genji)'),
        ('Burre', 'బుర్రె', ['Burare'], ['బూరరే', 'బుర్రే'], [680, 681, 728, 731, 732],
         'one Burre family (houses 2-38 to 2-50); Swamy and his father Nukaraju were printed "Burare"'),
        ('Sesetti', 'సేసెట్టి', ['Setti'], ['సేట్టీ'], [567], 'son of Samudralu Sesetti, same house 2-8'),
        ('Indali', 'ఇందలి', ['Indala', 'Indla'], ['ఇందల', 'ఇండ్ల'], [654, 677, 679],
         'house 2-37: Lakshmi Indala wife of Sattibabu Indali, Prasad Indla their son; Bhavani Shankar son of Srinu Indali'),
        ('Kottala', 'కొత్తల', ['Kottali'], ['కొత్తలి'], [697], 'mother of Kottala Nageswara Rao (both list Veerachari), house 2-41'),
        ('Mylaru', 'మైలారు', ['Mylarapu'], ['మైలారపూ'], [720], 'same house 2-47 as the Mylaru family'),
        ('Moolapaka', 'మూలేమపాక', ['Ulampakama'], ['ఉలామ్పకమ'], [491], 'house 1-146: wife of Hari Prasad Moolapaka, brother of Krishna Moolapaka'),
        ('Rayi', 'రాయి', ['Raya', 'Raye'], ['రాయ', 'రాయ్', 'రయే'], None,
         'one surname printed రాయి / రాయ / రాయ్ / రయి (e.g. Devi Rayi, father Appala Naidu Rayi, whose own card reads Raya)'),
    ],
    3: [
        ('Kothali', 'కొత్తలి', ['Kottali'], [], [984], 'spelling Kothali (not Kottali) confirmed by the user, 2026-11-22'),
        ('Kothwala', 'కొత్వాల', ['Kotwal', 'Kothvala'], ['కోఠవాల్', 'కోత్యాల', 'కోత్వాల', 'కొఠవాల', 'కోవ్వాల', 'కొత్తువాల', 'కొత్తువాలు'], None,
         'one Kothwala family (houses 2-69 to 2-125); spelling confirmed by the user'),
        ('Kriyadula', 'క్రియాదుల', ['Kiryadula'], ['కిర్యడుల'], [850], 'son of Ramu (husband of China Venkatalakshmi Kriyadula)'),
        ('Earla', 'ఈర్ల', ['Veerla', 'Eerla', 'Irla'], ['వీర్ల', 'ఇర్ల'], [864, 977, 978],
         'Earla family (confirmed by the user): Achiyamma and her son Srinu (house 2-140), Ponika (house 2-100/1)'),
        ('Appikonda', 'అప్పికొండ', ['Appalakonda'], ['అప్పలకోండ'], [1059], 'house 3-5 of Durga Rao Appikonda ("Appalakonda" is a given name)'),
        ('Rayi', 'రాయి', ['Raya', 'Raye'], ['రాయ', 'రాయ్', 'రయే'], None,
         'one surname printed రాయి / రాయ / రాయ్ / రయి (e.g. Devi Rayi, father Appala Naidu Rayi, whose own card reads Raya)'),
    ],
    4: [
        ('Kothwala', 'కొత్వాల', ['Kotwal'], ['కొఠవాలా', 'కోఠవాల్', 'కొథఈలా'], None, 'house 3-16; spelling confirmed by the user (as in Ward 3)'),
        ('Odaboyina', 'ఓడబోయిన', ['Vadaboyina'], ['వడబోయిన', 'వాడబోయిన', 'ఒడిబోయిన'], [1312, 1313, 1352],
         'Shankara Rao (wife Durga Devi) and Arjuna, son of Nukaraju and Appalaraju Odaboyina'),
        ('Dara', 'దారా', ['Dana'], ['దానా'], [1127], 'husband of Nooka Ratnam Dara (husband Sanyasi Rao), house 3-18'),
        ('Rayi', 'రాయి', ['Raya'], ['రాయ'], [1409], 'daughter of Satyanarayana Rayi (house 3-95 / 3-97)'),
    ],
    5: [
        ('Kothwala', 'కొత్వాల', ['Kotwal'], ['కోఠవాల్'], None, 'house 3-121/1; spelling confirmed by the user (as in Ward 3)'),
        ('Gantim', 'గంటిం', ['Gantem'], ['గంటెం'], [1506], 'husband of Manga Gantim, house 3-116'),
        ('Kanaparti', 'కనపర్తి', ['Kanaparhi', 'Kinaparthi'], ['కనపర్హీ', 'కినపార్థి'], [1522, 1533],
         'wives of Venkata Rao and Nageswara Rao Kanaparti (houses 3-121, 3-124)'),
        ('Bodda', 'బొడ్డా', ['Bonda'], ['బొండా'], [1581], 'wife of Srinivasu Bodda, house 3-139'),
        ('Senapathi', 'శానాపతి', ['Sanapatha'], ['సానపాత', 'సానపత్తి'], [1608], 'Kasu, husband of Peda Manga Senapathi, father of Srinu Senapathi'),
        ('Sennamsetti', 'శెన్నంశెట్టి', ['Sanamasetti'], ['సనమసేట్టీ'], [1651], 'son of Nageswara Rao Sennamsetti, house 4-16'),
        ('Shrigakolapu', 'శ్రిగకొలపు', ['Shregakollapu', 'Shrikakulam'], ['శ్రెగాకోళ్లపు', 'శ్రీకాకుళం'], [1624, 1625],
         'house 4-8/4-9: family of Ramachandra Rao Shrigakolapu'),
        ('Jagu', 'జాగు', ['Jegu', 'Jogu'], ['జేగు', 'జోగు'], [1633, 1708],
         'mother (husband Venkata Ramana) and wife of Ramachandra Rao Jagu'),
        ('Padilam', 'పదిలం', ['Padilama', 'Padilamu'], ['పదిలమ', 'పదిలము'], [1655, 1685, 1687],
         'Narasimha Murthy Padilam (father of Kesava Rao Padilam, house 4-25), his wife and daughter'),
        ('Pedapati', 'పెదపాటి', ['Padapati', 'Pdepati'], ['పడాపతి', 'ప్డేపాటి'], [1684], 'wife of Prasadu Pedapati, house 4-24'),
        ('Silaparasetti', 'శిలపరశెట్టి', ['Selaparasetti'], ['సేలాపారసేట్టీ'], [1689], 'one surname printed two ways'),
        ('Korimilli', 'కోరిమిల్లి', ['Korumilli'], ['కోరుమిల్లి'], [1722], 'Chinappalakonda, husband of Ammaji and father of Babuji Korimilli'),
        ('Rayi', 'రాయి', ['Raya', 'Raye'], ['రాయ', 'రాయ్', 'రయే'], None,
         'one surname printed రాయి / రాయ / రాయ్ / రయి (e.g. Devi Rayi, father Appala Naidu Rayi, whose own card reads Raya)'),
        ('Amidi', 'అమిడి', ['Amadi'], ['అమడి', 'అమా డీ'], [1544, 1555], 'Amidi family of houses 3-126 / 3-129 (Nageswara Rao is the son of Veeraswamy Amidi)'),
        ('Bangari', 'బంగారి', ['Bangaru'], ['బంగారు'], [1479, 1480], 'Ramu, husband of Ratnam Bangari, and his brother, house 3-109'),
        ('Nanisetti', 'నానిశెట్టి', ['Vanisetti'], ['వానీసేట్టీ'], [1771], 'same house 4-57 as the Nanisetti family'),
        ('Ainavilli', 'ఐనవిల్లి', ['Ainavalli', 'Ayinavilli', 'Ayinavalli'], ['ఐనవల్లి', 'అయినవిల్లి', 'ఆయినవల్లి'], [1786, 1788, 1789],
         'house 4-65: Ganeswara Rao, his mother (husband Apparao) and his wife Sudha Rani'),
        ('Gonthumurthy', None, ['Gontumurati', 'Gonthimurti'], ['గోన్టుమూరటీ', 'గోన్ఠిమూర్తీ'], [1810, 1813], 'Gonthumurthy family, houses 4-72/4-73'),
    ],
    6: [
        ('Kothali', 'కొత్తలి', ['Kottali'], ['కొథళి', 'కొథఅలి', 'కొట్టాలి'], [2001, 2004, 2006, 2007],
         'house 4-133: Ramana Kothali, his mother Narayanamma (husband Bhuloka) and children; '
         'spelling Kothali confirmed by the user, 2026-11-22'),
        ('Medeti', 'మెదేటి', ['Madetee'], ['మడేటీ'], [2113], 'house 5-5; spelling confirmed by the user (as in Wards 11 and 12)'),
        ('Sontena', 'సొంటేన', ['Sonthyana'], ['శొంఠ్యాన'], [1862], 'brother of Sontena Eswara Rao (father Konda), house 4-93'),
        ('Mogili', 'మొగిలి', ['Mogali'], ['మొగలి'], [1872], 'wife of Mogili Narayana, house 4-95'),
        ('Gonthumurthy', None, ['Gonthumurti', 'Gonthumu', 'Gontumurati'], ['గొన్ఠుమూర్తి', 'గొంతుమూీీీీై', 'గొంతుమూీీీీై', 'గోన్టుమూరటీ'], [1877, 2174, 1943, 2103],
         'Gonthumurthy family ("గొంతుమూీీీీై" is a damaged print)'),
        ('Sennamsetti', 'శెన్నంశెట్టి', ['Sennamasetti'], ['సేన్నామాసెట్టి', 'సేన్నామసెట్టి', 'సేనంసెట్టి'], [1899, 1901],
         'Ganapathi and his wife Revathi, house 4-105'),
        ('Potnuri', 'పొట్నూరి', ['Potnuru'], ['పోట్నురూ'], [2024], 'same house 4-139 as Paidithalli Potnuri'),
        ('Gajjilapu', 'గజ్జిలపు', ['Gajjalapu'], ['గజ్జలపు', 'గజ్జిలాపూ'], [2042, 2043], 'mother of Santosha Kumara Gajjilapu (Apparao), house 4-146'),
        ('Sunkara', 'సుంకర', ['Sankara'], ['సంకర', 'సంకరా'], [2097], 'son of Kanna Rao and Nagamani Sunkara, house 4-177'),
        ('Polimera', 'పొలిమేర', ['Polamera'], ['పొలమేరా'], [2144], 'same house 5-12 as the Polimera family'),
        ('Annam', 'అన్నం', ['Annama'], ['అన్నమ'], None, 'Annam / Annama share houses 4-110 to 4-129 and fathers (one family)'),
        ('Karanam', None, ['Karnam', 'Karanama'], ['కర్ణమ్', 'కరానామ'], [1988, 1989, 1994, 1996], 'brothers Appalaraju / Raghubabu (father Venkataramana), houses 4-131 / 4-132'),
        ('Devarapu', 'దేవరపు', ['Deva Varapu'], ['దేవా వరపు'], [2022], 'Babu Rao, father of Rajesh Devarapu, house 4-138/A'),
    ],
    7: [
        ('Sakireddi', 'సకిరెడ్డి', ['Sagiraeddi'], ['సగీరఏడ్డీ'], [2227], 'husband of Satyavathi Sakireddi, house 5-39'),
        ('Dokul', 'డొకుల్', ['Dokula'], ['డోకుల'], [2240], 'Venkata Rao Dokul (wife Ramani, son Srinivas Rao), house 5-44'),
        ('Balji', 'బల్జి', ['Balija'], ['బాలిజా'], [2259], 'son of Kalyanam Balji, house 5-52'),
        ('Akina', 'అకిన', ['Akin'], ['అకిన్'], [2338], 'son of Kalyanam Akina'),
        ('Isarapu', 'ఇసరపు', ['Inrepu', 'Inarapu'], ['ఇన్రేపూ', 'ఇనరపు'], [2375, 2377, 2383],
         'house 5-106/107: wife of Rajulu and wives of his sons Gangaraju and Rajibabu Isarapu'),
        ('Burre', 'బుర్రె', ['Burri'], ['బుర్రి', 'బుర్రే'], [2379, 2380], 'sisters, both daughters of Ramana Burre, house 5-106'),
        ('Madagala', 'మడగల', ['Madgala', 'Medagala'], ['మద్గఅలా', 'మేడగఅలా'], [2465, 2511],
         'Bandayya (wife Appalanarasa Madagala) and his son Joga Rao'),
        ('Lokanadham', 'లోకనాధం', ['Lokanatham'], ['లోకనాథం'], [2477], 'wife of Siva Lokanadham, house 5-133'),
        ('Nagireddi', 'నాగిరెడ్డి', ['Nagioreddi', 'Najireddi'], ['నాగిఓరెడ్డి', 'నాజీరేడ్డీ'], [2484, 2508], 'misprints of Nagireddi'),
        ('Bogudu', 'బొగుడు', ['Bodugu'], ['బొడుగు'], [2517], 'wife of Appalanaydu Bogudu, house 5-148'),
        ('Genji', 'గెంజి', ['Ganji'], ['గంజి'], [2450, 2451], 'children of Govindu and Jyothi Genji, house 5-127'),
        ('Annam', 'అన్నం', ['Annama'], ['అన్నమ'], None, 'Annam / Annama share houses 5-75, 5-149, 6-15 (one family)'),
        ('Gantem', 'గంటెం', ['Gantema'], ['గంటేమ'], [2557, 2558, 2559], 'children of Appalaraju Gantem (houses 6-11 / 6-13)'),
        ('Korukonda', None, ['Korukundi'], ['కోరుకుండి'], [2363, 2364], 'Naga Lakshmi is the wife of Shesu Korukonda (father Ramana); Raju his brother'),
        ('Rayi', 'రాయి', ['Raya', 'Raye'], ['రాయ', 'రాయ్', 'రయే'], None,
         'one surname printed రాయి / రాయ / రాయ్ / రయి (e.g. Devi Rayi, father Appala Naidu Rayi, whose own card reads Raya)'),
    ],
    8: [
        ('Kanthamreddi', 'కంఠంరెడ్డి', ['Kanthanareddi'], ['కాంథనరేడ్డీ'], [2594], 'one surname printed two ways'),
        ('Ramoju', 'రామోజు', ['Ramoji'], ['రామోజి'], [2691], 'son of Bhadram Ramoju, house 6-55'),
        ('Yannamsetti', 'యన్నంశెట్టి', ['Ennamsetti'], ['ఎన్నంశెట్టి'], [2701], 'wife of Sanyasi Rao Yannamsetti, house 6-61'),
        ('Lokanadham', 'లోకనాధం', ['Lokanatham'], ['లోకనాథం'], [2755], 'wife of Bujjibabu Lokanadham, house 6-84'),
        ('Korumilli', 'కోరుమిల్లి', ['Koriballi'], ['కోరిబల్లి'], [2879], 'wife of Appala Naidu Korumilli, house 6-124'),
        ('Isarapu', 'ఇసరపు', ['Yisarapu'], ['యిసరపు'], [2904, 2905], 'one surname (ఇసరపు / యిసరపు)'),
        ('Uggina', 'ఉగ్గిన', ['Vuggina'], ['వుగ్గిన'], [2693], 'wife of Manikyam, mother of Apparao Uggina, house 6-56'),
        ('Annam', 'అన్నం', ['Annama'], ['అన్నమ'], None, 'Annam / Annama share houses 6-29 to 6-42 (one family)'),
        ('Karanam', 'కరణం', ['Karnamm'], ['కర్ణం'], [2678, 2726], 'same house 6-72 as Ramana Karanam (కర్ణం / కరణం)'),
        ('Grandhi', 'గ్రంధి', ['Granthi'], ['గ్రంథి'], [2769, 2790, 2793], 'sons of Krishnamurti and of Kannayyasetti Grandhi (houses 6-92 / 6-96)'),
        ('Kandrakota', 'కాండ్రకోట', ['Kandrikota'], ['కాండ్రికోట'], [2625, 2627], 'Apparao, husband of Ramanamma Kandrakota, and his brother'),
        ('Rayi', 'రాయి', ['Raya', 'Raye'], ['రాయ', 'రాయ్', 'రయే'], None,
         'one surname printed రాయి / రాయ / రాయ్ / రయి (e.g. Devi Rayi, father Appala Naidu Rayi, whose own card reads Raya)'),
    ],
    9: [
        ('Imandi', 'ఇమంది', ['Imandula'], ['ఇమందుల'], [3062], 'father of Satya Sai Kumar Imandi (Siva Imandi), house 7-50'),
        ('Thelugunta', 'తెలుగుంట', ['Thelugota'], ['తేలుగోటా'], [3066], 'brother of Ravitheja Thelugunta (father Srinivasa Rao)'),
        ('Konamkondal', 'కోణంకొండల్', ['Konankandal', 'Konankandit'], ['కోణంకండల్', 'కోణంకండిత్'], [3087, 3089],
         'house 7-57: Satish and Sarojini, both listing Mani Ayyar; spelling confirmed by the user'),
        ('Pappala', 'పప్పల', ['Peppula'], ['పేప్పులా'], [3111], 'son of Ramana Pappala, house 7-65'),
        ('Kandipalli', 'కండిపల్లి', ['Kandipilli', 'Kandivilli'], ['కండిపిల్లి', 'కండివిల్లి'], [3176, 3190],
         'sister of Suribabu Kandipalli (father Paidiyya); mother of Nagasankhar Rao Kandipalli'),
        ('Kasinkota', 'కశింకోట', ['Kasimakota'], ['కాసిమకోటా'], [3226], 'brother of Ramana and Ramu Kasinkota (father Lakshmana)'),
        ('Choudavada', 'చౌడవాడ', ['Choudvada', 'Chouduvada'], ['చౌడ్వాద', 'చౌడువాడ', 'చౌడువ'], [3264, 3266, 3267, 3269, 3270],
         'family of Kamaraju Choudavada (houses 7-121, 7-124)'),
        ('Chippala', 'చిప్పల', ['Chippula', 'Chipam'], ['చిప్పుల', 'చిపం'], [3280, 3281], 'wife and daughter of Bandayya Chippala, house 7-127'),
        ('Isarapu', 'ఇసరపు', ['Yisarapu', 'Eesarapu'], ['యిసరపు', 'ఈసారపు', 'ఈసరపు'],
         [2959, 2969, 2972, 2974, 2984, 2989, 2996, 2997, 3001, 3004, 3006, 3057, 3058],
         'one family spelt three ways (e.g. Lakshmi Yisarapu is the wife of Manikyam Eesarapu, house 7-27)'),
        ('Isarapu', 'ఇసరపు', ['Isarsu'], ['ఇసర్సు'], [2956, 2973], 'Appalakonda and Nagaraju, fathers of Isarapu voters at houses 7-17 / 7-20'),
        ('Murukurti', 'మురుకుర్తి', ['Murukkarti'], ['మురుక్కర్తి'], [3037, 3039], 'wife of Bandayya Murukurti (house 7-41) and her neighbour'),
        ('Palaki', 'పాలకి', ['Paliki'], ['పాలికి'], [3242, 3248], 'daughter of Demullu Palaki (house 7-109); Ramu of house 7-112'),
        ('Devakurti', 'దేవకుర్తి', ['Devakurathi'], ['దేవకూరఠీ'], [3249, 3253, 3271], 'Devakurti family of houses 7-113 / 7-125'),
        ('Rayi', 'రాయి', ['Raya', 'Raye'], ['రాయ', 'రాయ్', 'రయే'], None,
         'one surname printed రాయి / రాయ / రాయ్ / రయి (e.g. Devi Rayi, father Appala Naidu Rayi, whose own card reads Raya)'),
    ],
    10: [
        ('Sirramsetti', 'సిర్రంశెట్టి', ['Seramesti'], ['సేరామేస్తీ'], [3411], 'same house 8-37 as Ramesh Sirramsetti'),
        ('Yerramsetti', None, ['Yarramsetti', 'Yarramsti', 'Erramsetti'], ['యార్రంశెట్టి', 'యార్రామ్స్తీ', 'ఏర్రంశెట్టి', 'ఏర్రమసేట్టీ'],
         [3389, 3392, 3401, 3405, 3406, 3420, 3479, 3651, 3656], 'one surname (ఎర్రంశెట్టి / ఏర్రంశెట్టి / యర్రంశెట్టి)'),
        ('Kancharla', 'కంచర్ల', ['Kancherla'], ['కంచెర్ల'], [3491], 'wife of Veerraju Kancharla, house 8-61'),
        ('Uggina', 'ఉగ్గిన', ['Uggena', 'Vuggina'], ['ఉగ్గేనా', 'వుగ్గిన'], [3595, 3596, 3606, 3657, 3658],
         'one family (Chiranjeevi Vuggina and Babuji Uggina are brothers, house 8-104)'),
        ('Ontipalli', 'ఒంటిపల్లి', ['Ontupalli', 'Ontipilli', 'Ontipali'], ['ఓంటుపల్లి', 'ఒంటిపిల్లి', 'ఓన్టిపఅలీ'],
         [3584, 3585, 3587, 3589, 3590, 3611, 3614, 3619], 'one family (Satyavathi Ontipilli is the wife of Apparao Ontipalli, house 8-105)'),
        ('Yallapu', None, ['Yelapu', 'Ellapu'], ['యేలాపూ', 'ఎల్లపు', 'ఏల్లపు'], None, 'one surname (యల్లపు / యేలాపూ / ఎల్లపు) sharing houses 8-84 / 8-87 / 8-112'),
        ('Cheekatla', 'చీకట్ల', ['Chikatala'], ['చీకటఅలా'], [3368], 'son of Sattibabu Cheekatla, house 8-22'),
        ('Sunkara', 'సుంకర', ['Sunkari'], ['సుంకరి'], [3659, 3660], 'Apparao (wife Lakshmi Sunkara) and his relative, house 8-125'),
        ('Koreddi', 'కోరెడ్డి', ['Kareddi'], ['కరెడ్డి'], [3497, 3498], 'Srinu, son of Malleswara Rao Koreddi (house 8-64), and his cousin'),
    ],
    11: [
        ('Lanka', 'లంకా', ['Lamka'], [], [3786, 3787], 'house 9-12: son and granddaughter of Bandayya Charyulu Lanka; spelling confirmed by the user'),
        ('Medeti', 'మెదేటి', ['Madetee'], ['మడేటీ'], [4018, 4019], 'house 9-79; spelling confirmed by the user (Medeti / మెదేటి as in Ward 12)'),
        ('Yallapu', 'యల్లపు', ['Yelapu'], ['యేలాపూ'], [3694], 'same house 8-139 as Naveena Yallapu'),
        ('Kadari', 'కడారి', ['Kedari'], ['కేడారి'], [3755], 'wife of Srinu Kadari, house 8-159'),
        ('Giduturi', 'గిడుతూరి', ['Giduturu'], ['గిడుతూరు'], [3815], 'wife of Giduturi Sattibabu'),
        ('Kodooru', 'కోడూరు', ['Kodoori'], ['కొడూరి', 'కోడూరి'], [3935, 3938], 'wife and daughter of Appa Lacharyulu Kodooru, house 9-55'),
        ('Polumuri', 'పొలుమూరి', ['Polamuri', 'Polimuri'], ['పొలమూరీ', 'పొలిమూరి'], [3959, 3962], 'husband and daughter of Lalitha Polumuri, house 9-61'),
        ('Mathurthi', 'మాథుర్తి', ['Matturthi', 'Matturi', 'Mathurathi'], ['మత్తుర్తి', 'మత్తురి', 'మాథురతి'], [4039, 4040],
         'house 9-86: Sivashamkar and his wife Bhavani; spelling Mathurthi confirmed by the user'),
        ('Kothali', 'కొత్తలి', ['Kottala', 'Kottali'], ['కొత్తా ల', 'కొట్టా ల'], [3883, 3976, 3978], 'Anna Poorna, daughter of Nageswara Rao Kothali; Nanaji of house 9-64'),
        ('Kothali', 'కొత్తలి', ['Kottali'], ['కోత్తలి'], None, 'spelling Kothali (not Kottali) confirmed by the user, 2026-11-22'),
        ('Kasi', 'కాశి', ['Kashee', 'Kasee', 'Kashi'], ['కాశీ', 'కాసీ', 'కాసి'],
         [3751, 3944, 3954, 3955, 3956, 3957, 3958, 3966, 3971, 4056],
         'one Kasi family (Someswara Rao Kasi, house 9-60, and his sons; Prasada Rao Kasi) printed Kashee / Kasee / Kashi; '
         'spelling Kasi confirmed by the user, 2026-11-22'),
        ('Chinthala', 'చింతల', ['Chintala'], [], [3742, 3743, 4008, 4010, 4021, 4022, 4023, 4024],
         'spelling Chinthala (Ward 11) confirmed by the user, 2026-11-22'),
        ('Darabala', None, ['Dorabula'], ['దొరబుల'], [3797, 3798, 3799], 'Ganga Raju Dorabula and Tatabbayi Darabala are both sons of Venkataswamy'),
    ],
    12: [
        ('Kasi', 'కాశి', ['Kashee', 'Kasee', 'Kashi'], ['కాశీ', 'కాసీ', 'కాసి'], [4187, 4188],
         'house 9-128: Venkata Rao Kasi, his wife Kamala and son Kanaka Durga Rao (spelling Kasi as in Ward 11)'),
        ('Pichchuka', 'పిచ్చుక', ['Pichchika'], ['పిచ్చిక'], [4085], 'mother of Raju Pichchuka (both list Malleswara Rao), house 9-98'),
        ('Adlaboyina', 'అడ్లబోయిన', ['Addaboyina', 'Edlaboyina'], ['అడ్డబోయిన', 'ఎడ్లబోయిన'], [4164, 4166],
         'Mahesh (wife Lakshmi Adlaboyina, father Paradesi) and his son Prabhakara'),
        ('Pinapothula', 'పినపోతుల', ['Penapothula'], ['పెనపోతుల'], [4178], 'daughter of Appalakonda Pinapothula, house 9-125'),
        ('Devakurti', 'దేవకుర్తి', ['Devakurathi'], ['దేవకూరఠీ'], [4329], 'brother of Appala Raju Devakurti (father Venkanna)'),
        ('Pakkurti', 'పక్కుర్తి', ['Pakkurathi'], ['పక్కూరఠీ'], [4354], 'son of Rajababu Pakkurti'),
        ('Yandamuri', 'యండమూరి', ['Yandamurti', 'Endamuri'], ['యండమూర్తి', 'ఎండమూరి'], [4361, 4365],
         'Appalaraju (mother Chilakamma, son Rudra Ravi Yandamuri) and his wife Lova Lakshmi'),
        ('Pedapati', 'పెదపాటి', ['Padapathi'], ['పదపతీ'], [4255], 'sister of Gouthami Pedapati (father Ramu), house 9-152'),
        ('Mathurthi', 'మాథుర్తి', ['Mathurathi'], ['మాథూరఠీ'], [4114, 4116],
         'sons of Siva Shankar Mathurthi (Sivashamkar, Ward 11 house 9-86); spelling confirmed by the user'),
        ('Lakkoju', 'లక్కోజు', ['Lakkoji'], ['లక్కోజీ', 'లక్కొజి'], [4065, 4066], 'Gangachari, father of the Lakkoju brothers, and his wife'),
    ],
    13: [
        ('Dammu', 'దమ్ము', ['Dammi'], ['దమ్మీ'], [4520], 'same house 10-86 as the Dammu family (husband Nageswara Rao Dammu)'),
        ('Thanthara', 'తంతర', ['Thanthadi'], ['తంతడి'], [4614], 'husband of Satyavathi Thanthara, house 10-110'),
        ('Gara', 'గారా', ['Gra'], ['గ్ర'], [4645], 'same house 10-116 as the Gara family'),
        ('Mailapalli', 'మైలపల్లి', ['Malpali', 'Melapalli'], ['మల్పఅలీ', 'మేలాపల్లి'], [4671, 4686],
         'daughter of Appalakonda Mailapalli (house 10-120); Parvathi of house 10-129'),
        ('Kandrakota', None, ['Kandrakot'], ['కంద్రకోట్'], [4739], 'same house 10-158 as the Kandrakota family'),
        ('Chitumuri', 'చిటుమూరి', ['Chittimuri'], ['చిట్టిమూరి'], [4778], 'son of Appalakonda and Ramu Chitumuri, house 11-9'),
        ('Risa', 'రీసా', ['Resha'], ['రేషా'], [4514, 4522, 4523], 'Vijayagouri is the wife of Appalaraju Risa (house 10-84); children of Ramu Risa'),
        ('Chapala', 'చాపల', ['Sapala'], ['సాపల'], [4619, 4621], 'Nukaraju (wife Kumari Chapala, house 10-112) and his mother'),
    ],
    14: [
        ('Kunduru', None, ['Kunduri'], ['కుందూరీ'], [4825], 'Kunduru family'),
        ('Ethula', 'ఏతులా', ['Yethula'], ['యేతులా'], [4844], 'son of Appalakonda Ethula, house 11-27'),
        ('Draksharapu', 'ద్రాక్షారపు', ['Draksarpu', 'Dhaksarapu'], ['డ్రాక్శార్పూ', 'ధాక్శరపూ'], [4874, 5059],
         'Appa Rao (wife Meri Draksharapu) and Jyothi of house 11-105'),
        ('Musurupamu', 'ముసురుపాము', ['Masurupamu', 'Musarupamu', 'Masur Upamu', 'Musala Pamu'],
         ['మసురుపాము', 'ముసరుపాము', 'మసూర్ ఉపము', 'ముసల పాము'], [4928, 4929, 4930, 4932, 4933, 4934],
         'house 11-51: one family printed four ways (Samyul is the son of Ananda Rao Musurupamu)'),
        ('Chakkidala', 'చక్కిడాల', ['Chakkirala'], ['చ క్కిరా ల'], [4982], 'wife of Ramana Chakkidala, house 11-68'),
        ('Nagireddi', None, ['Nagereddi', 'Nagiraddi'], ['నాగేరెడ్డి', 'నగిరడ్డి'], [5009, 5055], 'misprints of Nagireddi'),
        ('Genji', 'గెంజి', ['Ganji'], ['గంజి'], [4971, 4990], 'Anusha (father Nageswara Rao Genji, house 11-62) and Ramana, father of Mangayya Naidu Genji'),
        ('Genji', 'గెంజి', ['Genja'], ['గెంజ'], [4993, 4995, 4997, 4999],
         'houses 11-73 to 11-75: Nageswara Rao (wife Satyavathi Genji), Chanti Babu (son Kasubabu Genji), their father Satyanarayana and brother Prasadu'),
        ('Maisarla', 'మైసర్ల', ['Misarla'], ['మిసర్ల'], [4868, 4871], 'sons of Venkataramana Maisarla, house 11-34'),
        ('Rayi', 'రాయి', ['Raya', 'Raye'], ['రాయ', 'రాయ్', 'రయే'], None,
         'one surname printed రాయి / రాయ / రాయ్ / రయి (e.g. Devi Rayi, father Appala Naidu Rayi, whose own card reads Raya)'),
    ],
}

# వీసం is written Veesam (not Visam) in every ward; spelling confirmed by the user, 2026-11-22.
for _w in (9, 10, 12, 14):
    R.setdefault(_w, []).append(('Veesam', 'వీసం', ['Visam'], [], None, 'surname వీసం spelt Veesam; confirmed by the user, 2026-11-22'))


# Surnames printed as two words ("Kandra Kota", "Chetti Palli"): joined, so that the grouping never matches two
# families on a shared fragment ("Nane Palli" / "Chetti Palli" both ended in "Palli").
JOINED = {
    1: [('Mulampaka', None, ['Mulam Paka'], ['మూలమ్ పాక'], [224], 'surname printed in two words'),
        ('Budireddi', 'బుడిరెడ్డి', ['Budi Reddi'], ['బుడి రెడ్డి'], [354], 'surname printed in two words')],
    5: [('Gonthumurthy', None, ['Gonthu Murthy'], ['గొంతు ముర్తి', 'గొంతు మూర్తి'], [1804, 1844], 'surname printed in two words')],
    6: [('Gonthumurthy', None, ['Gonthu Murthy'], ['గోంతు మూర్తి'], [2111], 'surname printed in two words'),
        ('Pothanasetti', None, ['Pothana Setti'], ['పోతన సెట్టి'], [2002, 2187], 'surname printed in two words')],
    7: [('Pothanasetti', 'పోతనశెట్టి', ['Pothana Setti', 'Potannashetti'], ['పోతన శెట్టి', 'పోతన్నశెట్టి'], None,
         'one surname printed "Pothana Setti" / "Potannashetti" (spelling as in Ward 6)'),
        ('Korukonda', None, ['Koru Konda'], ['కోరు కోండ'], [2392], 'surname printed in two words'),
        ('Nagireddi', None, ['Nagi Reddi'], ['నాగి రెడ్డి'], [2421], 'surname printed in two words'),
        ('Balji', 'బల్జి', ['Baliji'], ['బలీజి'], [2256], 'son of Kalyanam Balji, house 5-52')],
    8: [('Thatikonda', 'తటికొండ', ['Thati Konda'], ['తటి కొండా'], [2688], 'surname printed in two words')],
    9: [('Garikapati', None, ['Garika Pati'], ['గరిక పాటి'], [3202], 'surname printed in two words')],
    10: [('Kandrakota', None, ['Kandra Kota'], ['కాండ్ర కోట', 'కాండ్ర కొట'], [3383, 3469, 3516], 'surname printed in two words'),
         ('Nanepalli', 'నానేపల్లి', ['Nane Palli'], ['నానే పల్లి'], [3567], 'surname printed in two words'),
         ('Chettipalli', 'చెట్టిపల్లి', ['Chetti Palli'], ['చెట్టి పల్లి'], [3618], 'surname printed in two words')],
    11: [('Gonnabattula', 'గొన్నాబత్తుల', ['Gonna Battula'], ['గొన్నా బత్తుల', 'గోన్నా బతుల'], [3697, 3698], 'surname printed in two words')],
    12: [('Sirasapalli', None, ['Sirasa Palli'], ['సిరసా పల్లి'], [4263], 'surname printed in two words')],
    14: [('Yerramsetti', None, ['Yarram Setti'], ['యర్రం శేట్టీ'], [5081], 'surname printed in two words')],
}


# Households that were titled "Household - House x": the surname of a family at the same house,
# printed differently, or glued to the given name ("Varalakshmidevakurti").
SAME_HOUSE = {
    1: [('Kurandasu', 'కురందాసు', ['Kuramadasu'], ['కురమాదాసు'], [269], 'house 1-73 of the Kurandasu family')],
    4: [('Satapathi', 'శతపతి', ['Senapathi'], ['సేనాపతి'], [1121], 'wife of Srinivasa Rao Satapathi; Jayanthi Satapathi at the same house 3-17')],
    6: [('Gonthumurthy', None, ['Gontahamaurthi'], ['గొన్తహమఉర్ఠి'], [1945], 'Raviteja: surname garbled in print; Gonthumurthy confirmed by the user, 2026-11-22'),
        ('Gadapa', 'గడపా', ['Gadasa'], ['గడసా'], [2163], 'mother of Konda Naidu Gadapa (both list Ramana), house 5-19'),
        ('Satyavathi Amalakanti', 'సత్యవతి అమలకంటి', ['Satyavathiamalakanti'], ['సత్యవతిఅమలకంటి'], [2009], 'given name and surname printed as one word')],
    7: [('Gonthumurthy', None, ['Gotumurti'], ['గోతుమూర్తి'], [2335], 'house 5-91 of the Gonthumurthy family')],
    8: [('Mallipati', 'మల్లిపాటి', ['Mailapati'], ['మైలపాటి'], [2765], 'son of Apparao Mallipati, husband of Ramalakshmi Mallipati, house 6-90'),
        ('Uggina', 'ఉగ్గిన', ['Vuggina'], ['వుగ్గిన'], [2591], 'house 6-24 of the Uggina family'),
        ('Varalakshmi Devakurti', 'వరలక్ష్మి దేవకుర్తి', ['Varalakshmidevakurti'], ['వరలక్ష్మిదేవకుర్తి'], [2749], 'given name and surname printed as one word')],
    9: [('Ademma Choppa', 'ఆదెమ్మ చొప్పా', ['Ademmachoppa'], ['ఆదెమ్మచోప్పా'], [3042], 'given name and surname printed as one word')],
    10: [('Kandrakota', None, ['Kandrikota'], ['కాండ్రికోట'], [3517], 'Kandrakota family'),
         ('Govindalapudi', 'గోవిందలపూడి', ['Govind Alapudi'], ['గోవింద్ అలపూడి'], [3355], 'wife of Raja Rao Govindalapudi, house 8-10'),
         ('Varahalamma Koreddi', 'వరహాలమ్మ కోరెడ్డి', ['Varahalammakoreddi'], ['వరహాలమ్మకోరెడ్డి'], [3493], 'given name and surname printed as one word')],
    12: [('Uggina', 'ఉగ్గిన', ['Vuggina'], ['వుగ్గిన'], [4107], 'house 9-107 of the Uggina family'),
         ('Appayamma Kambala', 'అప్పయమ్మ కంబాల', ['Appayammakambala'], ['అప్పయమ్మకంబాల'], [4421], 'given name and surname printed as one word')],
    13: [('Boddu', 'బొడ్డు', ['Bodda'], ['బొడ్డా'], [4527], 'wife of Thatha Rao Boddu, house 10-89')],
    14: [('Kunduru', None, ['Punduru'], ['పుందూరు'], [4905], 'house 11-43 of the Kunduru family'),
         ('Uggina', 'ఉగ్గిన', ['Vuggina'], ['వుగ్గిన'], [5106], 'one surname (ఉగ్గిన / వుగ్గిన)')],
}


# Given-name spellings corrected by the user (same rule format; only the listed records are touched).
#   "Teja" is always written Teja, never Theja (English only; the Telugu తేజ is unchanged). 2026-11-22.
GIVEN = {
    6: [('Raviteja', None, ['Ravitheja'], [], [1945, 2099], 'Teja, not Theja; corrected by the user, 2026-11-22'),
        ('Durgateja', None, ['Durgatheja'], [], [2020], 'Teja, not Theja; corrected by the user, 2026-11-22')],
    9: [('Raviteja', None, ['Ravitheja'], [], [3067], 'Teja, not Theja; corrected by the user, 2026-11-22'),
        ('Tejaswini', None, ['Thejaswini'], [], [3266], 'Teja, not Theja; corrected by the user, 2026-11-22')],
    8: [('Raviteja', None, ['Ravitheja'], [], [2916], 'Teja, not Theja; corrected by the user, 2026-11-22'),('Govinda', 'గోవింద', ['Govanda'], ['గోవంద'], [2647], 'Govinda Rao Annam, house 6-40; corrected by the user, 2026-11-22'),
        # English only: the printed Telugu "స్వరాజ్యరావు" is kept as printed.
        ('Swaraj', None, ['Svarajya', 'Svarajiya'], [], [2626, 2646, 2649],
         'Swaraj Rao Annam (2649) and where he is the father / husband (2626, 2646); corrected by the user, 2026-11-22')],
}


def word_re(words):
    alt = '|'.join(re.escape(w).replace(r'\ ', r'\s+') for w in sorted(words, key=len, reverse=True))
    return re.compile(r'(?<![A-Za-z])(?:%s)(?![A-Za-z])' % alt, re.I)


def te_sub(text, variants, canon):
    """Replace whole Telugu tokens (or multi-token variants) by the canonical spelling."""
    if not text:
        return text
    out = text
    for v in sorted(variants, key=len, reverse=True):
        out = re.sub(r'(?<!\S)%s(?!\S)' % re.escape(v), canon, out)
    return out


def load(ward):
    path = ward_config.ward(ward)['xlsx']
    wb = openpyxl.load_workbook(path)
    ws = wb.worksheets[0]
    head = [c.value for c in ws[1]]
    assert head[:7] == HEAD, (path, head)
    rows = {}
    for r in ws.iter_rows(min_row=2):
        if isinstance(r[0].value, (int, float)):
            rows[int(r[0].value)] = r
    return path, wb, rows


def canonical_te(rows, en):
    """Most common Telugu spelling of [en] as a surname in the ward (for rules that give None)."""
    from collections import Counter
    c = Counter()
    for r in rows.values():
        ew = (r[1].value or '').split(); tw = (r[4].value or '').split()
        if len(ew) == len(tw) and len(ew) >= 2:
            for i in (0, len(ew) - 1):
                if ew[i] == en:
                    c[tw[i]] += 1
    assert c, en
    return c.most_common(1)[0][0]


def main():
    mode = sys.argv[2]; assert mode in ('preview', 'apply'), mode
    wards = [int(w) for w in sys.argv[3:]] or sorted(R)
    log = []
    for ward in wards:
        path, wb, rows = load(ward)
        changed = 0
        for canon_en, canon_te, en_var, te_var, serials, why in (R.get(ward, []) + JOINED.get(ward, []) + SAME_HOUSE.get(ward, [])
                                                                 + GIVEN.get(ward, [])):
            canon_te = canon_te or (canonical_te(rows, canon_en) if te_var else '')
            rx = word_re(en_var)
            if serials is None:          # every record of the ward that carries a variant
                serials = [s for s, r in rows.items() if rx.search(r[1].value or '') or rx.search(r[3].value or '')]
            for s in serials:
                r = rows[s]
                before = [r[i].value for i in (1, 3, 4, 5)]
                en, rel_en, te, rel_te = before
                new = [rx.sub(canon_en, en) if en else en,
                       rx.sub(canon_en, rel_en) if rel_en else rel_en,
                       te_sub(te, te_var, canon_te), te_sub(rel_te, te_var, canon_te)]
                # already corrected (in the voter's own name or, for a relative-only fix, the relative's name)
                is_canon = re.search(r'(?<![A-Za-z])%s(?![A-Za-z])' % re.escape(canon_en), '%s | %s' % (en or '', rel_en or ''))
                if new == before:
                    if not is_canon:
                        raise SystemExit('ward %d serial %d: no variant of %s found in %r' % (ward, s, canon_en, before))
                    continue
                # an English change must come with the matching Telugu change (and the other way round)
                for e0, e1, t0, t1, what in ((en, new[0], te, new[2], 'voter'), (rel_en, new[1], rel_te, new[3], 'relative')):
                    if (e0 != e1) != (t0 != t1) and not (e0 == e1 and canon_en in (e0 or '')):
                        print('  ! ward %d serial %d %s: English %s, Telugu %s  (%r / %r)'
                              % (ward, s, what, 'changed' if e0 != e1 else 'unchanged', 'changed' if t0 != t1 else 'unchanged', e1, t1))
                for (col, name), b, a in zip(((1, 'Voter Name (English)'), (3, 'Relative Name (English)'),
                                              (4, 'Voter Name (Telugu)'), (5, 'Relative Name (Telugu)')), before, new):
                    if b != a:
                        log.append([ward, s, name, b, a, canon_en, why])
                        print('  ward %2d %5d %-24s %s  ->  %s' % (ward, s, name, b, a))
                        r[col].value = a
                changed += 1
        print('ward %d: %d records changed' % (ward, changed))
        if mode == 'apply' and changed:
            wb.save(path)
    if mode == 'apply' and log:
        old = []
        if os.path.exists(LOG):
            with open(LOG, encoding='utf-8-sig', newline='') as f:
                old = [row for row in csv.reader(f)][1:]
        with open(LOG, 'w', encoding='utf-8-sig', newline='') as f:
            w = csv.writer(f)
            w.writerow(['ward', 'gp_serial_no', 'field', 'before', 'after', 'family surname', 'evidence'])
            w.writerows(old + log)
        print('logged %d field changes to %s' % (len(log), LOG))


main()
