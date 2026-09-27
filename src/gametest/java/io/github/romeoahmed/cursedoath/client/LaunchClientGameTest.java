package io.github.romeoahmed.cursedoath.client;

import static com.google.common.base.Preconditions.checkState;
import static io.github.romeoahmed.cursedoath.client.Screenshots.*;
import static java.util.Objects.requireNonNull;

import io.github.romeoahmed.cursedoath.client.input.CombatInput;
import io.github.romeoahmed.cursedoath.client.render.TechniqueProjectileRenderer;
import io.github.romeoahmed.cursedoath.client.render.limitless.LimitlessEffects;
import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectile;
import io.github.romeoahmed.cursedoath.technique.TechniqueTuning;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class LaunchClientGameTest implements FabricClientGameTest {
    private static final List<Technique> TECHNIQUES = List.of(Technique.BLUE, Technique.RED, Technique.PURPLE);
    private static final double TOLERANCE = 1e-4;

    @Override
    public void runTest(ClientGameTestContext context) {
        prepareScreenshots(context);
        var camera = context.computeOnClient(client -> client.options.getCameraType());
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
            world.getServer().runCommand("gamemode creative @a");
            for (boolean moving : List.of(false, true))
                for (var technique : TECHNIQUES) verifyLaunch(context, world, technique, moving);
        } finally {
            context.getInput().releaseKey(options -> options.keyUp);
            context.runOnClient(client -> client.options.setCameraType(camera));
        }
    }

    private static void verifyLaunch(
            ClientGameTestContext context, TestSingleplayerContext world, Technique technique, boolean moving) {
        world.getServer().runCommand(moving ? "tp @a 0.5 40 0.5 0 0" : "tp @a 0.5 40 0.5 37 -12");
        world.getServer().runCommand("execute as @a run cursedoath practice");
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        context.waitFor(client ->
                CombatInput.snapshot() != null && CombatInput.snapshot().enabled());
        if (moving) context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(8);
        context.runOnClient(client -> CombatInput.choose(technique));
        context.getInput().pressKey(CombatInput.CAST);
        context.waitFor(client -> {
            var projectile = projectile(client);
            if (projectile == null) return false;
            verifyRenderedFlight(client);
            return true;
        });
        var id = context.computeOnClient(
                client -> requireNonNull(projectile(client)).getUUID());
        var origin = world.getServer().computeOnServer(server -> {
            var entity = world.getConnection().getServerLevel().getEntity(id);
            checkState(entity instanceof TechniqueProjectile, "Missing server projectile");
            return ((TechniqueProjectile) entity).launchPosition();
        });
        context.runOnClient(client -> {
            var projectile = requireNonNull(projectile(client));
            checkState(projectile.launchPosition().equals(origin), "Launch position must reach the client exactly");
        });
        String name = "launch-" + technique.path() + (moving ? "-moving" : "-standing");
        for (int frame = 0; frame < 6; frame++) {
            context.runOnClient(LaunchClientGameTest::verifyRenderedFlight);
            if (frame == 0 || frame == 5) capture(context, name + "-" + frame);
            context.waitTick();
        }
        context.getInput().releaseKey(options -> options.keyUp);
        // This fixture verifies launch frames. Lifetime and impact belong to server and integration tests.
        world.getServer().runOnServer(server -> {
            var entity = world.getConnection().getServerLevel().getEntity(id);
            if (entity != null) entity.discard();
        });
        context.waitFor(client -> projectile(client) == null);
    }

    private static void verifyRenderedFlight(Minecraft client) {
        var entity = requireNonNull(projectile(client));
        var renderer = client.getEntityRenderDispatcher().getRenderer(entity);
        for (float partial : new float[] {0, 0.5f, 1}) {
            var position = entity.position();
            var state = (TechniqueProjectileRenderer.State) renderer.createRenderState(entity, partial);
            var form = state.forms.getFirst();
            checkState(
                    form.direction().dot(requireNonNull(client.player).getViewVector(partial)) > 0.999,
                    "Launch fixture must synchronize the aiming direction before casting");
            var eye = requireNonNull(client.player).getEyePosition(partial);
            var center = new Vec3(state.x, state.y, state.z).add(form.center());
            double depth = center.subtract(eye).dot(form.direction());
            double launch = TechniqueTuning.launchDistance(entity.technique());
            var charge = LimitlessEffects.charge(entity.technique(), 1, form.direction())
                    .getFirst();
            checkState(depth >= launch - TOLERANCE, "%s retracts behind the preparation plane", entity.technique());
            checkState(
                    form.radius() / depth <= charge.radius() / launch + TOLERANCE,
                    "%s suddenly grows across the caster's view",
                    entity.technique());
            checkState(entity.position().equals(position), "Presentation must not move the tracked entity");
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            try {
                var external = renderer.createRenderState(entity, partial);
                var actual = new Vec3(entity.xOld, entity.yOld, entity.zOld).lerp(position, partial);
                checkState(
                        actual.distanceToSqr(new Vec3(external.x, external.y, external.z)) < TOLERANCE * TOLERANCE,
                        "External cameras must retain the native interpolated trajectory");
            } finally {
                client.options.setCameraType(CameraType.FIRST_PERSON);
            }
        }
    }

    private static @Nullable TechniqueProjectile projectile(Minecraft client) {
        for (var entity : requireNonNull(client.level).entitiesForRendering())
            if (entity instanceof TechniqueProjectile projectile) return projectile;
        return null;
    }
}
