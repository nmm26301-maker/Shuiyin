#!/usr/bin/env python3
"""Compile production code and run search regressions with JDK 17 + Node, no ECJ."""
import argparse
import base64
import hashlib
import os
import pathlib
import re
import subprocess
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
JSON_URL = 'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar'
JSON_SHA = '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'


def run(*command):
    subprocess.run([str(x) for x in command], cwd=ROOT, check=True, timeout=180)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--deps', required=True, type=pathlib.Path)
    parser.add_argument('--android', required=True, type=pathlib.Path)
    args = parser.parse_args()
    deps, android = args.deps.resolve(), args.android.resolve()
    if not android.is_file():
        parser.error('Android platform jar is missing')
    run('python3', ROOT / 'tooling/prepare-engine.py', '--dir', deps)
    json_jar = deps / 'org-json.jar'
    if not json_jar.exists():
        with urllib.request.urlopen(JSON_URL, timeout=45) as source:
            payload = source.read(2_000_000)
        if hashlib.sha256(payload).hexdigest() != JSON_SHA:
            raise RuntimeError('JSON dependency checksum mismatch')
        json_jar.write_bytes(payload)
    if hashlib.sha256(json_jar.read_bytes()).hexdigest() != JSON_SHA:
        raise RuntimeError('Cached JSON dependency checksum mismatch')
    build = ROOT / 'build-search'
    classes = build / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    # Reuse no old bytecode; deleted classes must not hide missing production code.
    for old in classes.rglob('*.class'):
        old.unlink()
    sources = sorted((ROOT / 'app/src/main/java').rglob('*.java'))
    raw = sorted(set(re.findall(r'R\.raw\.(\w+)', '\n'.join(p.read_text() for p in sources))))
    generated = build / 'generated/com/zizai/watermark/R.java'
    generated.parent.mkdir(parents=True, exist_ok=True)
    generated.write_text('package com.zizai.watermark;public final class R{public static final class raw{' +
                         ''.join(f'public static final int {name}={i+1};' for i, name in enumerate(raw)) + '}}')
    # Real org.json must precede Android's non-executable framework stubs.
    jars = [json_jar, android] + sorted(p for p in deps.glob('*.jar') if p.name not in {'android.jar', 'org-json.jar'})
    cp = os.pathsep.join(map(str, [classes] + jars))
    compiler = ['java', 'com.sun.tools.javac.Main', '-source', '17', '-target', '17', '-encoding', 'UTF-8', '-classpath', cp, '-d', classes]
    # Compile production against Android's API contracts; real JSON is for JVM execution only.
    production_compiler = compiler.copy()
    production_compiler[production_compiler.index('-classpath') + 1] = os.pathsep.join(map(str, [classes, android] + [j for j in jars if j != android]))
    run(*production_compiler, *sources, generated)
    sample = build / 'source.mp4'
    sample.write_bytes(base64.b64decode((ROOT / 'tests/fixtures/probe-video.mp4.base64').read_text().strip(), validate=True))
    tests = {
        'DownloadSiteTests': [build / 'site-form.js', build / 'site-configs.json'], 'ProviderHttpTests': [], 'ParallelSearchTests': [], 'SearchFailureTests': [],
        'DouyinSearchTests': [], 'DouyinImageTests': [],
        'PlaybackRequestTests': [build / 'scanner.js'],
        'VideoCandidateTests': [sample], 'LinkTests': [sample],
        'TransferTests': [ROOT / 'app/src/main/res/raw/ytdlp', sample, build / 'transfer.mp4'],
    }
    run(*compiler, *(ROOT / 'tests' / (name + '.java') for name in tests))
    for name, arguments in tests.items():
        run('java', '-cp', cp, 'com.zizai.watermark.' + name, *arguments)
    for script, source in [
        ('parallel-site-script-tests.js', ROOT / 'app/src/main/res/raw/search_sites.js'),
        ('web-scanner-tests.js', build / 'scanner.js'),
        ('douyin-browser-scanner-tests.js', build / 'scanner.js'),
    ]:
        if script == 'parallel-site-script-tests.js':
            run('node', ROOT / 'tests' / script, source, build / 'site-configs.json')
        else:
            run('node', ROOT / 'tests' / script, source)
    run('node', ROOT / 'tests/download-site-form-tests.js', build / 'site-form.js')
    run('python3', ROOT / 'tests/test-release.py')
    print('PASS search regression gate: 10 Java suites, 4 actual JavaScript collectors and release guards', flush=True)


if __name__ == '__main__':
    main()
