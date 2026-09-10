#!/usr/bin/env python3
"""Compile isolated tests and run with the device's real Android framework.
Usage: run-device-tests.py SERIAL SDK_ROOT
Does not install or modify GmsCompat/Wallet. Leaves one dex in /data/local/tmp.
"""
import pathlib
import subprocess
import sys
import tempfile

serial, sdk_arg = sys.argv[1:]
sdk = pathlib.Path(sdk_arg)
repo = pathlib.Path(__file__).resolve().parents[3]
android = sdk / 'platforms/android-36/android.jar'
d8 = sdk / 'build-tools/37.0.0/d8'

def run(*args):
    subprocess.run([str(a) for a in args], check=True)

with tempfile.TemporaryDirectory(prefix='gms-provider-tests-') as tmp:
    out = pathlib.Path(tmp)
    classes = out / 'classes'
    dex = out / 'dex'
    classes.mkdir()
    dex.mkdir()
    src = repo / 'lib/src/app/grapheneos/gmscompat/lib'
    tests = repo / 'lib/tests/providerinstaller'
    run('javac', '--release', '17', '-cp', android, '-d', classes,
        tests / 'compile-only/android/os/BinderWrapper.java',
        *sorted((src / 'providerinstaller').glob('*.java')),
        src / 'util/ServiceConnectionWrapper.java', *sorted(tests.glob('*Test.java')))
    # The hidden API stub is compile-only; Android supplies the real class at runtime.
    run('jar', 'cf', out / 'platform-signature.jar', '-C', classes, 'android')
    run(d8, '--lib', android, '--lib', out / 'platform-signature.jar', '--output', dex,
        *sorted((classes / 'app').rglob('*.class')))
    target = '/data/local/tmp/gmscompat-provider-test.dex'
    run('adb', '-s', serial, 'push', dex / 'classes.dex', target)
    run('adb', '-s', serial, 'shell', 'app_process', '-Djava.class.path=' + target,
        '/system/bin', 'app.grapheneos.gmscompat.lib.providerinstaller.ProviderInstallerCompatTest')
