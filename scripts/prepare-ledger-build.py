"""Prepare the default V6 app from pinned public inputs before running Gradle.

Native AARs are versioned in third_party/ocr-runtime; no binary host or full-runtime
fallback is used. SDK/models/notices come from the existing upstream lock. Once
prepared, --offline verifies/reuses the cache without making network requests.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def verify(path, expected, size=None):
    if not path.is_file():
        raise RuntimeError(f'Missing pinned input: {path}')
    if size is not None and path.stat().st_size != size:
        raise RuntimeError(f'Pinned input size mismatch: {path}')
    if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        raise RuntimeError(f'Pinned input checksum mismatch: {path}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--abi', choices=['all', 'arm64-v8a', 'x86_64'], default='all')
    parser.add_argument('--offline', action='store_true')
    args = parser.parse_args()
    native = json.loads((ROOT / 'third_party/ocr-runtime/config/ledger-v6-runtime-lock.json').read_text(encoding='utf-8'))
    upstream = json.loads((ROOT / 'third_party/ocr-runtime/config/upstream-lock.json').read_text(encoding='utf-8'))
    bundle = ROOT / 'third_party/ocr-runtime'
    files = [item for item in native['files'] if args.abi == 'all' or item['build']['abi'] == args.abi]
    if len(files) != (4 if args.abi == 'all' else 2):
        raise RuntimeError('Expected one reduced ONNX and one minimal OpenCV runtime per ABI')
    # Verify the complete selected bundle before modifying the ignored build cache.
    for item in files:
        source = bundle / Path(item['path']).name
        verify(source, item['sha256'], item['bytes'])
        with zipfile.ZipFile(source) as archive:
            abis = {name.split('/')[1] for name in archive.namelist() if name.startswith('jni/') and name.endswith('.so')}
            if abis != {item['build']['abi']}:
                raise RuntimeError(f'Unexpected native ABIs in {source.name}: {sorted(abis)}')
    for notice in upstream['notices']:
        verify(bundle / 'licenses' / notice['file'], notice['sha256'])
    for item in files:
        target = ROOT / item['path']
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.is_file() or hashlib.sha256(target.read_bytes()).hexdigest() != item['sha256']:
            shutil.copyfile(bundle / target.name, target)
        verify(target, item['sha256'], item['bytes'])
    # Seed notices from the same checked-in distribution, avoiding three extra API calls.
    for notice in upstream['notices']:
        target = ROOT / '.tools/ocr-ab/v6-assets/licenses' / notice['file']
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(bundle / 'licenses' / notice['file'], target)
    command = [sys.executable, str(ROOT / 'scripts/prepare-paddle-sdk.py')]
    if args.offline:
        command.append('--offline')
    subprocess.run(command, cwd=ROOT, check=True)
    print(f'Prepared {len(files)} verified reduced AARs for {args.abi}; pinned V6 SDK/models ready.', flush=True)


if __name__ == '__main__':
    main()
