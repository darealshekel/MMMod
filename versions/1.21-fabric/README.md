# MMMod

Track your mining, complete collections, and compete with other vanilla diggers.

MMMod is the official client mod for [Manual Mining Maniacs](https://www.mmmaniacs.com). Mining, sessions, goals, and collections work locally without an account. Website linking adds shared progress, leaderboards, and social features.

## Supported Versions

This folder builds the released **1.0.22 for Minecraft 1.21**: `mmm-1.0.22+1.21.jar`. See the [full guide](../../README.md), [Downloads](https://www.mmmaniacs.com/downloads) and [1.0.22 release](https://github.com/darealshekel/MMMod/releases/tag/v1.0.22). Do not install a jar for a different Minecraft version.

## Install

1. Install Fabric Loader 0.16.14 or newer.
2. Put the jar for your Minecraft version in the `mods` folder.
3. Start Minecraft and press `X + V`.

Fabric API is included. Mod Menu is optional.

## First Five Minutes

1. Join a world and start mining. Tracking starts automatically; linking is not required.
2. The new-install HUD shows world/session totals, session time, and pace. Existing settings stay unchanged. Use **Settings > HUD Layout > Compact HUD** to switch to this layout.
3. Open the inventory's red book or `/mmm collections` to inspect progress. Pin a collection to track its next milestone.
4. Use the Start/End Session hotkey to finish a run. Sessions with at least 10,000 blocks are saved to history.
5. Use **Share** in a session summary or the paper icon in a collection to save a PNG under `screenshots/mmm`. **Copy text** includes a profile link when this Minecraft account is website-linked.

`/mmm` opens settings. Linking adds account-connected features, but source sync still needs owner-granted canary access. New source submissions may need owner approval. Local tracking excludes creative and spectator mining; client-side checks are not an anticheat guarantee.

## Collections (Minecraft 1.21)

Open the inventory's red collection book or use `/mmm collections`. Hover a category for its completion summary, hover a block for its current tier and next target, then click it to view numbered milestone panes. Green panes are unlocked; red panes show the remaining blocks. Smaller screens have milestone page controls. The pin button tracks the next target on the HUD.

Common milestones are 1K, 2.5K, 5K, 10K, 25K, 50K, 100K, 250K, 500K, 1M, 2.5M, 5M, 10M, 15M, 20M, and 25M. Each collection stops at its existing cap. Rare collections retain their smaller targets, including 100, 250, 500, 750, and 1,000 for collections capped at 1,000.

Existing totals unlock reached milestones automatically. The update shows one local catch-up summary per account and saves that migration state; historical tiers are never queued into shared chat.

## Features

- Tracks valid mined blocks, source totals, block breakdowns, and mining pace.
- Records sessions with blocks, Session IGT, total Session Time, best hour, fastest 100k, and source/world history.
- Shows Blocks/hr, Blocks/min, Blocks/sec, daily and weekly progress, records, projects, and timer data.
- Automatically starts, pauses, and resumes sessions from valid mining activity.
- Provides configurable HUD modules, colors, scale, position, background, and visibility.
- Supports daily goals, XP-bar progress, milestone sounds, pickaxe animations, and shared milestones.
- Includes projects, a 24-hour challenge timer, Block ESP, Flat Digger, Perimeter Wall Dig Helper, Small Dig Items, static tools, translucent lava, and breaking indicators.
- Includes scoreboard sorting, paging, formatting, export, editing, movement, transparency, and Tier / Name Tags.
- Includes global MMM Chat for website-linked players, with optional censoring and per-player ignores.
- Adds an MMM advancement tree for session hours, streaks, Best Hour speed, endurance, and precision.
- Saves shared records and sessions across supported MMMod versions.

## Website Sync

Website sync is optional and **off by default**.

- Link the mod with a code generated on the MMM website.
- Ask an owner to add your linked account in Owner Tools > Sync. Linking alone does not grant source-sync access.
- Select the correct total-mined scoreboard for each source.
- MMMod queues changed totals, scoreboard evidence, block breakdowns, and eligible sessions.
- Each source can publish one complete scoreboard snapshot per UTC day.
- Visible Tab players include UUID evidence, and confirmed Carpet fake players are excluded from complete snapshots.
- Failed requests stay queued for a later retry.
- The MMM API validates data before publishing it.

MMMod is client-side evidence, not a server-authoritative anticheat.

## Timer Commands

```text
/mmm timer start [duration]
/mmm timer pause
/mmm timer stop
/mmm timer reset
/mmm timer set <duration>
/mmm timer status
```

Durations support `s`, `m`, and `h`, up to 24 hours.

## Default Hotkeys

| Action | Default |
| --- | --- |
| Open MMMod | `X + V` |
| Session summary | `Left Alt + S` |
| Session history | `Left Alt + H` |
| Pause/resume session | `Left Alt + P` |
| Start/end session | `Left Alt + T` |
| Scoreboard page | `Page Up` / `Page Down` |

All hotkeys are editable. `Escape` clears an assignment.

## Data Location

- Windows: `%APPDATA%\ManualMiningManiacs`
- Linux/macOS: `~/.manual-mining-maniacs`

## Build

Keep the entire shared-core repository together. From this wrapper directory, use JDK 21 and Gradle 9.4.1:

```text
gradle clean test build
```

The release jar is written to `build/libs`.

## Credits

- **Shekel** - creator and lead developer
- **[Aquatictw](https://github.com/Aquatictw)** - mining overlay, pace, tracking and UI contributions
- **[useleess](https://github.com/useleess)** - the 1.12.2 Forge + LiteLoader compatibility fix
- **MMM community** - testing and feedback
- **BlockTimer**, **I-See-Lava**, and **ScoreboardHelper** - MIT-licensed feature references
- **Fabric API** and **Mod Menu** - Fabric platform and optional config integration

MMMod is an independent community project and is not affiliated with Mojang Studios or Microsoft.

## License

[MIT](LICENSE)
