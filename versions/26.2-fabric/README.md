# MMMod for Minecraft 26.2

MMMod 1.0.20.1: `mmm-1.0.22+26.2.jar`.

Requires Minecraft 26.2, Java 25 and Fabric Loader 0.19.3 or newer.
Fabric API is bundled. Mod Menu is optional. Open settings with `X + V`.

Includes mining HUD and sessions, collections with live progress and per-block caps,
collection pins, synced rank and Top %, shared milestones, advancements, in-game leaderboards, rank
estimates, scoreboard tools, visual settings, MMM Chat and optional website sync.
Sync is off by default and requires a website link and server-side sync access.

Build with Java 25: `gradle test build`.
Isolated client checks: `gradle -PsmokeTest runClient`.
The smoke-test mod is not included in the distributable jar.

See [the release notes](RELEASE-1.0.20.md) for a short changelog.
