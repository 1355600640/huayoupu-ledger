from pathlib import Path
import subprocess
import sys
import unittest


class NativeBuildOptionsTest(unittest.TestCase):
    def test_native_builds_accept_an_explicit_upstream_aar(self):
        root = Path(__file__).resolve().parents[2]
        for name in ['build-ocr-ort.py', 'build-ocr-opencv.py']:
            result = subprocess.run([sys.executable, str(root / 'scripts' / name), '--help'], capture_output=True, text=True)
            self.assertEqual(0, result.returncode)
            self.assertIn('--base-aar', result.stdout, name)


if __name__ == '__main__':
    unittest.main()
