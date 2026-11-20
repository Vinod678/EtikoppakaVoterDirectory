"""Ward 4 (GP serials 1108-1476) corrections - used by tools/ward_corrections.py.

Spelling references: corrected Ward 2 workbook first, then Ward 11. Telugu replacements (TE_FIELDS)
were read from the printed card images (relative names printed over two lines, broken glyphs).
"""

TOKENS = {
    # Naidu (Ward 2: "Naidu", compounds split like "Appala Naidu")
    'Nayudu': 'Naidu', 'Appalanayudu': 'Appala Naidu', 'Musilinayudu': 'Musili Naidu', 'Somunayudu': 'Somu Naidu',
    'Ramunayudu': 'Ramu Naidu', 'Venkunayudu': 'Venku Naidu', 'Veerunayudu': 'Veeru Naidu',
    'Achayyanayudu': 'Achchayya Naidu',
    # "X Rao" written separately (Ward 2 / Ward 11)
    'Mallikarjunarao': 'Mallikarjuna Rao', 'Venkatrao': 'Venkata Rao', 'Venkatarao': 'Venkata Rao',
    'Someswararao': 'Someswara Rao', 'Malleswararao': 'Malleswara Rao', 'Rajeswararao': 'Rajeswara Rao',
    'Tarakeswararao': 'Tarakeswara Rao', 'Sivasankararao': 'Siva Shankara Rao', 'Maheswararao': 'Maheswara Rao',
    'Umamaheswararao': 'Uma Maheswara Rao', 'Venkateswararao': 'Venkateswara Rao', 'Kondalarao': 'Kondala Rao',
    'Varahalarao': 'Varahala Rao', 'Subbarao': 'Subba Rao', 'Mohanarao': 'Mohana Rao', 'Chinnarao': 'Chinna Rao',
    'Eeswararao': 'Eswara Rao', 'Eeswara': 'Eswara', 'Eeswar': 'Eswara', 'Sanyasirao': 'Sanyasi Rao',
    'Sankararao': 'Shankara Rao', 'Veerabhadrarao': 'Veerabhadra Rao',
    # Ward 2 given-name spellings
    'Sankara': 'Shankara', 'Sankar': 'Shankar', 'Umasankar': 'Umashankar', 'Sankaramraju': 'Shankaramraju',
    'Sankararaju': 'Shankararaju', 'Achibabu': 'Achchibabu', 'Achiraju': 'Achchiraju', 'Achiyya': 'Achchiyya',
    'Buchi': 'Buchchi', 'Buchivenkatapathiraju': 'Buchchivenkatapathiraju', 'Eeswaramma': 'Eswaramma',
    'Swami': 'Swamy', 'Sathyaveni': 'Satyaveni', 'Thathalu': 'Tatalu', 'Chinathathalu': 'Chinatatalu',
    'Chinaapparao': 'China Apparao',
    # surnames (one spelling each; Ward 2 / Ward 11 established forms)
    'Senapati': 'Senapathi', 'Kothavala': 'Kotwal', 'Gontumurthi': 'Gonthumurthy', 'Polarapu': 'Polavarapu',
    'Yenukonti': 'Enukonti', 'Kasireddi': 'Kasireddy', 'Odiboyina': 'Odaboyina',
}

FIELDS = {
    (1132, 3): 'Sanyasi Rao', (1142, 3): 'Musili Naidu', (1179, 3): 'Tata Raju Kankipati',
    (1316, 3): 'Apparao Dara', (1384, 3): 'Chinatatalu Polavarapu', (1387, 3): 'Chinatatalu',
    (1388, 1): 'Sanyasamma Polavarapu', (1395, 1): 'Chinni Uma Maheswara Rao', (1420, 3): 'Appalakonda',
    (1266, 1): 'Sravani Sankara', (1266, 3): 'Raju Sankara',  # surname printed సంకర (not శంకర)
    (1452, 1): 'Jagu Eswaramma',
    # Cross-ward consistency with Wards 2/3 (same printed Telugu); కొఠవాల variants -> Kotwal as in Ward 3 via TOKENS:
    (1378, 1): 'Nagabhooshanam Enukonti',    # నాగభూషణం: Wards 2/11 Nagabhooshanam
    (1409, 1): 'Jhansi Lakshmi Raya',        # రాయ: Wards 2/3 Raya
    (1459, 3): 'Govinda',                    # గోవింద: Wards 2/3 Govinda
}

# Read from the card images (relative names printed over two lines; broken glyphs).
TE_FIELDS = {
    (1142, 5): 'ముసిలినాయుడు',
    (1168, 5): 'రావణమ్మ దోడ్డీ',
    (1182, 5): 'సూర్యనారాయణ రాజు కంకిపాటి',
    (1205, 5): 'చేకూరి వెంకటేశ్వర రాజు చేకూరి',
    (1215, 5): 'సత్యనారాయణ రాజు పోతూరు',
    (1222, 5): 'స్వామి శివ సీతారామ రాజు సకినేటి',
    (1276, 5): 'వెంకటపతి రాజు చింతలపాటి',
    (1277, 5): 'శ్రావంతీ చింతలపాటి',
    (1298, 5): 'సత్యనారాయణ రాజు పూసపాటి',
    (1299, 5): 'బుచ్చి బంగర రాజు చింతలపాటి',
    (1302, 5): 'విశ్వాసరబంగరాజు చింతలపాటి',
    (1327, 4): 'శ్వేతా దారా',
    (1406, 4): 'నాగేంద్ర మైలారు',
}

UNCERTAIN = {
    1142: 'Card prints no relationship label (relative Musili Naidu)',
    1168: 'Card prints no relationship label (relative Ravanamma Doddi)',
    1277: 'Card prints no relationship label (relative Sravanthi Chintalapati)',
    1114: 'Surname printed ఇస్రాపు; written Isarapu',
    1128: 'Surname printed ఏసారపు; written Isarapu',
    1133: 'Surname printed ఏసరపు; written Isarapu. 1133/1134/1135 are all "Ramya Isarapu" with father Venkataramana - possible duplicate registrations',
    1134: 'Surname printed ఏసారపూ; written Isarapu. Same name and father as 1133/1135 - possible duplicate registration',
    1135: 'Surname printed ఏసరపు; written Isarapu. Same name and father as 1133/1134 - possible duplicate registration',
    1187: 'Surname printed "ఇస్సార్అపూ" (garbled); written Isarapu',
    1125: 'Surname printed శనంసేట్టీ; written Sennamsetti',
    1157: 'Surname printed పెద్దపాటి (Peddapati); husband Brahmaji Pedapati (1119, 1284) - written Pedapati',
    1284: 'Surname printed పెధపతి; written Pedapati',
    1170: 'Surname printed గొద్ది (Goddi); written Goddu',
    1177: 'Surname printed జుగు; written Jagu (husband Ramana Jagu, 1175)',
    1179: 'Relative printed "తాఠ రాజు కాకిపాటి"; written Tata Raju Kankipati',
    1182: 'Name printed "తాఠ రాజు"; written Tata Raju',
    1196: 'Surname printed చందన్డ; written Chandanada',
    1219: 'Name printed "రాజ్న్ష" (garbled); written Rajansha',
    1256: 'Name printed "స్మ్ప దేవీ" (garbled); written Sampath Devi',
    1283: 'Surname printed కండపల్లి; written Kandipalli',
    1294: 'Surname printed పోలారపు (Polarapu); same family as Polavarapu (1308) - written Polavarapu',
    1296: 'Surname printed పోలారపు; written Polavarapu',
    1300: 'Surname printed పోలారపు; son 1308 is printed Bandayya Polavarapu - written Polavarapu',
    1301: 'Surname printed పాలారపు; written Polavarapu',
    1307: 'Surname printed పోలారపు; written Polavarapu',
    1387: 'Surname printed పోలారపూ; written Polavarapu',
    1388: 'Surname printed "పోలా రావు"; husband Varahala Rao (1387) - written Polavarapu',
    1302: 'Relative printed "విశ్వాసరబంగరాజు" (Viswasara Bangaraju?); written Visweswarabangaraju',
    1327: 'Name printed with a broken glyph (శ్ వేతా); relative printed "అరాజున" - written Arjuna',
    1356: 'Surname printed రాయుడు (Rayudu); father Konda Rayidi (1355) suggests Rayidi',
    1362: 'Surname printed కందూరి; written Kunduru',
    1368: 'Surname printed ధర; written Dara',
    1377: 'Surname printed యేనుకూంటీ; written Enukonti',
    1379: 'Surname printed ఎరుకొంటి; written Enukonti. Card labels Mangayyamma as father',
    1395: 'Relative printed "బన్డ్డియ" (garbled); written Bandayya',
    1411: 'Surname printed చేక్; written Shaik',
    1413: 'Name printed with missing glyphs ("ర్ానా"); written Farhana Begum',
}
