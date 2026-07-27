# MMMod

MMMod is the official client-side companion for [Manual Mining Maniacs](https://www.mmmaniacs.com). It tracks manual mining, sessions, goals, projects, mining pace, source totals, and block breakdowns while keeping website synchronization optional and disabled by default.

## Supported Versions

| Minecraft | Release asset |
| --- | --- |
| 1.21 | `mmm-1.0.17+1.21.jar` |
| 1.21.1 | `mmm-1.0.17+1.21.1.jar` |
| 1.21.4 | `mmm-1.0.17+1.21.4.jar` |
| 1.21.6 | `mmm-1.0.17+1.21.6.jar` |

MMMod 1.0.17 packages its matching Fabric API inside the release jar. You only need:

- Fabric Loader 0.16.14 or newer
- Mod Menu is recommended for opening the settings screen

No external configuration or utility mod is required. Mod Menu remains optional because the settings screen can also be opened with `X + V`.

## Installation

1. Install Fabric Loader for your Minecraft version.
2. Place the matching MMMod jar in the instance's `mods` folder.
3. Optionally install Mod Menu.
4. Start Minecraft and press `X + V`, or use **Mods > MMM > Configure** when Mod Menu is installed.

Do not install both MMMod version jars in the same instance.

## Features

### Mining and sessions

- Counts valid manually mined blocks from MMM's maintained block catalog.
- Tracks per-session totals, duration, block breakdown, pace, best streak, and fastest 100k.
- Automatically starts after sustained valid mining, pauses after inactivity, and resumes the same paused session when mining continues.
- Supports manual start/end and pause/resume controls.
- Saves eligible sessions locally and queues them for later synchronization when enabled.
- Keeps session history separated by source/world while sharing account-level records across supported MMMod versions.
- Finalizes active session data during normal disconnect and shutdown paths.

### Mining pace and HUD

- Live Blocks/hr, Blocks/sec, and optional Blocks/min readings.
- Rolling BPS smoothing modes.
- Current hour and best-hour statistics.
- Global, source, session, daily, weekly, personal-record, reset, project, and timer lines.
- Configurable HUD position, anchor, scale, background, visibility, and colors.
- Movable timer, block-stat, hourly-stat, and notification modules.
- Optional abbreviated numbers using lowercase `k` and uppercase `M`, `B`, and `T`.

### Daily goals and milestones

- Daily progress resets automatically at `00:00 UTC` and cannot be disabled.
- Weekly progress uses the Wednesday `00:00 UTC` boundary.
- Daily and weekly personal records are retained across resets.
- Optional vanilla XP-bar replacement and goal information below the Tab player list.
- Goal percentage can use no decimals or 1-3 decimal places.
- The goal bar progresses from red to green, then cycles through hues beyond 100%.
- Fixed milestone notifications at 25%, 50%, 75%, and 100%.
- Pickaxe milestone animation: stone, iron, diamond, then netherite.
- Built-in milestone sounds plus a separate custom OGG sound for every milestone.
- Custom sounds are stored in MMM's shared data folder and work across supported versions.
- Optional milestone sharing between linked MMMod players on the same server.

### Timer challenge

- Challenge timer from one second up to 24 hours.
- Timer start/resume controls the existing MMM session instead of creating a separate mining system.
- Boss-bar-style timer HUD, hourly statistics, and top-block tracking.
- Optional completion screen showing the timer run's top five blocks.
- Persistent timer state across restarts.

Commands:

```text
/mmm timer start [duration]
/mmm timer pause
/mmm timer stop
/mmm timer reset
/mmm timer set <duration>
/mmm timer status
/mmm timer 1h ... /mmm timer 24h
```

Durations accept values such as `90s`, `30m`, and `2h`.

### Projects and history

- Create, edit, activate, and remove mining projects.
- Track active project progress in the HUD.
- Browse saved sessions by source/world.
- Inspect session totals, duration, pace, and block breakdowns.
- Export history for the current source using a configurable hotkey.
- View current-session and completed-session summaries.

### Mining helpers

- Block ESP with single-color or rainbow modes, opacity, animation speed, and render style controls.
- Flat Digger protection against digging below the configured level while allowing sneak bypass.
- Perimeter Wall Dig Helper with configurable protected blocks.
- Small Dig Items mode with an adjustable item scale.
- No Swinging Animation mode that keeps the first-person tool visible and static.
- Toggle Tab keeps the player list open and can be assigned its own hotkey.
- Optional translucent animated lava with an automatically managed resource pack and adjustable opacity.
- Optional colored breaking indicators replace the vanilla crack overlay with live breaking progress.

### Scoreboard tools

- Dedicated **Scoreboard** screen below Summary in the MMM sidebar.
- Hide the sidebar or its scores, add commas, or abbreviate large values.
- Make the Tab player-list background transparent.
- Optional Tier / Name Tags show each ranked player's website total before their name in chat, Tab, scoreboards, and world nametags.
- Sort by score or name in either direction and page through up to 100 rows at a time.
- Freely move and scale the sidebar, then adjust its background, title, and text opacity.
- Export the current objective directly to CSV or record, reorder, remove, and combine several snapshots.
- Add, edit, and remove scoreboard rows when the server grants operator permission level 2.
- Optional team chat by default, with `#` used to send a normal public message.
- `/mmm scoreboard max <count>`, `/mmm scoreboard pageUp`, and `/mmm scoreboard pageDown` client commands.

### Website linking and synchronization

- Website synchronization is **off by default**.
- In-game website-link flow associates MMMod with a claimed Minecraft account.
- Per-source scoreboard selector chooses the total-mined objective used for website synchronization.
- Reads visible scoreboard evidence, player/source totals, source identity, block breakdowns, and sessions.
- Delta-based queued synchronization avoids repeatedly sending unchanged local data.
- Failed requests remain queued for later delivery.
- Source and player evidence is sent together so the website can validate updates server-side.
- Local sanity checks reject repeated-position abuse and excessive local mining rates before data is queued.

The website/API remains responsible for validating data before publishing it. MMMod should not be treated as a server-authoritative anticheat.

## Default Hotkeys

| Action | Default |
| --- | --- |
| Open MMMod settings | `X + V` |
| Open session summary | `Left Alt + S` |
| Open session history | `Left Alt + H` |
| Pause/resume session | `Left Alt + P` |
| Start/end session | `Left Alt + T` |
| Export current history | Unbound |
| Previous/next scoreboard page | `Page Up` / `Page Down` |
| Open scoreboard exports | `Backslash` |
| Edit current scoreboard | `C` |

Every hotkey can be changed in the searchable MMMod Hotkeys screen. Hotkeys support single keys or ordered two-key chords, and `Escape` clears an assignment.

## Local Data

Shared cross-version data is stored outside a single Minecraft instance:

- Windows: `%APPDATA%\ManualMiningManiacs`
- Linux/macOS fallback: `~/.manual-mining-maniacs`

This contains shared state, sessions, timer state, and custom milestone sounds. Instance-specific configuration remains in the Minecraft instance's config folder.

## Building

MMMod uses Java 21 and Gradle:

```text
gradle clean build
```

The remapped release jar is written to `build/libs`.

## Credits

- **Shekel** - creator and lead developer
- **Manual Mining Maniacs community** - testing, mining data, and feature feedback
- **BlockTimer project (MIT)** - reference for timer and hourly-stat behavior
- **I-See-Lava by AzureDoom / jlstout1998 (MIT)** - reference for translucent lava behavior
- **ScoreboardHelper by 小猪之最 (MIT)** - scoreboard rendering, export, editing, and team-chat feature reference
- **Fabric API** and **Mod Menu** - modding platform and optional settings integration
- Minecraft is a trademark of Microsoft. MMMod is an independent community project and is not affiliated with Mojang Studios or Microsoft.

## License

MMMod is available under the [MIT License](LICENSE).
