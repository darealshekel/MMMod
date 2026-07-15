# Changelog

## 1.0.15 - 2026-07-15

### Added

- Added a complete Scoreboard screen with sorting, paging, layout, opacity, export, record, and operator editing tools.
- Added score and Tab-list commas, score abbreviation, scoreboard visibility, team-chat, and scoreboard hotkeys.
- Added optional milestone sharing between linked MMMod players on the same server.
- Added Tier / Name Tags that show a player's website total before their name in chat, Tab, scoreboards, and world nametags.
- Added a hotkey for No Swinging Animation.

### Changed

- Website totals used by Tier / Name Tags now share the exact milestone colors used on MMM profiles and leaderboards.
- Minecraft team prefixes and suffixes are replaced by the MMM tag only when a matching website player is found.
- Tier / Name Tag totals are abbreviated to keep names readable, for example `16M`.
- Tier / Name Tags load directly from the live canonical leaderboard API and report a concise result in the client log.
- Goal milestone messages were rewritten to be shorter and more natural.
- The settings sidebar now includes the dedicated Scoreboard screen below Summary.

### Removed

- No existing MMMod features were removed in this release.

## 1.0.14 - 2026-07-14

### Added

- Added a search bar to settings.
- Added an optional Daily Goal bar to the main HUD. It is off by default.
- Added `tweakToggleTab` and a hotkey for keeping the player list open.
- Added translucent lava with adjustable opacity.

### Changed

- World Total now uses supported tool scoreboards when a server has no total scoreboard.
- Timer and Block Stats controls are now grouped under HUD Content and HUD Layout.
- HUD modules can scale down to 25%, and their move boxes now fit their content.
- Lava opacity changes now apply correctly.

### Removed

- Removed the separate Timer and Block Stats settings cards.
- Hid the unfinished Speed Graph and removed unused HUD move boxes.

## 1.0.13 - 2026-07-10

### Daily goals and notifications

- Replaced editable notification thresholds with fixed 25%, 50%, 75%, and 100% milestones.
- Added independent custom OGG sounds for every milestone, with preview and reset controls.
- Added built-in success sounds for 25%/50%/75% and a completion sound for 100%.
- Added configurable whole-number or 1-3 decimal-place goal percentages.
- Made the daily `00:00 UTC` reset mandatory and removed its disable toggle.
- Added the vanilla-style goal XP bar and daily-goal line below the Tab player list.
- Added continuous red-to-green progress coloring and hue cycling beyond 100%.
- Matched milestone chat colors to the live goal percentage color.
- Rewrote milestone messages in clearer, more natural language.

### Settings and interface

- Rebuilt MMMod settings around the MMM black/red design system.
- Added responsive layouts for different Minecraft GUI scales.
- Unified remaining vanilla buttons with MMMod's settings-button styling.
- Removed duplicate controls from the Feature Toggles screen.
- Added menu, HUD, graph, and Block ESP color controls.
- Added draggable/resizable HUD modules and detailed visibility settings.

### Sessions, timer, and mining statistics

- Added automatic session start, idle pause, and resume behavior.
- Added rolling Blocks/sec, Blocks/hr, Blocks/min, current-hour, and best-hour statistics.
- Added the session-backed 1-second-to-24-hour challenge timer and boss-bar HUD.
- Added timer block statistics and a top-five completion screen.
- Improved cross-version session, daily, weekly, and personal-record storage.
- Improved disconnect/shutdown session persistence and deferred session synchronization.

### Mining tools

- Added adjustable Small Dig Items rendering.
- Added No Swinging Animation while keeping the held tool visible.
- Improved Block ESP, Flat Digger, and Perimeter Wall Dig Helper behavior.
- Added local repeated-position and mining-rate sanity checks.

### Website synchronization

- Disabled website synchronization by default for new installs.
- Added delta-based payload storage and queued retry handling.
- Improved scoreboard refresh, source totals, player rows, source identity, and block-breakdown payloads.
- Improved shared account linking and cross-version state handling.

### Fixes

- Fixed a cancellation-sensitive item-render matrix leak that could cause `Pose stack not empty` crashes in larger modpacks.
- Fixed MMMod screens colliding or overflowing at larger GUI scales.
- Fixed stale daily/weekly values and generated development runs leaking into saved history.
- Fixed timer, HUD visibility, color persistence, and project-screen issues.

## 1.0.11 - 2026-07-09

- Released the Minecraft 1.21 build.
- Improved full scoreboard refresh before sync payload creation.
- Improved authoritative source and player total synchronization.
- Fixed first-person no-swing rendering behavior and local record repair.

## 1.0.5 - 2026-04-16

- Added website account linking, queued cloud synchronization, source scanning, scoreboard parsing, and server favicon evidence.
- Restyled Website Link, Summary, Session History, Projects, and HUD panels.
- Improved session storage, pace visualization, and project management.

Older release notes remain available in [`CHANGELOG-1.0.5.md`](CHANGELOG-1.0.5.md).
