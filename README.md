# MMMod

Track your mining, complete collections, and compare your progress with other manual diggers.

The client mod for [Manual Mining Maniacs](https://www.mmmaniacs.com).

**Current release: [1.0.22](https://github.com/darealshekel/MMMod/releases/tag/v1.0.22).**

[Downloads](https://www.mmmaniacs.com/downloads) | [Discord](https://discord.gg/U2vQ93wWZ9) | [Report a bug or suggest a feature](https://github.com/darealshekel/MMMod/issues) | [Support MMM](https://buymeacoffee.com/manualminingmaniacs)

## Downloads and Requirements

Choose the file for your exact Minecraft version. All builds below are available in the 1.0.22 release.

| Minecraft | Loader | Java runtime | File |
| --- | --- | --- | --- |
| 1.12.2 | LiteLoader, optionally with Forge | Java 8 | [LiteMod](https://github.com/darealshekel/MMMod/releases/download/v1.0.22/mmm-1.0.22%2B1.12.2-liteloader.litemod) |
| 1.17.1 | Fabric Loader 0.16.14+ | Java 16+ | [JAR](https://github.com/darealshekel/MMMod/releases/download/v1.0.22/mmm-1.0.22%2B1.17.1.jar) |
| 1.19.4 | Fabric Loader 0.16.14+ | Java 17+ | [JAR](https://github.com/darealshekel/MMMod/releases/download/v1.0.22/mmm-1.0.22%2B1.19.4.jar) |
| 1.20.1 | Fabric Loader 0.16.14+ | Java 17+ | [JAR](https://github.com/darealshekel/MMMod/releases/download/v1.0.22/mmm-1.0.22%2B1.20.1.jar) |
| 1.21 | Fabric Loader 0.16.14+ | Java 21+ | [JAR](https://github.com/darealshekel/MMMod/releases/download/v1.0.22/mmm-1.0.22%2B1.21.jar) |
| 1.21.1 | Fabric Loader 0.16.14+ | Java 21+ | [JAR](https://github.com/darealshekel/MMMod/releases/download/v1.0.22/mmm-1.0.22%2B1.21.1.jar) |
| 1.21.4 | Fabric Loader 0.16.14+ | Java 21+ | [JAR](https://github.com/darealshekel/MMMod/releases/download/v1.0.22/mmm-1.0.22%2B1.21.4.jar) |
| 1.21.6 | Fabric Loader 0.16.14+ | Java 21+ | [JAR](https://github.com/darealshekel/MMMod/releases/download/v1.0.22/mmm-1.0.22%2B1.21.6.jar) |
| 1.21.11 | Fabric Loader 0.19.2+ | Java 21+ | [JAR](https://github.com/darealshekel/MMMod/releases/download/v1.0.22/mmm-1.0.22%2B1.21.11.jar) |
| 26.1.2 | Fabric Loader 0.19.3+ | Java 25+ | [JAR](https://github.com/darealshekel/MMMod/releases/download/v1.0.22/mmm-1.0.22%2B26.1.2.jar) |
| 26.2 | Fabric Loader 0.19.3+ | Java 25+ | [JAR](https://github.com/darealshekel/MMMod/releases/download/v1.0.22/mmm-1.0.22%2B26.2.jar) |
| 26.3 | Fabric Loader 0.19.3+ | Java 25+ | [JAR](https://github.com/darealshekel/MMMod/releases/download/v1.0.22/mmm-1.0.22%2B26.3.jar) |

Fabric builds bundle their matching Fabric API. Mod Menu is optional. The 1.12.2 file is a LiteLoader mod, not a Fabric or Ornithe mod. No separate MMM core mod is needed.

## Install

1. Set up the matching Minecraft version and loader.
2. Remove the previous MMM file from that instance's `mods` folder.
3. Add the matching release JAR or LiteMod. Keep only one MMM version installed.
4. Launch Minecraft, join a world, and open MMM with `X + V`.

Do not install source ZIPs, rebuild ZIPs, or files marked `sources`, `dev`, `development`, or `smoke`.

## Features

- **Mining stats:** session and world totals, block breakdowns, pace, daily goals, records and mining activity.
- **Sessions:** automatic tracking, pause/resume, history, summaries, Best Hour and fastest 100K. Session IGT excludes pauses; Session Time includes them.
- **Collections:** block-specific milestones, completion tiers, live progress and pinned goals.
- **Competition:** in-game player and block leaderboards, collection rankings and rank-overtake estimates.
- **HUD:** configurable modules, position, scale, colors and visibility.
- **Digging tools:** projects, timers, Flat Digger, perimeter wall helper, block highlights, breaking indicators and visual settings.
- **Social:** website-linked MMM Chat, milestones, chat filtering, ignored players and active-digger display.
- **Progress:** MMM advancements, playtime tracking and shareable session/collection images where supported.
- **Scoreboard tools:** sorting, paging, formatting, exports and optional leaderboard display.
- **Website linking:** optional account-linked progress and approved source sync.

Features and controls differ between Minecraft versions. The shared-core release preserves each build's existing features; it does not make every legacy build identical to 1.21. Local mining and session tracking do not require a website account.

## Sessions and Controls

Default bindings can be changed in MMM's hotkey menu. Press Escape while assigning a binding to clear it.

| Action | Default |
| --- | --- |
| Open MMM settings | `X + V` |
| Session summary | `Left Alt + S` |
| Session history | `Left Alt + H` |
| Pause/resume session | `Left Alt + P` |
| Start/end session | `Left Alt + T` |
| Previous/next scoreboard page | `Page Up` / `Page Down` |

Sessions need at least **10,000 blocks** to be saved to history. End a qualifying session to save it; pausing does not end it. Existing keybinds and preferences are retained when updating.

On the 1.21 build, `/mmm` opens settings and `/mmm collections` opens the collection book. Timer commands include `/mmm timer start [duration]`, `pause`, `stop`, `reset`, `set <duration>` and `status`. Durations accept `s`, `m` and `h`, up to 24 hours.

## Website Sync

Sync is **off by default**. Linking an account does **not** automatically grant permission to sync sources.

1. Generate a mod link code on the MMM website and enter it in MMM's Website Link screen.
2. Ask an MMM owner for canary sync access. Owners manage the allowlist in **Owner Tools > Sync**.
3. Enable website sync and select the correct total-mined scoreboard for the world or server, not a temporary project scoreboard.
4. Check the status shown by the mod. New sources may need owner approval before appearing publicly.

Scheduled source sync follows the UTC daily reset. Cooldowns, retry delays, missing scoreboard evidence and pending approval can delay publication. Failed requests are queued for retry; repeatedly pressing retry will not bypass server limits.

A client can only submit player scores it can actually read. Linking does not give it access to hidden server data. Fake-player checks and server validation reduce bad submissions, but MMMod is not a server-authoritative anticheat.

## Troubleshooting and Data

- **Cannot open MMM:** check the keybind and conflicts with other mods; on 1.21, try `/mmm`.
- **Sync is not accepted:** check linking, canary access, the selected scoreboard and the exact sync status before retrying.
- **Missing history:** check the 10,000-block minimum and that the session was ended successfully.
- **Crash or stutter:** attach the Minecraft version, MMM version, loader, mod list, `latest.log` and crash report to a [GitHub issue](https://github.com/darealshekel/MMMod/issues). Remove tokens, link codes and private server details first.
- **26.3 native startup crash on Windows:** update the launcher's Minecraft metadata. Affected launchers may need `-XX:StackShadowPages=32` in that instance's Java arguments.

MMM stores shared data under `%APPDATA%\ManualMiningManiacs` on Windows and `~/.manual-mining-maniacs` on Linux/macOS. Back up this folder before downgrading or testing an unreleased build.

## What Changed in 1.0.22?

Shared code and regression tests now live in one repository, with version-specific Minecraft/loader adapters. Saved data and the one-file installation remain unchanged. The release retains the 1.12.2 Forge + LiteLoader compatibility fix and introduces no new sync-access rules.

This is an internal refactor, not a new-feature release. See the [release notes](https://github.com/darealshekel/MMMod/releases/tag/v1.0.22) and [verification report](https://github.com/darealshekel/MMMod/blob/refactor/shared-core/docs/1.0.22-verification.md) for tested coverage and limitations.

## Source and Building

**The maintained multi-version source is on [`refactor/shared-core`](https://github.com/darealshekel/MMMod/tree/refactor/shared-core).** The default branch still has the historical single-version code. Use that maintained branch or the full source ZIP from the 1.0.22 release to build current versions; a single wrapper folder is not enough.

- `core/`: shared models, rules, storage, API and statistics implementations.
- `adapters/shared/`: reusable Minecraft integrations.
- `versions/`: version-specific code, dependencies, resources and source manifests.
- `shared-tests/`: common regression tests.
- `release.properties`: current build version. `versions.json` records historical import baselines, not current downloads.

Build toolchains are different from the Java runtimes used to play:

| Build target | Build JDK | Gradle | Command from its wrapper directory |
| --- | --- | --- | --- |
| Fabric 1.x | 21 | 9.4.1 | `gradle clean test build` |
| Fabric 26.x | 26 | 9.7.0 | `gradle clean test build` |
| LiteLoader 1.12.2 | 21 | 8.7 | `gradle clean test releaseLiteMod` |

Outputs are in each wrapper's `build/libs`. The LiteLoader build converts its release artifact for Java 8. Initial builds need network access for dependencies.

See the [shared-core maintenance guide](https://github.com/darealshekel/MMMod/blob/refactor/shared-core/docs/shared-core.md) for source ownership, compatibility variants and testing. Shared fixes affect only wrappers that select that implementation; game-specific changes still need separate ports and tests.

## Credits

- **Shekel** - creator and lead developer.
- **[Aquatictw](https://github.com/Aquatictw)** - mining overlay, pace graph, tracking, settings and session improvements.
- **[useleess](https://github.com/useleess)** - 1.12.2 Forge + LiteLoader SRG refmap crash fix ([PR #21](https://github.com/darealshekel/MMMod/pull/21)).
- **MMM community** - reports, testing and feedback.
- **BlockTimer, I-See-Lava and ScoreboardHelper** - MIT-licensed feature references.
- **Fabric, LiteLoader, Mod Menu and bundled library authors** - platform and library support. Version-specific third-party notices are kept with their source wrappers.

MMMod is licensed under [MIT](https://github.com/darealshekel/MMMod/blob/refactor/shared-core/LICENSE). It is not affiliated with Mojang Studios or Microsoft.
