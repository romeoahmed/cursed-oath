# Contributing to Cursed Oath

Bug reports, fixes, translations, documentation, and art improvements are welcome. Start with the [README](README.md) to try the mod. The [design](docs/design.zh-CN.md) describes implemented features and future scope; detailed guides are in Chinese and contain manga spoilers.

## Report a problem or propose a change

Search [existing issues](https://github.com/romeoahmed/cursed-oath/issues) first. For a bug, include reproduction steps, expected and actual behavior, Minecraft and Mod versions, and relevant logs. For visual problems, add a screenshot or short recording, GPU, and graphics backend.

For gameplay proposals, explain the player experience you want to improve. Distinguish original manga or anime evidence from suggested game rules; include chapter or episode references where relevant.

## Make a change

Fork and clone the repository, then work on a branch. Install **JDK 25** and use the Gradle wrapper from the repository root (`gradlew.bat` on Windows):

```sh
./gradlew genSources
./gradlew runClient
```

Inspect the generated Minecraft sources before changing version-sensitive calls. Use a disposable world for combat testing: terrain destruction is permanent.

Common Java code and server resources live in `src/main/`; client code, translations, and audiovisual assets live in `src/client/`. JUnit tests are in `src/test/`, Fabric integration tests in `src/gametest/`, and editable assets in `art/`. See [architecture](docs/architecture.zh-CN.md) for ownership and lifecycle details.

- Use Java 25 without preview features and keep Gradle Kotlin DSL. Follow `.editorconfig`; Spotless applies the default Palantir Java format.
- Prefer Minecraft/Fabric APIs, keep client imports out of common code, and preserve resource IDs, save keys, and wire IDs. Address static-analysis findings rather than broadly suppressing them.
- Test behavior and relevant boundaries, with cleanup scoped to each test. Avoid assertions tied to private implementation details.
- Use `///` Javadoc for non-obvious contracts and `//` for implementation reasons. Keep documentation aligned with the behavior you change.
- Update English, Simplified Chinese, and Japanese keys and placeholders together; follow the [translation guide](docs/localization.zh-CN.md).
- For art, update editable sources and runtime exports together and follow the [asset guide](art/README.md).

## Verify your work

For code or runtime asset changes, run these commands separately, in order:

```sh
./gradlew spotlessApply
./gradlew build
./gradlew runClientGameTest
```

`build` compiles, checks formatting and static analysis, runs unit and server tests, and produces coverage reports and JARs. The complete client suite requires a graphics session with Vulkan; backend fallback fails verification.

Reports are in `build/reports/`. Inspect captures in `build/run/clientGameTest/screenshots/` for visual changes; pixel assertions do not establish visual quality. Preserve captures before rerunning server tests, which can delete them. During development, use `./gradlew test --tests '*ClassName'` or `./gradlew runGameTest` for focused checks.

Documentation or comment-only changes need `spotlessApply`, `spotlessCheck`, and link checks. Validate edited Javadoc with the JDK 25 standard doclet. Shrine model or exporter changes also need the [model export checks](art/shrine/README.md#export-and-verify).

## Open a pull request

Keep each PR focused on one coherent change. Explain the problem, resulting behavior, and checks performed; link relevant issues and attach before/after captures for visual changes. State any checks you could not run and why.

Use concise, imperative Conventional Commits, such as `fix(domain): preserve caster footing`. Review the diff before submitting and exclude builds, caches, and run directories. Use a draft PR while the work is incomplete.
