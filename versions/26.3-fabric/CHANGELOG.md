# Changelog

## 1.0.22 - Shared core

- Build shared MMM code from one repository while preserving this version's existing features.
- Keep Minecraft/loader integrations in version-specific adapters.
- No new sync rules or required extra mod installation.

## 1.0.20.1 - 2026-10-03

- Linking enables personal progress sync without canary access; public source changes still require approval.
- Creative, Spectator and unknown game modes cannot upload mining data. Unsafe old queue items are discarded.
- Personal progress and ended sessions can sync during the public source cooldown.
- Added pending-review backoff and clearer personal/public sync receipts. Public updates follow the UTC daily reset.
- Existing version-specific features remain unchanged.

## 1.0.20 - Minecraft 26.3

- Port the latest local 1.0.20 features to Minecraft 26.3.
- Adapt input, chat, inventories, advancements and rendering to the new client APIs.
- Include the updated collection caps, combined Coral blocks and Terracotta, and
  separate Non-insta Deepslate tracking.
- Preserve live collection progress, pinned collections and milestone deduplication.
- Include collection ranks/top percentages, small-cap tiers and the scrollable
  glass-pane completion overview.
- Fix SDL mouse controls, MMM chat tab selection, automatic search input and
  collection icon rendering for 26.3.
- Include current sessions, sync validation, social controls and in-game rankings.
