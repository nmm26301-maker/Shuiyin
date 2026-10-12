#!/usr/bin/env python3
"""Prepare pinned offline OCR and source separation dependencies; never accepts partial files."""
import argparse, hashlib, json, pathlib, shutil, tarfile, urllib.request

def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream,'sha256').hexdigest()

def prepare(cache, offline=False):
    root=pathlib.Path(__file__).resolve().parents[1]
    cache=pathlib.Path(cache).resolve();cache.mkdir(parents=True,exist_ok=True)
    lock=json.loads((root/'tooling/auto-lock.json').read_text())
    for item in lock:
        target=cache/item['name']
        if not target.is_file() or sha(target)!=item['sha256']:
            if offline:raise SystemExit('Missing or invalid auto component: '+item['name']+'; run prepare-auto.py first')
            temporary=target.with_suffix(target.suffix+'.partial')
            try:
                request=urllib.request.Request(item['url'],headers={'User-Agent':'WatermarkWorkshop-build/0.7'})
                with urllib.request.urlopen(request,timeout=120) as source,temporary.open('wb') as out:shutil.copyfileobj(source,out)
                if sha(temporary)!=item['sha256']:raise RuntimeError('Checksum mismatch: '+item['name'])
                temporary.replace(target)
            finally:temporary.unlink(missing_ok=True)
        print('Verified '+item['name'],flush=True)
    assets=root/'app/src/main/assets/auto_models';(assets/'tessdata').mkdir(parents=True,exist_ok=True)
    for name in ('eng.traineddata','chi_sim.traineddata'):shutil.copy2(cache/name,assets/'tessdata'/name)
    shutil.copy2(cache/'UVR_MDXNET_9482.onnx',assets/'UVR_MDXNET_9482.onnx')
    libs=root/'app/libs';libs.mkdir(exist_ok=True)
    for name in ('jna-5.18.1.aar','tesseract4android-4.9.0.aar'):shutil.copy2(cache/name,libs/name)
    jni=root/'app/src/main/jniLibs/arm64-v8a';jni.mkdir(parents=True,exist_ok=True)
    with tarfile.open(cache/'sherpa-onnx-v1.13.8-android.tar.bz2') as archive:
        for name in ('libonnxruntime.so','libsherpa-onnx-c-api.so'):
            matches=[m for m in archive.getmembers() if m.isfile() and m.name.endswith('/arm64-v8a/'+name)]
            if len(matches)!=1:raise RuntimeError('Unexpected sherpa archive layout: '+name)
            with archive.extractfile(matches[0]) as source,(jni/name).open('wb') as out:shutil.copyfileobj(source,out)
    return lock

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--dir',required=True);p.add_argument('--offline',action='store_true');a=p.parse_args();prepare(a.dir,a.offline)
