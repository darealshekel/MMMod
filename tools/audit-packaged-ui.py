"""Audit current local packages and their framebuffer evidence. No client or build is launched.

python tools/audit-packaged-ui.py [--write-report]
The command exits nonzero if any supported package lacks matching extended UI evidence.
"""

import argparse
import json
import re
import runpy
from pathlib import Path


lib = runpy.run_path(str(Path(__file__).with_name("verify-packaged-fabric.py")))
ROOT, require, digest, read_json = (lib[key] for key in ("ROOT", "require", "digest", "read_json"))


def fabric(r, mc, sha):
    suite = lib["ui_suite"](mc)
    require(r.get("suite") == suite and r["scope"] == "screens", f"missing visible UI evidence: {suite}")
    require(digest(Path(r["installed"])) == sha and r["minecraft"] == mc, "installed bytes/game mismatch")
    log = Path(r["log"]).read_text(errors="replace")
    marker = f"MMM_PACKAGED_PASS {r['nonce']} {mc} {sha} screens {suite}"
    require(Path(r["marker"]).read_text() == marker and marker in log, "missing matching marker and log")
    folder = Path(r["screenshotDir"])
    ui = lib["ui_evidence"](log, r["nonce"], mc, folder)
    require(all(r.get(k) == v for k, v in ui.items()), "changed route, widget or framebuffer evidence")
    return dict(suite=suite, routes=ui["routes"], gated=ui["gated"], picker=ui["picker"], screenshots=str(folder))


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


def legacy(r, sha):
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
    return dict(contexts=result)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write-report", action="store_true")
    args = parser.parse_args()
    release = lib["properties"](ROOT / "release.properties")["mod_version"]
    require(re.fullmatch(r"\d+(\.\d+){2,}", release), "invalid mod_version")
    out = ROOT / "build/verification" / release
    results = []
    for wrapper in read_json(ROOT / "versions.json"):
        key, mc = wrapper["id"], wrapper["minecraft"]
        loader = key.split("-")[-1]
        ext = ".jar" if loader == "fabric" else "-liteloader.litemod"
        artifact = ROOT / "versions" / key / "build/libs" / f"mmm-{release}+{mc}{ext}"
        path = out / (key + "-packaged.json")
        row = dict(wrapper=key, artifact=str(artifact), receipt=str(path), status="unverified")
        try:
            row["sha256"] = digest(artifact)
            r = read_json(path)
            require(r["status"] == "pass" and r["exitCode"] == 0 and r["wrapper"] == key and r["loader"] == loader,
                    "invalid receipt")
            require(r["sha256"] == row["sha256"], "new binary; receipt for different bytes")
            row.update(fabric(r, mc, row["sha256"]) if loader == "fabric" else legacy(r, row["sha256"]))
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
