#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Run packaged gallery-dl as a metadata-only subprocess with bounded same-site delegation."""
import argparse,json,os,sys
from urllib.parse import urlsplit
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from gallery_dl import config,extractor,version
from gallery_dl.extractor.message import Message
parser=argparse.ArgumentParser()
parser.add_argument("--catalog",action="store_true")
parser.add_argument("--cookies")
parser.add_argument("url",nargs="?")
args=parser.parse_args()
def emit(prefix,data):
    print(prefix+json.dumps(data,ensure_ascii=False,separators=(",",":")),flush=True)
if args.catalog:
    rules=sorted(set(c.category+"."+c.subcategory for c in extractor.extractors()))
    emit("__WM_GALLERY_CATALOG__",{"version":version.__version__,"rules":rules})
    raise SystemExit(0)
config.clear()
config.set(("extractor",),"timeout",15)
config.set(("extractor",),"retries",1)
config.set(("extractor",),"sleep",0)
config.set(("extractor",),"sleep-request",0)
config.set(("extractor",),"image-range","1-24")
if args.cookies:
    config.set(("extractor",),"cookies",args.cookies)
ex=extractor.find(args.url or "")
if ex is None:
    raise SystemExit("Unsupported URL: image gallery extractor unavailable")
count=0
queued=0
def messages(current,depth=0):
    global queued
    for message in current:
        if message[0]==Message.Queue:
            # A single post can delegate to its album/image extractor (e.g. Imgur gallery).
            # Do not traverse profiles, searches, arbitrary external URLs or unbounded queues.
            if depth>=2 or queued>=8 or current.subcategory not in ("gallery","post","pin","status","tweet","url","illustration"):
                continue
            target=message[1]
            if not isinstance(target,str) or not target.startswith("https://"):
                continue
            host=lambda value:(urlsplit(value).hostname or "").removeprefix("www.")
            if host(target)!=host(args.url):
                continue
            child=extractor.find(target)
            if child is not None:
                queued+=1
                yield from messages(child,depth+1)
        else:
            yield message
for message in messages(ex):
    if message[0]!=Message.Url:
        continue
    url,meta=message[1],message[2]
    if not isinstance(url,str) or not url.startswith("https://"):
        continue
    ext=str(meta.get("extension","")).lower()
    if ext not in ("jpg","jpeg","png","webp","gif","avif","heic","heif","mp4","webm","m4v","mov","mp3","m4a","ogg"):
        continue
    count+=1
    headers=meta.get("_http_headers") or {}
    referer=headers.get("Referer") or headers.get("referer") or args.url
    useragent=headers.get("User-Agent") or headers.get("user-agent") or ex.session.headers.get("User-Agent","")
    emit("__WM_GALLERY__",{"url":url,"extension":ext,"index":count,
        "title":str(meta.get("title") or meta.get("post_title") or meta.get("content") or ex.category)[:160],
        "description":str(meta.get("description") or meta.get("caption") or meta.get("content") or "")[:6000],
        "referer":referer,"userAgent":useragent})
    if count>=24:
        break
if count==0:
    raise SystemExit("No image gallery media found; login or page access may be required")
