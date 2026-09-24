# Repository Guidelines

## Scope and layout

Cursed Oath (咒誓) is a Minecraft 26.3 Fabric combat prototype. [README](README.md) explains how to run it; [design](docs/design.zh-CN.md) includes planned features.

The package root is `io.github.romeoahmed.cursedoath`:

- `src/main/kotlin/`: `combat`, `technique`, `world`, `network`, and `command`.
- `src/main/java/`: narrow vanilla mixins.
- `src/client/kotlin/`: input, HUD, animation, and rendering. Keep client imports out of common code.
- `src/main/resources/`: Fabric metadata, mixin configuration, translations, and PAL animations.
- `src/test/kotlin/`: unit tests; `src/gametest/`: server and client GameTests.

## Commands and verification

Use JDK 25 and the wrapper; substitute `gradlew.bat` on Windows.

- `./gradlew genSources`: generate Minecraft sources; inspect actual targets before changing mixins or version-sensitive API calls.
- `./gradlew spotlessApply`: apply ktlint, default Palantir Java Format, and text whitespace rules.
- `./gradlew build`: compile, run unit/server tests, check formatting, Detekt, and scoped coverage, then package JARs.
- `./gradlew runClientGameTest`: run client checks after `build`; requires a graphics session. Inspect `build/run/clientGameTest/screenshots/` for visual changes. Later server-test cleanup can delete these outputs.
- `./gradlew runClient` / `runServer`: launch development environments.

For focused work, use `./gradlew test --tests '*ClassName'` or `./gradlew runGameTest`. Documentation-only changes need formatting and link checks. Report checks actually performed; screenshot assertions establish presence and expiry, not visual quality.

## Implementation constraints

Follow `.editorconfig` and formatter output. Use PascalCase types/files, camelCase members, and UPPER_SNAKE_CASE constants. Prefer native Minecraft/Fabric APIs; justify additional dependencies. Explain non-obvious constraints in comments rather than restating code.

Keep combat and terrain state server-owned, render snapshots immutable, and world access on its owning thread. Preserve mod/resource IDs, save keys, and explicit wire IDs across refactors. Construct identifiers with `CursedOath.id(...)`.

Test behavioral boundaries and regressions with `kotlin-test-junit5`/JUnit 6 or GameTest. Kover requires 90% line coverage only for `CursedEnergy` and `RequestGate`. Keep warnings and Detekt findings actionable; avoid baselines and broad suppressions.

## Text and contributions

Update `en_us`, `zh_cn`, and `ja_jp` together, including placeholders; follow [terminology](docs/localization.zh-CN.md). Separate implemented behavior, planned rules, and verified canon in documentation.

Use imperative commit subjects. PRs describe behavior, relevant issues, validation, and screenshots for visible changes. Keep generated builds, caches, and run directories out of commits.
