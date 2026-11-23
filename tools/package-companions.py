"""Record a same-build LiteLoader pair; supplement a verified, read-only release stage."""

import argparse
from hashlib import sha256
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from zipfile import BadZipFile, ZipFile, ZipInfo


ROOT = Path(__file__).resolve().parents[1]
LEGACY = "1.12.2-liteloader"
WRAPPER = ROOT / "versions" / LEGACY
SUMS = "SHA256SUMS.txt"
VENDOR = ROOT / "release-artifacts/1.0.22/rebuild"
PINNED = {
    "gson-2.10.1-sources.jar": "eee1cc5c1f4267ee194cc245777e68084738ef390acd763354ce0ff6bfb7bcc1",
    "Gson-Apache-2.0.txt": "3ddf9be5c28fe27dad143a5dc76eea25222ad1dd68934a047064e56ed2fa40c5",
    "JvmDowngrader-2.0.1-source.zip": "da2d4874bcf8de206509546f9153865d3d680d47a9e266a7c1a7a787909aad92",
    "JvmDowngrader-LGPL-2.1.txt": "87aa8e295c9b0050122f94b7f83235d92563d1c0d26c950820ace45d0c32af52",
}


def require(ok, msg):
    if not ok:
        raise ValueError(msg)


def git(*args):
    return subprocess.check_output(["git", "--no-replace-objects", *args], cwd=ROOT,
                                   env={**os.environ, "TZ": "UTC"})


def digest(path):
    return sha256(path.read_bytes()).hexdigest()


def release_at(commit):
    require(re.fullmatch("[0-9a-f]{40}|[0-9a-f]{64}", commit), "--commit needs a full Git commit ID")
    require(git("rev-parse", "--verify", commit + "^{commit}").decode().strip() == commit,
            "not a commit ID")
    version = re.search(rb"^mod_version=(\d+(?:\.\d+){2,3})\r?$",
                        git("show", commit + ":release.properties"), re.M)
    require(version, "no release version in commit")
    return version[1].decode()


def record_pair(commit, version, gradle, receipt):
    def check_tree():
        require(git("rev-parse", "HEAD").decode().strip() == commit and
                not git("status", "--porcelain", "--untracked-files=normal"),
                "record from a clean checkout of the specified release commit")

    require(not receipt.exists(), "will not overwrite a pair receipt")
    check_tree()
    # Both outputs are validated by Gradle's task graph against the same checkout.
    subprocess.run([gradle, "--offline", "jar", "releaseLiteMod"], cwd=WRAPPER, check=True)
    check_tree()
    base = "mmm-" + version + "+" + LEGACY
    libs = WRAPPER / "build/libs"
    pair = dict(commit=commit, dev=digest(libs / (base + "-dev.jar")),
                litemod=digest(libs / (base + ".litemod")))
    with receipt.open("x", encoding="ascii", newline="\n") as f:
        f.write(json.dumps(pair, indent=2) + "\n")


def matrix(commit, version, stage, work):
    wrappers = json.loads(git("show", commit + ":versions.json"))
    expected = {}
    for w in wrappers:
        mc = w["minecraft"]
        require(re.fullmatch(r"\d+(?:\.\d+)+", mc), "invalid Minecraft version")
        loader = "liteloader" if w["id"] == LEGACY else "fabric"
        require(w["id"] == mc + "-" + loader, "unexpected wrapper: " + w["id"])
        legacy = loader == "liteloader"
        full = version + "+" + mc + ("-liteloader" if legacy else "")
        name = "mmm-" + full + (".litemod" if legacy else ".jar")
        expected[name] = (mc, loader, full)
    require(len(expected) == len(wrappers) and "mmm-" + version + "+" + LEGACY + ".litemod" in expected,
            "invalid release matrix")
    require({p.name for p in stage.iterdir()} == expected.keys() | {"manifest.json", SUMS},
            "input must be the complete binary-only stage (no missing or unexpected assets)")
    require(all(p.is_file() and not p.is_symlink() for p in stage.iterdir()), "invalid staged file")
    shutil.copyfile(stage / "manifest.json", work / "manifest.json")
    manifest = json.loads((work / "manifest.json").read_text(encoding="utf-8-sig"))
    require(len(manifest) == len(expected) and {r["filename"] for r in manifest} == expected.keys(),
            "manifest does not match release matrix")
    hashes = {}
    for row in manifest:
        name = row["filename"]
        require((row["minecraft"], row["loader"], row["version"]) == expected[name] and
                row["tests"] > row["skipped"] >= 0 and row["failures"] == row["errors"] == 0,
                "invalid stage record: " + name)
        path = work / name
        shutil.copyfile(stage / name, path)
        hashes[name] = digest(path)
        require(path.stat().st_size == row["bytes"] and hashes[name] == row["sha256"],
                "staged bytes differ from manifest: " + name)
        mc, loader, full = expected[name]
        with ZipFile(path) as z:
            legacy = loader == "liteloader"
            meta = json.loads(z.read("litemod.json" if legacy else "fabric.mod.json"))
            identity = (meta["name"], meta["mcversion"]) if legacy else (meta["id"], meta["depends"]["minecraft"])
            require(meta["version"] == full and identity == ("mmm", mc), "stale or wrong binary: " + name)
    sums = (stage / SUMS).read_text(encoding="ascii").splitlines()
    entries = {f"{value}  {name}" for name, value in hashes.items()}
    manifest_sum = digest(work / "manifest.json") + "  manifest.json"
    require(len(sums) == len(set(sums)) and set(sums) in (entries, entries | {manifest_sum}),
            "stage checksums disagree")
    return hashes


def package(commit, version, stage, output, dev, receipt):
    stage, output = stage.resolve(strict=True), output.resolve()
    require(not output.exists() and not output.is_relative_to(stage),
            "output must be a new directory outside the input stage")
    base = "mmm-" + version
    legacy = base + "+" + LEGACY
    with tempfile.TemporaryDirectory(dir=output.parent) as scratch:
        work = Path(scratch) / "assets"
        work.mkdir()
        hashes = matrix(commit, version, stage, work)
        pair = json.loads(receipt.read_text(encoding="utf-8-sig"))
        require(pair["commit"] == commit and pair["litemod"] == hashes[legacy + ".litemod"],
                "recorded build is not the staged LiteLoader build at this commit")
        require(dev.name == legacy + "-dev.jar", "need the matching unshaded -dev.jar")
        inputs = {dev.name: dev.read_bytes()}
        require(sha256(inputs[dev.name]).hexdigest() == pair["dev"], "dev jar differs from verified pair")
        with ZipFile(io.BytesIO(inputs[dev.name])) as z:
            meta = json.loads(z.read("litemod.json"))
            header = z.read("com/mmm/liteloader/LiteModMMM.class")[:8]
            require(meta["version"] == version + "+" + LEGACY and meta["mcversion"] == "1.12.2"
                    and header[:4] == bytes.fromhex("cafebabe") and int.from_bytes(header[6:], "big") == 65,
                    "wrong or downgraded LiteLoader dev jar")
            require(all(n.startswith("com/mmm/") and "/internal/" not in n and "/smoke/" not in n
                        for n in z.namelist() if n.endswith(".class")), "bundled or probe code in dev jar")
        for name, pin in PINNED.items():
            data = (VENDOR / name).read_bytes()
            require(sha256(data).hexdigest() == pin, "vendor input changed: " + name)
            inputs[name] = data
        src = work / (base + "-source.zip")
        git("-c", "core.autocrlf=false", "-c", "core.eol=lf", "archive", "--format=zip",
            "-6", "--output=" + str(src), commit)
        with ZipFile(src) as z:
            require(z.comment.decode() == commit, "source archive missing commit ID")
            inputs["THIRD-PARTY.md"] = z.read("versions/" + LEGACY + "/THIRD-PARTY.md")
            inputs["LICENSE"] = z.read("LICENSE")
            for name in ("LICENSE-MIT", "LICENSING.md"):
                if name in z.namelist():
                    inputs[name] = z.read(name)
        inputs["README.txt"] = (
            f"MMM {version} LiteLoader rebuild inputs, commit {commit}\n"
            f"Install {legacy}.litemod, not this ZIP. The dev jar is unshaded and undowngraded.\n"
            f"Extract {src.name}; use JDK 21 and Gradle 8.7 in versions/{LEGACY}.\n"
            "Run gradle releaseLiteMod. Minecraft, LiteLoader and mappings are external.\n"
            "Gson 2.10.1 is unmodified upstream source. JvmDowngrader 2.0.1 includes its API backports\n"
            "(upstream commit 3a561342fb09fbb19022e0f910d765e51e9711e9). See THIRD-PARTY.md.\n"
            "To replace the compatibility library, build its supplied sources, publish locally,\n"
            "point the wrapper's dependency resolution at the replacement, and rebuild.\n"
        ).encode("ascii")
        with ZipFile(work / (legacy + "-rebuild.zip"), "x") as z:
            for name, data in sorted(inputs.items()):
                entry = ZipInfo(name)  # ZIP epoch, no host timestamps
                entry.create_system, entry.external_attr = 3, 0o100644 << 16
                z.writestr(entry, data)
        (work / SUMS).write_bytes("".join(f"{digest(p)}  {p.name}\n"
                                  for p in sorted(work.iterdir(), key=lambda p: p.name)).encode("ascii"))
        # Reserve the destination exclusively; even an existing empty directory is never replaced.
        output.mkdir()
        for p in sorted(work.iterdir(), key=lambda p: p.name == SUMS):
            with p.open("rb") as src_file, (output / p.name).open("xb") as dest_file:
                shutil.copyfileobj(src_file, dest_file)


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--commit", required=True, help="full immutable release commit ID")
    sub = cli.add_subparsers(dest="action", required=True)
    record = sub.add_parser("record", help="run the offline LiteLoader build and record both output hashes")
    record.add_argument("--gradle", required=True, help="Gradle 8.7 executable (use JDK 21)")
    record.add_argument("--receipt", type=Path, required=True, help="new receipt path outside source/stage")
    pack = sub.add_parser("package", help="read an already verified stage and create a new complete asset set")
    for flag in ("stage", "output", "receipt"):
        pack.add_argument("--" + flag, type=Path, required=True)
    pack.add_argument("--dev-jar", type=Path, help="default: release wrapper's build/libs/*-dev.jar")
    args = cli.parse_args()
    version = release_at(args.commit)
    if args.action == "record":
        record_pair(args.commit, version, args.gradle, args.receipt)
    else:
        dev = args.dev_jar or WRAPPER / "build/libs" / f"mmm-{version}+{LEGACY}-dev.jar"
        package(args.commit, version, args.stage, args.output, dev, args.receipt)
    print("Recorded:" if args.action == "record" else "Prepared:",
          args.receipt if args.action == "record" else args.output)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, BadZipFile, subprocess.CalledProcessError) as exc:
        raise SystemExit(f"Companion packaging stopped: {exc}") from None
