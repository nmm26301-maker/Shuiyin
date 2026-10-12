#!/usr/bin/env python3
"""Check watermark fixture pixels, audio, geometry and duration after real FFmpeg repair."""
import json, pathlib, subprocess, sys
import numpy as np
folder=pathlib.Path(sys.argv[1]);checks=0
def check(condition,reason):
    global checks
    assert condition,reason
    checks+=1
def frame(path):
    data=subprocess.check_output(['ffmpeg','-v','error','-i',str(path),'-frames:v','1','-f','rawvideo','-pix_fmt','rgb24','-'])
    return np.frombuffer(data,dtype=np.uint8).reshape(180,320,3).astype(float)
source=frame(folder/'marked.mp4');result=frame(folder/'repaired.mp4')
check(source[33:47,55:105].mean()>230,'fixture watermark is visibly white')
check(abs(result[33:47,55:105]-np.array([0,0,255])).mean()<8,'selected white watermark replaced by surrounding blue')
check(abs(result[100:120,200:220]-source[100:120,200:220]).mean()<4,'area outside repair remains visually intact')
info=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(folder/'repaired.mp4')]))
video=next(x for x in info['streams'] if x['codec_type']=='video');audio=next(x for x in info['streams'] if x['codec_type']=='audio')
check(video['codec_name']=='h264' and video['width']==320 and video['height']==180,'Android compatible video and retained geometry')
check(audio['codec_name']=='aac' and abs(float(info['format']['duration'])-2)<.1,'audio preserved and duration retained')
samples=np.frombuffer(subprocess.check_output(['ffmpeg','-v','error','-i',str(folder/'repaired.mp4'),'-vn','-ar','48000','-ac','1','-f','s16le','-']),dtype='<i2')[12000:60000].astype(float)
frequency=np.fft.rfftfreq(len(samples),1/48000)[np.argmax(abs(np.fft.rfft(samples*np.hanning(len(samples)))))]
check(abs(frequency-440)<3,'original audio pitch retained')
print('PASS repaired media:',checks,'checks; watermark pixels, surrounding image and audio verified')
