# Included Libraries

## Gson 2.10.1

Copyright Google Inc. Licensed under Apache License 2.0.

- Source: https://github.com/google/gson/tree/gson-parent-2.10.1
- License text: `src/main/resources/licenses/Gson-Apache-2.0.txt`
- Packaged namespace: `com.mmm.liteloader.internal.gson`.
- The game's Gson is not overwritten or bundled in its original namespace.

## JvmDowngrader 2.0.1 Java API Implementations

Copyright William Gray and contributors. Used under LGPL 2.1.

- Source: https://github.com/unimined/JvmDowngrader
- License text: `src/main/resources/licenses/JvmDowngrader-LGPL-2.1.txt`
- Only implementations referenced by the converted mod are shaded into the
  mod artifact. No separate mod installation is required for them.
- The source/build inputs, undowngraded/unshaded artifact and applicable library
  source/version information accompany the release so the compatibility layer
  can be inspected, replaced and rebuilt. The source archive includes the Gradle
  build, and the rebuild archive includes the unshaded mod classes and API source.

Minecraft, LiteLoader, LaunchWrapper and native libraries are external runtime
requirements. This project does not bundle them into its mod artifact.
