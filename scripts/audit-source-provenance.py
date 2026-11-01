"""Reproducible local evidence, NOT a copyright or relicensing determination.

python scripts/audit-source-provenance.py --baseline C:/path/to/upstream-checkout \
  --runtime .tools/source-audit/runtime.json --gradle-home C:/Users/Public/codex-gradle-home \
  --apk app/build/outputs/apk/release/app-release-unsigned.apk \
  --output .tools/source-audit/report.json
"""
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE_EXTENSIONS = {'.kt', '.java', '.js', '.css', '.html', '.xml', '.kts', '.json', '.pro'}
LEX = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*|[\w$]+|[^\s]', re.UNICODE)
OLD = re.compile(r'net[/\.]ankio[/\.]ocr(?:[/;\.]|$)|ncnnandroidppocr|paddleocr4android|PP_OCR|\.ncnn\.bin', re.I)
COPYLEFT = re.compile(r'\b(?:A?GPL|LGPL)(?:[- .]?\d)?\b|GNU (?:Affero |Lesser |Library )?General Public License', re.I)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def code_parts(path):
    content = path.read_text('utf-8', errors='replace')
    # Lex strings before comments so https:// and comment-like literals survive unchanged.
    tokens = [m.group() for m in LEX.finditer(content) if not m.group().startswith(('//', '/*'))]
    # Five-line comparison uses a separate comment-aware stream retaining original whitespace.
    uncommented = LEX.sub(lambda m: re.sub(r'[^\r\n]', ' ', m.group())
                         if m.group().startswith(('//', '/*')) else m.group(), content)
    lines = [re.sub(r'\s+', '', line) for line in uncommented.splitlines()
             if line.strip() and not re.match(r'^\s*(?:package|import)\b', line)]
    windows = {''.join(lines[i:i+5]) for i in range(len(lines)-4)}
    return tokens, {digest(w.encode()) for w in windows if len(w) >= 130}


def fingerprints(tokens):
    return {digest('\x1f'.join(tokens[i:i+80]).encode()) for i in range(len(tokens)-79)
            if len(set(tokens[i:i+80])) >= 20}


def source_audit(baseline):
    roots = [ROOT / name / 'src/main' for name in ('app', 'core')]
    local = sorted(p for folder in roots for p in folder.rglob('*') if p.is_file())
    other = sorted(p for p in baseline.rglob('*') if p.is_file()
                   and not any(x in {'.git', 'build', '.gradle'} for x in p.relative_to(baseline).parts))
    exact = defaultdict(list)
    line_index, token_index = defaultdict(set), defaultdict(set)
    for file in other:
        relative = file.relative_to(baseline).as_posix()
        exact[digest(file.read_bytes())].append(relative)
        if file.suffix in SOURCE_EXTENSIONS:
            tokens, lines = code_parts(file)
            for key in lines:
                line_index[key].add(relative)
            for key in fingerprints(tokens):
                token_index[key].add(relative)
    manifest, matches, residue = [], [], []
    for file in local:
        name = file.relative_to(ROOT).as_posix()
        data = file.read_bytes()
        notice_asset = name.startswith('app/src/main/assets/licenses/')
        row = {'file': name, 'sha256': digest(data), 'bytes': len(data),
               'scope': 'third_party_notice' if notice_asset or '/vendor/' in name else 'application_or_resource'}
        manifest.append(row)
        # Empty placeholder files do not provide evidence of copied expression.
        if data and row['sha256'] in exact:
            matches.append({'file': name, 'kind': 'whole_file', 'upstream': exact[row['sha256']]})
        # Vendor fonts/CSS are inventoried above, but are not claimed as original application code.
        if file.suffix in SOURCE_EXTENSIONS and '/vendor/' not in name and not notice_asset:
            tokens, lines = code_parts(file)
            for kind, keys, index in [('five_lines', lines, line_index), ('80_tokens', fingerprints(tokens), token_index)]:
                found = defaultdict(int)
                for key in keys:
                    for target in index.get(key, ()):
                        found[target] += 1
                if found:
                    matches.append({'file': name, 'kind': kind, 'upstream_windows': dict(sorted(found.items()))})
            if OLD.search(data.decode('utf-8', errors='replace')):
                residue.append(name)
    baseline_commit = subprocess.check_output(['git', '-C', str(baseline), 'rev-parse', 'HEAD'], text=True).strip()
    return {'baseline_commit': baseline_commit, 'production_files': len(local), 'baseline_files': len(other),
            'thresholds': 'exact SHA256; 5 comment-free lines >=130 characters; 80 exact lexical tokens >=20 distinct',
            'files': manifest, 'matches_for_manual_review': matches, 'old_ocr_source_hits': residue,
            'notice_files': sum(x['scope'] == 'third_party_notice' for x in manifest)}


def pom_metadata(file, cache, seen=None):
    seen = set() if seen is None else seen
    if file in seen:
        return {'error': 'POM parent cycle'}
    seen.add(file)
    try:
        root = ET.parse(file).getroot()
        for node in root.iter():
            node.tag = node.tag.rsplit('}', 1)[-1]
        licenses = [{part.tag: (part.text or '').strip() for part in node}
                    for node in root.findall('./licenses/license')]
        if licenses:
            return {'licenses': licenses, 'license_pom': str(file)}
        parent = root.find('parent')
        if parent is not None:
            group, artifact, version = (parent.findtext(key) for key in ('groupId', 'artifactId', 'version'))
            candidates = list((cache / group / artifact / version).glob('*/*.pom')) if all((group, artifact, version)) else []
            if candidates:
                return pom_metadata(candidates[0], cache, seen)
        return {'error': 'No license in resolved POM or cached parent'}
    except (OSError, ET.ParseError) as error:
        return {'error': str(error)}


def dependency_audit(runtime, gradle_home):
    inventory = json.loads(runtime.read_text('utf-8-sig'))
    result = []
    for item in inventory['artifacts']:
        row = {'coordinate': item['coordinate'], 'type': item['type'],
               'sha256': digest(Path(item['file']).read_bytes())}
        if item.get('project'):
            row.update({'project': True, 'source': 'included project; covered by production source audit',
                        'copyleft_in_pom': False, 'bundled_copyleft_mentions_for_manual_review': []})
            result.append(row)
            continue
        poms = [Path(p['file']) for p in item['poms'] if 'file' in p]
        row.update(pom_metadata(poms[0], gradle_home / 'caches/modules-2/files-2.1') if poms else {'error': 'POM unresolved'})
        row['copyleft_in_pom'] = bool(COPYLEFT.search(json.dumps(row.get('licenses', []))))
        mentions, components = [], []
        with zipfile.ZipFile(item['file']) as archive:
            for name in archive.namelist():
                if not re.search(r'(license|notice|copying)', name, re.I) or archive.getinfo(name).file_size > 5_000_000:
                    continue
                content = archive.read(name).decode('utf-8', errors='replace')
                if COPYLEFT.search(content):
                    mentions.append(name)
            # Google's notice index uses UTF-8 byte offsets, not character offsets.
            if 'third_party_licenses.json' in archive.namelist() and 'third_party_licenses.txt' in archive.namelist():
                index = json.loads(archive.read('third_party_licenses.json'))
                notice = archive.read('third_party_licenses.txt')
                for component, bounds in sorted(index.items()):
                    section = notice[bounds['start']:bounds['start'] + bounds['length']]
                    if COPYLEFT.search(section.decode('utf-8', errors='replace')):
                        components.append({'component': component, 'notice_sha256': digest(section),
                                           'bytes': len(section)})
        row['bundled_copyleft_mentions_for_manual_review'] = mentions
        row['indexed_copyleft_mentions_for_manual_review'] = components
        result.append(row)
    return {'configuration': inventory['configuration'], 'artifacts': result, 'pom_failures': inventory['pomFailures']}


def apk_audit(apk, baseline):
    # Exact binary evidence also catches renamed copies of the four historical Paddle models.
    legacy_assets = baseline / 'ocr/src/main/assets'
    model_hashes = {digest(p.read_bytes()): p.relative_to(baseline).as_posix()
                    for p in legacy_assets.rglob('*') if p.is_file()}
    matches, dex_hits = [], []
    with zipfile.ZipFile(apk) as archive:
        for item in archive.infolist():
            if item.is_dir():
                continue
            data = archive.read(item)
            if OLD.search(item.filename):
                matches.append({'file': item.filename, 'reason': 'legacy OCR filename'})
            hashed = digest(data)
            if hashed in model_hashes:
                matches.append({'file': item.filename, 'upstream': model_hashes[hashed]})
            if item.filename.endswith('.dex'):
                hits = sorted({m.group() for m in OLD.finditer(data.decode('latin1'))})
                if hits:
                    dex_hits.append({'file': item.filename, 'hits': hits})
        return {'file': str(apk), 'sha256': digest(apk.read_bytes()), 'historical_models_compared': len(model_hashes),
                'legacy_asset_hits': matches, 'legacy_dex_hits': dex_hits,
                'native_libraries': [n for n in archive.namelist() if n.endswith('.so')]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--gradle-home', type=Path, required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = {'scope': 'local evidence only; similarity absence is not proof of exclusive ownership',
              'source': source_audit(args.baseline.resolve()),
              'runtime': dependency_audit(args.runtime, args.gradle_home),
              'apk': apk_audit(args.apk, args.baseline)}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'production_files': report['source']['production_files'],
                      'similarity_candidates': len(report['source']['matches_for_manual_review']),
                      'runtime_artifacts': len(report['runtime']['artifacts']),
                      'unresolved_licenses': [x['coordinate'] for x in report['runtime']['artifacts'] if 'error' in x],
                      'copyleft_pom_candidates': [x['coordinate'] for x in report['runtime']['artifacts'] if x['copyleft_in_pom']],
                      'legacy_asset_hits': report['apk']['legacy_asset_hits'],
                      'legacy_dex_hits': report['apk']['legacy_dex_hits']}, ensure_ascii=False))
    if (report['source']['old_ocr_source_hits'] or report['apk']['legacy_asset_hits'] or report['apk']['legacy_dex_hits']
            or report['runtime']['pom_failures'] or any('error' in x or x['copyleft_in_pom'] for x in report['runtime']['artifacts'])):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
