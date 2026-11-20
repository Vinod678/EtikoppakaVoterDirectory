"""Ward 3 (GP serials 739-1107) corrections for tools/ward_corrections.py.

Spelling follows the corrected Ward 2 workbook (then Ward 11): Naidu, Senapathi, Sennamsetti,
Rayi (రాయి/రయి/రాయీ) vs Raya (రాయ/రాయ్), Kasireddy, Kasinkota, Kashi, Bandayya, Eswara..., "X Rao".
Conflicts with the draft's own standards: draft "Kothavala" -> "Kotwal" (Ward 2: కొత్వాల్ = Kotwal),
draft "Senapati" -> "Senapathi", draft "Nayudu" -> "Naidu", draft "Kasi" -> "Kashi" (Ward 2/11),
draft "Kasireddi" -> "Kasireddy", draft "Kashimkota" -> "Kasinkota", draft "Rayi" for రాయ/రాయ్ -> "Raya".
"""

TOKENS = {
    'Nayudu': 'Naidu', 'Senapati': 'Senapathi', 'Kothavala': 'Kotwal', 'Kotvala': 'Kotwal',
    'Kasireddi': 'Kasireddy', 'Kashimkota': 'Kasinkota', 'Kasi': 'Kashi', 'Kumcha': 'Kuncha',
    'Bandiyya': 'Bandayya', 'Eeswaramma': 'Eswaramma', 'Eeswari': 'Eswari', 'Eeswararao': 'Eswara Rao',
    'Baburao': 'Babu Rao', 'Narayanarao': 'Narayana Rao', 'Srinivasarao': 'Srinivasa Rao', 'Krishnarao': 'Krishna Rao',
    'Kannarao': 'Kanna Rao', 'Prakasarao': 'Prakasa Rao', 'Rajarao': 'Raja Rao', 'Sanyasirao': 'Sanyasi Rao',
    'Durgarao': 'Durga Rao', 'Anandarao': 'Ananda Rao', 'Sankararao': 'Shankara Rao', 'Narasingarao': 'Narasinga Rao',
    'Someswararao': 'Someswara Rao', 'Govindarao': 'Govinda Rao', 'Jogarao': 'Joga Rao', 'Subbarao': 'Subba Rao',
    'Anantarao': 'Ananta Rao', 'Kondalarao': 'Kondala Rao', 'Yallayyanayudu': 'Yallayya Naidu',
    'Gangunayudu': 'Gangu Naidu', 'Somunayudu': 'Somu Naidu', 'Kalavati': 'Kalavathi', 'Prabhavati': 'Prabhavathi',
    'Parvati': 'Parvathi', 'Shiva': 'Siva', 'Praveenkumar': 'Praveen Kumar', 'Narasimhamurthy': 'Narasimha Murthy',
}

FIELDS = {
    (750, 3): 'Mylaru Shanti Vijay Kumar',
    (778, 1): 'Jaya Darla', (779, 1): 'Chinna Darla', (779, 3): 'Suryanarayana Darla',
    (783, 1): 'Suryalakshmi Tamarapalli', (785, 1): 'Lalitha Mahalakshmi Tamarapalli',
    (798, 1): 'Jayasurya Naga Satyanarayana Rayi',
    (813, 3): 'Shankar Rao Anjuri', (823, 1): 'Vijayalakshmi Gude',
    (828, 1): 'Varalakshmi Simhadrinalli', (828, 3): 'Chinnabbayi Simhadrinalli',
    (896, 1): 'Gopi Raya', (896, 3): 'Rajubabu Raya', (897, 1): 'Durga Raya', (897, 3): 'Ramaraju Raya',
    (921, 1): 'Peda Venkatalakshmi Kriyadula', (922, 1): 'China Venkatalakshmi Kriyadula',
    (932, 1): 'Mahalakshmi Lanka', (942, 1): 'Poorna Mahalakshmi Rayi', (943, 1): 'Poorna Mahalakshmi Rayi',
    (945, 1): 'Rajapamusili Raya', (945, 3): 'Apparao Raya', (947, 1): 'Seetha Raya', (947, 3): 'Rajapamusili Raya',
    (954, 1): 'Mangaraju', (967, 1): 'Dhanalakshmi Sennamsetti', (975, 1): 'Shanti Raya',
    (990, 1): 'Adilakshmi Rayi', (994, 1): 'Vara Lakshmi Raya', (1004, 3): 'Devullu',
    (1010, 3): 'Surakasula Ramana Surakasula', (1019, 1): 'Nooka Ratnam Raya',
    (1023, 1): 'Suryalakshmi Sennamsetti', (1033, 3): 'Venkataramana', (1036, 3): 'Venkataramana',
    (1037, 1): 'Venkataramana Sennamsetti', (1038, 3): 'Venkataramana',
    (1045, 3): 'Suryanarayana Raju Chintalapati', (1046, 3): 'Chintalapati Srinivas Sudarsana Raju',
    (1059, 1): 'Raju Appalakonda', (1097, 1): 'Nagavenkata Varalakshmi N.', (1105, 3): 'Tatabbayi Nakkina',
}

# Read on the card images (names printed over two lines; the text layer loses the second line).
TE_FIELDS = {
    (750, 5): 'మైలారు శాంతి విజయ్ కుమార్',
    (1010, 5): 'సూరాకసూలా రమణ సూరాకసూలా',
    (1045, 5): 'సూర్యనారాయణ రాజు చంటాలపాటి',
    (1046, 5): 'చింతల పాటి శ్రీనువాస్ సుదర్శనరాజు',
}

UNCERTAIN = {
    740: 'Card prints no relationship label', 771: 'Card prints no relationship label',
    897: 'Card prints no relationship label',
    764: 'Relative surname printed "రమరపలీ"; written Tamarapalli',
    779: 'Surname printed ధర్ల; wife 778 is దార్లా (Darla); written Darla',
    782: 'Surname printed తామరవల్లి; written Tamarapalli (children 759-761)',
    792: 'Surname printed మాయలూరు; written Mylaru',
    793: 'Surname printed సన్నంసెట్టి; written Sennamsetti', 794: 'Surname printed సన్నంశెట్టి; written Sennamsetti',
    877: 'Surname printed సానంశెట్టి; written Sennamsetti',
    953: 'Surname printed "సేన్న్మేసేట్టీ"; written Sennamsetti', 955: 'Surname printed "సేననామసేట్టీ"; written Sennamsetti',
    966: 'Surname printed "శ్రీఎన్నాఎంసెట్టి" (garbled); written Sennamsetti',
    828: 'Surname printed సింహాద్రినల్లి (one word); may be the Simhadri family',
    883: 'Surname printed తూరుబిల్ల; written Thurubatla (husband 882)',
    895: 'Relative printed "రజాబ్అణు రయి" (garbled); written Rajababu Rayi',
    898: 'Surname printed కోనూరీ; children 899/900 are Kosuru; written Kosuru',
    901: 'Relative printed only "షేక్" (Shaik)',
    903: 'Surname printed కోత్యాల; written Kotwal', 911: 'Surname printed కోత్యాల; written Kotwal',
    914: 'Label printed Mother but relative "Sattibabu" is usually a man\'s name; surname printed కోవ్వాల',
    1005: 'Label printed Mother but relative "Govinda" is usually a man\'s name',
    1050: 'Label printed Mother but relative "Chinnayya" is usually a man\'s name',
    1073: 'Label printed Mother but relative "Baburao" is usually a man\'s name',
    822: 'Relative name printed identical to the voter name (Apparao Gude)',
    931: 'Surname printed "ాయి" (first letter missing on the card); written Rayi',
    978: 'Surname printed వీర్ల; may be the Eerla family (977, same relative Apparao)',
    1004: 'Relative printed దేవూల్లు (Devullu); may be Demullu (1003)',
    1010: 'Surname printed twice (సూరాకసూలా ... సూరాకసూలా) for voter and relative',
    1045: 'Surname printed చంటాలపాటి; written Chintalapati', 1047: 'Surname printed చంటాలపాటి; written Chintalapati',
    1059: 'Surname printed అప్పలకోండ (Appalakonda); father Kondala Durga Ramana; may be Appikonda',
    1062: 'Surname printed "శారాపర్ఎడి" (garbled); written Serapareddi',
    1069: 'Surname printed కన్నూరాం; written Kannuru (1074)',
    1090: 'Surname printed "సన్ పతి" / relative "సానపత్తి"; written Senapathi',
    1107: 'Surname printed twice (దారా ... దారా)',
}

# Cross-ward consistency with Wards 2/4 (same printed Telugu): శంకర/శంకర్ -> Shankara/Shankar, -తల్లి -> -thalli.
TOKENS.update({'Sankara': 'Shankara', 'Sankar': 'Shankar', 'Chinatalli': 'Chinathalli', 'Paiditalli': 'Paidithalli'})
