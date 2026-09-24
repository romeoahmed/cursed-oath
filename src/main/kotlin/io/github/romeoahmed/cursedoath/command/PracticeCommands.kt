package io.github.romeoahmed.cursedoath.command

import io.github.romeoahmed.cursedoath.combat.CombatRuntime
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

object PracticeCommands {
    fun initialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands
                    .literal("cursedoath")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
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
}
