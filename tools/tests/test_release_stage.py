import hashlib
import json
from pathlib import Path
import runpy
import sys
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile


class ReleaseGate(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.main = runpy.run_path(str(Path(__file__).resolve().parents[1] / "stage-feature-release.py"))["main"]
        self.main.__globals__["ROOT"] = self.root
        (self.root / "release.properties").write_text("mod_version=1.0.23")
        (self.root / "versions.json").write_text('[{"id":"1.21-fabric","minecraft":"1.21"}]')
        build = self.root / "versions/1.21-fabric/build"
        self.jar = build / "libs/mmm-1.0.23+1.21.jar"
        self.jar.parent.mkdir(parents=True)
        with ZipFile(self.jar, "w") as jar:
            jar.writestr("fabric.mod.json", json.dumps({
                "version": "1.0.23+1.21", "id": "mmm", "depends": {"minecraft": "1.21"},
                "entrypoints": {"client": ["com.mmm.MMM"]}}))
            for cls in ("MMM", "ui/SettingsNavigation", "ui/CollectionBoardState"):
                jar.writestr("com/mmm/" + cls + ".class", b"fixture")
        self.report = build / "test-results/test/TEST-suite.xml"
        self.report.parent.mkdir(parents=True)
        self.report.write_text('<testsuite tests="3" skipped="1" failures="0" errors="0"/>')
        self.receipt = self.root / "build/verification/1.0.23/1.21-fabric-packaged.json"
        self.receipt.parent.mkdir(parents=True)
        self.receipt.write_text(json.dumps(dict(
            sha256=hashlib.sha256(self.jar.read_bytes()).hexdigest(), status="pass",
            loader="fabric", scope="screens", exitCode=0, wrapper="1.21-fabric")))
        self.dest = self.root / "release-artifacts/1.0.23"

    def stage(self):
        with patch.object(sys, "argv", ["stage", "--stage"]):
            return self.main()

    def test_stages_exact_tested_archive_and_skips_are_explicit(self):
        self.assertEqual(0, self.stage())
        self.assertEqual(self.jar.read_bytes(), (self.dest / self.jar.name).read_bytes())
        self.assertEqual(1, json.loads((self.dest / "manifest.json").read_text())[0]["skipped"])

    def test_rebuilt_candidate_never_staged_with_old_receipt(self):
        with ZipFile(self.jar, "a") as jar:
            jar.writestr("changed.txt", "new build")
        self.assertEqual(1, self.stage())
        self.assertFalse(self.dest.exists())

    def test_other_wrapper_missing_prevents_partial_staging(self):
        (self.root / "versions.json").write_text('[{"id":"1.21-fabric","minecraft":"1.21"},'
                                                '{"id":"1.20.1-fabric","minecraft":"1.20.1"}]')
        self.assertEqual(1, self.stage())
        self.assertFalse(self.dest.exists())

    def test_failed_suite_blocks_upload_even_after_a_runtime_pass(self):
        self.report.write_text('<testsuite tests="3" failures="1"/>')
        self.assertEqual(1, self.stage())
        self.assertFalse(self.dest.exists())

    def test_embedded_board_probe_never_distributed(self):
        with ZipFile(self.jar, "a") as jar:
            jar.writestr("com/mmm/liteloader/smoke/LiteModMMMBoardProbe.class", b"fixture")
        with self.assertRaisesRegex(ValueError, "test or Timelapse"):
            self.main.__globals__["inspect_archive"](self.jar, "1.21", "1.0.23+1.21", False)
