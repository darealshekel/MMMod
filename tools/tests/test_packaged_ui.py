"""Offline regression controls for route identity and framebuffer evidence."""
import runpy
import tempfile
import unittest
from pathlib import Path

from PIL import Image, ImageDraw


TOOLS = Path(__file__).resolve().parents[1]
verifier = runpy.run_path(str(TOOLS / "verify-packaged-fabric.py"))
audit = runpy.run_path(str(TOOLS / "audit-packaged-ui.py"))
check_picker = verifier["picker_evidence"]


class VisibleToolsTest(unittest.TestCase):
    expected = staticmethod(verifier["expected_routes"])
    check = staticmethod(verifier["route_evidence"])

    @staticmethod
    def log(routes):
        return "\n".join(f"[STDOUT]: MMM_UI_ROUTE fresh {path} {dest}" for path, dest in routes.items())

    def test_routes_distinguish_path_and_viewport(self):
        routes = self.expected("1.21")
        self.assertEqual(routes, self.check(self.log(routes), "fresh", "1.21"))
        for key in ("wide-home-Hotkeys", "wide-tools-Hotkeys", "compact-home-Hotkeys",
                    "compact-tools-Hotkeys", "compact-home-WebsiteLink", "compact-home", "wide-tools"):
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, "wrong or missing"):
                self.check(self.log({k: v for k, v in routes.items() if k != key}), "fresh", "1.21")

    def test_wrong_destination_duplicate_or_stale_nonce_fails(self):
        routes = self.expected("1.21")
        with self.assertRaisesRegex(ValueError, "wrong or missing"):
            self.check(self.log({**routes, "wide-tools-Profile": routes["wide-tools-Projects"]}), "fresh", "1.21")
        with self.assertRaisesRegex(ValueError, "duplicate"):
            self.check(self.log(routes) + "\n" + self.log(routes), "fresh", "1.21")
        with self.assertRaisesRegex(ValueError, "wrong or missing"):
            self.check(self.log(routes), "other", "1.21")

    def test_version_matrix(self):
        for mc, leaderboard, ignored, share in [("1.17.1", False, False, True), ("1.19.4", True, True, True),
                                                ("1.20.1", False, True, True), ("26.1.2", False, False, False),
                                                ("26.2", True, True, False), ("26.3", True, True, False)]:
            with self.subTest(mc=mc):
                routes = self.expected(mc)
                self.assertEqual("compact-tools-Leaderboards" in routes, leaderboard)
                self.assertEqual("wide-tools-Leaderboards" in routes, leaderboard)
                self.assertEqual("MmmChatIgnoreListScreen" in routes, ignored)
                self.assertEqual("ProgressShareScreen" in routes, share)
                self.assertIn("picker", routes)

    def test_obsolete_receipts_are_not_ui_passes(self):
        with self.assertRaisesRegex(ValueError, "missing visible"):
            audit["fabric"]({"scope": "screens", "suite": "routes-picker-v2"}, "1.21", "old")

    def test_rail_only_replaces_home_cards_on_shared_board(self):
        home = self.expected("1.21")
        for mc in ("1.21.1", "1.21.4", "1.21.6"):
            with self.subTest(mc=mc):
                self.assertEqual("visible-rail-v4", verifier["ui_suite"](mc))
                with self.assertRaisesRegex(ValueError, "missing visible"):
                    audit["fabric"]({"scope": "screens", "suite": "visible-tools-v3"}, mc, "old")
                routes = self.expected(mc)
                self.assertEqual(routes, self.check(self.log(routes), "fresh", mc))
                for view in ("wide", "compact"):
                    self.assertNotIn(view + "-home", routes)
                    self.assertEqual(routes[view + "-everyday"], routes[view + "-digging"])
                    self.assertEqual(routes[view + "-everyday-Hotkeys"], home[view + "-home-Hotkeys"])
                    self.assertEqual(routes[view + "-everyday-WebsiteLink"], home[view + "-home-WebsiteLink"])
                self.assertEqual({k: v for k, v in home.items() if "-home" not in k},
                                 {k: v for k, v in routes.items() if "-everyday" not in k and "-digging" not in k})
                with self.assertRaisesRegex(ValueError, "wrong or missing"):
                    self.check(self.log(home), "fresh", mc)
        for mc in ("1.21", "1.21.11", "1.20.1", "26.3"):
            self.assertEqual("visible-tools-v3", verifier["ui_suite"](mc))
            self.assertIn("wide-home", self.expected(mc))

    def test_legacy_clicks_and_framebuffers_are_required_per_size(self):
        tools = ("Hotkeys", "Projects", "History", "Summary", "Scoreboard", "Leaderboards")
        clicks, screenshots = [], []
        for size in ("wide", "compact"):
            screenshots.extend((f"board-launcher-{size}.png", f"board-tools-{size}.png"))
            for tool in tools:
                clicks.append(f"[Client] MMM_BOARD_TOOL_OK {size} {tool}")
                screenshots.append(f"board-tool-{size}-{tool.upper()}.png")
        check = audit["legacy_tool_evidence"]
        run = dict(toolClicks=clicks, screenshots=screenshots)
        log = "\n".join(clicks)
        self.assertEqual(set(check(run, log)), set(screenshots))
        with self.assertRaisesRegex(ValueError, "tool clicks"):
            check({**run, "toolClicks": clicks[:-1] + clicks[:1]}, log)
        with self.assertRaisesRegex(ValueError, "framebuffer"):
            check({**run, "screenshots": screenshots[1:]}, log)

    def test_rendered_controls_required_at_reported_positions(self):
        self.check_rendered_controls("1.21", "home")

    def test_rail_and_inline_controls_must_be_painted_at_both_sizes(self):
        self.check_rendered_controls("1.21.6", "everyday")

    def check_rendered_controls(self, mc, entry):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            lines = []
            for view, sw in (("wide", 600), ("compact", 340)):
                keys = verifier["expected_tools"](mc)
                first = f"{view}-{entry}"
                shots = {first: ["Hotkeys", "WebsiteLink", "Tools"], f"{view}-tools": list(keys),
                         first + "-Hotkeys": ["Back"], **{f"{view}-tools-{k}": ["Back"] for k in keys}}
                if entry == "everyday":
                    shots[first] += ["Everyday", "Digging", "MiningTracker"]
                    shots[f"{view}-digging"] = ["Everyday", "Digging", "Tools", "FlatDigger"]
                    shots[f"{view}-tools"] += ["Everyday", "Digging", "Tools"]
                for name, widgets in shots.items():
                    im = Image.new("RGB", (2*sw, 160))
                    draw = ImageDraw.Draw(im)
                    for i, key in enumerate(widgets):
                        x = 8 + 32*i
                        lines.append(f"MMM_UI_WIDGET fresh {name} {key} {x} 8 26 18 {sw} 80")
                        draw.rectangle((2*x+4, 20, 2*x+12, 29), fill="white")
                    im.save(folder / (name + ".png"))
            check = verifier["widget_evidence"]
            log = "\n".join(lines)
            self.assertIn("Hotkeys", check(log, "fresh", mc, folder)["compact-" + entry])
            with self.assertRaisesRegex(ValueError, "missing visible"):
                check("\n".join(lines[1:]), "fresh", mc, folder)
            with self.assertRaisesRegex(ValueError, "unpainted"):
                check(log.replace(f"wide-{entry} Hotkeys 8 8", f"wide-{entry} Hotkeys 510 8"), "fresh", mc, folder)
            with self.assertRaisesRegex(ValueError, "wrong framebuffer"):
                check(log.replace("600 80", "610 80"), "fresh", mc, folder)
            if entry == "everyday":
                for missing in ("wide-everyday MiningTracker", "compact-digging FlatDigger", "compact-tools Everyday"):
                    with self.subTest(missing=missing), self.assertRaisesRegex(ValueError, "missing visible"):
                        check("\n".join(line for line in lines if missing not in line), "fresh", mc, folder)
                (folder / "compact-digging.png").unlink()
                with self.assertRaises(FileNotFoundError):
                    check(log, "fresh", mc, folder)


class PickerPixelsTest(unittest.TestCase):
    def measure(self, columns=200, rows=120, marker=True):
        with tempfile.TemporaryDirectory() as tmp:
            img = Image.new("RGB", (240, 160), "#151515")
            px = img.load()
            for y in range(120):
                r = round(255 * (1 - (y * rows // 120) / (rows - 1)))
                for x in range(200):
                    g = round(r * (1 - (x * columns // 200) / (columns - 1)))
                    px[20+x, 20+y] = (r, g, g)
            if marker:
                draw = ImageDraw.Draw(img)
                draw.rectangle((110, 75, 117, 82), outline="black")
                draw.rectangle((111, 76, 116, 81), outline="white")
            path = Path(tmp) / "picker.png"
            img.save(path)
            return check_picker(path)

    def test_high_resolution_plane_and_crisp_marker(self):
        metrics = self.measure()
        self.assertGreaterEqual(metrics["saturationLevels"], 72)
        self.assertGreaterEqual(metrics["brightnessLevels"], 52)
        self.assertGreaterEqual(metrics["sharpCursorEdgePixels"], 4)

    def test_old_47_by_28_field_is_not_a_pass(self):
        with self.assertRaisesRegex(ValueError, "saturation still coarse"):
            self.measure(47, 28)

    def test_coarse_vertical_axis_is_not_a_pass(self):
        with self.assertRaisesRegex(ValueError, "brightness still coarse"):
            self.measure(rows=28)

    def test_smooth_field_without_cursor_is_not_a_pass(self):
        with self.assertRaisesRegex(ValueError, "no sharp selection cursor"):
            self.measure(marker=False)
