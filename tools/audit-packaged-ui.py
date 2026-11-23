"""Audit current local packages and their framebuffer evidence. No client or build is launched.

python tools/audit-packaged-ui.py [--write-report | --released]
--released checks the immutable 1.0.23 distribution against its original suites.
Use --legacy-candidate-root PATH when LiteLoader was built and verified in an isolated checkout.
The command exits nonzero if any supported package lacks matching extended UI evidence.
"""

import argparse
import json
import re
import runpy
from pathlib import Path


lib = runpy.run_path(str(Path(__file__).with_name("verify-packaged-fabric.py")))
ROOT, require, digest, read_json = (lib[key] for key in ("ROOT", "require", "digest", "read_json"))
LEGACY_CANDIDATE = "legacy-inline-settings-v1"


def fabric(r, mc, sha, released=False):
    suite = lib["released_suite"](mc) if released else lib["UI_SUITE"]
    require(r.get("suite") == suite and r["scope"] == "screens", f"missing visible UI evidence: {suite}")
    require(digest(Path(r["installed"])) == sha and r["minecraft"] == mc, "installed bytes/game mismatch")
    log = Path(r["log"]).read_text(errors="replace")
    marker = f"MMM_PACKAGED_PASS {r['nonce']} {mc} {sha} screens {suite}"
    require(Path(r["marker"]).read_text() == marker and marker in log, "missing matching marker and log")
    folder = Path(r["screenshotDir"])
    ui = lib["ui_evidence"](log, r["nonce"], mc, folder, suite)
    require(all(r.get(k) == v for k, v in ui.items()), "changed route, widget or framebuffer evidence")
    return dict(suite=suite, routes=ui["routes"], gated=ui["gated"], picker=ui["picker"], screenshots=str(folder))


def candidate_fabric(out, key, sha):
    # --probe-only never replaces a receipt. Bind its immutable per-run result to that run's actual files.
    runs = sorted((out / "runs").glob(key + "-*/probe-result.json"), key=lambda p: p.stat().st_mtime_ns, reverse=True)
    for path in runs:
        r = read_json(path)
        if r.get("sha256") != sha or r.get("suite") != lib["UI_SUITE"]:
            continue
        run = path.parent
        require(r.get("diagnostic") is True and run.name == key + "-" + r["nonce"]
                and Path(r["log"]) == run / "console.log" and Path(r["marker"]) == run / "probe.pass"
                and Path(r["screenshotDir"]) == run / "game/screenshots"
                and Path(r["installed"]).parent == run / "game/mods", "mixed candidate run evidence")
        return path, r
    path = out / (key + "-packaged.json")
    require(path.is_file(), "no successful candidate probe for current bytes")
    return path, read_json(path)


def legacy_tool_evidence(run, log):
    tools = ("Hotkeys", "Projects", "History", "Summary", "Scoreboard", "Leaderboards")
    expected = {(size, name) for size in ("compact", "wide") for name in tools}
    pattern = r"MMM_BOARD_TOOL_OK (\w+) (\w+)$"
    lines = run.get("toolClicks", [])
    observed = re.findall(pattern, "\n".join(lines), re.MULTILINE)
    require(len(observed) == len(lines) == len(expected) and set(observed) == expected
            and all(line in log for line in lines), "missing wide/compact LiteLoader tool clicks")
    caps = {Path(p).name for p in run["screenshots"]}
    required = {f"board-tool-{size}-{name.upper()}.png" for size, name in expected}
    required.update(f"board-{page}-{size}.png" for page in ("launcher", "tools") for size in ("wide", "compact"))
    require(required <= caps, "missing LiteLoader launcher/Tools framebuffer")
    return sorted(required)


def legacy_pager_evidence(run, receipt, log):
    lines = [line for line in log.splitlines() if "MMM_BOARD_PAGER_OK" in line]
    require(len(lines) == 2 and lines == run.get("pagerClicks") == receipt.get("pagerClicks"),
            "missing, duplicate, or stale per-context pager evidence")
    counts = {}
    for line in lines:
        match = re.search(r"(?:^|\s)MMM_BOARD_PAGER_OK (wide|compact) forward=([1-9][0-9]*) back=\2$", line)
        require(match is not None, "invalid or unbalanced pager counts")
        counts[match[1]] = int(match[2])
    require(set(counts) == {"wide", "compact"}, "missing pager viewport")
    return counts


def legacy_inline_evidence(run, receipt, log):
    lines = run.get("inlineSettings", [])
    keys = run.get("toolArrowKeys", [])
    caps = {Path(p).name for p in run["screenshots"]}
    require(receipt.get("toolArrowKeys") == keys and len(keys) == 2 and all(v in log.splitlines() for v in keys),
            "missing current per-context Tools keyboard markers")
    require(receipt.get("inlineSettings") == lines and len(lines) == 8 and len(set(lines)) == 8
            and all(line in log.splitlines() for line in lines), "missing current per-context inline Settings markers")
    for size in ("wide", "compact"):
        require(any(re.search(rf"MMM_BOARD_INLINE_OK {size} categories=[1-9]\d* controls=[1-9]\d*$", line) for line in lines)
                and any(line.endswith(f"MMM_BOARD_EVERYDAY_CLICK_OK {size} Mining Tracker") for line in lines),
                f"missing {size} rail or Everyday click")
        for category in ("Everyday settings", "HUD"):
            require(any(re.search(rf"MMM_BOARD_INLINE_PIXELS {size} {category} ink=[1-9]\d*$", line) for line in lines),
                    f"missing {size} inline pixel evidence for {category}")
        require(f"board-inline-hud-{size}.png" in caps, f"missing {size} HUD capture")
        layout = "columns=1 pagedBothWays=true" if size == "compact" else "columns=2 pagedBothWays=false"
        require(any(v.endswith(f"MMM_BOARD_TOOL_KEYS_OK {size} {layout} enter=Leaderboards") for v in keys)
                and f"board-tool-keys-{size}.png" in caps, f"missing {size} Tools keyboard navigation")
    require(run["mode"] == receipt["mode"] and "MMM_BOARD_PROBE_OK" in log and "MMM_BOARD_PROBE_FAILED" not in log
            and "MMM_BOARD_KEYBOARD_OK" in log and "MMM_BOARD_FOCUS_PIXELS" in log, "missing candidate input checks")
    return lines


def legacy(r, sha, released=False):
    if not released:
        require(r.get("suite") == LEGACY_CANDIDATE and r.get("testedExtension") == ".litemod"
                and r.get("keyboardFocus") == "pass", "no current LiteLoader inline Settings verification")
        require(len({Path(run["gameDirectory"]) for run in r["runs"]}) == 2, "expected distinct isolated game directories")
    require(r["scope"] == "screens" and r["legacyEditorRoutes"] == r["colorPicker"] == "pass", "older LiteLoader suite")
    require(sorted(x["mode"] for x in r["runs"]) == ["liteloader", "liteloader+forge"], "missing loader context")
    result = []
    for run in r["runs"]:
        receipt = read_json(Path(run["receipt"]))
        require(receipt["sha256"] == run["sha256"] == sha and run["exitCode"] == receipt["exitCode"] == 0,
                "stale per-context receipt")
        require(digest(Path(run["loadedArtifact"])) == sha, "loaded LiteMod differs")
        log = Path(run["stdout"]).read_text(errors="replace")
        require(run["loadedFrom"] in log and "MMM_BOARD_EDITORS_OK" in log, "missing loaded-origin or editor marker")
        require(run["routes"] and all(line in log for line in run["routes"]), "missing legacy route evidence")
        require(len(run["picker"]) >= 2 and all(line in log for line in run["picker"]), "missing legacy picker evidence")
        require(run["shareExport"] and all(Path(p).is_file() for p in [*run["screenshots"], *run["shareExport"]]),
                "missing capture or export")
        captures = legacy_tool_evidence(run, log)
        require(receipt.get("toolClicks") == run["toolClicks"], "stale per-context tool clicks")
        result.append(dict(mode=run["mode"], routes=len(run["routes"]), picker=run["picker"],
                           toolCaptures=captures, screenshots=len(run["screenshots"]), evidence=run["receipt"]))
        if not released:
            result[-1]["inlineSettings"] = legacy_inline_evidence(run, receipt, log)
            result[-1]["pagerClicks"] = legacy_pager_evidence(run, receipt, log)
    return dict(contexts=result)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--write-report", action="store_true")
    mode.add_argument("--released", action="store_true", help="read-only audit of the published 1.0.23 files")
    parser.add_argument("--legacy-candidate-root", type=Path,
                        help="isolated LiteLoader checkout containing the built .litemod and dual-mode candidate receipt")
    args = parser.parse_args()
    if args.legacy_candidate_root:
        args.legacy_candidate_root = args.legacy_candidate_root.resolve()
        require(not args.released and args.legacy_candidate_root != ROOT,
                "an isolated LiteLoader root is only valid for the candidate audit")
    release = "1.0.23" if args.released else lib["properties"](ROOT / "release.properties")["mod_version"]
    require(re.fullmatch(r"\d+(\.\d+){2,}", release), "invalid mod_version")
    original = ROOT / "build/verification" / release
    out = original if args.released else lib["evidence_dir"](release)
    results = []
    for wrapper in read_json(ROOT / "versions.json"):
        key, mc = wrapper["id"], wrapper["minecraft"]
        loader = key.split("-")[-1]
        ext = ".jar" if loader == "fabric" else "-liteloader.litemod"
        artifact = ROOT / "versions" / key / "build/libs" / f"mmm-{release}+{mc}{ext}"
        if args.released:
            artifact = ROOT / "release-artifacts" / (release + "-distribution") / artifact.name
        path = (out if loader == "fabric" or args.released else original / "candidates" / LEGACY_CANDIDATE) / (key + "-packaged.json")
        if loader == "liteloader" and args.legacy_candidate_root:
            artifact = args.legacy_candidate_root / artifact.relative_to(ROOT)
            path = args.legacy_candidate_root / "build/verification" / release / (key + "-packaged.json")
        row = dict(wrapper=key, artifact=str(artifact), receipt=str(path), status="unverified")
        try:
            row["sha256"] = digest(artifact)
            if loader == "fabric" and not args.released:
                path, r = candidate_fabric(out, key, row["sha256"])
                row["receipt"] = str(path)
            else:
                r = read_json(path)
            require(r["status"] == "pass" and r["exitCode"] == 0 and r["wrapper"] == key and r["loader"] == loader,
                    "invalid receipt")
            require(r["sha256"] == row["sha256"], "new binary; receipt for different bytes")
            row.update(fabric(r, mc, row["sha256"], args.released) if loader == "fabric" else legacy(r, row["sha256"], args.released))
            row["status"] = "pass"
        except (ValueError, KeyError, TypeError, OSError) as exc:
            row["reason"] = str(exc)
        results.append(row)
        print(key, row["status"], row.get("reason", row.get("sha256")))
    passed = sum(r["status"] == "pass" for r in results)
    if args.write_report:
        out.mkdir(parents=True, exist_ok=True)
        report = dict(release=release, audited=lib["timestamp"](), passed=passed, total=len(results), artifacts=results,
                      limits="Exact local build bytes only. Route/pixel evidence is not exhaustive gameplay, visual, or live-service coverage.")
        path = out / "packaged-ui-matrix.json"
        tmp = path.with_suffix(".tmp")
        tmp.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        tmp.replace(path)
    return int(passed != len(results))


if __name__ == "__main__":
    raise SystemExit(main())
