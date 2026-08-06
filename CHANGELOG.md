# Changelog

## 1.0.18.1 - 2026-07-29

### Added

- Added Minecraft and MMM channel tabs directly above the normal chat input.
- Added global MMM Chat for website-linked players without sending messages to the current server.
- Added global daily-goal milestones from linked players across servers and singleplayer worlds.
- Added separate settings for MMM Chat messages and milestone messages.
- Added a saved 0-100% MMM menu background opacity slider.

### Changed

- Menu opacity now updates live without dimming text, controls, or borders.
- In singleplayer, active sessions now pause while ESC is open or Minecraft is paused, then resume when play continues.

## 1.0.18 - 2026-07-29

### Changed

- Replaced boxed per-block coordinate and timestamp histories with bounded primitive collections.
- Coalesced repeated background persistence requests so slow storage cannot grow an unbounded executor backlog.
- Cached mining HUD and custom scoreboard render models at Minecraft tick cadence.
- Reused fixed breaking-indicator buffers instead of rebuilding indicator lists every frame.
- In singleplayer, active sessions now pause while ESC is open or Minecraft is paused, then resume when play continues.

### Fixed

- Reduced memory retention during long mining sessions.
- Reduced frame-time spikes and garbage-collection stutters while the MMM HUD, scoreboard, and breaking indicators are visible.

## 1.0.17 - 2026-07-28

### Added

- Bundled Fabric API, so MMMod now only requires Fabric Loader.
- Added colored breaking indicators that replace the vanilla crack overlay.
- Added Move Scoreboard, Transparent Tab, Tab-list commas, and scoreboard appearance controls.
- Added a per-source sync scoreboard selector.
- Added a searchable hotkey screen with two-key chords and `Escape` to unbind.
- Added a configurable Perimeter Wall Helper block list.
- Added tests for config storage, sync scoreboard selection, timers, and rolling mining speed.

### Changed

- Replaced the old config and hotkey libraries with MMMod-owned settings, color picker, hotkeys, and render helpers.
- Moved mining helpers into Settings and Visuals, and removed the separate Feature Toggles screen.
- Updated the Scoreboard screen with MMM-styled toggles, sliders, paging, movement, and editing tools.
- Moved periodic session, timer, calendar, config, and sync-queue saves off Minecraft's render thread.
- Replaced repeated mining-metric scans with a fixed-size rolling buffer.
- Reduced scoreboard evidence scans and source-identity refreshes without delaying visible values.
- Disabled hidden Speed Graph sampling while the feature remains unavailable.

### Removed

- Removed MaLiLib and Tweakeroo dependencies.
- Removed the duplicate Feature Toggles screen.

### Fixed

- Reduced stutters and freeze frames caused by disk writes, scoreboard scans, and metric recalculation.
- Fixed World Total switching between unrelated objectives or drifting from the selected source total.
- Fixed scoreboard changes inflating active session totals.
- Fixed website sync selecting project or non-mining scoreboards.
- Fixed Tier / Name Tag decorations being mistaken for server scores.
- Fixed `X + V` and other ordered two-key hotkeys.
- Fixed breaking indicators for normal and instant-mined blocks.
- Fixed settings, colors, and hotkeys not persisting correctly.

## 1.0.16 - 2026-07-24

### Added

- Added a Minecraft 1.21.6 build with the complete 1.0.16 feature set.
- Added `/mmm timer pause` to pause the timer and its run stats.
- Added Red Sand, Moss Block, Mud, and Coarse Dirt to block breakdowns.
- Added clearer sync messages for linking, cooldowns, uploads, and retries.
- Added daily-goal chat messages and pickaxe animations at every 25% milestone beyond 100%.
- Typing anywhere in MMMod Settings now starts a search automatically.

### Changed

- The 24-hour sync cooldown now applies separately to each source.
- World Total and website sync now use the validated mining scoreboard exactly, with supported tool-use totals as the fallback.
- Settings, sessions, calendars, and queued syncs now recover more safely after interrupted or malformed saves.

### Fixed

- Fixed unrelated scoreboards, such as sprint distance, replacing mining totals.
- Fixed World Total drifting after local mining and refusing corrected lower scoreboard values.
- Fixed the settings hotkey typing into search and made search properly editable.
- Fixed expired website links getting stuck in queued or retrying states.
- Fixed stale daily and weekly progress while keeping lifetime personal records.
- Fixed existing 1.0.15 settings not carrying over correctly.

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
- Tier / Name Tag totals keep one useful decimal without trailing zeroes, for example `18.5M` or `16M`.
- Tier / Name Tags load directly from the live canonical leaderboard API and report a concise result in the client log.
- Tier / Name Tags keep their last verified value through Tab-list refreshes and ignore older totals.
- Goal milestone messages were rewritten to be shorter and more natural.
- The settings sidebar now includes the dedicated Scoreboard screen below Summary.

### Removed

- No existing MMMod features were removed in this release.

## 1.0.14 - 2026-07-14

### Added

- Added a search bar to settings.
- Added an optional Daily Goal bar to the main HUD. It is off by default.
- Added `mmmToggleTab` and a hotkey for keeping the player list open.
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
