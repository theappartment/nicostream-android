#!/usr/bin/env python3
"""Build and copy the Media3 demo APK, preserving its local development key."""
import os
import pathlib
import shutil
import subprocess
ROOT=pathlib.Path(__file__).resolve().parent
ENV=dict(os.environ)
ENV.setdefault('JAVA_HOME','/opt/homebrew/opt/openjdk')
ENV.setdefault('ANDROID_HOME',str(pathlib.Path.home()/'Library/Android/sdk'))
shared=ROOT.parents[1]/'scripts/sync-ios-addon-knowledge.py'
if shared.is_file():subprocess.run(['python3',str(shared)],check=True,env=ENV)
key=ROOT/'.signing/demo.keystore'
if not key.exists():
    key.parent.mkdir(exist_ok=True)
    subprocess.run([str(pathlib.Path(ENV['JAVA_HOME'])/'bin/keytool'),'-genkeypair','-keystore',str(key),'-storepass','android','-keypass','android','-alias','demo','-keyalg','RSA','-validity','3650','-dname','CN=NicoStream Demo'],check=True,env=ENV)
subprocess.run([str(ROOT/'gradlew'),'--no-daemon','assembleDebug'],cwd=ROOT,env=ENV,check=True)
source=ROOT/'build/outputs/apk/debug/NicoStreamDemo-debug.apk'
target=ROOT/'build/NicoStream-demo.apk'
shutil.copy2(source,target)
print(target)
