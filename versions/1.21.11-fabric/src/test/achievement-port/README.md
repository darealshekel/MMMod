# Achievement Port Integration

The production adapter and the client smoke harness compile against the cached
1.21.11 Yarn build.5 classes. `verify.ps1` runs 15 focused JUnit checks and compiles
the pure core with `--release 16`, without invoking Gradle. It requires the
existing Loom cache, a target dev jar, cached JUnit 5.11.4, and `JAVA_HOME` (21+).
The graphical smoke still needs the parent integration run.

## Source Mappings

All paths below are repository-relative. Add these logical entries to the target
`source-set.json`; replace an existing mapping rather than selecting duplicates.

| Logical main source under `com/mmm/` | Canonical source |
| --- | --- |
| `advancement/AchievementCardState.java` | `core/src/main/java/com/mmm/advancement/AchievementCardState.java` |
| `advancement/AchievementBadgeCatalog.java` | `core/src/main/java/com/mmm/advancement/AchievementBadgeCatalog.java` |
| `advancement/AchievementBadges.java` | `versions/1.21.11-fabric/src/main/java/com/mmm/advancement/AchievementBadges.java` |
| `advancement/AchievementCardRenderer.java` | `versions/1.21.11-fabric/src/main/java/com/mmm/advancement/AchievementCardRenderer.java` |
| `mixin/AdvancementWidgetMixin.java` | `versions/1.21.11-fabric/src/main/java/com/mmm/mixin/AdvancementWidgetMixin.java` |

| Logical test under `com/mmm/advancement/` | Canonical source |
| --- | --- |
| `AchievementCardStateTest.java` | `shared-tests/src/test/java/com/mmm/advancement/AchievementCardStateTest.java` |
| `AchievementBadgeCatalogTest.java` | `shared-tests/src/test/java/com/mmm/advancement/AchievementBadgeCatalogTest.java` |
| `AchievementBadgesTest.java` | `versions/1.21.11-fabric/src/test/java/com/mmm/advancement/AchievementBadgesTest.java` |

The two pure classes/tests also belong in the core standalone source manifest.
Both 1.21 and 1.21.11 can select the same pure classes/tests. The 1.21 state was
copied without behavioral changes; its original was left for the parent to
remove. This port does not edit the 1.21 badge renderer. Parent should delegate
its lookup to `AchievementBadgeCatalog.forDefinition(definition)` and
`forAdvancement(namespace, path)`, translating `Badge.texturePath()` into an
`mmm` Identifier and retaining `Badge.sourceSize()`.

Latest collection tier spacing requires the existing
`core/compat/1.21-fabric/src/main/java/com/mmm/tracker/CollectionProgress.java`
and matching `CollectionLiveProgress.java` selection, plus their compat tests.
Those selections and milestone producers remain parent-owned.

## Resources And Registration

- All ten PNGs and five session-hours `.png.mcmeta` files were copied byte-for-byte
  into target `src/main/resources/assets/mmm/textures/gui/advancements/`.
  They need no extra resource mappings; avoid also mapping the same logical paths.
- `AdvancementWidgetMixin` already appears in target `mmm.mixins.json`. Select the
  target implementation; it wraps both `renderWidgets` and `drawTooltip`.
- At initialization call
  `MmmAdvancementManager.setNotificationRenderer(AchievementCardRenderer::show)`.
- Call `AchievementCardRenderer.render(drawContext, client)` from the HUD renderer,
  after ordinary HUD elements. The adapter creates a root GUI layer rather than
  attempting a Z translation on `Matrix3x2fStack`.
- Call `AchievementCardRenderer.clear()` on disconnect/world-session reset.
- Existing collection award logic should call
  `AchievementCardRenderer.showCollection(collectionId, displayName, milestone)`.
  This queues a visual card only; it does not award, persist, or publish anything.
  Keep feature gating, migration/backfill suppression, and chat in their owners.
- Sound uses `PositionedSoundInstance.ui(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
  1F)`, the 1.21.11 replacement for `master(...)`, with the same event, pitch and
  default 0.25 gain, gated by `FeatureToggle.MMM_SOUND_ALERTS`.
- No production preview button was added. The parent owns `-PachievementPreview`
  and release compilation of settings controls.

## Client Smoke

After the isolated smoke world has loaded and the usual HUD/manager hooks are
registered, invoke once from the client thread:

```java
new com.mmm.smoke.SessionHoursBadgeSmoke().install();
```

The default completion action stops the client. To chain another probe use
`install(Runnable onComplete)` instead. It always stops the client on failure.
Parent can route `-PachievementSmoke` to this call in its smoke entry point.
Do not run another screen-driving probe concurrently.

The harness checks all ten rendered cards and widget mappings, both families'
tooltips, unchanged achievement award state, collection item fallback, no red top
stripe, small viewport rendering, and texture reuse. Asynchronous screenshot
readback must finish before the next stage or final success is reported.
Outputs stay relative to the isolated game directory:

- `mmm-achievement-badges-smoke.txt`
- `achievement-badge-screenshots/*.png`
- stdout marker `MMM_ACHIEVEMENT_BADGES_SMOKE_PASS`

`SessionHoursBadgeSmoke.java` belongs only to the existing opt-in smoke source set.
The standalone launcher and PowerShell verifier in this directory are not main or
test manifest entries and must not be packaged into release jars.
