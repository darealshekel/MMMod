# MMM 1.12.2 LiteLoader Port

MMMod 1.0.20.2 for Minecraft 1.12.2 and LiteLoader (Java 8).

- Fixed startup crashes when Forge is installed alongside LiteLoader.
- Keeps LiteLoader-only compatibility. See [release notes](docs/1.0.20.2-release.md).

- Fixed MMM messages replaying when switching dimensions or reconnecting.
- Collections now show only blocks available in 1.12.2.
- Fixed legacy log, leaf and mushroom item metadata used by collection icons.

The current local 1.21 source is preserved in `reference-1.21`. Collections,
leaderboards, social/chat, advancements, visual features, full settings, sessions
and sync have legacy implementations. This is not a claim of certified exact
1:1 gameplay parity: see [PORT-STATUS.md](PORT-STATUS.md) for verification and
platform differences.

## Build

Use JDK 21 and the checked Gradle 8.7 distribution:

```powershell
$env:JAVA_HOME = 'C:/Users/mult0/AppData/Roaming/PrismLauncher/java/java-runtime-delta'
./tools/gradle-8.7/bin/gradle.bat releaseLiteMod
```

Output: `build/libs/mmm-1.0.20.2+1.12.2-liteloader.litemod`.

Install the `.litemod` file into a Minecraft 1.12.2 LiteLoader instance's mods folder (with or without Forge).
It is not a Fabric/Ornithe JAR. Do not install another MMM version beside it.
MMM's shared cross-version storage is retained, so back up existing MMM settings
and history before testing with a real account.

Compilation uses Java 21. JvmDowngrader converts the packaged code and includes
the required Java API implementations for Java 8. Artifact checks validate
bytecode, metadata, licenses, mixin package isolation and refmaps, and ensure
the normal output excludes the optional runtime harness.

Gson is privately relocated so Minecraft retains its own version. Fabric API,
Fabric Loader, OSL, MaLiLib and Tweakeroo are not required or bundled.
See [THIRD-PARTY.md](THIRD-PARTY.md) for library notices.

The Gradle 8.7 distribution SHA-256 is
`544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d`.

## Runtime Verification

```powershell
# Normal production-loader startup, isolated from regular instances.
./tools/smoke-client.ps1

# Test-only helper creates a flat singleplayer test world and exercises screens.
./tools/gradle-8.7/bin/gradle.bat check -PruntimeProbe
./tools/smoke-client.ps1 -RuntimeProbe -TimeoutSeconds 180

# Packaged HTTP backport, tested under Java 8 against a local HTTP server.
./tools/gradle-8.7/bin/gradle.bat compileJava8Probe
./tools/smoke-client.ps1 -TransportProbe -TimeoutSeconds 35

# Rebuild without the helper before giving anyone an artifact.
./tools/gradle-8.7/bin/gradle.bat testingLiteMod
```

The smoke script uses installed Prism libraries but its own game directory,
APPDATA, user home, config and test username under `build`. It never uses an
account token or enables website sync. Public leaderboard/tag lookups are
read-only. Logs are under `build`; screenshots are under
`build/smoke-game/screenshots`.

Authenticated sync/chat, other mods and resource-pack compatibility still need
account-specific gameplay checks. Automated checks do not certify exact parity
with every modern-version interaction. See PORT-STATUS.md for the tested scope.
