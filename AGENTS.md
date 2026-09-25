# Repository Guidelines

## Project map

Cursed Oath (咒誓) is a Minecraft 26.3 Fabric combat mod. Read [README](README.md) for setup and controls, [design](docs/design.zh-CN.md) for scope, and [architecture](docs/architecture.zh-CN.md) for ownership and lifecycle constraints.

The package root is `io.github.romeoahmed.cursedoath`:

- `src/main/kotlin/`: server combat, techniques, domains, terrain, networking, and commands.
- `src/client/kotlin/`: input, GUI, animation, rendering, and sound; feature visuals live in `render/domain/` and `render/limitless/`.
- `src/main/java/` and `src/client/java/`: narrow vanilla mixins.
- `src/main/resources/`: metadata, translations, assets, and data.
- `src/test/` and `src/gametest/`: unit tests and a separate test mod. GameTests follow production packages; shared fixtures stay at the package root.
- `art/`: editable assets and tools; follow the [asset instructions](art/README.md).

## Build and verify

Use JDK 25 and the wrapper from the repository root (`gradlew.bat` on Windows).

- `./gradlew genSources`: inspect targets before changing mixins or version-sensitive calls.
- `./gradlew spotlessApply`: apply ktlint, default Palantir Java Format, and text formatting. Run before other checks.
- `./gradlew build`: compile, run unit/server tests, check formatting, Detekt, and coverage, then package JARs.
- `./gradlew runClientGameTest`: run the complete Vulkan client suite after `build`; requires a graphics session. Its 30-second preflight rejects unavailable graphics, and tests reject backend fallback.
- `./gradlew test --tests '*ClassName'` or `./gradlew runGameTest`: focused verification.

Inspect captures in `build/run/clientGameTest/screenshots/`; preserve them before server tests, whose cleanup can delete them. Pixel assertions establish appearance and expiry, not visual quality. Text-only changes need formatting and link checks. Report only checks actually performed.

## Implementation and tests

Follow `.editorconfig` and formatter output. Match filenames to types; use camelCase members and UPPER_SNAKE_CASE constants. Keep client imports out of common code. Prefer Minecraft/Fabric APIs; justify dependencies.

Keep combat and terrain server-owned, world access on its owning thread, and render snapshots immutable. Preserve resource IDs, save keys, and wire IDs. Create identifiers with `CursedOath.id(...)`.

Test behavior with `kotlin-test-junit5`/JUnit 6 or GameTest. Use scoped fixtures and `onFinish` cleanup; never clear shared runtime state from a test. Use spectators for isolated visual captures and ordinary players for input tests. Kover requires 90% line coverage for `CursedEnergy` and `RequestGate`. Fix findings without baselines or broad suppressions.

## Text and contributions

Align `en_us`, `zh_cn`, and `ja_jp` keys and placeholders; follow [terminology](docs/localization.zh-CN.md). Separate implemented behavior, plans, and canon evidence. Comments explain constraints or reasoning.

Use imperative commit subjects. PRs describe behavior, relevant issues, validation, and screenshots for visible changes. Exclude builds, caches, and run directories.
