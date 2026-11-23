"""Offline integration with archived, real inputs; never run a release build or touch staging."""

from hashlib import sha256
import json
from pathlib import Path
import runpy
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile


SCRIPT = Path(__file__).resolve().parents[1] / "package-companions.py"
ROOT = SCRIPT.parents[1]
HISTORY = ROOT / "release-artifacts/1.0.22"
BASE = "mmm-1.0.22"
LITE = BASE + "+1.12.2-liteloader"
COMMIT = "6c226e2334a65b9e88e6f8ae1b0e52c3fe036f27"


@unittest.skipUnless((HISTORY / (LITE + "-rebuild.zip")).is_file(), "archived release fixture not installed")
class Companions(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.stage = self.root / "binary-stage"
        self.stage.mkdir()
        self.out = self.root / "assets"
        self.dev = HISTORY / "rebuild" / (LITE + "-dev.jar")
        self.receipt = self.root / "archived-pair.json"
        rows = json.loads((HISTORY / "manifest.json").read_text(encoding="utf-8-sig"))
        for row in rows:
            shutil.copyfile(HISTORY / row["filename"], self.stage / row["filename"])
            row.update(loader=row["id"].split("-")[-1], errors=0)
        (self.stage / "manifest.json").write_text(json.dumps(rows), encoding="utf8")
        (self.stage / "SHA256SUMS.txt").write_text(
            "".join(f"{row['sha256']}  {row['filename']}\n" for row in rows), encoding="ascii")
        # The previous release's rebuild ZIP is the independent witness for its dev jar.
        with ZipFile(HISTORY / (LITE + "-rebuild.zip")) as z:
            dev_hash = sha256(z.read(self.dev.name)).hexdigest()
        self.receipt.write_text(json.dumps(dict(commit=COMMIT, dev=dev_hash,
            litemod=next(r["sha256"] for r in rows if r["loader"] == "liteloader"))))

    def invoke(self, *extra, ok=False):
        p = subprocess.run([sys.executable, str(SCRIPT), "--commit", COMMIT, "package",
                            "--stage", str(self.stage), "--output", str(self.out),
                            "--receipt", str(self.receipt), "--dev-jar", str(self.dev), *extra],
                           capture_output=True, text=True)
        self.assertEqual(ok, p.returncode == 0, p.stdout + p.stderr)
        return p.stderr

    def test_reproducible_complete_assets_and_read_only_stage(self):
        before = {p.name: sha256(p.read_bytes()).hexdigest() for p in self.stage.iterdir()}
        self.invoke(ok=True)
        sums = (self.out / "SHA256SUMS.txt").read_bytes()
        self.assertEqual(sums.decode().splitlines(), sorted(
            (f"{sha256(p.read_bytes()).hexdigest()}  {p.name}" for p in self.out.iterdir()
             if p.name != "SHA256SUMS.txt"), key=lambda s: s.split("  ")[1]))
        self.assertEqual({p.name for p in self.out.iterdir()},
            before.keys() | {BASE + "-source.zip", LITE + "-rebuild.zip"})
        with ZipFile(self.out / (BASE + "-source.zip")) as z:
            self.assertEqual(COMMIT.encode(), z.comment)
            self.assertEqual(subprocess.check_output(["git", "show", COMMIT + ":release.properties"],
                                                     cwd=ROOT), z.read("release.properties"))
        with ZipFile(self.out / (LITE + "-rebuild.zip")) as z:
            self.assertEqual(z.read(self.dev.name), self.dev.read_bytes())
            for name in runpy.run_path(str(SCRIPT))["PINNED"]:
                self.assertEqual(z.read(name), (HISTORY / "rebuild" / name).read_bytes())
            self.assertIn(COMMIT.encode(), z.read("README.txt"))
            self.assertIn("THIRD-PARTY.md", z.namelist())
            self.assertIn("LICENSE", z.namelist())
        self.invoke("--output", str(self.root / "repeat"), ok=True)
        self.assertEqual(sums, (self.root / "repeat/SHA256SUMS.txt").read_bytes())
        self.invoke()  # Destination already exists, including the checksum file.
        self.assertEqual(sums, (self.out / "SHA256SUMS.txt").read_bytes())
        self.assertEqual(before, {p.name: sha256(p.read_bytes()).hexdigest() for p in self.stage.iterdir()})

    def test_wrong_pair_or_same_name_stale_dev_rejected(self):
        pair = json.loads(self.receipt.read_text())
        for key in ("commit", "dev", "litemod"):
            self.receipt.write_text(json.dumps({**pair, key: "0" * len(pair[key])}))
            self.assertIn("dev jar differs" if key == "dev" else "not the staged LiteLoader build", self.invoke())
            self.assertFalse(self.out.exists())

    def test_missing_extra_and_changed_binary_rejected(self):
        jar = self.stage / (BASE + "+1.21.jar")
        jar.unlink()
        self.assertIn("complete binary-only stage", self.invoke())
        shutil.copyfile(HISTORY / jar.name, jar)
        (self.stage / "stale.jar").touch()
        self.assertIn("complete binary-only stage", self.invoke())
        (self.stage / "stale.jar").unlink()
        with jar.open("ab") as f:
            f.write(b"wrong build")
        self.assertIn("staged bytes differ", self.invoke())
        self.assertFalse(self.out.exists())

    def test_record_requires_clean_matching_checkout_and_successful_build(self):
        record = runpy.run_path(str(SCRIPT))["record_pair"]
        record.__globals__["WRAPPER"] = self.root
        libs = self.root / "build/libs"
        libs.mkdir(parents=True)
        shutil.copyfile(self.dev, libs / self.dev.name)
        shutil.copyfile(self.stage / (LITE + ".litemod"), libs / (LITE + ".litemod"))
        path = self.root / "pair.json"
        args = (COMMIT, "1.0.22", "gradle", path)
        yes = [COMMIT.encode(), b""]
        with patch.dict(record.__globals__, git=lambda *a: COMMIT.encode() if a[0] == "rev-parse" else b" M changed"):
            with self.assertRaisesRegex(ValueError, "clean checkout"), patch("subprocess.run") as build:
                record(*args)
            build.assert_not_called()
        with patch.dict(record.__globals__, git=lambda *a: yes[a[0] == "status"]):
            with patch("subprocess.run", side_effect=subprocess.CalledProcessError(1, "gradle")), self.assertRaises(subprocess.CalledProcessError):
                record(*args)
            self.assertFalse(path.exists())
            with patch("subprocess.run") as build:
                record(*args)
            build.assert_called_once_with(["gradle", "--offline", "jar", "releaseLiteMod"], cwd=self.root, check=True)
        self.assertEqual(json.loads(self.receipt.read_text()), json.loads(path.read_text()))
        with self.assertRaisesRegex(ValueError, "overwrite"):
            record(*args)

    def test_checksum_disagreement_and_input_as_output_rejected(self):
        self.assertIn("new directory", self.invoke("--output", str(self.stage / "new")))
        (self.stage / "SHA256SUMS.txt").write_bytes(b"")
        self.assertIn("stage checksums disagree", self.invoke())
        self.assertFalse(self.out.exists())


if __name__ == "__main__":
    unittest.main()
