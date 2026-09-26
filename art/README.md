# Art assets

- [Shrine](shrine/README.md): editable Blockbench model, exporter, and tests.
- [Unlimited Void](#unlimited-void): panorama composition and runtime mapping.
- [Visual references](references/README.md): selected concept art and its intended use.

Client runtime assets belong in `src/client/resources/assets/cursed-oath/`; keep editable sources, tools, and reference provenance here. Update the source and its runtime export together. Preserve resource IDs when reorganizing files, and inspect changes in-game after asset checks pass.

## Unlimited Void

Runtime asset: [void.png](../src/client/resources/assets/cursed-oath/textures/environment/void.png), a 1774 × 887 panorama generated with ImageGen. Supplied anime and manga images informed composition and stellar density; the reference images are not shipped as textures.

### Composition

Almost-black, horizonless space surrounds one black focal shape. Asymmetric blue-gray and ivory clouds bend around it, with light integrated into their edges. Sparse stellar clusters and broad dark areas preserve depth. Avoid a separate luminous ring, visible ground, architecture, or characters.

The cloud trail extends left in the texture and appears to the viewer's right after spherical mapping. Keep the poles and horizontal seam black for a continuous environment.

### Runtime mapping

A camera-centered sphere retains the casting orientation as the viewer turns or moves. The native `position_tex_color` pipeline samples the painting; vertex colors provide the reveal and black seam. Native vertex-color geometry supplies the brief backdrop and depth streaks before the panorama is revealed; timing and reduced-flash behavior are described in the [presentation guide](../docs/presentation.zh-CN.md#无量空处). The opening does not delay gameplay. The exterior barrier stays at the domain origin; the panorama does not change world collisions.

Check the mapped result in-game: forward, sideways, at the seam, and toward both poles. Flat-image composition alone does not establish how the environment reads around the player.
