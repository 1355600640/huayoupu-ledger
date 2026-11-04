"""Preserve resolved runtime artifacts' original notices in offline application assets.

Run auditReleaseRuntime and audit-source-provenance.py first. This records notices;
it does not decide whether an SDK's license grants all intended distribution rights.
"""
import hashlib
import html
import io
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'app/src/main/assets/licenses'
STYLE = 'body{font:16px sans-serif;line-height:1.65;color:#193e36;padding:16px;overflow-wrap:anywhere}pre{white-space:pre-wrap;font:13px monospace}a{color:#087b62}small{color:#61726e}'


def page(title, body):
    return ('<!doctype html><html lang="zh-CN"><meta charset="utf-8">'
            '<meta name="viewport" content="width=device-width,initial-scale=1">'
            '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'">'
            f'<title>{html.escape(title)}</title><style>{STYLE}</style>{body}</html>')


def notice_files(archive, prefix=''):
    for name in archive.namelist():
        if archive.getinfo(name).is_dir():
            continue
        if re.search(r'(license|notice|copying)', name, re.I):
            yield prefix + name, archive.read(name)
        elif name == 'classes.jar':
            with zipfile.ZipFile(io.BytesIO(archive.read(name))) as nested:
                yield from notice_files(nested, 'classes.jar!/')


def main():
    runtime = json.loads((ROOT / '.tools/source-audit/runtime.json').read_text('utf-8-sig'))
    audit = json.loads((ROOT / '.tools/source-audit/report.json').read_text('utf-8'))
    metadata = {r['coordinate']: r for r in audit['runtime']['artifacts']}
    OUTPUT.mkdir(parents=True, exist_ok=True)
    rows, inventory, count, generated = [], [], 0, set()
    for artifact in runtime['artifacts']:
        if artifact.get('project'):
            continue
        coordinate = artifact['coordinate']
        row = metadata[coordinate]
        assert 'error' not in row, coordinate
        label = html.escape(coordinate)
        licenses = row['licenses']
        statement = '; '.join(x.get('name', '') + ' ' + x.get('url', '') for x in licenses)
        links, originals = [], []
        with zipfile.ZipFile(artifact['file']) as archive:
            for name, data in notice_files(archive):
                identifier = hashlib.sha256(data).hexdigest()[:20]
                raw = identifier + '.txt'
                view = identifier + '.html'
                (OUTPUT / raw).write_bytes(data)
                (OUTPUT / view).write_text(page('第三方原始声明',
                    '<a href="index.html">返回目录</a><h2>第三方原始声明</h2>' +
                    '<pre>' + html.escape(data.decode('utf-8', errors='replace')) + '</pre>'), encoding='utf-8')
                generated.update((raw, view))
                links.append(f'<li><a href="{view}">{html.escape(name)}</a></li>')
                originals.append({'original_path': name, 'file': raw,
                                  'sha256': hashlib.sha256(data).hexdigest()})
                count += 1
        inventory.append({'coordinate': coordinate, 'artifact_sha256': row['sha256'],
                          'licenses': licenses, 'original_notices': originals})
        rows.append('<li><strong>' + label + '</strong><p>' + html.escape(statement) + '</p>' +
                    ('<ul>' + ''.join(links) + '</ul>' if links else
                     '<small>该依赖包未附独立通知文件；以上为发布者 POM 中的许可声明。</small>') + '</li>')
    (OUTPUT / 'index.html').write_text(page('第三方依赖与许可',
        '<h1>第三方依赖与许可</h1><p>以下为当前 Release 实际解析的基础库及其原始通知。'
        '这些代码、模型和字体不是花有谱自研。许可中出现 GPL/LGPL 等名称时，应结合具体文件、双重许可及例外条款理解，'
        '本目录不作“整个安装包无 GPL”的保证。</p><ol>' + ''.join(rows) + '</ol>'
        '<p>Font Awesome 的完整通知另见应用内“字体与图标声明”。</p>'), encoding='utf-8')
    (OUTPUT / 'inventory.json').write_text(json.dumps(inventory, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    # Only remove obsolete generated hash-named files, after checking the exact managed directory.
    assert OUTPUT.resolve() == (ROOT / 'app/src/main/assets/licenses').resolve()
    for file in OUTPUT.iterdir():
        if file.is_file() and re.fullmatch(r'[a-f0-9]{20}\.(?:txt|html)', file.name) and file.name not in generated:
            assert file.resolve().parent == OUTPUT.resolve()
            file.unlink()
    print(f'Preserved {count} original notices from {len(inventory)} external runtime artifacts')


if __name__ == '__main__':
    main()
