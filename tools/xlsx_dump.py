import zipfile, re, sys, json
import xml.etree.ElementTree as ET
ns={'m':'http://schemas.openxmlformats.org/spreadsheetml/2006/main','r':'http://schemas.openxmlformats.org/officeDocument/2006/relationships'}
z=zipfile.ZipFile(sys.argv[1])
print("FILES:",z.namelist())
ss=[]
if 'xl/sharedStrings.xml' in z.namelist():
    for si in ET.fromstring(z.read('xl/sharedStrings.xml')).findall('m:si',ns):
        ss.append(''.join(t.text or '' for t in si.iter('{%s}t'%ns['m'])))
wb=ET.fromstring(z.read('xl/workbook.xml'))
rels=ET.fromstring(z.read('xl/_rels/workbook.xml.rels'))
rmap={r.get('Id'):r.get('Target') for r in rels}
def col(c):
    n=0
    for ch in re.match(r'[A-Z]+',c).group(): n=n*26+ord(ch)-64
    return n-1
out={}
for s in wb.find('m:sheets',ns):
    name=s.get('name'); t=rmap[s.get('{%s}id'%ns['r'])]
    path='xl/'+t.lstrip('/').replace('xl/','')
    sh=ET.fromstring(z.read(path))
    rows=[]
    for r in sh.iter('{%s}row'%ns['m']):
        row={}
        for c in r.findall('m:c',ns):
            v=c.find('m:v',ns); typ=c.get('t')
            if typ=='inlineStr':
                val=''.join(x.text or '' for x in c.iter('{%s}t'%ns['m']))
            elif v is None: val=None
            elif typ=='s': val=ss[int(v.text)]
            else: val=v.text
            row[col(c.get('r'))]=val
        mx=max(row) if row else -1
        rows.append([row.get(i) for i in range(mx+1)])
    merges=[m.get('ref') for m in sh.iter('{%s}mergeCell'%ns['m'])]
    out[name]={'rows':rows,'merges':merges,'dim':(sh.find('m:dimension',ns).get('ref') if sh.find('m:dimension',ns) is not None else None)}
    print("SHEET",name,"rows",len(rows),"dim",out[name]['dim'],"merges",merges[:10])
json.dump(out,open(sys.argv[2],'w',encoding='utf-8'),ensure_ascii=False,indent=0)
