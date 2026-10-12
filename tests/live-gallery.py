#!/usr/bin/env python3
"""Optional public HTTPS gallery check on the host; not an Android device test."""
import tempfile,pathlib,zipfile,subprocess,sys,json,urllib.request,hashlib
from PIL import Image
root=pathlib.Path(__file__).resolve().parents[1]
url=sys.argv[1] if len(sys.argv)>1 else 'https://commons.wikimedia.org/wiki/File:Example.jpg'
with tempfile.TemporaryDirectory() as tmp:
    with zipfile.ZipFile(root/'app/src/main/res/raw/gallery_bundle') as archive:archive.extractall(tmp)
    r=subprocess.run([sys.executable,str(pathlib.Path(tmp)/'gallery_runner.py'),url],capture_output=True,text=True,timeout=90,check=True)
    rows=[json.loads(line.removeprefix('__WM_GALLERY__')) for line in r.stdout.splitlines() if line.startswith('__WM_GALLERY__')]
    assert rows
    item=rows[0]
    req=urllib.request.Request(item['url'],headers={'User-Agent':item['userAgent'],'Referer':item['referer']})
    with urllib.request.urlopen(req,timeout=20) as response:
        body=response.read(10*1024*1024+1);mime=response.headers.get_content_type()
    assert len(body)<=10*1024*1024 and mime.startswith('image/')
    target=root/'build-offline/live-gallery-image';target.write_bytes(body)
    with Image.open(target) as im:fmt=im.format;dimensions=im.size;im.verify()
    record={'source':url,'count':len(rows),'mime':mime,'bytes':len(body),'format':fmt,'size':dimensions,'sha256':hashlib.sha256(body).hexdigest()}
    (root/'build-offline/live-gallery.json').write_text(json.dumps(record,indent=2)+'\n')
    print('PASS live public gallery: '+json.dumps(record,ensure_ascii=False))
