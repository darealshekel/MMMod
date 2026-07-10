# MMM Anti-Abuse Notes

## Legacy System Removed

The old client-side anti-cheat, anti-farm, suspicious scoring, AFK mining, place-and-break, and player flag telemetry system has been removed from MMMod.

Removed legacy pieces:

- `MiningValidationTracker`
- `AutomationModDetector`
- local automation-mod flag payloads
- place-and-break telemetry from block interaction hooks
- low camera variance / low position variance / no action pauses / repeated cluster scoring
- `validation*` config options
- `validation` objects in MMMod website sync payloads

## Remaining Minimal Guard

Until the new server-side anti-abuse system is implemented, MMMod keeps only a minimal local sanity guard:

- only blocks from the MMM block breakdown catalog are accepted
- blocks are counted through the survival block-break detection path, after the observed world block becomes air
- repeated coordinates in the same world/dimension are suppressed locally
- `maxBlocksPerMinute` caps accepted local valid block breaks per minute
- over-limit activity is logged and excess local counts are ignored
- no automatic bans, trust-state changes, or public flag payloads are produced by the client

This guard is intentionally small. It is not a replacement for server-side abuse detection.

## Server-Side Review Evidence

The separate `server-sync` module now collects server-authoritative, event-based review evidence:

- trust server-owned source scoreboards and server-side block break events over client totals
- accept client sessions as evidence, not as final authority
- detect farm abuse with server-side patterns such as repeated coordinates, tiny mining area, place-and-break loops, impossible BPS/BPH, and long low-variance runs
- queue suspicious syncs for owner review instead of auto-punishing players
- persist evidence until the website accepts it or an owner reviews it
- hold the suspicious authoritative snapshot instead of silently publishing it

## Follow-Up

- Add longer-window movement and placement correlation without increasing false positives.
- Show per-player server evidence and accepted baseline deltas in Owner Tools.
- Add signed server audit receipts for approved review decisions.
