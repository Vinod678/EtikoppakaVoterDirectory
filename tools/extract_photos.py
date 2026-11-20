import sys,os,hashlib,csv,re,json,io,collections; sys.path.insert(0, sys.argv[1]); sys.stdout.reconfigure(encoding='utf-8')
import fitz
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ward_config
pdf,xlsx_json,outdir,manifest=sys.argv[2:6]
ref=sys.argv[6] if len(sys.argv)>6 and sys.argv[6]!='-' else None
W=ward_config.ward(int(sys.argv[7]) if len(sys.argv)>7 else 11)
sha=lambda b: hashlib.sha256(b).hexdigest()
h0=sha(open(pdf,'rb').read())
doc=fitz.open(pdf)
expected=list(range(W['first'],W['last']+1))
excel=[int(r[0]) for r in json.load(open(xlsx_json,encoding='utf-8'))[W['sheet']]['rows'][1:]]
if excel!=expected: print('WARNING Excel serials differ from expected range: missing',sorted(set(expected)-set(excel)),'extra',sorted(set(excel)-set(expected)))
os.makedirs(outdir,exist_ok=True)
rows=[];errors=[];used_xrefs=collections.Counter();seen=collections.Counter()
for pno,p in enumerate(doc):
    placed=[(im,r) for im in p.get_images(full=True) for r in p.get_image_rects(im[0])]
    for w in ward_config.card_serials(p.get_text('words'),W['first'],W['last']):
        serial=int(w[4]); seen[serial]+=1
        x0,y0=w[0],w[1]; card=fitz.Rect(x0-62,y0-6,x0+122,y0+74)
        hits=[(im,r) for im,r in placed if card.contains(fitz.Point((r.x0+r.x1)/2,(r.y0+r.y1)/2))]
        if len(hits)!=1: errors.append((serial,'images in card',len(hits))); continue
        im,r=hits[0]; xref=im[0]; used_xrefs[xref]+=1
        filt=im[8]; info=doc.extract_image(xref); data=info['image']
        if filt=='DCTDecode':
            ext='jpg'; raw_identical = doc.xref_stream_raw(xref)==data
            if not raw_identical: errors.append((serial,'jpeg bytes differ from raw stream'))
        else:
            ext=info['ext']; raw_identical=False
        fn='%d.%s'%(serial,ext); path=os.path.join(outdir,fn)
        open(path,'wb').write(data)
        back=open(path,'rb').read(); assert back==data
        pi=Image.open(io.BytesIO(back)); pi.load()
        if pi.size!=(im[2],im[3]): errors.append((serial,'size mismatch',pi.size,(im[2],im[3])))
        rows.append(dict(gp_serial_no=serial,photo_filename=fn,format=pi.format,width_px=pi.size[0],height_px=pi.size[1],
            file_size_bytes=len(back),pdf_page=pno+1,pdf_image_xref=xref,pdf_filter=filt,
            byte_identical_to_pdf_stream='yes' if raw_identical else 'no (lossless pixel export)',sha256=sha(back),
            photo_rect_on_page='%.1f,%.1f,%.1f,%.1f'%tuple(r)))
rows.sort(key=lambda x:x['gp_serial_no'])
with open(manifest,'w',newline='',encoding='utf-8') as f:
    wr=csv.DictWriter(f,fieldnames=list(rows[0].keys())); wr.writeheader(); wr.writerows(rows)
doc.close()
got=[x['gp_serial_no'] for x in rows]
print('photos written',len(rows))
print('errors',errors)
print('missing',sorted(set(expected)-set(got)),'extra',sorted(set(got)-set(expected)))
print('serial seen more than once',[k for k,v in seen.items() if v>1])
print('xref reused',[k for k,v in used_xrefs.items() if v>1],'unique xrefs',len(used_xrefs))
print('total images in pdf',sum(len(pg.get_images()) for pg in fitz.open(pdf)))
print('duplicate file hashes',[k for k,v in collections.Counter(x['sha256'] for x in rows).items() if v>1])
print('formats',collections.Counter((x['format'],x['pdf_filter']) for x in rows))
print('non-identical',[(x['gp_serial_no'],x['pdf_filter']) for x in rows if x['byte_identical_to_pdf_stream']!='yes'])
print('files in folder',len(os.listdir(outdir)))
print('pdf unchanged',sha(open(pdf,'rb').read())==h0)
if ref: print('reference photo matches', sha(open(os.path.join(outdir,os.path.basename(ref)),'rb').read())==sha(open(ref,'rb').read()))
print('per-page counts',collections.Counter(x['pdf_page'] for x in rows))
print('total bytes',sum(x['file_size_bytes'] for x in rows))
