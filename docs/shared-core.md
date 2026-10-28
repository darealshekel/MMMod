# Shared-core architecture

## Why source composition

The supported loaders need incompatible Gradle plugins, Minecraft mappings and Java runtimes. Rather than forcing them into one Gradle plugin classpath or loading an extra runtime mod, each independently buildable wrapper compiles the shared sources it needs. This preserves package names, storage formats, entrypoints, mixin targets and installation behavior.

Source composition is real build input, not a copy-and-paste release process: every build reads the central files. Generated copies exist only in `build/shared-sources` and must not be edited or committed.

## Ownership

- `core/src/main/java`: common implementation of game-independent models, calculations, policies, storage and service logic.
- `core/compat/<profile>`: alternative implementations required to preserve older release behavior or runtime/library differences. A profile name identifies the first consuming wrapper; its consumers are listed in `migration/ownership.json`.
- `adapters/shared`: Minecraft-facing implementations reused by compatible wrappers. These can reference game APIs and must not become dependencies of standalone domain tests.
- `versions/<minecraft>-<loader>`: dependency configuration, entrypoints, mappings, version-specific UI/rendering/hooks, resources and integration tests.
- `shared-tests`: existing tests whose identical implementations now have one owner.

Some shared services still call wrapper-provided classes such as configuration and logging adapters. They are compiled and tested inside each wrapper. The standalone `core` target selects the dependency-closed foundation: it deliberately has no Minecraft, Fabric or LiteLoader dependency. It is not a claim that every shared service is already an independently deployable library.

## Making changes

1. Find the logical class in a wrapper's `source-set.json` and edit its owning shared file, not generated output.
2. Check `migration/ownership.json` for all consumers. Run their wrapper tests/builds; the full matrix is required before a release.
3. If a platform needs different behavior, put that behavior in its adapter. Do not duplicate a portable class merely to avoid defining the boundary.
4. New source files must be registered in the consuming manifests. `tools/check-layout.mjs` rejects missing, conflicting and orphaned shared files.
5. Set the version once in `release.properties`. Keep release documentation consistent and run all release checks again.

## What did not change

No Minecraft gameplay logic, sync-access rules, API routes, account storage locations or session formats were changed during extraction. The LiteLoader version fallback now says `unknown` instead of a stale hardcoded release; the packaged `mmm-build.properties` remains its authoritative version.

The existing compatibility implementations were not silently upgraded to the 1.21 feature set. Reducing those differences further requires separately reviewed behavioral changes, not a blind replacement with the newest file.

## Provenance

`versions.json` records the exact released baseline for all twelve builds. Most were imported from checksum-verified source assets because their historical Git tags did not contain the matching source tree. Minecraft 1.21 was imported from its actual 1.0.21 release tag. Original imports and migration tools are not used at runtime.

`migration/source-baseline.json` fingerprints the original Java compiler inputs. The before/after comparison established that moving source ownership did not change compiled main classes. This is a one-time extraction proof, not a permanent prohibition on feature changes. Continuous integration checks the ownership rules and runs the core and wrapper tests; it does not require future features to match old release hashes.
