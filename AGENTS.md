# Repository Guidelines

## Project map

Cursed Oath (咒誓) is a Minecraft 26.3 Fabric combat mod. Read [README](README.md) for setup, [design](docs/design.zh-CN.md) for scope, and [architecture](docs/architecture.zh-CN.md) for state ownership.

Under `io.github.romeoahmed.cursedoath`:

- `src/main/java/`: common combat, techniques, domains, terrain, networking, commands, and mixins.
- `src/client/java/`: input, GUI, animation, rendering, sound, and client mixins.
- `src/main/resources/`: metadata, icon, Mixin configuration, and server data.
- `src/client/resources/`: translations and audiovisual assets.
- `src/test/`: JUnit tests; `src/gametest/`: isolated test mod, grouped by production package.
- `art/`: editable assets and tools; follow the [asset instructions](art/README.md).

## Build and verify

Use JDK 25 and the repository wrapper (`gradlew.bat` on Windows).

- `./gradlew genSources`: inspect generated targets before version-sensitive changes.
- `./gradlew spotlessApply`: format Java and normalize text; run separately before checks.
- `./gradlew build`: compile, check formatting and static analysis, run unit/server tests and coverage, package JARs.
- `./gradlew runClientGameTest`: run the complete Vulkan suite after `build`; requires a graphics session. Graphics preflight times out after 30 seconds; tests reject backend fallback.
- `./gradlew test --tests '*ClassName'` or `./gradlew runGameTest`: focused verification.

Inspect `build/run/clientGameTest/screenshots/`; preserve captures before server tests, whose cleanup can delete them. Pixel assertions cannot establish visual quality. Text-only changes need formatting and link checks; validate edited Javadoc with the JDK doclet. Report checks actually performed.

## Code and tests

Use Java 25 without preview features and Gradle Kotlin DSL. Follow `.editorconfig` and default Palantir formatting. Match filenames to types. Use JSpecify nullness annotations and fix Error Prone/NullAway findings without broad suppressions. Keep client imports out of common code; prefer native Minecraft/Fabric APIs.

Keep simulation server-owned, world access on its owning thread, and render submissions isolated from live entities. Preserve resource IDs, save keys, and wire IDs; create identifiers with `CursedOath.id(...)`.

Test behavior and boundary cases. Use scoped fixtures with completion cleanup, never global runtime clearing. Use spectators for isolated visual captures and ordinary players for input tests. JaCoCo requires 90% line coverage for `CursedEnergy` and `RequestGate`.

## Text and contributions

Keep three-language keys and placeholders aligned; follow [terminology](docs/localization.zh-CN.md). Separate implementation, plans, and canon evidence. Use `///` Javadoc for declaration contracts and `//` for local reasoning; avoid comments that repeat code.

Use concise, imperative Conventional Commits. PRs explain behavior and validation, link relevant issues, and include screenshots for visual changes. Exclude builds, caches, and run directories.
