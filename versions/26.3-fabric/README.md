# MMMod for Minecraft 26.3

MMMod 1.0.20.1: `mmm-1.0.22+26.3.jar`.

Requires Minecraft 26.3, Java 25 and Fabric Loader 0.19.3 or newer.
Fabric API is bundled. Mod Menu is optional. Open settings with `X + V`.

On Windows, update your launcher's Minecraft metadata or add
`-XX:StackShadowPages=32` to the instance's Java arguments. Minecraft 26.3 needs
this setting to avoid a native startup crash on affected launchers, even without
MMM. The development launch task supplies it automatically.

Includes mining HUD and sessions, collections with live progress and per-block caps,
collection pins and shared milestones, advancements, in-game leaderboards, rank
estimates, scoreboard tools, visual settings, MMM Chat and optional website sync.
Sync is off by default and requires a website link and server-side sync access.

Build with Java 25: `gradle test build`.
Isolated client checks: `gradle -PsmokeTest runClient`.
The smoke-test mod is not included in the distributable jar.

See `PORTING-STATUS.md` for verification details.
