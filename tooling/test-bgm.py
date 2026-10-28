#!/usr/bin/env python3
"""Exercise real production DSP/model inference and FFmpeg remux on local synthetic media."""
import argparse
import array
import hashlib
import json
import math
import os
import pathlib
import subprocess
import sys
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
RUNTIME_URL = 'https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime/1.24.3/onnxruntime-1.24.3.jar'
RUNTIME_SHA = 'ca7664d1bec1527606629cdae0803beb124ef47cadd90e2f0201b092141ec107'

def run(command, **kwargs):
    return subprocess.run(list(map(str, command)), cwd=ROOT, check=True, timeout=180, **kwargs)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--deps', type=pathlib.Path, required=True)
    args = parser.parse_args()
    deps = args.deps.resolve()
    run(['python3', 'tooling/prepare-bgm.py'])
    runtime = deps / 'onnxruntime-desktop.jar'
    if not runtime.exists():
        with urllib.request.urlopen(RUNTIME_URL, timeout=60) as response:
            payload = response.read(60_000_000)
        if hashlib.sha256(payload).hexdigest() != RUNTIME_SHA:
            raise RuntimeError('Desktop inference dependency checksum mismatch')
        runtime.write_bytes(payload)
    if hashlib.sha256(runtime.read_bytes()).hexdigest() != RUNTIME_SHA:
        raise RuntimeError('Cached desktop inference dependency checksum mismatch')
    directory = ROOT / 'build-search/bgm-test'
    directory.mkdir(parents=True, exist_ok=True)
    voice_file = directory / 'reference.pcm'
    run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'lavfi', '-i',
         "flite=text='The spoken voice should remain clear while the background music is reduced.':voice=slt",
         '-t', '5', '-ar', '44100', '-ac', '2', '-f', 's16le', voice_file])
    voice = array.array('h', voice_file.read_bytes())
    if sys.byteorder != 'little':
        voice.byteswap()
    samples = 44100 * 5
    voice.extend([0] * (samples * 2 - len(voice)))
    music = [int(2400 * (math.sin(2*math.pi*261.63*i/44100) + math.sin(2*math.pi*329.63*i/44100) + math.sin(2*math.pi*392*i/44100)) * (.6+.4*math.sin(2*math.pi*2*i/44100)**2)) for i in range(samples)]
    mixture = array.array('h', [max(-32768, min(32767, voice[i] + music[i//2])) for i in range(samples*2)])
    if sys.byteorder != 'little':
        mixture.byteswap()
    raw = directory / 'mixed.pcm'
    raw.write_bytes(mixture.tobytes())
    source = directory / 'input.mp4'
    run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'lavfi', '-i', 'color=c=blue:s=64x64:r=10:d=5',
         '-f', 's16le', '-ar', '44100', '-ac', '2', '-i', raw, '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-shortest', source])
    # Match production decode arguments; the Java test exports the exact final mux command.
    run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-nostdin', '-i', source, '-map', '0:a:0', '-vn', '-t', '181', '-ar', '44100', '-ac', '2', '-c:a', 'pcm_s16le', '-f', 's16le', directory / 'decoded.pcm'])
    classes = ROOT / 'build-search/classes'
    jars = [runtime, classes, deps / 'org-json.jar'] + sorted(p for p in deps.glob('*.jar') if p not in {runtime, deps / 'org-json.jar'})
    cp = os.pathsep.join(map(str, jars))
    run(['java', 'com.sun.tools.javac.Main', '-source', '17', '-target', '17', '-classpath', cp, '-d', classes, ROOT / 'tests/BgmTests.java'])
    models = ROOT / 'app/src/main/assets/bgm'
    run(['java', '-Xmx2g', '-cp', cp, 'com.zizai.watermark.BgmTests', directory, models / 'vocals.fp16.onnx', models / 'accompaniment.fp16.onnx'])
    commands = json.loads((directory / 'commands.json').read_text())
    run(['ffmpeg', '-hide_banner', '-loglevel', 'error'] + commands['mux'])
    def probe(file):
        return json.loads(run(['ffprobe', '-v', 'error', '-show_streams', '-show_format', '-of', 'json', file], capture_output=True, text=True).stdout)
    before, after = probe(source), probe(directory / 'output.mp4')
    assert {s['codec_type'] for s in after['streams']} == {'video', 'audio'}
    assert abs(float(before['format']['duration'])-float(after['format']['duration'])) < .1
    def picture_hash(file):
        return run(['ffmpeg', '-v', 'error', '-i', file, '-map', '0:v:0', '-c', 'copy', '-f', 'hash', '-hash', 'sha256', '-'], capture_output=True, text=True).stdout.strip()
    assert picture_hash(source) == picture_hash(directory / 'output.mp4')
    # Report controlled-fixture energy projections; these do not establish quality on arbitrary mixes.
    separated = array.array('h', (directory / 'actual-vocals.pcm').read_bytes())
    if sys.byteorder != 'little':
        separated.byteswap()
    vv=mm=vm=yv=ym=0.0
    for i in range(min(samples*2, len(separated))):
        v, m, y = voice[i], music[i//2], separated[i]
        vv += v*v; mm += m*m; vm += v*m; yv += y*v; ym += y*m
    determinant = vv*mm-vm*vm
    vocal_gain = (yv*mm-ym*vm)/determinant
    music_gain = (ym*vv-yv*vm)/determinant
    print(f'Controlled synthetic fixture projection: vocal={vocal_gain:.3f}, background={music_gain:.3f}', flush=True)
    assert vocal_gain > .1 and abs(music_gain) < abs(vocal_gain)*.8, 'Synthetic fixture did not reduce background relative to voice'
    print('PASS BGM FFmpeg integration: 4 checks; audio/video tracks, duration, original picture packets and controlled-fixture background reduction', flush=True)

if __name__ == '__main__':
    main()
