# Unlimited Void environment

Runtime asset: [void.png](../src/main/resources/assets/cursed-oath/textures/environment/void.png), a 1774 × 887 panorama generated with ImageGen. Supplied anime and manga images informed composition and stellar density; the reference images are not shipped as textures.

## Composition

Almost-black, horizonless space surrounds one black focal shape. Asymmetric blue-gray and ivory clouds bend around it, with light integrated into their edges. Sparse stellar clusters and broad dark areas preserve depth. Avoid a separate luminous ring, visible ground, architecture, or characters.

The cloud trail extends left in the texture and appears to the viewer's right after spherical mapping. Keep the poles and horizontal seam black for a continuous environment.

## Runtime mapping

A camera-centered sphere retains the casting orientation as the viewer turns or moves. The native textured vertex shader supplies UVs; [void.fsh](../src/main/resources/assets/cursed-oath/shaders/core/void.fsh) samples the static painting and fades it in with a black seam. The exterior barrier stays at the domain origin; the panorama does not change world collisions.

Check the mapped result in-game: forward, sideways, at the seam, and toward both poles. Flat-image composition alone does not establish how the environment reads around the player.
