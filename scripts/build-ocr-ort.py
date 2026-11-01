"""Build reduced-operator ORT 1.21.1 for V6 research, preserving model precision.

Requires prepared V6 models/operator config, NDK 28.2 and CMake 3.31.
Only public pinned source is downloaded. No OCR inputs leave this machine.
Run via the ASCII workspace junction on Windows to avoid llvm-ar path errors.
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
ORT_COMMIT = '8f7cce3a49fdbdac96e0868b75b7d0159db7ac7f'
ORT_SHA = 'f867db83cb997f93b45a572ca4a4d409c7dad6f614179d59dd4b08eea5f9df02'
EIGEN_COMMIT = '1d8b82b0740839c0de7f1242a3585e3390ff5f33'
EIGEN_SHA = 'c8adb6532fbfacd2ec1418c66404159b00c34d855bdc19cefa1f0db0e5e0aa5f'
BASE_AAR_SHA = '30e594a4b9246fe3ca25768570e90f71e6d33ceb7b7dd72f92dcd7c267611d3f'
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--abi', choices=['arm64-v8a', 'x86_64'], required=True)
p.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', ROOT / '.tools/android-sdk')))
p.add_argument('--base-aar', type=Path, help='Official ONNX Runtime 1.21.1 Android AAR; checked against the pinned hash')
p.add_argument('--jobs', type=int, default=1)
p.add_argument('--dependency-cache', type=Path, help='Previously verified FetchContent sources; bypasses repeated patch/download')
p.add_argument('--package-only', action='store_true')
a = p.parse_args()
if not str(ROOT).isascii():
    p.error('Run through an ASCII workspace path (for example C:/Users/Public/simple-ledger/scripts/build-ocr-ort.py)')
WORK.mkdir(parents=True, exist_ok=True)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source(archive, url, expected, folder):
    if not archive.is_file():
        with urllib.request.urlopen(url, timeout=120) as response, archive.open('wb') as out:
            shutil.copyfileobj(response, out)
    assert sha(archive) == expected, f'Pinned source checksum mismatch: {archive.name}'
    if folder.is_dir():
        return
    folder.mkdir(parents=True)
    with tarfile.open(archive) as tar:
        for item in tar.getmembers():
            parts = Path(item.name).parts[1:]
            if not parts or not item.isfile():
                continue
            target = folder.joinpath(*parts)
            assert target.resolve().is_relative_to(folder.resolve())
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(tar.extractfile(item).read())


source(WORK / 'onnxruntime-1.21.1.tar.gz',
       f'https://codeload.github.com/microsoft/onnxruntime/tar.gz/{ORT_COMMIT}', ORT_SHA, WORK / 'onnxruntime')
source(WORK / 'eigen-pinned.tar.gz',
       f'https://codeload.github.com/eigen-mirror/eigen/tar.gz/{EIGEN_COMMIT}', EIGEN_SHA, WORK / 'eigen-pinned')
build = WORK / ('ort-slim-arm64' if a.abi == 'arm64-v8a' else 'ort-slim-x86_64')
config = WORK / 'v6-required-ops.config'
assert config.is_file(), 'Run prepare-ocr-ort-config.py first'
driver = WORK / 'onnxruntime/tools/ci_build/build.py'
# Upstream realpath resolves a junction to the Chinese workspace name. LLVM's
# archive response files fail on that name on Windows. Change path resolution
# only in a separate ignored driver; never modify ORT algorithms or the original.
text = driver.read_text(encoding='utf-8')
patched = text.replace('os.path.realpath(__file__)', 'os.path.abspath(__file__)').replace(
    'os.path.realpath(os.path.dirname(__file__))', 'os.path.abspath(os.path.dirname(__file__))')
shim = driver.with_name('build_ledger_ascii.py')
shim.write_text(patched, encoding='utf-8')
cmd = [sys.executable, str(shim), '--build_dir', str(build), '--config', 'MinSizeRel',
       '--update', '--build', '--parallel', str(a.jobs), '--skip_tests', '--skip_submodule_sync',
       '--cmake_generator', 'Ninja', '--cmake_path', str(WORK / 'python-build/cmake/data/bin/cmake.exe'),
       '--android', '--android_abi', a.abi, '--android_api', '30', '--android_sdk_path', str(a.sdk),
       '--android_ndk_path', str(a.sdk / 'ndk/28.2.13676358'), '--android_cpp_shared', '--build_shared_lib',
       '--target', 'onnxruntime', '--include_ops_by_config', str(config), '--disable_contrib_ops',
       '--disable_ml_ops', '--disable_rtti', '--compile_no_warning_as_error', '--cmake_extra_defines',
       'FETCHCONTENT_SOURCE_DIR_EIGEN=' + str(WORK / 'eigen-pinned'), 'onnxruntime_BUILD_UNIT_TESTS=OFF',
       'FETCHCONTENT_BASE_DIR=' + str(build / 'MinSizeRel/_deps')]
if a.abi == 'arm64-v8a':
    # ORT 1.21.1's ARM FP16 provider still references this shared activation
    # helper when contrib kernels are disabled. Retain the original helper,
    # without enabling unused contrib kernels or changing inference code.
    build.mkdir(parents=True, exist_ok=True)
    hook = build / 'ledger-provider-helper.cmake'
    hook.write_text('''include_guard(GLOBAL)
function(ledger_retain_activation_helper)
  target_sources(onnxruntime_providers PRIVATE "${ONNXRUNTIME_ROOT}/contrib_ops/cpu/fused_activation.cc")
endfunction()
cmake_language(DEFER DIRECTORY "${CMAKE_SOURCE_DIR}" CALL ledger_retain_activation_helper)
''', encoding='utf-8')
    cmd += ['CMAKE_PROJECT_INCLUDE=' + str(hook)]
if a.dependency_cache:
    for folder in sorted(a.dependency_cache.glob('*-src')):
        if folder.name != 'eigen-src' and any(folder.iterdir()):
            cmd += ['FETCHCONTENT_SOURCE_DIR_' + folder.name[:-4].upper() + '=' + str(folder)]
if not a.package_only:
    env = os.environ.copy()
    env['PATH'] = str(a.sdk / 'cmake/3.22.1/bin') + os.pathsep + env['PATH']
    # Upstream omits Ninja -j when --parallel=1; CMake's explicit environment
    # prevents Ninja falling back to many concurrent memory-heavy compilations.
    env['CMAKE_BUILD_PARALLEL_LEVEL'] = str(a.jobs)
    subprocess.run(cmd, cwd=ROOT, env=env, check=True)

native = build / 'MinSizeRel/libonnxruntime.so'
assert native.is_file(), 'ORT build did not produce a shared library'
strip = a.sdk / 'ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe'
slim = WORK / ('libonnxruntime-reduced-' + a.abi + '.so')
shutil.copyfile(native, slim)
subprocess.run([str(strip), '--strip-unneeded', str(slim)], check=True)

# The official Android JNI binary imports NNAPI even when the application only
# selects CPU. Rebuild the unchanged same-version bridge against this CPU-only
# runtime, so unsupported providers retain upstream's explicit error path.
jni_work = build / 'jni-cpu'
jni_work.mkdir(parents=True, exist_ok=True)
headers = jni_work / 'headers'
classes = jni_work / 'classes'
headers.mkdir(exist_ok=True)
classes.mkdir(exist_ok=True)
java_root = WORK / 'onnxruntime/java'
java_sources = sorted((java_root / 'src/main/java').rglob('*.java')) + sorted((java_root / 'src/main/android').rglob('*.java'))
java_args = jni_work / 'java-sources.rsp'
java_args.write_text('\n'.join('"' + str(f).replace('\\', '/') + '"' for f in java_sources), encoding='utf-8')
jdk = Path(os.environ['JAVA_HOME'])
subprocess.run([str(jdk / 'bin/javac.exe'), '--release', '8', '-encoding', 'UTF-8', '-h', str(headers), '-d', str(classes), '@' + str(java_args)], check=True)
toolchain = a.sdk / 'ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64'
target = 'aarch64-none-linux-android30' if a.abi == 'arm64-v8a' else 'x86_64-none-linux-android30'
jni = jni_work / 'libonnxruntime4j_jni.so'
native_root = java_root / 'src/main/native'
jni_cmd = [str(toolchain / 'bin/clang.exe'), '--target=' + target,
           '--sysroot=' + str(toolchain / 'sysroot'), '-shared', '-fPIC', '-Os', '-std=c11',
           '-ffunction-sections', '-fdata-sections', '-Wl,--gc-sections', '-Wl,--no-undefined',
           '-Wl,-z,max-page-size=16384', '-Wl,-soname,libonnxruntime4j_jni.so']
for inc in [headers, WORK / 'onnxruntime/include', build / 'MinSizeRel',
            WORK / 'onnxruntime/include/onnxruntime/core/session',
            WORK / 'onnxruntime/orttraining/orttraining/training_api/include']:
    jni_cmd += ['-I', str(inc)]
jni_cmd += [str(f) for f in sorted(native_root.glob('*.c'))] + [str(slim), '-llog', '-ldl', '-o', str(jni)]
subprocess.run(jni_cmd, check=True)
subprocess.run([str(strip), '--strip-unneeded', str(jni)], check=True)
gradle_home = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle'))
base = [a.base_aar.resolve()] if a.base_aar else list((gradle_home / 'caches/modules-2/files-2.1/com.microsoft.onnxruntime/onnxruntime-android/1.21.1').glob('*/*.aar'))
assert len(base) == 1, 'Resolve the official ORT 1.21.1 AAR first'
assert sha(base[0]) == BASE_AAR_SHA, 'Official same-version Java/JNI AAR checksum mismatch'
output = WORK / ('onnxruntime-reduced-' + a.abi + '.aar')
# Keep official Java classes, resources, licenses and notices. Replace runtime
# and same-version JNI with matched CPU builds; no other ABI is packaged.
with zipfile.ZipFile(base[0]) as src, zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as dst:
    for item in src.infolist():
        if item.filename.startswith('jni/'):
            continue
        dst.writestr(item, src.read(item.filename))
    dst.write(slim, f'jni/{a.abi}/libonnxruntime.so')
    dst.write(jni, f'jni/{a.abi}/libonnxruntime4j_jni.so')
meta = {'abi': a.abi, 'version': '1.21.1', 'sourceCommit': ORT_COMMIT, 'sourceSha256': ORT_SHA,
        'eigenCommit': EIGEN_COMMIT, 'eigenSourceSha256': EIGEN_SHA, 'baseAarSha256': sha(base[0]),
        'opsConfigSha256': sha(config), 'nativeBytes': slim.stat().st_size, 'nativeSha256': sha(slim),
        'aarSha256': sha(output), 'buildType': 'MinSizeRel', 'ndk': '28.2.13676358',
        'driverPatch': 'realpath -> abspath for two source-directory resolutions only',
        'exceptionsEnabled': True, 'onnxLoadingRetained': True, 'modelPrecisionChanged': False,
        'jniBridge': 'unmodified same-version C sources rebuilt CPU-only; official Java classes retained',
        'jniNativeBytes': jni.stat().st_size, 'jniNativeSha256': sha(jni), 'jniCommand': jni_cmd, 'command': cmd}
meta['armProviderBuildFix'] = 'retain original fused_activation.cc helper' if a.abi == 'arm64-v8a' else None
(WORK / ('onnxruntime-reduced-' + a.abi + '.json')).write_text(json.dumps(meta, indent=2) + '\n', encoding='utf-8')
print(json.dumps({k: v for k, v in meta.items() if k != 'command'}, indent=2))
