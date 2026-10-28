# MMMod

Track your mining, complete collections, and compare your progress with other vanilla diggers.

Download one file for your Minecraft version from [MMM](https://www.mmmaniacs.com/downloads) or [GitHub releases](https://github.com/darealshekel/MMMod/releases). No separate core mod is needed.

## Shared code, separate game adapters

```
core/                 Shared models, rules, persistence, API and statistics code
adapters/shared/      Reusable Minecraft and loader integrations
versions/             Version-only integrations, dependencies and resources
shared-tests/         Regression tests used by multiple versions
gradle/               Shared source assembly and release configuration
release.properties    The release version, set once for all builds
```

Supported builds: Fabric 1.17.1, 1.19.4, 1.20.1, 1.21, 1.21.1, 1.21.4, 1.21.6, 1.21.11, 26.1.2, 26.2 and 26.3; LiteLoader 1.12.2, with or without Forge.

Each wrapper's `source-set.json` selects shared implementations. Gradle assembles these into its ignored build directory and compiles them with that Minecraft version's mappings. The resulting JAR or LiteMod contains the core and adapter together.

Compatibility variants under `compat/` intentionally preserve existing differences between released versions. This is a behavior-preserving refactor, not a feature-parity upgrade. Changes to shared files apply to all wrappers that select them; Minecraft-specific code and intentionally different compatibility implementations still require their own changes and tests.

## Building

Use Java 21 and Gradle 9.4.1 for Fabric 1.x. Use Java 26 and Gradle 9.7.0 for Fabric 26.x. LiteLoader uses Java 21 and Gradle 8.7, then converts the resulting mod to Java 8.

From a wrapper directory, run:

```text
gradle clean test build
```

For LiteLoader, use `gradle clean test releaseLiteMod` instead. Build output is in that wrapper's `build/libs`. Never install a `-sources`, `-dev`, `-development`, or smoke artifact.

From `core/`, `gradle clean test` validates the independent domain foundation without Minecraft or loader dependencies. Wrapper tests cover runtime-coupled services and compatibility variants.

The first build requires access to the official dependency repositories. Offline builds work only after those dependencies have been cached. Original dependency versions and loader requirements are preserved.

## Verification

- `node tools/verify-sources.mjs`: checks the extraction against pinned release source fingerprints, allowing only documented test-path and metadata-fallback changes.
- `node tools/check-layout.mjs`: validates source ownership, duplicate-free compiler inputs and shared resource coverage.
- Each wrapper: its full original test suite, production build and artifact validation.
- `docs/shared-core.md`: migration scope, maintenance workflow and compatibility boundaries.
- `docs/1.0.22-verification.md`: release verification results and limitations.

Report issues at [GitHub Issues](https://github.com/darealshekel/MMMod/issues).
