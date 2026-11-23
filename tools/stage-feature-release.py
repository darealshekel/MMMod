"""Validate every release build. With --stage, copy only the tested bytes for upload."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
from tempfile import TemporaryDirectory
import xml.etree.ElementTree as ET
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def digest(path):
    with path.open("rb") as f:
        sha = hashlib.sha256()
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            sha.update(chunk)
        return sha.hexdigest()


def inspect_archive(artifact, mc, version, legacy):
    with ZipFile(artifact) as jar:
        names = set(jar.namelist())
        require(len(names) == len(jar.infolist()), "duplicate ZIP entries")
        require(jar.testzip() is None, "archive CRC failure")
        require(not any(re.search(r"(com/mmm/(?:[^/]+/)*(?:smoke|timelapse)/|RuntimeProbe|mmm_smoke)", n)
                        for n in names), "found test or Timelapse code")
        for cls in ("SettingsNavigation", "CollectionBoardState"):
            require(f"com/mmm/ui/{cls}.class" in names, f"shared {cls} missing")
        config = json.loads(jar.read("litemod.json" if legacy else "fabric.mod.json"))
        require(config["version"] == version, "incorrect embedded release version")
        if legacy:
            require(config["mcversion"] == mc and config["name"] == "mmm", "wrong legacy metadata")
            refmap = json.loads(jar.read("mmm.refmap.json"))["data"]
            require(refmap.get("notch") and refmap.get("searge"), "incomplete legacy refmap")
            for name in names:
                if name.endswith(".class"):
                    with jar.open(name) as stream:
                        header = stream.read(8)
                    require(header[:4] == bytes.fromhex("cafebabe") and len(header) == 8
                            and int.from_bytes(header[6:], "big") <= 52, "not Java 8: " + name)
        else:
            require(config["id"] == "mmm" and config["depends"]["minecraft"] == mc,
                    "wrong Fabric/Minecraft metadata")
            require(config["entrypoints"].get("client"), "missing client entrypoint")
            for entries in config["entrypoints"].values():
                for entry in entries:
                    cls = entry if isinstance(entry, str) else entry["value"]
                    require(cls.split("::")[0].replace(".", "/") + ".class" in names,
                            "missing entrypoint: " + cls)
            for nested in config.get("jars", []):
                require(nested["file"] in names, "missing nested jar: " + nested["file"])


def verify(release, wrapper):
    key, mc = wrapper["id"], wrapper["minecraft"]
    legacy = key.endswith("-liteloader")
    version = f"{release}+{mc}" + ("-liteloader" if legacy else "")
    root = ROOT / "versions" / key / "build"
    name = f"mmm-{version}." + ("litemod" if legacy else "jar")
    artifact = root / "libs" / name
    before = digest(artifact)
    inspect_archive(artifact, mc, version, legacy)
    counts = dict.fromkeys(("tests", "skipped", "failures", "errors"), 0)
    for result in (root / "test-results/test").glob("TEST-*.xml"):
        suite = ET.parse(result).getroot()
        for field in counts:
            counts[field] += int(suite.get(field, "0"))
    require(counts["tests"] > counts["skipped"] and not (counts["errors"] or counts["failures"]),
            f"missing or failing wrapper tests: {counts}")
    receipt = read_json(ROOT / "build/verification" / release / f"{key}-packaged.json")
    require(receipt["status"] == "pass" and receipt["sha256"].lower() == before,
            "no runtime pass for this SHA-256")
    loader = "liteloader" if legacy else "fabric"
    require(receipt["loader"] == loader, "wrong loader in runtime receipt")
    if legacy:
        require(set(receipt.get("modes", [])) == {"liteloader", "liteloader+forge"},
                "missing LiteLoader-only or LiteLoader+Forge launch")
        require(receipt["scope"] == "screens" and receipt["exitCode"] == 0
                and receipt["wrapper"] == key, "packaged legacy boards not verified")
    else:
        require(receipt["scope"] == "screens" and receipt["exitCode"] == 0
                and receipt["wrapper"] == key, "packaged Settings/Collections not verified")
    require(digest(artifact) == before, "build changed during verification")
    return artifact, dict(minecraft=mc, loader=loader, version=version, filename=name,
                          sha256=before, bytes=artifact.stat().st_size, **counts)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", action="store_true", help="create a new release directory")
    args = parser.parse_args()
    release = re.search(r"^mod_version=(\d+\.\d+\.\d+)$",
                        (ROOT / "release.properties").read_text().strip(), re.M)[1]
    wrappers = read_json(ROOT / "versions.json")
    require(wrappers and len({v["id"] for v in wrappers}) == len(wrappers), "bad version matrix")
    results = []
    for wrapper in wrappers:
        try:
            results.append(verify(release, wrapper))
            print("PASS", wrapper["id"], results[-1][1]["sha256"])
        except (OSError, ValueError, KeyError) as exc:
            print("NOT VERIFIED", wrapper["id"], str(exc))
    if len(results) != len(wrappers):
        return 1
    if args.stage:
        dest = ROOT / "release-artifacts" / release
        require(not dest.exists(), "staging destination already exists; will not overwrite")
        dest.parent.mkdir(exist_ok=True)
        with TemporaryDirectory(dir=dest.parent) as scratch:
            work = Path(scratch) / release
            work.mkdir()
            for src, info in results:
                target = work / info["filename"]
                shutil.copyfile(src, target)
                require(digest(target) == info["sha256"], "staged bytes differ: " + target.name)
            manifest = [info for _, info in results]
            (work / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
            (work / "SHA256SUMS.txt").write_text(
                "".join(f"{i['sha256']}  {i['filename']}\n" for i in manifest), encoding="ascii")
            work.rename(dest)
        print("Staged:", dest)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
