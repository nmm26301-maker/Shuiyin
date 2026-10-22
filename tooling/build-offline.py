#!/usr/bin/env python3
"""Build with Android SDK + a JDK, or ECJ on a JRE. Requires cached engine dependencies; see prepare-engine.py.
Example: python3 tooling/build-offline.py --sdk /path/to/sdk --ecj /path/to/ecj.jar
"""
import argparse,pathlib,subprocess,shutil,zipfile,os,json,hashlib,uuid
p=argparse.ArgumentParser();p.add_argument('--sdk',default=os.getenv('ANDROID_HOME'));p.add_argument('--ecj');p.add_argument('--deps',required=True,help='Verified prepare-engine.py cache');p.add_argument('--output',default='build-offline/WatermarkWorkshop-0.6.8.apk');a=p.parse_args()
if not a.sdk:p.error('--sdk or ANDROID_HOME is required')
root=pathlib.Path(__file__).resolve().parents[1];sdk=pathlib.Path(a.sdk).resolve();bt=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-35/android.jar';build=root/'build-offline';build.mkdir(exist_ok=True)
gallery=json.loads((root/'tooling/gallery-bundle.json').read_text())
if hashlib.sha256((root/'app/src/main/res/raw/gallery_bundle').read_bytes()).hexdigest()!=gallery['sha256'] or gallery['sha256'] not in (root/'app/src/main/java/com/zizai/watermark/GalleryEngine.java').read_text():raise SystemExit('Gallery bundle checksum does not match installer; run gallery tests after rebuilding it')
deps=pathlib.Path(a.deps).resolve();items=json.loads((root/'tooling/engine-lock.json').read_text());jars=[]
for item in items:
 file=deps/item['name']
 if not file.exists() or hashlib.sha256(file.read_bytes()).hexdigest()!=item['sha256']:raise SystemExit('Run prepare-engine.py first: '+item['name'])
 if item['type']=='aar':
  with zipfile.ZipFile(file) as archive:(deps/item['jar']).write_bytes(archive.read('classes.jar'))
 jars.append(deps/(item.get('jar') or item['name']))
for directory in ('classes','dex','generated'):
 shutil.rmtree(build/directory,ignore_errors=True);(build/directory).mkdir()
def run(*cmd):subprocess.run([str(x) for x in cmd],check=True,cwd=root)
manifest=(root/'app/src/main/AndroidManifest.xml').read_text().replace('<manifest xmlns:', '<manifest package="com.zizai.watermark" xmlns:').replace('<application','<application android:debuggable="true"',1)
(build/'AndroidManifest.xml').write_text(manifest)
run(bt/'aapt2','compile','--dir',root/'app/src/main/res','-o',build/'resources.zip')
run(bt/'aapt2','link','-o',build/'resources.apk','--manifest',build/'AndroidManifest.xml','-I',android,build/'resources.zip','--min-sdk-version','29','--target-sdk-version','33','--version-code','14','--version-name','0.6.8','--java',build/'generated','--extra-packages','com.yausername.youtubedl_android')
sources=sorted((root/'app/src/main/java').rglob('*.java'))+sorted((build/'generated').rglob('*.java'))
if a.ecj:
 # The Android platform jar contains Java platform stubs. Use the JRE module
 # implementations during ECJ type checking to avoid split packages on JDK17.
 api=build/'android-api.jar'
 with zipfile.ZipFile(android) as src,zipfile.ZipFile(api,'w') as dst:
  for entry in src.infolist():
   if not entry.filename.startswith(('java/','javax/','org/w3c/','org/xml/')):dst.writestr(entry,src.read(entry.filename))
 run('java','-jar',pathlib.Path(a.ecj).resolve(),'-17','-encoding','UTF-8','-warn:none','-classpath',os.pathsep.join(map(str,[api]+jars)),'-d',build/'classes',*sources)
else:
 run('javac','-source','17','-target','17','-encoding','UTF-8','-classpath',os.pathsep.join(map(str,[android]+jars)),'-d',build/'classes',*sources)
classes=build/'classes.jar'
with zipfile.ZipFile(classes,'w') as jar:
 for file in (build/'classes').rglob('*.class'):jar.write(file,file.relative_to(build/'classes'))
run('java','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','29','--lib',android,'--output',build/'dex',classes,*jars)
shutil.copy2(build/'resources.apk',build/'unsigned.apk')
with zipfile.ZipFile(build/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as apk:
 for dex in (build/'dex').glob('*.dex'):apk.write(dex,dex.name)
 for item in items:
  if item['type']=='aar':
   with zipfile.ZipFile(deps/item['name']) as archive:
    for name in archive.namelist():
     if name.startswith('jni/arm64-v8a/') and name.endswith('.so'):apk.writestr(name.replace('jni/','lib/',1),archive.read(name))
run(bt/'zipalign','-f','4',build/'unsigned.apk',build/'aligned.apk')
key=root/'tooling/development.jks'
if not key.exists():run('keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=WatermarkWorkshop Development')
output=pathlib.Path(a.output);output=output if output.is_absolute() else root/output;output.parent.mkdir(parents=True,exist_ok=True)
temporary=output.with_name(output.name+'.signing-'+uuid.uuid4().hex)
try:
 run('java','-jar',bt/'lib/apksigner.jar','sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--ks-key-alias','androiddebugkey','--out',temporary,build/'aligned.apk')
 run('java','-jar',bt/'lib/apksigner.jar','verify','--verbose',temporary)
 with zipfile.ZipFile(temporary) as archive:
  if archive.testzip() is not None:raise RuntimeError('Signed APK contains corrupt ZIP entries')
 os.replace(temporary,output)
 run('java','-jar',bt/'lib/apksigner.jar','verify','--verbose',output)
finally:
 if temporary.exists():temporary.unlink()
print(output)
