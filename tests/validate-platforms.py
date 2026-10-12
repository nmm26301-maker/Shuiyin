#!/usr/bin/env python3
"""Validate routed mainstream URLs against the packaged extractor definitions; no network claims."""
import pathlib,json,sys,subprocess,hashlib
root=pathlib.Path(__file__).resolve().parents[1];script=root/'app/src/main/res/raw/ytdlp';sys.path.insert(0,str(script))
from yt_dlp.extractor import gen_extractor_classes
catalog=json.loads((root/'app/src/main/res/raw/platforms.json').read_text());classes=gen_extractor_classes();count=0
for row in catalog['platforms']:
 actual=next((c.IE_NAME for c in classes if c.IE_NAME!='generic' and c.suitable(row['sample'])),None)
 assert actual==row['extractor'],(row['name'],row['extractor'],actual)
 assert row['mode']!='内置站点适配' or actual is not None
 count+=1
rules=subprocess.check_output([sys.executable,str(script),'--list-extractors'],text=True).splitlines();assert len(rules)==catalog['extractorCount'];count+=1
assert hashlib.sha256(script.read_bytes()).hexdigest()=='1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6';count+=1
print(f'PASS platform routes: {count} checks; {len(rules)} extractor rules. URL matching only, not live download verification.')
