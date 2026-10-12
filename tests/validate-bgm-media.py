#!/usr/bin/env python3
"""Real FFmpeg checks for shared BgmCommands: audio delay, trailing silence and picture preservation."""
import json, pathlib, subprocess, sys
import numpy as np
d=pathlib.Path(sys.argv[1]);d.mkdir(parents=True,exist_ok=True)
def run(*a):subprocess.run(list(map(str,a)),check=True,stdout=subprocess.DEVNULL)
run('ffmpeg','-hide_banner','-y','-loglevel','error','-f','lavfi','-i','testsrc2=s=320x180:r=30:d=3','-itsoffset','0.5','-f','lavfi','-i','sine=f=440:r=44100:d=1.5','-c:v','libx264','-threads','2','-c:a','aac',d/'input.mp4')
run('ffmpeg',*json.loads((d/'decode.json').read_text()))
samples=np.fromfile(d/'audio.f32',dtype='<f4').reshape(-1,2)
assert len(samples)>=44100*1.9
assert np.mean(samples[:int(.4*44100)]**2)<1e-7, 'leading audio delay was lost'
assert np.mean(samples[int(.6*44100):44100]**2)>1e-4, 'delayed sound missing'
run('ffmpeg',*json.loads((d/'mux.json').read_text()))
def frames(path):return subprocess.check_output(['ffmpeg','-v','error','-i',str(path),'-map','0:v:0','-c','copy','-f','hash','-hash','sha256','-'])
assert frames(d/'input.mp4')==frames(d/'output.mp4'), 'picture packets changed'
info=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-of','json',str(d/'output.mp4')]))
video=next(s for s in info['streams'] if s['codec_type']=='video');audio=next(s for s in info['streams'] if s['codec_type']=='audio')
assert abs(float(video['duration'])-3)<.05, 'video truncated when audio ends early'
assert abs(float(audio['duration'])-3)<.15
print('PASS BGM media: delayed audio preserved, trailing silence padded, video packets identical, both tracks complete')
