# Port Status

Updated: 2026-09-22.

Target: the current local MMMod 1.0.20 for Minecraft 1.21, adapted to Minecraft
1.12.2 with LiteLoader. The feature areas below are implemented and a local
release artifact is available. **Exact 1:1 gameplay parity is not certified.**
Authenticated multiplayer checks and platform differences remain listed below.

## Implemented

| Area | Legacy implementation |
| --- | --- |
| Loader | LiteLoader entrypoint, once-per-game-tick processing, instance adapter, production remapping, isolated mixin packages and Java 8 API backports. |
| Mining and sessions | World/global totals, metadata-aware block counts, projects, IGT versus elapsed time, pause/resume, hourly statistics, fastest 100K, save-on-end, history and summary. |
| Efficiency | Active-time samples, movement/mining/inventory/idle accounting, summary sections and asynchronous saved-session sidecars. |
| Sync | Website link/config, source and scoreboard selection, source identity, scheduled sync, queues, retries and status explanations. |
| Collections | Red inventory book, categories, search, caps/tiers, glass-pane completion overview, global website worlds, live local deltas, pinning, ranks/percentiles and milestone sending. |
| Leaderboards | Player, server, world, TMC/SSP/HSP, country and block rankings, search, paging, categories and optional sidebar replacement. |
| Social | Vanilla/MMM chat tabs, automatic input focus, vanilla reset on reopen, client commands/completion, authenticated public chat/SSE, censorship, ignore controls, milestones and active/friend digger markers. |
| Tags and scoreboard | Offline name metadata, live numeric scores, tab score formatting/visibility, free placement, sorting, paging, records, CSV export and permission-gated scoreboard edits. |
| Advancements | All 19 current MMM advancements in the vanilla screen, live progress, website reconciliation, native toasts, clipped scrolling and milestone publication. |
| Visuals | Break indicators replacing cracks, instamine completion pulse, actual-shape ESP, translucent lava mesh, no-swing including hoes/shears, small block items and transparent tab background. |
| Goals and sounds | Goal/over-goal notifications, pickaxe animation, XP-bar replacement, custom OGG playback and sound controls. |
| Settings and HUD | Full settings groups, search, two-key hotkeys, Escape unassignment, colors/opacity, HUD movement, timer/results and projects. Collections remain accessible through the inventory book and command, not the settings sidebar. |
| Updates | Minecraft-version-specific release lookup and clickable join-world update notice. |

The source snapshot in `reference-1.21` preserves the current local modern build.
Parity tests compare 15 portable data/accounting models against that reference
with only documented loader/logger substitutions. This is deliberately narrower
than claiming every rendered screen and multiplayer interaction is identical.

## Verified

Final normal build command:

```powershell
$env:MMM_COLLECTIONS_LIVE = 'false'
$env:MMM_LEADERBOARD_LIVE = 'false'
./tools/gradle-8.7/bin/gradle.bat testingLiteMod compileJava8Probe
```

- 390 tests passed; zero failures or errors, with two optional live checks skipped.
- A separate live run passed collections but received HTTP 503 from the public
  leaderboard service. This build has not verified an authenticated link claim.
- Tests cover mining/scoreboard reconciliation, pause and hourly accounting,
  sessions, persistence, config migration, sync scheduling/queues, social HTTP/SSE,
  command parsing, hotkeys, visuals, scoreboard edits, collections and achievements.
- Earlier read-only live checks loaded all eight leaderboard types and a real player's
  collection breakdown across seven website worlds and 83 block types.
- Artifact checks validated 643 packaged Java 8 classes, metadata, production
  refmaps, narrow mixin packages, embedded licenses and absence of the runtime
  test helper. Gson is relocated; Minecraft/LiteLoader classes are not bundled.
- A Java 8 packaged-client probe against a local HTTP server passed blocking and
  asynchronous POST checks for JSON bodies, authorization headers, status and
  response bodies. Tests do not submit data to the website.
- The initial normal artifact, without the runtime helper, also passed a separate
  45-second original-loader startup check. MMM, textures and audio initialized;
  the test then stopped at its time limit without a startup crash. The expected
  Realms warning came from its deliberately invalid test token.

An isolated original Minecraft 1.12.2 + LiteLoader + Java 8 runtime completed an
automated singleplayer test, exited normally and printed `MMM_RUNTIME_PROBE_OK`:

- Joined a generated flat world and mined a diorite block through the client
  interaction manager; the metadata-aware count increased by exactly one.
- Opened the pause menu, checked that session IGT stopped, then resumed it.
- Enabled ESP/break indicators during the mining check. This exercises their
  hooks, but is not a comprehensive visual/resource-pack compatibility check.
- Typed into chat without clicking the input, selected MMM, reopened to vanilla,
  and ran `/mmm collections` without leaking that command to the server.
- Rendered the HUD, pinned collection, inventory book, all 12 settings pages,
  collections, leaderboards, scoreboard, advancements, summary/history, HUD editor,
  timer and projects. Also rendered settings, collections and summary at a smaller
  logical GUI size. Screenshots were inspected.
- Confirmed public player-tag HTTPS requests work under Java 8.
- Added a right-aligned link/sync header on every settings tab, with hover details
  and click-through to Website Account. Runtime checks opened that screen at
  normal/small GUI sizes and confirmed focus and code preservation on resize.
- Website Account now shows persisted identity and current sync status, opens
  the website, prevents duplicate pending submissions and retains retry reasons.
- Unstructured HTTP 401/403 responses no longer erase a saved link or discard
  queued claims. Official sync requests can use the existing compatibility route
  with the same credentials/item ID. Explicit credential failures still unlink;
  permission failures remain separate. Added six regression tests and checked
  the explicit JSON/User-Agent headers in the packaged Java 8 transport probe.
- Reproduced Analog's rejected `Dihs` scoreboard and verified that World Total
  replaces the 22,931 local fallback with the 183,977 score, follows increases
  and downward corrections, and does not inflate session blocks. Custom display
  titles also retain mining evidence from their objective names. Known project,
  daily and distance counters remain excluded.
- Removed the separate Hourly Stats overlay and its HUD-editor entry/position
  controls. The main HUD's Hour / Best Hour line and its visibility setting remain.
- Found and fixed a LWJGL scissor-buffer crash, advancement-tree overflow and an
  oversized single-sample summary graph during these checks.

The optional in-game test helper lives in `src/smoke`, only enters builds using
`-PruntimeProbe`, and is excluded from the distributed testing artifact. Runtime
logs and screenshots are under `build`; no account token, normal Prism instance,
real session data, website writes or public chat messages were used.

## Platform Differences

- Blocks introduced after 1.12.2 are excluded from the collections book and pins.
  Their saved cross-version history is retained; local counts are never invented.
- Logs/leaves use inventory metadata without orientation/decay bits; mushroom
  block icons use the legacy inventory variant, avoiding missing item models.
- Social identity follows the network connection rather than the dimension's
  world object. Delivered chat/milestone IDs survive reconnects for the same
  linked account, while undelivered events can replay once after a transition.
- Native diamond/obsidian assets replace unavailable netherite/blackstone assets.
- Legacy chat uses its nearest supported 16-color entry. HUD colors retain RGB.
- Vanilla 1.12.2 has no `/teammsg` or modern custom scoreboard number components;
  normal legacy team formatting and integer scores are retained.
- Native legacy screen widgets and layout adaptations are not pixel-identical to
  the modern custom GUI.

## Still Needs Gameplay Verification

- Authenticated website link and source sync against a real account, including
  cooldown/retry/reconnect and existing source reconciliation.
- Two linked clients exchanging chat, ignores, goal/collection/advancement
  milestones and active/friend presence, including deduplication after sync.
- Multiplayer server changes, live sidebar/tab/name-tag combinations and actual
  scoreboard recording, export and permitted edits.
- Slow/instant block mining, block entities, slabs, lava toggles, sneak overrides,
  enchanted/offhand tools and resource packs under normal user interaction.
- Other LiteLoader mods and extended gameplay/memory/frame-time testing.

Unit tests and a bounded singleplayer test cannot establish those results.
Use a separate test instance and back up shared MMM settings/history before
testing with a real account.

## Artifact

`build/libs/mmm-1.0.20+1.12.2-liteloader.litemod`

- Release checksums are included in the GitHub release assets.
- Requires Minecraft 1.12.2 and LiteLoader, not Fabric or Ornithe. Works with or without Forge installed.
- No Fabric API, OSL, MaLiLib or Tweakeroo dependency.

`releaseLiteMod` runs tests and artifact validation before packaging. Builds with
the runtime test helper cannot be distributed through this task. The isolated
singleplayer runtime probe passed again on September 22 after the fixes above.
The previous Ornithe project is unchanged.

## Toolchain Notes

Build with JDK 21 and the checked Gradle 8.7 distribution. JvmDowngrader produces
Java 8 bytecode and required API backports. See `THIRD-PARTY.md` for licenses and
source/rebuild requirements before publication.

Unimined's remapped LiteLoader development dependency retains MCP references in
some loader-owned mixin metadata. The production smoke script deliberately uses
the original installed Minecraft/LiteLoader artifacts; `runClient` is not verified.
The Feather mapping override avoids a camera-wrapper method collision. Changing
it can require rebuilding the project-local mapped LiteLoader dependency cache.
