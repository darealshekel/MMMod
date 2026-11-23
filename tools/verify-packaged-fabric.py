"""Offline Windows x64 packaged-client verification. No launcher, accounts, or Gradle tasks.

python tools/verify-packaged-fabric.py 1.21-fabric [--jar PATH] [--sha256 HEX]
python tools/verify-packaged-fabric.py --preflight
python tools/verify-packaged-fabric.py --status
python tools/verify-packaged-fabric.py 1.21-fabric --ui --sha256 HEX
python tools/verify-packaged-fabric.py --ui --status

Requires a warm Loom cache (assets, vanilla client, Maven libraries) and a JDK.
Use --gradle-home, --java-home or JAVA_HOME (JAVA26_HOME for 26.x).
--library-root optionally adds a read-only Maven-layout library cache.
Only the runtime command starts a GUI. Build results and probe live under build/.
--ui requires the visible home Hotkeys and Website Link buttons, the named Tools grid,
and Back at both wide and compact sizes. --ui --probe-only does not update the receipt.
On 1.21.1, 1.21.4 and 1.21.6 the entry page is Everyday settings; test the category
rail, inline rows, and both search exits instead of the home cards.
"""

import argparse
import hashlib
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import uuid
import zipfile
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
UI_SUITE = "visible-tools-v3"
RAIL_SUITE = "visible-rail-v4"
RAIL_VERSIONS = {"1.21.1", "1.21.4", "1.21.6"}


def ui_suite(mc):
    return RAIL_SUITE if mc in RAIL_VERSIONS else UI_SUITE


def entry_page(mc):
    return "everyday" if mc in RAIL_VERSIONS else "home"


PROBE = r"""
package smoke;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.*;

public final class PackagedProbe implements ClientModInitializer {
    final FabricLoader loader = FabricLoader.getInstance();
    final String version = prop("mc");
    final boolean mapped = version.startsWith("1."), gui = !mapped && !version.equals("26.1.2");
    final PackagedRoutes routes = Boolean.getBoolean("probe.ui") ? new PackagedRoutes(version) : null;
    Object parent, expected;
    int age, phase;
    static String prop(String k) { return System.getProperty("probe." + k); }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static Class<?> type(String name) throws Exception { return Class.forName(name); }
    void verifyOrigin() throws Exception {
        check(!loader.isDevelopmentEnvironment(), "development environment");
        check(loader.getModContainer("minecraft").orElseThrow().getMetadata().getVersion()
            .getFriendlyString().equals(version), "wrong Minecraft");
        ModContainer mod = loader.getModContainer("mmm").orElseThrow();
        check(mod.getMetadata().getVersion().getFriendlyString().equals(prop("mod")), "wrong MMM");
        Path installed = Path.of(prop("jar")).toRealPath();
        check(mod.getOrigin().getPaths().size() == 1 &&
            Files.isSameFile(installed, mod.getOrigin().getPaths().get(0)), "wrong mod origin");
        check(Files.isSameFile(installed, Path.of(type("com.mmm.MMM").getProtectionDomain()
            .getCodeSource().getLocation().toURI())), "wrong MMM code source");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(installed));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format("%02x", b));
        check(hex.toString().equals(prop("sha")), "wrong installed sha256");
    }
    public void onInitializeClient() {
        try {
            verifyOrigin();
            String events = "net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents";
            Object event = type(events).getField("END_CLIENT_TICK").get(null);
            Class<?> listener = type(events + "$EndTick");
            Object proxy = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener},
                (p, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) return method.invoke(this, args);
                    try { tick(args[0]); }
                    catch (Throwable t) { t.printStackTrace(); Runtime.getRuntime().halt(2); }
                    return null;
                });
            type("net.fabricmc.fabric.api.event.Event").getMethod("register", Object.class)
                .invoke(event, proxy);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    void tick(Object client) throws Exception {
        if (phase == 4) return;
        Class<?> mc = client.getClass();
        Object owner = gui ? mc.getField("gui").get(client) : client;
        Object current = gui ? owner.getClass().getMethod("screen").invoke(owner)
            : mc.getField(mapped ? "field_1755" : "screen").get(client);
        if (phase == 0) {
            String title = mapped ? "net.minecraft.class_442" : "net.minecraft.client.gui.screens.TitleScreen";
            if (current == null || !current.getClass().getName().equals(title)) { age = 0; return; }
            Object overlay = owner.getClass().getMethod(gui ? "overlay" : mapped ? "method_18506" : "getOverlay")
                .invoke(owner);
            if (overlay != null) { age = 0; return; }
            parent = current;
        } else if (routes == null || (phase != 1 && phase != 2)) check(current == expected, "active screen changed before tick verification");
        if (++age < (routes != null && (phase == 1 || phase == 2) ? 10 : 20)) return;
        age = 0;
        if (phase == 1 && routes != null && !routes.advance(client, current)) return;
        if (phase == 2 && routes != null && !routes.collection(client, current)) return;
        System.out.println("MMM_PACKAGED_SCREEN " + prop("nonce") + " " + current.getClass().getName());
        Class<?> screen = type(mapped ? "net.minecraft.class_437" : "net.minecraft.client.gui.screens.Screen");
        Method set = owner.getClass().getMethod(mapped ? "method_1507" : "setScreen", screen);
        if (phase == 0 || phase == 1) {
            String name = phase == 0 ? "MmmSettingsScreen" : "CollectionBookScreen";
            expected = type("com.mmm.ui." + name).getConstructor(screen).newInstance(parent);
            set.invoke(owner, expected);
            phase++;
        } else if (phase != 3) {
            expected = parent;
            set.invoke(owner, parent);
            phase = 3;
        } else {
            phase = 4;
            verifyOrigin();
            String marker = "MMM_PACKAGED_PASS " + prop("nonce") + " " + version + " " + prop("sha") +
                    (routes == null ? " screens" : " screens " + prop("suite"));
            Files.writeString(Path.of(prop("marker")), marker, StandardOpenOption.CREATE_NEW);
            System.out.println(marker);
            mc.getMethod(mapped ? "method_1592" : "stop").invoke(client);
        }
    }
}
"""


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def properties(path):
    return dict(line.split("=", 1) for line in path.read_text().splitlines()
                if "=" in line and not line.lstrip().startswith("#"))


def digest(path, algorithm="sha256"):
    h = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1048576), b""):
            h.update(block)
    return h.hexdigest()


def require(ok, reason):
    if not ok:
        raise ValueError(reason)


def allowed(lib):
    if "rules" not in lib:
        return True
    result = False
    for rule in lib["rules"]:
        target = rule.get("os", {})
        if target.get("name", "windows") == "windows" and target.get("arch", "x86_64") in ("x86_64", "amd64"):
            require(not (set(target) - {"name", "arch"}) and "features" not in rule, "unsupported library rule")
            result = rule["action"] == "allow"
    return result


def runtime(args, wrapper, mc):
    caches = args.gradle_home.resolve() / "caches"
    loom = caches / "fabric-loom"
    modules = caches / "modules-2/files-2.1"
    lib_roots = [ROOT / "build/verification/runtime-libraries", *[p.resolve() for p in args.library_root]]

    def artifact(coord, sha1=None):
        group, name, ver, *classifier = coord.split(":")
        filename = "-".join([name, ver, *classifier]) + ".jar"
        gradle = modules / group / name / ver
        candidates = list(gradle.glob("*/" + filename))
        candidates += [p / group.replace(".", "/") / name / ver / filename for p in lib_roots]
        for path in candidates:
            if path.is_file() and (not sha1 or digest(path, "sha1") == sha1):
                return path
        raise ValueError(f"missing or mismatched cached library: {coord}")

    info = read_json(loom / mc / "mojang_minecraft_info.json")
    client = loom / mc / "minecraft-client.jar"
    require(info["id"] == mc and digest(client, "sha1") == info["downloads"]["client"]["sha1"],
            "original Mojang client missing or incorrect")
    loader_version = properties(ROOT / "versions" / wrapper / "gradle.properties")["loader_version"]
    loader = artifact(f"net.fabricmc:fabric-loader:{loader_version}")
    with zipfile.ZipFile(loader) as jar:
        installer = json.loads(jar.read("fabric-installer.json"))
    cp = [loader]
    # The Fabric profile overrides vanilla coordinates (notably ASM in 1.21.4/6).
    def module_key(coord):
        group, name, _, *classifier = coord.split(":")
        return (group, name, *classifier)

    overrides = set()
    for lib in installer["libraries"]["common"] + installer["libraries"]["client"]:
        cp.append(artifact(lib["name"], lib.get("sha1")))
        overrides.add(module_key(lib["name"]))
    mapping = None
    if mc.startswith("1."):
        mapping = loom / mc / "intermediary-v2.tiny"
        if mapping.is_file():
            with mapping.open("rb") as stream:
                require(stream.readline().rstrip() == b"tiny\t2\t0\tofficial\tintermediary",
                        f"unexpected mapping namespaces for {mc}")
        else:
            cp.append(artifact(f"net.fabricmc:intermediary:{mc}:v2"))
            mapping = None
    natives = []
    for lib in info["libraries"]:
        if not allowed(lib) or lib["name"].endswith((":natives-windows-x86", ":natives-windows-arm64")):
            continue
        downloads = lib["downloads"]
        if "artifact" in downloads and module_key(lib["name"]) not in overrides:
            cp.append(artifact(lib["name"], downloads["artifact"]["sha1"]))
        classifier = lib.get("natives", {}).get("windows")
        if classifier:
            classifier = classifier.replace("${arch}", "64")
            natives.append(artifact(lib["name"] + ":" + classifier, downloads["classifiers"][classifier]["sha1"]))
    cp.append(client)
    assets = loom / "assets"
    index = next((p.stem for p in (assets / "indexes").glob("*.json")
                  if p.stem in (info["assetIndex"]["id"], mc + "-" + info["assetIndex"]["id"])
                  and p.is_file()), None)
    require(index and (assets / "objects").is_dir(), f"missing cached Minecraft assets for {mc}")
    return cp, natives, loader, loader_version, installer["mainClass"]["client"], assets, index, mapping


def timestamp():
    return datetime.now(timezone.utc).isoformat()


def expected_tools(mc):
    tools = dict(Hotkeys="com.mmm.gui.GuiConfigs", Projects="com.mmm.ui.ProjectManagerScreen",
                 Profile="com.mmm.ui.PlayerProfileScreen", History="com.mmm.hud.SessionHistoryScreen",
                 Summary="com.mmm.hud.SummaryScreen", Scoreboard="com.mmm.scoreboard.ScoreboardScreen")
    if mc not in {"1.17.1", "1.20.1", "26.1.2"}:
        tools["Leaderboards"] = "com.mmm.ui.LeaderboardScreen"
    return tools


def expected_routes(mc):
    tools = expected_tools(mc)
    routes = {s: "com.mmm.ui." + s for s in ("GoalSoundSettingsScreen", "PerimeterBlockListScreen", "SyncScoreboardScreen")}
    routes["picker"] = "com.mmm.ui.MmmColorEditorScreen"
    if mc not in {"1.17.1", "26.1.2"}:
        routes["MmmChatIgnoreListScreen"] = "com.mmm.ui.MmmChatIgnoreListScreen"
    if mc.startswith("1."):
        routes["ProgressShareScreen"] = "com.mmm.ui.ProgressShareScreen"
    for view in ("wide", "compact"):
        entry = f"{view}-{entry_page(mc)}"
        routes.update({f"{view}-tools-{name}": dest for name, dest in tools.items()})
        routes[entry + "-Hotkeys"] = tools["Hotkeys"]
        routes[entry + "-WebsiteLink"] = "com.mmm.ui.WebsiteLinkScreen"
        routes[entry] = routes[f"{view}-tools"] = "com.mmm.ui.MmmSettingsScreen"
        if mc in RAIL_VERSIONS:
            routes[f"{view}-digging"] = routes[entry]
    return routes


def route_evidence(console, nonce, mc):
    pairs = re.findall(r"MMM_UI_ROUTE " + re.escape(nonce) + r" ([\w-]+) ([.\w]+)(?=\s|$)", console)
    routes = dict(pairs)
    expected = expected_routes(mc)
    require(len(routes) == len(pairs), "duplicate route evidence")
    require(routes == expected, f"wrong or missing visible routes: {sorted(expected.items() - routes.items())}; "
            f"unexpected: {sorted(routes.items() - expected.items())}")
    return routes


def widget_evidence(console, nonce, mc, folder):
    from PIL import Image, ImageStat
    expected = set()
    for view in ("wide", "compact"):
        entry = f"{view}-{entry_page(mc)}"
        expected.update((entry, key) for key in ("Hotkeys", "WebsiteLink", "Tools"))
        expected.update((view + "-tools", key) for key in expected_tools(mc))
        expected.update((view + "-tools-" + key, "Back") for key in expected_tools(mc))
        expected.add((entry + "-Hotkeys", "Back"))
        if mc in RAIL_VERSIONS:
            for page in (entry, view + "-digging", view + "-tools"):
                expected.update((page, key) for key in ("Everyday", "Digging", "Tools"))
            expected.update(((entry, "MiningTracker"), (view + "-digging", "FlatDigger")))
    seen = {}
    pattern = r"MMM_UI_WIDGET " + re.escape(nonce) + r" ([\w-]+) (\w+)((?: -?\d+){6})(?=\s|$)"
    for shot, key, raw in re.findall(pattern, console):
        require((shot, key) in expected and key not in seen.get(shot, {}), f"unexpected widget evidence: {shot} {key}")
        x, y, w, h, sw, sh = map(int, raw.split())
        require(sw >= 530 if shot.startswith("wide-") else 320 <= sw <= 400, f"incorrect GUI width: {shot} {sw}")
        require(0 <= x < x+w <= sw and 0 <= y < y+h <= sh, f"clipped widget: {shot} {key}")
        with Image.open(folder / (shot + ".png")) as image:
            # Options force GUI scale 2. MC rounds odd framebuffer sizes up to GUI units.
            require(abs(2*sw - image.width) <= 1 and abs(2*sh - image.height) <= 1, f"wrong framebuffer for {shot}")
            region = image.crop((2*x, 2*y, min(image.width, 2*(x+w)), min(image.height, 2*(y+h)))).convert("L")
            dark, light = ImageStat.Stat(region).extrema[0]
            require(light-dark >= 45 and sum(region.histogram()[145:]) >= 6, f"unpainted widget: {shot} {key}")
        seen.setdefault(shot, {})[key] = [x, y, w, h, sw, sh]
    require({(s, k) for s, keys in seen.items() for k in keys} == expected, "missing visible widget evidence")
    return seen


def ui_evidence(console, nonce, mc, folder):
    routes = route_evidence(console, nonce, mc)
    gated = re.findall(r"MMM_UI_GATED " + re.escape(nonce) + r" ([\w:-]+)", console)
    require(gated == (["ProgressShareScreen:no-world"] if mc.startswith("26.") else []), "wrong title-world gate")
    images = set(routes) | ({"collection-detail"} if gated else set())
    hashes = {s + ".png": digest(folder / (s + ".png")) for s in sorted(images)}
    for view in ("wide", "compact"):
        pages = [entry_page(mc), "tools"] + (["digging"] if mc in RAIL_VERSIONS else [])
        require(len({hashes[f"{view}-{page}.png"] for page in pages}) == len(pages), f"unchanged page framebuffer: {view}")
    return dict(suite=ui_suite(mc), routes=routes, gated=gated, screenshots=hashes, screenshotDir=str(folder),
                widgets=widget_evidence(console, nonce, mc, folder), picker=picker_evidence(folder / "picker.png"))


def picker_evidence(path):
    from PIL import Image
    with Image.open(path) as src:
        image = src.convert("RGB")
    pixels = image.load()
    width, height = image.size

    def spans(line, component):
        best, colors, start = (0, 0, 0), set(), 0
        for i, (r, g, b) in enumerate(line + [(0, 0, 0)]):
            if r > g + 6 and abs(g-b) <= 1:
                colors.add((r, g)[component])
            else:
                if i-start >= 55 and len(colors) > best[0]:
                    best = (len(colors), start, i)
                colors, start = set(), i + 1
        return best

    # Measure contiguous scanlines of the known red HSV plane, excluding other UI.
    (across, x0, x1), _ = max((spans([pixels[x, y] for x in range(width)], 1), y)
                               for y in range(1, height-1, 3))
    require(across >= 72, f"picker saturation still coarse/missing ({across} distinct values): {path}")
    (down, y0, y1), _ = max((spans([pixels[x, y] for y in range(height)], 0), x)
                             for x in range(x0+2, x1-2, 3))
    require(down >= 52, f"picker brightness still coarse/missing ({down} distinct values): {path}")
    # The white selection cursor must have a one-framebuffer-pixel high-contrast edge inside the plane.
    sharp = sum(1 for y in range(y0+4, y1-4) for x in range(x0+4, x1-4)
                if min(pixels[x, y]) >= 245 and any(max(pixels[a, b]) <= 170
                for a, b in ((x-1,y), (x+1,y), (x,y-1), (x,y+1))))
    require(sharp >= 4, f"no sharp selection cursor inside color plane ({sharp} edge pixels): {path}")
    return dict(saturationLevels=across, brightnessLevels=down, sharpCursorEdgePixels=sharp,
                framebuffer=[width, height], sampleHex="#FFA03030", png=str(path), sha256=digest(path))


def execute(args, wrapper, mc, release, artifact, out):
    require(artifact.name == f"mmm-{release}+{mc}.jar", "expected the named final release jar")
    sha = digest(artifact)
    require(not args.sha256 or sha == args.sha256.lower(), "source SHA-256 differs from requested binary")
    with zipfile.ZipFile(artifact) as jar:
        meta = json.loads(jar.read("fabric.mod.json"))
        for screen in ("MmmSettingsScreen", "CollectionBookScreen"):
            require("com/mmm/ui/" + screen + ".class" in jar.namelist(), f"packaged jar is missing {screen}")
    require(meta["id"] == "mmm" and meta["version"] == f"{release}+{mc}", "wrong embedded mod ID/version")
    cp, natives, loader, loader_ver, main, assets, index, mapping = runtime(args, wrapper, mc)
    jdk = args.java_home or os.environ.get("JAVA26_HOME" if mc.startswith("26.") else "JAVA_HOME")
    require(jdk, "supply --java-home pointing to a JDK suitable for this Minecraft version")
    java = Path(jdk).resolve() / "bin/java.exe"
    javac = java.with_name("javac.exe")
    require(java.is_file() and javac.is_file(), f"missing JDK tools in {java.parent}")
    nonce = uuid.uuid4().hex
    run = out / "runs" / (wrapper + "-" + nonce)
    game, home, tmp = run / "game", run / "home", run / "tmp"
    for directory in [game / "mods", home, tmp, run / "appdata", run / "localappdata",
                      run / "natives", run / "classes"]:
        directory.mkdir(parents=True, exist_ok=False)
    if mapping:
        mapping_jar = run / "intermediary.jar"
        with zipfile.ZipFile(mapping_jar, "x", zipfile.ZIP_DEFLATED) as jar:
            jar.write(mapping, "mappings/mappings.tiny")
        cp.insert(1, mapping_jar)
    staged = game / "mods" / artifact.name
    shutil.copyfile(artifact, staged)
    require(digest(staged) == sha, "source changed while copying")
    env = {k: os.environ[k] for k in ("SYSTEMROOT", "WINDIR", "COMSPEC", "PROCESSOR_ARCHITECTURE",
                                      "NUMBER_OF_PROCESSORS", "SystemDrive") if k in os.environ}
    env.update(PATH=os.pathsep.join([str(java.parent), str(Path(os.environ["SYSTEMROOT"]) / "System32")]),
               APPDATA=str(run / "appdata"), LOCALAPPDATA=str(run / "localappdata"), USERPROFILE=str(home),
               HOME=str(home), TEMP=str(tmp), TMP=str(tmp), MMM_COLLECTIONS_LIVE="false", MMM_LEADERBOARD_LIVE="false")
    source = run / "PackagedProbe.java"
    source.write_text(PROBE, encoding="ascii")
    subprocess.run([str(javac), "-proc:none", "--release", "16", "-cp", str(loader), "-d", str(run / "classes"),
                    str(source), str(ROOT / "tools/PackagedRoutes.java")], env=env, cwd=run, check=True, timeout=60)
    with zipfile.ZipFile(game / "mods/packaged-probe.jar", "x") as probe:
        probe.writestr("fabric.mod.json", json.dumps(dict(schemaVersion=1, id="mmm_packaged_probe",
                       version="1", environment="client", entrypoints={"client": ["smoke.PackagedProbe"]},
                       depends={"mmm": "*"})))
        for compiled in (run / "classes").rglob("*.class"):
            probe.write(compiled, compiled.relative_to(run / "classes"))
    for native in natives:
        with zipfile.ZipFile(native) as jar:
            for name in jar.namelist():
                if name.lower().endswith(".dll"):
                    (run / "natives" / Path(name).name).write_bytes(jar.read(name))
    (game / "options.txt").write_text("onboardAccessibility:false\nfullscreen:false\nguiScale:2\n", encoding="ascii")
    hosts = run / "hosts"
    hosts.write_text("127.0.0.1 localhost\n::1 localhost\n", encoding="ascii")
    marker, log = run / "probe.pass", run / "console.log"
    vm = ["-Xms256m", "-Xmx2g", "-XX:ActiveProcessorCount=2", f"-Duser.home={home}", f"-Djava.io.tmpdir={tmp}",
          f"-Djdk.net.hosts.file={hosts}", "-Dhttp.proxyHost=127.0.0.1", "-Dhttp.proxyPort=9",
          "-Dhttps.proxyHost=127.0.0.1", "-Dhttps.proxyPort=9", f"-Djava.library.path={run / 'natives'}",
          f"-Dprobe.mc={mc}", f"-Dprobe.jar={staged}", f"-Dprobe.sha={sha}", f"-Dprobe.marker={marker}",
          f"-Dprobe.nonce={nonce}", f"-Dprobe.mod={meta['version']}", f"-Dprobe.ui={str(args.ui).lower()}",
          f"-Dprobe.suite={ui_suite(mc)}",
          "-cp", os.pathsep.join(map(str, cp)), main, "--gameDir", str(game), "--assetsDir", str(assets),
          "--assetIndex", index, "--username", "PackagedTester", "--uuid", "4b826cccfd9e45d9b58d927da9d15074",
          "--accessToken", "0", "--version", mc, "--versionType", "release",
          "--width", "1280" if args.ui else "960", "--height", "720" if args.ui else "640"]
    argfile = run / "client.args"
    argfile.write_text("\n".join('"' + arg.replace("\\", "/").replace('"', '\\"') + '"' for arg in vm), encoding="utf-8")
    started = timestamp()
    print(f"Testing {artifact} sha256={sha}\nEvidence: {run}", flush=True)
    with log.open("xb") as console:
        code = subprocess.run([str(java), "@" + str(argfile)], env=env, cwd=game, stdout=console,
                              stderr=subprocess.STDOUT, timeout=args.timeout).returncode
    expected = f"MMM_PACKAGED_PASS {nonce} {mc} {sha} screens" + (" " + ui_suite(mc) if args.ui else "")
    require(code == 0, f"client exited {code}; inspect {log}")
    require(marker.is_file() and marker.read_text() == expected and expected in log.read_text(errors="replace"),
            f"no matching fresh in-game and console marker; inspect {log}")
    require(digest(artifact) == digest(staged) == sha, "binary changed before verification completed")
    require(not any((game / "saves").glob("*")), "test unexpectedly created a world")
    ui = {}
    if args.ui:
        ui = ui_evidence(log.read_text(errors="replace"), nonce, mc, game / "screenshots")
        ui.update(routeCoverage="screen mouse input: home Hotkeys, Website Link, Tools grid, Back, search editors, Collections",
                  limits="synthetic title-screen context; no in-world editing, website login, exports, or exhaustive visual review")
        if mc in RAIL_VERSIONS:
            ui["routeCoverage"] = ("screen mouse input: header Hotkeys and Website Link, category rail, inline Everyday and "
                                   "Digging toggles, Tools grid and Back, search editors and Back/Esc, Collections")
        print(json.dumps(ui["picker"]), flush=True)
    if args.probe_only:
        print(f"PROBED {wrapper} {sha} (diagnostic only, no release receipt)", flush=True)
        return
    receipt = dict(status="pass", sha256=sha, loader="fabric", wrapper=wrapper, minecraft=mc, version=meta["version"],
                   loaderVersion=loader_ver, artifact=str(artifact), installed=str(staged), nonce=nonce, scope="screens",
                   checks=["production origin and code source", "title screen", "settings", "collections", "return to title"],
                   started=started, completed=timestamp(), exitCode=code, java=str(java), log=str(log),
                   marker=str(marker), gameLog=str(game / "logs/latest.log"), **ui)
    temp = run / "receipt.json"
    temp.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    temp.replace(out / (wrapper + "-packaged.json"))
    print(f"PASS {wrapper} {sha}", flush=True)


def main():
    wrappers = {v["id"]: v["minecraft"] for v in read_json(ROOT / "versions.json") if v["id"].endswith("-fabric")}
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("wrapper", choices=wrappers, nargs="?")
    mode = p.add_mutually_exclusive_group()
    mode.add_argument("--status", action="store_true", help="compare receipts to current artifacts; nonzero unless all pass")
    mode.add_argument("--preflight", action="store_true", help="check cached runtime inputs only, no PASS receipts")
    mode.add_argument("--probe-only", action="store_true", help="exercise a candidate, never issue a release receipt")
    p.add_argument("--jar", type=Path, help="exact final jar (default: wrapper build/libs)")
    p.add_argument("--java-home", type=Path)
    p.add_argument("--gradle-home", type=Path, default=Path(os.getenv("GRADLE_USER_HOME", Path.home() / ".gradle")))
    p.add_argument("--library-root", type=Path, action="append", default=[])
    p.add_argument("--timeout", type=int, default=240)
    p.add_argument("--sha256", help="also require this exact SHA-256")
    p.add_argument("--ui", action="store_true", help="require rendered child actions, Back, framebuffer and picker pixels (Pillow)")
    a = p.parse_args()
    require(a.wrapper or a.preflight or a.status, "select a wrapper, --preflight, or --status")
    require(not a.jar or a.wrapper, "--jar requires an explicit wrapper")
    require(a.timeout > 0, "--timeout must be positive")
    require(platform.system() == "Windows" and platform.machine().lower() in ("amd64", "x86_64"), "requires Windows x64")
    release = properties(ROOT / "release.properties")["mod_version"]
    require(re.fullmatch(r"\d+(\.\d+){2,}", release), "invalid mod_version")
    out = ROOT / "build/verification" / release
    failed = False
    for wrapper, mc in wrappers.items():
        if a.wrapper and wrapper != a.wrapper:
            continue
        jar = (a.jar or ROOT / "versions" / wrapper / "build/libs" / f"mmm-{release}+{mc}.jar").resolve()
        try:
            if a.status:
                require(jar.is_file(), "awaiting binary")
                path = out / (wrapper + "-packaged.json")
                require(path.is_file(), "no packaged pass")
                r = read_json(path)
                require(r["wrapper"] == wrapper and r["minecraft"] == mc and r["version"] == f"{release}+{mc}"
                        and r["scope"] == "screens" and r["status"] == "pass" and r["loader"] == "fabric" and r["exitCode"] == 0
                        and r["sha256"] == digest(jar)
                        and (not a.sha256 or r["sha256"] == a.sha256.lower()), "receipt does not match current jar")
                extended = r.get("suite") in {"routes-picker-v1", "routes-picker-v2", UI_SUITE, RAIL_SUITE}
                require(not a.ui or r.get("suite") == ui_suite(mc), f"no {ui_suite(mc)} verification for these bytes")
                if extended:
                    if r["suite"] == ui_suite(mc):
                        ui = ui_evidence(Path(r["log"]).read_text(errors="replace"), r["nonce"], mc, Path(r["screenshotDir"]))
                        require(all(r.get(k) == v for k, v in ui.items()), "changed visible route/framebuffer evidence")
                    else:
                        require(all(digest(Path(r["screenshotDir"]) / name) == sha for name, sha in r["screenshots"].items()),
                                "changed framebuffer evidence")
                expected = f"MMM_PACKAGED_PASS {r['nonce']} {mc} {r['sha256']} {r['scope']}" + (" " + r["suite"] if extended else "")
                require(Path(r["marker"]).read_text() == expected and expected in Path(r["log"]).read_text(errors="replace"),
                        "missing or mismatched runtime evidence")
                print(f"{wrapper}: PASS {r['sha256']} {r['scope']}")
            elif a.preflight:
                runtime(a, wrapper, mc)
                print(f"{wrapper}: cached runtime inputs available (not a pass)")
            else:
                execute(a, wrapper, mc, release, jar, out)
        except (OSError, ValueError, KeyError, subprocess.SubprocessError, zipfile.BadZipFile) as exc:
            print(f"{wrapper}: {exc}", file=sys.stderr, flush=True)
            failed = True
    return int(failed)


if __name__ == "__main__":
    sys.exit(main())
