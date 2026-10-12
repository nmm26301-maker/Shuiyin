#!/usr/bin/env python3
"""Fetch pinned engine dependencies, or verify a reusable cache. No SDK required."""
import argparse,concurrent.futures,hashlib,json,pathlib,urllib.request,zipfile
p=argparse.ArgumentParser();p.add_argument('--dir',default='engine-deps');a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];cache=pathlib.Path(a.dir).resolve();cache.mkdir(parents=True,exist_ok=True)
items=json.loads((root/'tooling/engine-lock.json').read_text())
def prepare(item):
 target=cache/item['name']
 if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest()!=item['sha256']:
  request=urllib.request.Request(item['url'],headers={'User-Agent':'WatermarkWorkshop-Build/0.5'})
  with urllib.request.urlopen(request,timeout=120) as response:data=response.read()
  if hashlib.sha256(data).hexdigest()!=item['sha256']:raise RuntimeError('Checksum mismatch: '+item['name'])
  target.write_bytes(data)
 if item['type']=='aar':
  with zipfile.ZipFile(target) as archive:(cache/item['jar']).write_bytes(archive.read('classes.jar'))
 print('Verified '+item['name'],flush=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:list(executor.map(prepare,items))
print(cache)
