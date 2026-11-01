"""Generate reduced-runtime operator config from both pinned V6 models (no weight changes)."""
from pathlib import Path
from collections import Counter, defaultdict
import hashlib
import json
import onnx

root = Path(__file__).resolve().parents[1]
work = root / '.tools/ocr-native'
required = defaultdict(set)
report = {}
for name in ['PP-OCRv6_tiny_det', 'PP-OCRv6_tiny_rec']:
    path = root / '.tools/ocr-ab/models' / name / 'inference.onnx'
    model = onnx.load(path)
    versions = {o.domain: o.version for o in model.opset_import}
    for node in model.graph.node:
        # Preserve the model's import version, including ONNX default-domain spelling.
        required[(node.domain or 'ai.onnx', versions[node.domain])].add(node.op_type)
    report[name] = {'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                    'operators': dict(Counter(n.op_type for n in model.graph.node)),
                    'weightTypes': dict(Counter(onnx.TensorProto.DataType.Name(i.data_type) for i in model.graph.initializer))}
work.mkdir(parents=True, exist_ok=True)
(work / 'v6-operators.json').write_text(json.dumps(report, indent=2) + '\n')
(work / 'v6-required-ops.config').write_text('\n'.join(
    f'{domain};{version};' + ','.join(sorted(ops)) for (domain, version), ops in sorted(required.items())) + '\n')
print((work / 'v6-required-ops.config').read_text())
