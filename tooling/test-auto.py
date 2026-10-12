#!/usr/bin/env python3
"""Host integration tests after build-offline.py, including the production JNA binding."""
import argparse, os, pathlib, subprocess
p=argparse.ArgumentParser();p.add_argument('--ecj',required=True);p.add_argument('--json',required=True)
p.add_argument('--jna',required=True,help='Desktop JNA 5.18.1 JAR');p.add_argument('--sherpa-lib',required=True,help='Linux sherpa-onnx 1.13.8 shared lib directory')
p.add_argument('--audio',required=True,help='A non-silent music+vocals WAV longer than 6 seconds');a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];out=root/'build-offline/test-auto';out.mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join(map(str,[out,root/'build-offline/classes',pathlib.Path(a.jna).resolve(),pathlib.Path(a.json).resolve()]))
def run(*args,**kw):subprocess.run(list(map(str,args)),check=True,cwd=root,**kw)
run('java','-jar',pathlib.Path(a.ecj).resolve(),'-17','-encoding','UTF-8','-warn:none','-classpath',cp,'-d',out,*[root/'tests'/n for n in ('AutoProcessingTests.java','SourceSeparatorSmoke.java','OcrFixtureTest.java')])
run('java','-cp',cp,'com.zizai.watermark.AutoProcessingTests',out/'media')
run('python3',root/'tests/validate-bgm-media.py',out/'media')
run('python3',root/'tests/validate-auto-ocr.py',out/'ocr',root/'app/src/main/assets/auto_models/tessdata',cp)
run('ffmpeg','-v','error','-y','-i',pathlib.Path(a.audio).resolve(),'-ar','44100','-ac','2','-f','f32le',out/'input.f32')
lib=pathlib.Path(a.sherpa_lib).resolve()
run('java','-cp',cp,'com.zizai.watermark.SourceSeparatorSmoke',lib/'libsherpa-onnx-c-api.so',root/'app/src/main/assets/auto_models/UVR_MDXNET_9482.onnx',out/'input.f32',out/'vocals.f32',env=dict(os.environ,LD_LIBRARY_PATH=str(lib)))
