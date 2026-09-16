# Minecraft 26.2 Verification

- Java 25; Fabric Loader 0.19.3; bundled Fabric API 0.156.0+26.2.
- Regression suite: 220 tests, 218 passed, two optional live tests skipped.
- Isolated Minecraft client: startup and complete mixin audit passed.
- Tested block-break tracking, collections search, inventory book, advancements, immediate vanilla/MMM chat typing, leaderboards, settings and Session Summary.
- Tested collection rank/Top % with a test-only fixture, pinned HUD progress, and small-screen tier scrolling; screenshots reviewed.
- The smoke-test source set and fixtures are excluded from the release jar.
- No live website sync or production chat message was sent. External server/modpack combinations were not exhaustively tested.

Build: `gradle test build` with Java 25.
In-game verification: `gradle -PsmokeTest runClient`.
