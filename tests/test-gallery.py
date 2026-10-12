#!/usr/bin/env python3
"""Exercise bundled production runner + actual Imgur album extractor against API fixtures.
No live site claims: only external API responses are substituted, not extractor/runner code.
"""
import pathlib,tempfile,zipfile,subprocess,sys,json,hashlib
root=pathlib.Path(__file__).resolve().parents[1]
bundle=root/'app/src/main/res/raw/gallery_bundle'
lock=json.loads((root/'tooling/gallery-bundle.json').read_text())
assert hashlib.sha256(bundle.read_bytes()).hexdigest()==lock['sha256']
assert lock['sha256'] in (root/'app/src/main/java/com/zizai/watermark/GalleryEngine.java').read_text()
out=root/'build-offline/gallery-fixture.jsonl';out.parent.mkdir(exist_ok=True)
with tempfile.TemporaryDirectory() as tmp:
    with zipfile.ZipFile(bundle) as z:
        assert not any(n.endswith(('.so','.pyd')) for n in z.namelist());z.extractall(tmp)
    runner=str(pathlib.Path(tmp)/'gallery_runner.py')
    result=subprocess.run([sys.executable,runner,'--catalog'],check=True,capture_output=True,text=True)
    data=json.loads(result.stdout.removeprefix('__WM_GALLERY_CATALOG__'))
    assert data['version']==lock['version'] and len(data['rules'])==973
    assert data['rules']==(root/'app/src/main/res/raw/gallery_rules.txt').read_text().splitlines()
    code='''import sys,runpy,json
sys.path.insert(0,sys.argv[1])
from gallery_dl.extractor.imgur import ImgurAPI
from gallery_dl import extractor
urls={"instagram":"https://www.instagram.com/p/B1X1DaRAJYJ/","pinterest":"https://www.pinterest.com/pin/858139485162992391/","pixiv":"https://www.pixiv.net/artworks/96641296","flickr":"https://www.flickr.com/photos/12480391@N04/2228970324/","deviantart":"https://www.deviantart.com/user/art/Example-123456789"}
for expected,url in urls.items():
 ex=extractor.find(url)
 assert ex is not None and ex.category==expected,(expected,ex)
def album(self,key):
 assert key=="abcde"
 images=[]
 for i,ext in enumerate(["jpeg","png","mp4"]):
  images.append({"id":"img000"+str(i),"ext":ext,"metadata":{"title":"fixture title","description":"fixture caption"},"is_animated":False,"created_at":"2026-10-10T00:00:00Z"})
 return {"id":key,"title":"fixture album","created_at":"2026-10-10T00:00:00Z","media":images}
ImgurAPI.album=album
ImgurAPI.gallery=lambda self,key: {"is_album":True}
runner=sys.argv[2]
sys.argv=[runner,"https://imgur.com/a/abcde"]
runpy.run_path(runner,run_name="__main__")
'''
    result=subprocess.run([sys.executable,'-c',code,tmp,runner],check=True,capture_output=True,text=True)
    lines=result.stdout.splitlines();assert len(lines)==3
    rows=[json.loads(line.removeprefix('__WM_GALLERY__')) for line in lines]
    assert [r['extension'] for r in rows]==['jpg','png','mp4']
    assert [r['index'] for r in rows]==[1,2,3] and rows[0]['userAgent']
    assert rows[0]['description']=='fixture caption'
    out.write_text(result.stdout)
    delegated=subprocess.run([sys.executable,'-c',code.replace('https://imgur.com/a/abcde','https://imgur.com/gallery/abcde'),tmp,runner],check=True,capture_output=True,text=True)
    assert len(delegated.stdout.splitlines())==3
    bad=subprocess.run([sys.executable,runner,'https://example.org/not-an-extractor'],capture_output=True,text=True)
    assert bad.returncode!=0 and '__WM_GALLERY__' not in bad.stdout
print('PASS packaged gallery: pinned bundle, complete catalog, five real routes, actual upstream Imgur album extraction and failure')
