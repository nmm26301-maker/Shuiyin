#!/usr/bin/env python3
"""JVM regression checks after build-offline.py. org.json jar must be a real implementation."""
import argparse, pathlib, subprocess, os
p=argparse.ArgumentParser()
p.add_argument('--ecj',required=True);p.add_argument('--json',required=True)
p.add_argument('--deps',required=True);p.add_argument('--sample-video',required=True)
p.add_argument('--live-xhs',help='Optional real XHS share URL; requires public DNS and HTTPS connectivity')
a=p.parse_args();root=pathlib.Path(__file__).resolve().parents[1]
out=root/'build-offline/test-classes';out.mkdir(exist_ok=True)
cp=os.pathsep.join(map(str,[out,pathlib.Path(a.json).resolve(),root/'build-offline/classes',root/'build-offline/android-api.jar']+list(pathlib.Path(a.deps).resolve().glob('*.jar'))))
subprocess.run(['java','-jar',str(pathlib.Path(a.ecj).resolve()),'-17','-encoding','UTF-8','-warn:none','-classpath',cp,'-d',str(out)]+[str(root/'tests'/name) for name in ['LinkTests.java','EngineTests.java','XhsTests.java','TransferTests.java','SaveWorkflowTests.java','MultiRegionTests.java','CatalogTests.java','GalleryQueueTests.java','DownloadSiteTests.java']],check=True)
tests=[('LinkTests',[str(pathlib.Path(a.sample_video).resolve())]),('EngineTests',[str(root/'tests/fixtures/engine-generic.json')]),('XhsTests',[str(root/'tests/fixtures/xhs-mobile.html')])]
if a.live_xhs:tests[-1][1].extend([a.live_xhs,str(root/'build-offline/live-selected-url.txt')])
for name,args in tests:subprocess.run(['java','-cp',cp,'com.zizai.watermark.'+name]+args,check=True)
subprocess.run(['java','-cp',cp,'com.zizai.watermark.TransferTests',str(root/'app/src/main/res/raw/ytdlp'),str(pathlib.Path(a.sample_video).resolve()),str(root/'build-offline/transfer-test.mp4')],check=True)
subprocess.run(['python3',str(root/'tests/validate-platforms.py')],check=True)

subprocess.run(['java','-cp',cp,'com.zizai.watermark.SaveWorkflowTests',str(root/'build-offline/web-scan.js'),str(root/'build-offline/save-repair-fixture')],check=True)
subprocess.run(['node',str(root/'tests/web-scanner-tests.js'),str(root/'build-offline/web-scan.js')],check=True)
subprocess.run(['python3',str(root/'tests/validate-save-repair.py'),str(root/'build-offline/save-repair-fixture')],check=True)

subprocess.run(['java','-cp',cp,'com.zizai.watermark.MultiRegionTests',str(root/'build-offline/multi-region-fixture')],check=True)
subprocess.run(['python3',str(root/'tests/validate-multi-regions.py'),str(root/'build-offline/multi-region-fixture')],check=True)

subprocess.run(['java','-cp',cp,'com.zizai.watermark.CatalogTests',str(root/'app/src/main/res/raw/ytdlp'),str(root/'app/src/main/res/raw/extractor_rules.txt')],check=True)

subprocess.run(['python3',str(root/'tests/test-gallery.py')],check=True)
subprocess.run(['java','-cp',cp,'com.zizai.watermark.GalleryQueueTests',str(root/'build-offline/gallery-fixture.jsonl')],check=True)

subprocess.run(['java','-cp',cp,'com.zizai.watermark.DownloadSiteTests',str(root/'build-offline/download-site-form.js')],check=True)
subprocess.run(['node',str(root/'tests/download-site-form-tests.js'),str(root/'build-offline/download-site-form.js')],check=True)
