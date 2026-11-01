import importlib.util
from pathlib import Path
import unittest


SPEC = importlib.util.spec_from_file_location('public_source', Path(__file__).parents[1] / 'check-public-source.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PublicSourceTest(unittest.TestCase):
    def test_build_inputs_and_original_notices_are_public(self):
        for name in ['app/src/main/assets/web/app.js', 'third_party/ocr-runtime/opencv-minimal-arm64-v8a.aar',
                     'gradle/wrapper/gradle-wrapper.jar', 'NOTICE.md',
                     '.github/workflows/android.yml', 'tools/ui-checks/package-lock.json']:
            self.assertTrue(MODULE.publishable(name), name)

    def test_private_artifacts_are_excluded_even_when_tracked(self):
        for name in ['output/capture.png', 'artifacts/app.apk', '.tools/private.txt', 'local.properties',
                     'app/build/generated/model.onnx', 'tools/ui-checks/node_modules/a/index.js',
                     'signing/release.jks', 'docs/sources.md', 'app/ledger.db', '.env', 'scripts/__pycache__/a.pyc']:
            self.assertFalse(MODULE.publishable(name), name)

    def test_path_escape_is_rejected(self):
        for name in ['../private.txt', '/app/src/main.java', 'app/../../private.txt', 'app\\..\\private.txt']:
            self.assertFalse(MODULE.publishable(name), name)

    def test_credential_values_are_detected_without_printing_them(self):
        examples = [('github-token', 'ghp_' + 'x' * 36), ('private-key', '-----BEGIN ' + 'PRIVATE KEY-----'),
                    ('openai-token', 'sk-proj-' + 'x' * 45)]
        for kind, value in examples:
            self.assertIn(kind, MODULE.secret_kinds(value.encode()))
        self.assertFalse(MODULE.secret_kinds(b'API_KEY=""\nNo credentials required.'))


if __name__ == '__main__':
    unittest.main()
