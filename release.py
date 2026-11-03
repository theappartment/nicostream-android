#!/usr/bin/env python3
"""Build a signed official APK and publish its verified GitHub release assets.
The signing key stays local (or in an explicitly configured build environment).
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT=Path(__file__).resolve().parent
REPOSITORY='theappartment/nicostream-android'

def run(args,**kwargs):return subprocess.run([str(a) for a in args],check=True,**kwargs)
def output(args,env):return subprocess.check_output([str(a) for a in args],text=True,env=env)
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--publish',action='store_true');parser.add_argument('--version-code',type=int);parser.add_argument('--version-name');parser.add_argument('--notes',type=Path,default=ROOT/'RELEASE_NOTES.md');args=parser.parse_args()
    env=dict(os.environ);sdk=Path(env.get('ANDROID_HOME',Path.home()/'Library/Android/sdk'));env['ANDROID_HOME']=str(sdk)
    if not env.get('JAVA_HOME') and Path('/opt/homebrew/opt/openjdk').is_dir():env['JAVA_HOME']='/opt/homebrew/opt/openjdk'
    key=Path(env.get('ANDROID_KEYSTORE_FILE',ROOT/'.signing/demo.keystore'))
    if not key.is_file():raise SystemExit('Official signing key missing. Set ANDROID_KEYSTORE_FILE; never generate a replacement for existing users.')
    versions=dict(line.split('=',1) for line in (ROOT/'version.properties').read_text().splitlines() if '=' in line)
    code=args.version_code or int(versions['versionCode']);name=args.version_name or versions['versionName']
    if code<=0 or not re.fullmatch(r'\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?',name):raise SystemExit('Use a positive versionCode and a semantic versionName.')
    shared=ROOT.parents[1]/'scripts/sync-ios-addon-knowledge.py'
    if shared.is_file():run(['python3',shared],env=env)
    gradle=ROOT/('gradlew.bat' if os.name=='nt' else 'gradlew')
    run([gradle,'--no-daemon','testDebugUnitTest','assembleRelease',f'-PappVersionCode={code}',f'-PappVersionName={name}'],cwd=ROOT,env=env)
    apk=ROOT/'build/outputs/apk/release/NicoStreamDemo-release.apk'
    tools=sorted((sdk/'build-tools').glob('*'),key=lambda p:tuple(int(n) for n in re.findall(r'\d+',p.name)))
    if not tools:raise SystemExit('Android SDK build-tools missing.')
    suffix='.bat' if os.name=='nt' else ''
    signature=output([tools[-1]/('apksigner'+suffix),'verify','--print-certs',apk],env)
    actual=re.search(r'certificate SHA-256 digest: ([a-fA-F0-9]+)',signature)
    expected=(ROOT/'official-signing.sha256').read_text().strip().lower()
    if not actual or actual.group(1).lower()!=expected:raise SystemExit('APK signing certificate differs from the official installed app.')
    badging=output([tools[-1]/('aapt.exe' if os.name=='nt' else 'aapt'),'dump','badging',apk],env)
    package=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging)
    if not package or package.groups()!=('com.nicostream.demo',str(code),name):raise SystemExit('Unexpected APK identity/version.')
    folder=ROOT/'build/releases'/f'android-v{name}';folder.mkdir(parents=True,exist_ok=True);target=folder/'NicoStream.apk';shutil.copy2(apk,target)
    sha=hashlib.sha256(target.read_bytes()).hexdigest()
    manifest={'schema':1,'packageName':'com.nicostream.demo','versionCode':code,'versionName':name,'minSdk':26,'apkName':target.name,'size':target.stat().st_size,'sha256':sha}
    (folder/'nicostream-update.json').write_text(json.dumps(manifest,indent=2)+'\n')
    (folder/'SHA256SUMS').write_text(f'{sha}  NicoStream.apk\n')
    print('Verified official release:',folder,flush=True)
    if args.publish:
        if not args.notes.is_file():raise SystemExit('Release notes file missing.')
        tag=f'android-v{name}'
        run(['gh','release','create',tag,target,folder/'nicostream-update.json',folder/'SHA256SUMS','--repo',REPOSITORY,'--target','main','--draft','--title',f'NicoStream Android {name}','--notes-file',args.notes],env=env)
        run(['gh','release','edit',tag,'--repo',REPOSITORY,'--draft=false','--latest'],env=env)
        print(f'https://github.com/{REPOSITORY}/releases/tag/{tag}')
if __name__=='__main__':main()
