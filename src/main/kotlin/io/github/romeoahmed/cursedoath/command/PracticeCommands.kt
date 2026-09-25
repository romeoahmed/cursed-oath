package io.github.romeoahmed.cursedoath.command

import com.mojang.brigadier.arguments.DoubleArgumentType
import io.github.romeoahmed.cursedoath.combat.CombatRuntime
import io.github.romeoahmed.cursedoath.combat.SorcererData
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

object PracticeCommands {
    private const val MIN_RADIUS = 16.0
    private const val MAX_RADIUS = 200.0

    fun initialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands
                    .literal("cursedoath")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(radiusCommand())
                    .then(
                        Commands.literal("practice").executes { context ->
                            CombatRuntime.practice(context.source.playerOrException, true)
                            context.source.sendSuccess(
                                { Component.translatable("message.cursed-oath.practice") },
                                false,
                            )
                            1
                        },
                    ).then(
                        Commands.literal("clear").executes { context ->
                            CombatRuntime.practice(context.source.playerOrException, false)
                            context.source.sendSuccess(
                                { Component.translatable("message.cursed-oath.cleared") },
                                false,
                            )
                            1
                        },
                    ),
            )
        }
    }

    private fun radiusCommand() =
        Commands.literal("radius").then(
            Commands.argument("blocks", DoubleArgumentType.doubleArg(MIN_RADIUS, MAX_RADIUS)).executes { context ->
                val player = context.source.playerOrException
                val radius = DoubleArgumentType.getDouble(context, "blocks")
                val profile = player.getAttachedOrCreate(SorcererData.PROFILE)
                player.setAttached(SorcererData.PROFILE, profile.copy(domainRadius = radius))
                context.source.sendSuccess({ Component.translatable("message.cursed-oath.radius", radius) }, false)
                1
            },
        )
}
