#!/usr/bin/env python3
"""Verify both corner marks are repaired and center subtitle/audio are retained."""
import json,pathlib,subprocess,sys
import numpy as np
folder=pathlib.Path(sys.argv[1]);checks=0
def check(ok,why):
    global checks
    assert ok,why
    checks+=1
def frame(name):
    data=subprocess.check_output(['ffmpeg','-v','error','-i',str(folder/name),'-frames:v','1','-f','rawvideo','-pix_fmt','rgb24','-'])
    return np.frombuffer(data,dtype=np.uint8).reshape(360,640,3).astype(float)
source=frame('two-marks.mp4');result=frame('repaired.mp4')
for x1,x2 in [(28,145),(535,609)]:
    check(source[316:339,x1:x2].mean()>230,'source has a visible corner mark')
    check(abs(result[316:339,x1:x2]-np.array([0,0,255])).mean()<8,'corner mark filled with surrounding blue')
check(abs(result[243:262,240:400]-source[243:262,240:400]).mean()<4,'center subtitle kept')
check(abs(result[50:100,200:450]-source[50:100,200:450]).mean()<4,'unselected content kept')
info=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(folder/'repaired.mp4')]))
v=next(s for s in info['streams'] if s['codec_type']=='video');a=next(s for s in info['streams'] if s['codec_type']=='audio')
check(v['width']==640 and v['height']==360 and v['codec_name']=='h264','dimensions and Android codec kept')
check(a['codec_name']=='aac' and abs(float(info['format']['duration'])-2)<.1,'sound and duration kept')
pcm=np.frombuffer(subprocess.check_output(['ffmpeg','-v','error','-i',str(folder/'repaired.mp4'),'-vn','-ar','48000','-ac','1','-f','s16le','-']),dtype='<i2')[12000:60000].astype(float)
freq=np.fft.rfftfreq(len(pcm),1/48000)[np.argmax(abs(np.fft.rfft(pcm*np.hanning(len(pcm)))))]
check(abs(freq-440)<3,'440Hz audio pitch kept')
eight=frame('eight-repaired.mp4')
for i in range(8):
    x=20+i*72;check(abs(eight[316:336,x+5:x+31]-np.array([0,0,255])).mean()<8,'logo '+str(i+1)+' repaired')
print('PASS multi-region media:',checks,'checks; two corner marks, subtitles, eight marks and audio verified')
