# MMMod

MMMod is the official client mod for [Manual Mining Maniacs](https://www.mmmaniacs.com). It tracks mining stats, sessions, goals, block breakdowns, and optional website sync.

## Supported Versions

| Minecraft | Release jar |
| --- | --- |
| 1.21 | `mmm-1.0.18.1+1.21.jar` |
| 1.21.1 | `mmm-1.0.18.1+1.21.1.jar` |
| 1.21.4 | `mmm-1.0.18.1+1.21.4.jar` |
| 1.21.6 | `mmm-1.0.18.1+1.21.6.jar` |
| 1.21.11 | `mmm-1.0.18.1+1.21.11.jar` |

## Install

1. Install Fabric Loader 0.16.14 or newer.
2. Put the jar for your Minecraft version in the `mods` folder.
3. Start Minecraft and press `X + V`.

Fabric API is included. Mod Menu is optional.

## Features

- Tracks valid mined blocks, source totals, block breakdowns, and mining pace.
- Records sessions with blocks, Session IGT, total Session Time, best hour, fastest 100k, and source/world history.
- Shows Blocks/hr, Blocks/min, Blocks/sec, daily and weekly progress, records, projects, and timer data.
- Automatically starts, pauses, and resumes sessions from valid mining activity.
- Provides configurable HUD modules, colors, scale, position, background, and visibility.
- Supports daily goals, XP-bar progress, milestone sounds, pickaxe animations, and shared milestones.
- Includes projects, a 24-hour challenge timer, Block ESP, Flat Digger, Perimeter Wall Dig Helper, Small Dig Items, static tools, translucent lava, and breaking indicators.
- Includes scoreboard sorting, paging, formatting, export, editing, movement, transparency, and Tier / Name Tags.
- Includes global MMM Chat for website-linked players.
- Saves shared records and sessions across supported MMMod versions.

## Website Sync

Website sync is optional and **off by default**.

- Link the mod with a code generated on the MMM website.
- Select the correct total-mined scoreboard for each source.
- MMMod queues changed totals, scoreboard evidence, block breakdowns, and eligible sessions.
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

MMMod uses Java 21:

```text
gradle clean build
```

The release jar is written to `build/libs`.

## Credits

- **Shekel** - creator and lead developer
- **MMM community** - testing and feedback
- **BlockTimer**, **I-See-Lava**, and **ScoreboardHelper** - MIT-licensed feature references
- **Fabric API** and **Mod Menu** - Fabric platform and optional config integration

MMMod is an independent community project and is not affiliated with Mojang Studios or Microsoft.

## License

[MIT](LICENSE)
