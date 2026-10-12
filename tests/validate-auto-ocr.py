#!/usr/bin/env python3
import csv, io, json, pathlib, subprocess, sys
from PIL import Image,ImageDraw,ImageFont
import numpy as np
out=pathlib.Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
image=Image.new('RGB',(1000,1000),(25,65,110));draw=ImageDraw.Draw(image)
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',30)
draw.text((25,935),'@creator',font=font,fill='white');draw.text((845,935),'TikTok',font=font,fill='white')
draw.text((380,790),'KEEP SUBTITLE',font=font,fill='yellow')
image.save(out/'marked.png')
tsv=subprocess.check_output(['tesseract',str(out/'marked.png'),'stdout','--tessdata-dir',sys.argv[2],'-l','chi_sim+eng','--psm','11','-c','tessedit_create_tsv=1'],text=True)
texts=[];groups={}
for r in csv.DictReader(io.StringIO(tsv),delimiter='\t'):
    if r['level']!='5' or not r['text'].strip():continue
    left,top,width,height=[int(r[x]) for x in ('left','top','width','height')]
    word={'text':r['text'],'confidence':float(r['conf']),'left':left,'top':top,'right':left+width,'bottom':top+height};texts.append(word)
    groups.setdefault(tuple(r[k] for k in ('block_num','par_num','line_num')),[]).append(word)
for words in groups.values():
    texts.append({'text':' '.join(w['text'] for w in words),'confidence':sum(w['confidence'] for w in words)/len(words),'left':min(w['left'] for w in words),'top':min(w['top'] for w in words),'right':max(w['right'] for w in words),'bottom':max(w['bottom'] for w in words)})
(out/'ocr.json').write_text(json.dumps(texts))
def run(*a):subprocess.run(list(map(str,a)),check=True,stdout=subprocess.DEVNULL)
run('ffmpeg','-v','error','-y','-loop','1','-i',out/'marked.png','-t','1','-r','30','-c:v','libx264','-threads','2','-pix_fmt','yuv420p',out/'marked.mp4')
run('java','-cp',sys.argv[3],'com.zizai.watermark.OcrFixtureTest',out/'ocr.json',out/'filter.json',out/'marked.mp4',out/'repaired.mp4')
run('ffmpeg',*json.loads((out/'filter.json').read_text()))
run('ffmpeg','-v','error','-y','-i',out/'repaired.mp4','-frames:v','1',out/'repaired.png')
fixed=np.array(Image.open(out/'repaired.png').convert('RGB')).astype(float);original=np.array(image).astype(float)
for x1,x2 in [(25,220),(845,965)]:
    before=original[935:973,x1:x2].mean(2);after=fixed[935:973,x1:x2].mean(2)
    assert (after>190).sum() < (before>190).sum()*.15, 'watermark text remains'
assert np.abs(fixed[790:830,380:645]-original[790:830,380:645]).mean()<12, 'subtitle changed'
print('PASS OCR -> repair media: both detected text watermarks removed on simple background, subtitle pixels retained')
