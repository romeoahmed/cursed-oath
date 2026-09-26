package io.github.romeoahmed.cursedoath;

import com.mojang.authlib.GameProfile;
import io.github.romeoahmed.cursedoath.combat.CombatRuntime;
import io.github.romeoahmed.cursedoath.combat.Fighter;
import io.github.romeoahmed.cursedoath.domain.Domains;
import io.github.romeoahmed.cursedoath.technique.Technique;
import io.github.romeoahmed.cursedoath.technique.TechniqueCombat;
import io.github.romeoahmed.cursedoath.technique.TechniqueOrb;
import io.github.romeoahmed.cursedoath.technique.TechniqueProjectiles;
import io.github.romeoahmed.cursedoath.technique.TechniqueWave;
import io.github.romeoahmed.cursedoath.world.TerrainDestruction;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class CombatFixtures {
    private CombatFixtures() {}

    public static <T extends Mob> T stationaryTarget(GameTestHelper helper, EntityType<T> type, BlockPos pos) {
        var target = helper.spawnWithNoFreeWill(type, pos);
        target.setNoAi(true);
        target.setNoGravity(true);
        if (target instanceof AgeableMob ageable) ageable.setAge(0);
        TestLifecycle.onFinish(helper, target::discard);
        return target;
    }

    @SuppressWarnings("ReferenceEquality") // Cancel only this fixture's runtime instance.
    public static ServerPlayer caster(GameTestHelper helper) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "combat-test"), false);
        var level = helper.getLevel();
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        player.connection = new ServerGamePacketListenerImpl(
                level.getServer(), new Connection(PacketFlow.SERVERBOUND), player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(2.5, 1, 1.5)));
        player.setYRot(0);
        player.setXRot(0);
        TestLifecycle.onFinish(helper, () -> {
            // Flights can leave the structure before completion, including releases made directly by a test.
            level.getEntities(TechniqueProjectiles.ORB, orb -> orb.getOwner() == player)
                    .forEach(Entity::discard);
            level.getEntities(TechniqueProjectiles.WAVE, wave -> wave.getOwner() == player)
                    .forEach(Entity::discard);
            var domain = Domains.ownedBy(player);
            if (domain != null) Domains.end(domain);
            for (var fighter : CombatRuntime.fighters(player.level()))
                if (fighter.player() == player) {
                    fighter.cancel();
                    break;
                }
            player.discard();
        });
        return player;
    }

    @SuppressWarnings("ReferenceEquality") // Identify projectiles created by this fixture.
    public static TechniqueWave launchWave(GameTestHelper helper, ServerPlayer player, Technique technique) {
        release(helper, player, technique);
        var waves = helper.getLevel().getEntities(TechniqueProjectiles.WAVE, wave -> wave.getOwner() == player);
        if (waves.size() != 1) throw new IllegalStateException("Expected exactly one fixture wave");
        return waves.getFirst();
    }

    @SuppressWarnings("ReferenceEquality") // Identify projectiles created by this fixture.
    public static TechniqueOrb launchOrb(GameTestHelper helper, ServerPlayer player, Technique technique) {
        release(helper, player, technique);
        var orbs = helper.getLevel().getEntities(TechniqueProjectiles.ORB, orb -> orb.getOwner() == player);
        if (orbs.size() != 1) throw new IllegalStateException("Expected exactly one fixture orb");
        return orbs.getFirst();
    }

    public static TerrainDestruction.@Nullable Work reserveTerrain(GameTestHelper helper, ServerPlayer player) {
        var work = TerrainDestruction.reserve(player);
        if (work != null) TestLifecycle.onFinish(helper, work::close);
        return work;
    }

    public static List<TerrainDestruction.Work> saturateTerrain(GameTestHelper helper, ServerPlayer player) {
        var reservations = new ArrayList<TerrainDestruction.Work>();
        // A broken capacity guard must fail instead of hanging the server thread inside the test timeout.
        for (int attempt = 0; attempt < 1024; attempt++) {
            var work = reserveTerrain(helper, player);
            if (work == null) return reservations;
            reservations.add(work);
        }
        throw new IllegalStateException("Terrain capacity accepted 1,024 simultaneous reservations");
    }

    @SuppressWarnings("ReferenceEquality") // Capture instances before an owner is removed or replaced.
    public static TerrainDestruction.@Nullable Work release(
            GameTestHelper helper, ServerPlayer player, Technique technique) {
        var work = technique.destroysTerrain() ? Objects.requireNonNull(reserveTerrain(helper, player)) : null;
        TechniqueCombat.release(player, UUID.randomUUID(), technique, work);
        var projectiles = new ArrayList<Entity>();
        projectiles.addAll(helper.getLevel().getEntities(TechniqueProjectiles.ORB, orb -> orb.getOwner() == player));
        projectiles.addAll(helper.getLevel().getEntities(TechniqueProjectiles.WAVE, wave -> wave.getOwner() == player));
        TestLifecycle.onFinish(helper, () -> projectiles.forEach(Entity::discard));
        return work;
    }

    public static Fighter fighter(GameTestHelper helper, ServerPlayer player) {
        var fighter = new Fighter(player);
        TestLifecycle.onFinish(helper, fighter::cancel);
        return fighter;
    }
}
