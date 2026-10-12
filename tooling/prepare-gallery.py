#!/usr/bin/env python3
"""Build a deterministic pure-Python gallery bundle from checksum-pinned wheels."""
import pathlib,json,urllib.request,hashlib,zipfile,concurrent.futures,argparse
p=argparse.ArgumentParser();p.add_argument("--cache",default="gallery-cache");a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];cache=pathlib.Path(a.cache).resolve();cache.mkdir(parents=True,exist_ok=True)
rows=json.loads((root/"tooling/gallery-lock.json").read_text())
def fetch(row):
    target=cache/row["filename"]
    if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest()!=row["sha256"]:
        with urllib.request.urlopen(row["url"],timeout=90) as response:data=response.read()
        if hashlib.sha256(data).hexdigest()!=row["sha256"]:raise RuntimeError("Wheel checksum mismatch: "+row["name"])
        target.write_bytes(data)
    return target
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:wheels=list(pool.map(fetch,rows))
files={}
for path in wheels:
    with zipfile.ZipFile(path) as wheel:
        for name in wheel.namelist():
            if name.endswith("/") or ".data/" in name:continue
            if name.endswith((".so",".pyd")):raise RuntimeError("Bundle must contain pure Python wheels only")
            files[name]=wheel.read(name)
files["gallery_runner.py"]=(root/"tooling/gallery_runner.py").read_bytes()
target=root/"app/src/main/res/raw/gallery_bundle"
with zipfile.ZipFile(target,"w",zipfile.ZIP_DEFLATED) as bundle:
    for name,data in sorted(files.items()):
        entry=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o644<<16;bundle.writestr(entry,data)
sha=hashlib.sha256(target.read_bytes()).hexdigest()
(root/"tooling/gallery-bundle.json").write_text(json.dumps({"version":rows[0]["version"],"sha256":sha},indent=2)+"\n")
print(target,sha)
