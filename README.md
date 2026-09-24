# Cursed Oath · 咒誓

Jujutsu Kaisen combat in Minecraft, built with Fabric, Kotlin, and Java. The project brings spatial techniques, close combat, and destructive attacks into Minecraft's first-person, block-based world.

**Development prototype for Minecraft 26.3.** Limitless and Shrine are the first technique systems. Domains, survival progression, and advanced variants are still planned.

## What you can try

- Launch **Blue** to pull targets toward a moving core, or **Red** for a repulsive impact.
- Form **Hollow Purple** by bringing Blue and Red together, then carve a path through terrain.
- Fire **Dismantle** or use close-range **Cleave** to cut an intersecting grid through targets and blocks.
- Toggle the **Infinity** defense prototype, heal yourself, and prepare melee strikes with a chance of **Black Flash**.
- Play with English, Simplified Chinese, or Japanese text and rebindable controls.

Terrain damage is permanent. Use a disposable world while testing. Containers, fluids, unbreakable blocks, and denied block-break events are protected; compatibility with other protection mods requires verification.

## Run from source

Install **JDK 25**, then run from the repository root:

```sh
./gradlew runClient
```

On Windows, use `gradlew.bat`. The wrapper resolves development dependencies. Create a world with commands enabled and run:

```text
/cursedoath practice
```

This grants the prototype abilities and refills cursed energy. Repeat it to reset your practice state; `/cursedoath clear` disables access. Both commands require operator permissions.

| Default key | Action                                            |
| ----------- | ------------------------------------------------- |
| R           | Select the next technique                         |
| V           | Cast; toggle Infinity when selected               |
| X           | Cancel preparation, melee readiness, and Infinity |
| G           | Prepare one reinforced melee strike               |

For melee, press G, then attack within three seconds with a nearly full attack cooldown. A qualifying strike has a 20% Black Flash chance. An attack or empty swing consumes the preparation. X does not recall released projectiles. Change bindings in Minecraft Controls.

To build a JAR or start a development server:

```sh
./gradlew build
./gradlew runServer
```

JARs are written to `build/libs/`. For a separate installation, use the main JAR with Minecraft 26.3, Fabric Loader, Fabric API, Fabric Language Kotlin, and Player Animation Library on the client and server. Use the versions in the [dependency table](docs/architecture.zh-CN.md#技术基线); dependencies are not bundled into the mod JAR.

## Contribute and test

See [Repository Guidelines](AGENTS.md) for code conventions and change-specific checks.

```sh
./gradlew spotlessApply
./gradlew build
./gradlew runClientGameTest
```

`build` runs formatting, static analysis, unit tests, server GameTests, and scoped coverage checks. Client GameTests require a graphics session and run separately. Reports are in `build/reports/`; client screenshots are in `build/run/clientGameTest/screenshots/`. CI uses Xvfb/Mesa and uploads reports, logs, and screenshots.

Include reproduction steps and expected behavior in bug reports. PRs should explain the change and its validation, with screenshots for visual changes. Multiplayer load, equipment combinations, and alternate rendering backends still need broader testing.

## Documentation

The detailed documents are in Chinese and contain **full-series manga spoilers**.

- [Design](docs/design.zh-CN.md): scope and roadmap.
- [Combat](docs/combat.zh-CN.md): current tuning and planned interactions.
- [Architecture](docs/architecture.zh-CN.md): dependencies, state, networking, and tests.
- [Presentation](docs/presentation.zh-CN.md): animation, effects, terrain, and performance targets.
- [Localization](docs/localization.zh-CN.md): terminology and translation conventions.
- [Sources](docs/sources.zh-CN.md): evidence and limits of the adaptation.

## License

See [LICENSE](LICENSE) for the repository's MPL-2.0 license.
