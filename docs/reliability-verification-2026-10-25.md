# MMMod reliability fixes - 2026-10-25

## Scope

The nine findings from the October 25 MMMod review were addressed in the current
Minecraft 1.21 / MMMod 1.0.21 development source and the twelve supported stable
ports: 1.12.2 LiteLoader, 1.17.1, 1.19.4, 1.20.1, 1.21, 1.21.1, 1.21.4,
1.21.6, 1.21.11, 26.1.2, 26.2 and 26.3. The incomplete, unpublished Ornithe
prototype is not part of this change.

These are source-fix branches, not new releases. Existing release jars, tags,
the main branch, website deployment and sync eligibility rules are unchanged.
Some release tags did not contain their corresponding port source. Each branch
therefore records its pre-fix local port baseline separately from the fix commit.

## Changes

- Singleplayer saves use a normalized, hashed save location instead of the shared
  `.` path component. Multiplayer identity is unchanged.
- Ending a qualifying session checks the durable save result. Failed saves leave
  a paused, recoverable session and a visible error rather than deleting its
  checkpoint and reporting success. Retrying does not duplicate the history row.
- Config snapshots are captured on the client thread. Background persistence
  operates on detached JSON and does not mutate live mining counters.
- Mining counts require local break completion followed by a server-confirmed
  removal, with world-instance identity and a bounded pending queue. Canceled
  attacks and rejected predictions are not counted; slow breaks no longer expire
  merely because the original attack was more than ten seconds ago.
- Repeated Fastest 100K improvements update immediately in memory and use normal
  periodic persistence/heartbeat scheduling instead of synchronous per-block I/O.
- Hour / Best Hour continue rolling after 23 completed hours, without changing
  their existing fixed-hour semantics or paused-IGT behavior.
- Website profile requests and cached totals are tied to the active UUID and
  request generation. Delayed results from a previous identity are discarded.
- New session history/checkpoints and session-efficiency data use account-owned
  storage while remaining shared across versions for the same UUID.
- Checkpoint retirement is queued after older writes. It no longer waits on the
  client thread or races an in-flight write that can recreate an ended session.

The existing 10,000-block saved-session threshold and three-break coordinate
guard are intentionally unchanged. No canary whitelist, authentication,
Creative-mode eligibility, scoreboard-selection or public source data was edited.

## Automated Verification

All 13 normal `test build` runs succeeded: 3,021 discovered tests, 3,001 passed,
20 optional live-website tests skipped, zero failures/errors. Counts include
the same shared regression tests executed against multiple supported ports.
All thirteen normal artifacts were inspected for the new account-storage and
confirmed-break classes and for absence of the optional smoke-test entrypoints.

| Source target | Tests | Skipped |
| --- | ---: | ---: |
| 1.21 development | 263 | 2 |
| 1.21 stable | 241 | 2 |
| 1.21.1 | 237 | 2 |
| 1.21.4 | 237 | 2 |
| 1.21.6 | 237 | 2 |
| 1.21.11 | 237 | 2 |
| 1.19.4 | 241 | 2 |
| 1.20.1 | 169 | 0 |
| 1.17.1 | 137 | 0 |
| 26.1.2 | 137 | 0 |
| 26.2 | 237 | 2 |
| 26.3 | 238 | 2 |
| 1.12.2 LiteLoader | 410 | 2 |

Regression coverage includes distinct save identities, account ownership,
legacy-file preservation, failed-write acknowledgement, idempotent save retry,
ordered cleanup behind a stalled writer, detached state merges, late profile
responses, canceled/duplicate/rejected mining events, and 24/48-hour boundaries.

## Runtime Verification

All twelve Fabric targets and the packaged LiteLoader target passed their
isolated in-game reliability probes. Each completed instamine, canceled-attack,
server-rollback and slow-mining checks and a mixin audit.

The modern Yarn ports also exercised live scoreboard refresh, vanilla/MMM chat
typing and advancement screens through their existing runtime checks. The
1.17.1 adapter exercised vanilla chat typing, advancements and MMM settings;
26.x runtime coverage here is limited to mining and mixin validation. Broader
UI coverage on 26.x remains a manual compatibility check.

The test clients use isolated game directories, user homes and application data.
They do not use real account tokens or enable authenticated website sync. The
mining probes exercise an actual integrated server in Survival mode: instamine,
an attacked target removed externally after cancellation, server rollback, and
a barehand break taking over ten seconds. Pass markers are checked in addition
to the Gradle exit code. No ordinary Prism instance or real save was modified.

LiteLoader additionally exercised packaged Java 8 bytecode, chat input and MMM
channel selection, pause/resume, timer controls, website-link controls, the live
scoreboard total, collections, leaderboards, advancements, settings, HUD editor,
history and summary screens at two GUI scales. It was rebuilt without its
optional runtime entrypoint after testing.

The normal Fabric commands are `gradle test build` and
`gradle runClient -PsmokeTest -PreliabilitySmoke`. Use Java 21 for the 1.x Fabric
ports; the 26.x sources use the installed Java 26 toolchain. LiteLoader uses
Gradle 8.7 and Java 21 for compilation/downgrading, with Java 8 for the packaged
runtime probe. Its build used a local mirror of the launcher's original
LiteLoader artifact because the upstream snapshot host was unavailable.

## Limits and Migration

- Existing ambiguous `.` history is preserved as unattributed history, not copied
  into every singleplayer world. Unlinked legacy history is retained for explicit
  recovery. A matching saved website UUID is required before automatic ownership
  of old shared history is established; already-mixed old records cannot be
  reliably separated without provenance.
  If linking is changed after storage initialization, restart the client before
  relying on automatic legacy ownership migration.
- Account separation here covers session-derived storage and website profile
  responses, not a rewrite of every historical configuration aggregate.
- Unit and isolated-client passes are not a guarantee against every modpack,
  resource pack, remote-server plugin or operating-system interaction.
- No long heap soak, frame-time benchmark, authenticated production sync/chat
  send, or production data mutation was performed. These checks do not justify
  a blanket claim that all lag or memory leaks are eliminated.
- Back up existing MMM settings/history before trying a rebuilt jar with a real
  account, especially when upgrading between old shared and UUID-owned storage.
