# MMMod for Minecraft 26.3

Current release: **1.0.22**. Install `mmm-1.0.22+26.3.jar`.

[Download](https://github.com/darealshekel/MMMod/releases/download/v1.0.22/mmm-1.0.22%2B26.3.jar) | [Full guide](../../README.md) | [Release notes](https://github.com/darealshekel/MMMod/releases/tag/v1.0.22)

## Install

Use Minecraft **26.3**, Fabric Loader **0.19.3 or newer**, and Java **25 or newer**. The matching Fabric API is bundled; Mod Menu is optional. Remove the previous MMM file and put this release JAR in the instance's `mods` folder. Do not install a source or development JAR, or a separate core mod.

Open MMM with `X + V`. Existing settings and saved progress are retained. Local mining/session tracking does not need a website account. Website source sync is off by default and requires both a linked account and owner-granted canary access.

On Windows, update your launcher's Minecraft metadata. Affected launchers may require `-XX:StackShadowPages=32` to avoid a native 26.3 startup crash; the development launch task supplies it automatically.

## Build and Verify

Keep the entire shared-core repository together. This wrapper reads shared files outside this directory.

Use **JDK 26 and Gradle 9.7.0** for building, even though the packaged mod targets Java 25:

```text
gradle clean test build
```

Output: `build/libs/mmm-1.0.22+26.3.jar`.

See the [release verification report](../../docs/1.0.22-verification.md) for this version's tested scope. The shared-core refactor preserves this wrapper's existing behavior; it does not promise every feature from newer Minecraft versions.
