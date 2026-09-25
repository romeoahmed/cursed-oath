# Cursed Oath · 咒誓

A **Minecraft 26.3 Fabric combat mod** inspired by _Jujutsu Kaisen_: Limitless, Shrine, Black Flash, and domain battles in Minecraft's first-person, block-based world.

**Development prototype.** Practice commands unlock the implemented abilities; survival progression is not available yet. English, Simplified Chinese, and Japanese are supported.

![Concept art of Blue, Red, and Hollow Purple in a Minecraft-inspired setting](art/references/limitless.png)

_AI-generated visual concept — not an in-game screenshot. Character details and lighting are illustrative._

## Abilities

- **Limitless:** Infinity defense, Blue's moving attraction field, Red's repulsive blast, and Hollow Purple's fusion and piercing flight.
- **Shrine:** ranged Dismantle, contact Cleave, and Malevolent Shrine's sustained sure hits and terrain destruction.
- **Unlimited Void:** a breakable closed barrier, a panoramic interior, and information overload that immobilizes and deals lethal damage to unprotected ordinary targets.
- **Shared techniques:** reinforced melee with a chance of Black Flash, self-healing, Simple Domain, and Domain Amplification.

Overlapping domains suppress sure hits in their shared area. Unlimited Void retains the original world's blocks and collisions beneath its interior scenery; it has no separate dimension. See [scope and limitations](docs/design.zh-CN.md#当前交付边界).

**Terrain destruction is permanent. Use a disposable world.** Containers, fluids, unbreakable blocks, and blocks denied by protection events are preserved. They do not shield targets from Purple. Compatibility with protection mods requires separate testing.

## Run locally

Install **JDK 25**, then run from the repository root:

```sh
./gradlew runClient
```

Use `gradlew.bat` on Windows. The wrapper resolves development dependencies. Create a world with commands enabled and run:

```text
/cursedoath practice
```

This unlocks the prototype and resets practice resources. `/cursedoath clear` disables access. `/cursedoath radius <blocks>` sets the next Malevolent Shrine radius to 16–200 blocks (default 96). These commands require operator permissions.

## Controls

| Default key | Action                                                 |
| ----------- | ------------------------------------------------------ |
| B           | Open or close the technique wheel                      |
| R           | Select the next technique                              |
| V           | Use the selected technique; toggle sustained abilities |
| X           | Cancel preparation, defenses, and your active domain   |
| G           | Prepare one reinforced melee strike                    |

The wheel keeps the world running. Point or press Tab to highlight a technique; left-click or Enter selects it, and right-click or the cast key uses it. Escape closes the wheel. Rebind controls in Minecraft Options.

After G, attack within three seconds with over 90% attack strength for a 20% Black Flash chance. Attacking or swinging at air consumes the preparation. Domains last up to 30 seconds and leave ten seconds of technique burnout. X does not recall released projectiles. Detailed costs, damage, and exceptions are in the [combat guide](docs/combat.zh-CN.md).

“Hide Lightning Flashes” reduces Shrine's decorative slashes without changing damage or range. Domain sounds have localized subtitles.

## Build and install

```sh
./gradlew build
```

Install the mod JAR from `build/libs/` on both client and server with **Fabric Loader, Fabric API, Fabric Language Kotlin, and Player Animation Library** for Minecraft 26.3. Use the runtime JAR, not the `-sources.jar`. Dependencies are not bundled; see [versions](docs/architecture.zh-CN.md#技术基线). `./gradlew runServer` launches a development server.

## Contribute

Follow [Repository Guidelines](AGENTS.md) for source layout and checks. Run formatting, `build`, then the complete `runClientGameTest` suite for code or visual changes; client tests require a Vulkan graphics session. Reports are in `build/reports/`, captures in `build/run/clientGameTest/screenshots/`. Model edits also require the [export checks](art/shrine/README.md#export-and-verify).

Bug reports should include reproduction steps, expected behavior, and relevant logs. PRs should explain the change and checks performed, with screenshots for visible changes. Automated tests do not replace multiplayer load or device compatibility testing.

## Documentation

The detailed guides are in Chinese and contain **full-series manga spoilers**.

- [Design](docs/design.zh-CN.md): goals, delivered scope, and future work.
- [Combat](docs/combat.zh-CN.md): implemented rules and tuning.
- [Architecture](docs/architecture.zh-CN.md): state ownership, rendering, and verification.
- [Presentation](docs/presentation.zh-CN.md): visual language, animation, and performance criteria.
- [Localization](docs/localization.zh-CN.md): terminology and translation conventions.
- [Sources](docs/sources.zh-CN.md): adaptation evidence and unresolved questions.

## License

[MPL-2.0](LICENSE).
