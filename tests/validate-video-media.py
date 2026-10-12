#!/usr/bin/env python3
"""Check actual VideoPipelineTests outputs with ffprobe and NumPy; no network."""
import json,subprocess,sys,pathlib,numpy as np
p=pathlib.Path(sys.argv[1]);count=0
for speed in [.5,1.5,2.,3.,4.]:
 f=p/f'speed-{speed}.mp4';data=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(f)]));streams=data['streams'];assert len(streams)==2
 video=next(x for x in streams if x['codec_type']=='video');audio=next(x for x in streams if x['codec_type']=='audio');assert video['codec_name']=='h264' and audio['codec_name']=='aac'
 duration=float(data['format']['duration']);expected=8/speed;assert abs(duration-expected)<.16,(speed,duration,expected)
 samples=np.frombuffer(subprocess.check_output(['ffmpeg','-v','error','-i',str(f),'-vn','-ar','48000','-ac','1','-f','s16le','-']),dtype='<i2')
 sample=samples[12000:60000].astype(float);freq=np.fft.rfftfreq(len(sample),1/48000);peak=freq[np.argmax(abs(np.fft.rfft(sample*np.hanning(len(sample)))))]
 assert abs(peak-440)<3,(speed,peak)
 assert abs(float(video['duration'])-float(audio['duration']))<.16
 count+=4;print('speed',speed,'duration',duration,'audio peak',peak,'Hz')
delay=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-of','json',str(p/'speed-delay.mp4')]))['streams'];audio=next(x for x in delay if x['codec_type']=='audio');assert abs(float(audio['start_time'])-.25)<.05,audio['start_time'];count+=1;print('scaled delayed-audio start:',audio['start_time'])
silent=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-of','json',str(p/'speed-silent.mp4')]))['streams'];assert len(silent)==1 and silent[0]['codec_type']=='video';count+=1
before=np.frombuffer(subprocess.check_output(['ffmpeg','-v','error','-i',str(p/'logo.mp4'),'-frames:v','1','-f','rawvideo','-pix_fmt','rgb24','-']),dtype=np.uint8).reshape(180,320,3)
after=np.frombuffer(subprocess.check_output(['ffmpeg','-v','error','-i',str(p/'repaired.mp4'),'-frames:v','1','-f','rawvideo','-pix_fmt','rgb24','-']),dtype=np.uint8).reshape(180,320,3)
area=after[33:47,55:105].astype(float);expected=np.array([0,0,255]);err=abs(area-expected).mean();assert err<8,err
assert abs(after[100:120,200:220].astype(float)-before[100:120,200:220].astype(float)).mean()<4;count+=2
print('PASS media checks:',count,'repair pixel error:',round(err,3))
