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

## 1.0.20 - Minecraft 26.2

- Port the latest released 1.0.20 features to Minecraft 26.2.
- Adapt input, chat, inventories, advancements and rendering to the new client APIs.
- Include the updated collection caps, combined Coral blocks and Terracotta, and
  separate Non-insta Deepslate tracking.
- Preserve live collection progress, pinned collections and milestone deduplication.
- Include current sessions, sync validation, social controls and in-game rankings.
- Add collection rank and Top %, accessible red/green completion panes, low-cap tiers, and the 1M Scaffolding cap.
- Fix collection icons being replaced by standard button labels on the new rendering API.

See [the release summary](RELEASE-1.0.20.md).
