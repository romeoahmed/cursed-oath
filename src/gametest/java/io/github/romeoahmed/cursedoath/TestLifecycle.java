package io.github.romeoahmed.cursedoath;

import io.github.romeoahmed.cursedoath.mixin.GameTestHelperAccessor;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NullMarked;

/// Scoped fixture cleanup tied to native test success, failure, and timeout notifications.
@NullMarked
public final class TestLifecycle {
    private TestLifecycle() {}

    public static void onFinish(GameTestHelper helper, Runnable action) {
        ((GameTestHelperAccessor) helper).cursedOath$testInfo().addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo info) {}

            @Override
            public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {}

            @Override
            public void testPassed(GameTestInfo info, GameTestRunner runner) {
                action.run();
            }

            @Override
            public void testFailed(GameTestInfo info, GameTestRunner runner) {
                action.run();
            }
        });
    }

    public static void beforeBlockBreak(
            GameTestHelper helper, ServerPlayer player, PlayerBlockBreakEvents.Before callback) {
        var id = player.getUUID();
        Callbacks.BLOCKS.put(id, callback);
        onFinish(helper, () -> Callbacks.BLOCKS.remove(id));
    }

    public static void allowDamage(
            GameTestHelper helper,
            Collection<? extends LivingEntity> targets,
            ServerLivingEntityEvents.AllowDamage callback) {
        for (var target : targets) {
            var id = target.getUUID();
            if (Callbacks.DAMAGE.putIfAbsent(id, callback) != null)
                throw new IllegalStateException("Damage callback already registered for fixture target " + id);
            onFinish(helper, () -> Callbacks.DAMAGE.remove(id));
        }
    }
    // Register once: Fabric events cannot unregister listeners, but fixture callbacks must be removed.
    private static final class Callbacks {
        private static final Map<UUID, PlayerBlockBreakEvents.Before> BLOCKS = new HashMap<>();
        private static final Map<UUID, ServerLivingEntityEvents.AllowDamage> DAMAGE = new HashMap<>();

        static {
            PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> {
                var callback = BLOCKS.get(player.getUUID());
                return callback == null || callback.beforeBlockBreak(level, player, pos, state, entity);
            });
            ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
                var callback = DAMAGE.get(entity.getUUID());
                return callback == null || callback.allowDamage(entity, source, amount);
            });
        }
    }
}
