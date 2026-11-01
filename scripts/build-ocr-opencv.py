"""Build minimal OpenCV JNI for isolated V6 research; keeps upstream algorithms intact.

Downloads pinned public source only. No screenshots or OCR text are transmitted.
Requires the project's SDK CMake/NDK and JDK. Outputs stay under ignored .tools/.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).absolute().parents[1]
WORK = ROOT / '.tools/ocr-native'
COMMIT = '49486f61fb25722cbcf586b7f4320921d46fb38e'
SOURCE_SHA256 = '8f00b42869ab2836be36090f6631ae4c38ba59171e22a69a8e0f92f8ef1771d4'
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--abi', choices=['arm64-v8a', 'x86_64'], required=True)
p.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', ROOT / '.tools/android-sdk')))
p.add_argument('--base-aar', type=Path, help='Official OpenCV 4.12.0 Android AAR; checked against the pinned hash')
p.add_argument('--jobs', type=int, default=6)
p.add_argument('--prepare-only', action='store_true')
a = p.parse_args()
WORK.mkdir(parents=True, exist_ok=True)
source = WORK / 'opencv'
archive = WORK / 'opencv-4.12.0.tar.gz'
if not archive.is_file():
    print('Downloading pinned OpenCV source', flush=True)
    with urllib.request.urlopen(f'https://codeload.github.com/opencv/opencv/tar.gz/{COMMIT}', timeout=120) as r:
        with archive.open('wb') as out:
            __import__('shutil').copyfileobj(r, out)
digest = hashlib.sha256(archive.read_bytes()).hexdigest()
assert digest == SOURCE_SHA256, 'Pinned OpenCV source checksum mismatch'
lock = WORK / 'opencv-source.json'
if lock.is_file():
    assert json.loads(lock.read_text())['sha256'] == digest, 'Cached source checksum changed'
else:
    lock.write_text(json.dumps({'repository': 'https://github.com/opencv/opencv', 'version': '4.12.0',
                               'commit': COMMIT, 'sha256': digest}, indent=2))
if not (source / 'CMakeLists.txt').is_file():
    print('Extracting source', flush=True)
    source.mkdir(exist_ok=True)
    with tarfile.open(archive) as tar:
        for member in tar.getmembers():
            parts = Path(member.name).parts[1:]
            if not parts or member.issym() or member.islnk():
                continue
            member.name = str(Path(*parts))
            target = (source / member.name).resolve()
            assert target.is_relative_to(source.resolve())
            tar.extract(member, source, filter='data')
if a.prepare_only:
    print('Prepared pinned OpenCV', digest)
    sys.exit(0)
cmake = a.sdk / 'cmake/3.22.1/bin/cmake.exe'
ndk = a.sdk / 'ndk/28.2.13676358'
build = WORK / ('opencv-' + a.abi)
env = dict(os.environ)
if not env.get('JAVA_HOME'):
    p.error('JAVA_HOME must point to JDK 21')
env['PATH'] = str(Path(env['JAVA_HOME']) / 'bin') + os.pathsep + env['PATH']
options = [str(cmake), '-S', str(source), '-B', str(build), '-G', 'Ninja',
           '-DCMAKE_MAKE_PROGRAM=' + str(cmake.parent / 'ninja.exe'),
           '-DCMAKE_TOOLCHAIN_FILE=' + str(ndk / 'build/cmake/android.toolchain.cmake'),
           '-DANDROID_ABI=' + a.abi, '-DANDROID_PLATFORM=android-30', '-DANDROID_STL=c++_shared',
           '-DCMAKE_BUILD_TYPE=MinSizeRel', '-DBUILD_LIST=core,imgproc,imgcodecs,java',
           '-DANDROID_PROJECTS_BUILD_TYPE=GRADLE', '-DWITH_KLEIDICV=OFF', '-DWITH_ADE=OFF',
           '-DBUILD_SHARED_LIBS=OFF', '-DBUILD_JAVA=ON', '-DBUILD_ANDROID_PROJECTS=OFF',
           '-DBUILD_ANDROID_EXAMPLES=OFF', '-DBUILD_TESTS=OFF', '-DBUILD_PERF_TESTS=OFF',
           '-DBUILD_EXAMPLES=OFF', '-DBUILD_opencv_apps=OFF', '-DBUILD_opencv_python3=OFF',
           '-DWITH_OPENCL=OFF', '-DWITH_IPP=OFF', '-DWITH_ITT=OFF', '-DWITH_TBB=OFF',
           '-DWITH_OPENEXR=OFF', '-DWITH_OPENJPEG=OFF', '-DWITH_JASPER=OFF', '-DWITH_WEBP=OFF',
           '-DWITH_TIFF=OFF', '-DWITH_FFMPEG=OFF', '-DWITH_PROTOBUF=OFF',
           '-DBUILD_PNG=ON', '-DBUILD_JPEG=ON', '-DWITH_PNG=ON', '-DWITH_JPEG=ON',
           '-DANDROID_SDK_ROOT=' + str(a.sdk), '-DPYTHON_DEFAULT_EXECUTABLE=' + sys.executable,
           '-DPYTHON3_EXECUTABLE=' + sys.executable]
print('Configuring', a.abi, flush=True)
subprocess.run(options, check=True, env=env)
print('Building minimal JNI', flush=True)
subprocess.run([str(cmake), '--build', str(build), '--target', 'opencv_java', '-j', str(a.jobs)], check=True, env=env)
# Keep official 4.12.0 Java bindings/resources/notices, replace native library only.
cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1/org.opencv/opencv/4.12.0'
original = a.base_aar.resolve() if a.base_aar else next(cache.rglob('opencv-4.12.0.aar'))
assert hashlib.sha256(original.read_bytes()).hexdigest() == 'f71846a313388d9da667a59be1e921669fcf792d1ba264b3898253ca789bc3f0', 'Official OpenCV AAR checksum mismatch'
target = WORK / ('opencv-minimal-' + a.abi + '.aar')
native = build / 'jni' / a.abi / 'libopencv_java4.so'
triple = {'arm64-v8a': 'aarch64-linux-android', 'x86_64': 'x86_64-linux-android'}[a.abi]
stdlib = ndk / 'toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib' / triple / 'libc++_shared.so'
stripped_stdlib = WORK / ('libc++-' + a.abi + '.so')
shutil.copyfile(stdlib, stripped_stdlib)
subprocess.run([str(ndk / 'toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe'),
                '--strip-unneeded', str(stripped_stdlib)], check=True)
with zipfile.ZipFile(original) as src, zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED) as dst:
    for item in src.infolist():
        if not item.filename.startswith(('jni/', 'prefab/')):
            dst.writestr(item, src.read(item))
    dst.write(native, f'jni/{a.abi}/libopencv_java4.so')
    dst.write(stripped_stdlib, f'jni/{a.abi}/libc++_shared.so')
metadata = {'abi': a.abi, 'sourceSha256': digest, 'sourceCommit': COMMIT,
            'baseAarSha256': hashlib.sha256(original.read_bytes()).hexdigest(),
            'nativeBytes': native.stat().st_size, 'nativeSha256': hashlib.sha256(native.read_bytes()).hexdigest(),
            'cppRuntimeBytes': stripped_stdlib.stat().st_size,
            'cppRuntimeSha256': hashlib.sha256(stripped_stdlib.read_bytes()).hexdigest(),
            'aarSha256': hashlib.sha256(target.read_bytes()).hexdigest(),
            'modules': ['core', 'imgproc', 'imgcodecs', 'java'], 'buildType': 'MinSizeRel',
            'ndk': '28.2.13676358', 'modelPrecisionChanged': False}
target.with_suffix('.json').write_text(json.dumps(metadata, indent=2) + '\n')
print(json.dumps(metadata, indent=2), flush=True)
