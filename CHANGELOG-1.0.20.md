# MMMod 1.0.20 - Minecraft 1.21.4

- Collections include unsynced mining and absorb it when website totals catch up. New tiers announce in MMM chat with saved duplicate prevention; sharing is configurable in Social.
- Restored red/green glass-pane tiers. Per-block caps range from 1K to 25M; actual mined counts remain uncapped.
- Stone and Netherrack end at 25M; Deepslate at 10M. Completion totals, pinned progress, and notifications use each block's tiers.
- Combined Terracotta includes all colors. Combined Coral includes living and dead blocks, not plants or fans.
- Non-insta Deepslate has its own 1M collection and still counts toward regular Deepslate. Only new mining can be classified; this cannot be reconstructed from historical block totals.

- Added an optional MMM leaderboard sidebar with saved ranking/block selection, background refresh, paging, and existing layout controls. Server scoreboard and sync data remain unchanged.
- Added Ores (including Raw Iron Block and Gilded Blackstone) and Pickaxe categories; removed Obsidian from Overworld.

- Ported the latest local Minecraft 1.21 build, including collections, pinned collection progress, session efficiency, in-game rankings, update notices, and rank overtake estimates.

- Pin a collection from its detail page to show its block, count, next-tier target and progress bar beneath the MMM HUD. Pins remember their world/global scope and survive restarts; unpin from the book.

- Collections Global Total includes block breakdowns from your website worlds, including worlds missing from this Minecraft instance. This World stays live and local. Loading and local-only fallback are labeled; synced blocks are not counted twice.

- Redesigned Collections as a SkyBlock-style item menu with category icons, paginated block grids, and individual collection tier pages. Added a red-book shortcut at the inventory's top-right; it opens the menu without occupying an item slot.
- Added Mining Efficiency: session time spent mining, mining without instamine, moving, in inventories, and in other menus. Pauses are excluded; old or unobserved time stays untracked.
- Mining Efficiency is included directly in Session Summary, not a separate tab. `/mmm efficiency` opens the summary. Details survive checkpoints and are saved beside qualifying session history without changing the older session file format.
- Added a Block Collection Book with 10K, 100K, 1M, and 10M milestones, per-tier completion counts, search, categories, and sorting. Open the red inventory book or `/mmm collections`; Collections is not a sidebar tab.
- Collection details have dedicated totals, tier labels and progress rows. Grid tier labels sit below block icons. Collections do not change website totals or sync payloads.
- Shows a clickable downloads notice on world join when a newer MMMod release is available for your exact Minecraft version. Checks run in the background and are cached for one hour.
- Added in-game Leaderboards in the MMM sidebar and `/mmm leaderboard`.
- Browse player, server, single-world, TMC, country, SSP, and HSP rankings without a browser.
- Browse block rankings with searchable Overworld, Nether, End, Shovel, Hoe, and Axe groups and Minecraft block icons.
- Search names, scroll results, change pages, and refresh public website data. Loading runs off the game thread, with a bounded one-minute cache and no website link required.

- Added an optional Rank Overtake Estimate in HUD Content.
- Shows the next player's manual leaderboard rank, name, and blocks needed to pass them.
- Estimates active mining time using the same Blocks/hr rate as the HUD, displaying the rate alongside the ETA. Paused sessions show Paused.
- Refreshes website totals every five minutes without blocking gameplay.

Enable it in MMM Settings > HUD Content > Rank Overtake Estimate.
The estimated rank adds new local mining to the last website baseline and moves to the next target before sync. Local progress is saved per player and reconciled against newly published increases, so unchanged refreshes do not erase progress. The official website rank is shown separately. Mining before the first baseline is not retroactively added.
The estimate assumes other players' totals stay unchanged between refreshes; it is not a guaranteed finish time.
