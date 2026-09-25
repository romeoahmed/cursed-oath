package io.github.romeoahmed.cursedoath

import io.github.romeoahmed.cursedoath.mixin.GameTestHelperAccessor
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/** Native completion includes asynchronous assertion failures and timeouts. */
internal fun GameTestHelper.onFinish(action: () -> Unit) {
    (this as GameTestHelperAccessor).`cursedOath$testInfo`().addListener(
        object : GameTestListener {
            override fun testStructureLoaded(info: GameTestInfo) = Unit

            override fun testAddedForRerun(
                original: GameTestInfo,
                copy: GameTestInfo,
                runner: GameTestRunner,
            ) = Unit

            override fun testPassed(
                info: GameTestInfo,
                runner: GameTestRunner,
            ) = action()

            override fun testFailed(
                info: GameTestInfo,
                runner: GameTestRunner,
            ) = action()
        },
    )
}

internal fun GameTestHelper.beforeBlockBreak(
    player: ServerPlayer,
    callback: PlayerBlockBreakEvents.Before,
) {
    val id = player.uuid
    TestCallbacks.blocks[id] = callback
    onFinish { TestCallbacks.blocks.remove(id) }
}

internal fun GameTestHelper.allowDamage(callback: ServerLivingEntityEvents.AllowDamage) {
    val id = UUID.randomUUID()
    TestCallbacks.damage[id] = callback
    onFinish { TestCallbacks.damage.remove(id) }
}

/** Fabric events have no unregister API; one dispatcher holds only currently running fixtures. */
private object TestCallbacks {
    val blocks = mutableMapOf<UUID, PlayerBlockBreakEvents.Before>()
    val damage = mutableMapOf<UUID, ServerLivingEntityEvents.AllowDamage>()

    init {
        PlayerBlockBreakEvents.BEFORE.register { level, player, pos, state, entity ->
            blocks[player.uuid]?.beforeBlockBreak(level, player, pos, state, entity) != false
        }
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, amount ->
            damage.values.all { it.allowDamage(entity, source, amount) }
        }
    }
}
