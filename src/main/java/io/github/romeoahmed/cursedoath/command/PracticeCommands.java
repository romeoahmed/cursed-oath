package io.github.romeoahmed.cursedoath.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.romeoahmed.cursedoath.combat.CombatRuntime;
import io.github.romeoahmed.cursedoath.combat.SorcererAttachments;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class PracticeCommands {
    private static final double MIN_RADIUS = 16, MAX_RADIUS = 200;

    private PracticeCommands() {}

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("cursedoath")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(radiusCommand())
                        .then(Commands.literal("practice").executes(context -> {
                            CombatRuntime.practice(context.getSource().getPlayerOrException(), true);
                            context.getSource()
                                    .sendSuccess(() -> Component.translatable("message.cursed-oath.practice"), false);
                            return 1;
                        }))
                        .then(Commands.literal("clear").executes(context -> {
                            CombatRuntime.practice(context.getSource().getPlayerOrException(), false);
                            context.getSource()
                                    .sendSuccess(() -> Component.translatable("message.cursed-oath.cleared"), false);
                            return 1;
                        }))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> radiusCommand() {
        return Commands.literal("radius")
                .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(MIN_RADIUS, MAX_RADIUS))
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            double radius = DoubleArgumentType.getDouble(context, "blocks");
                            var profile = player.getAttachedOrCreate(SorcererAttachments.PROFILE);
                            player.setAttached(SorcererAttachments.PROFILE, profile.withDomainRadius(radius));
                            context.getSource()
                                    .sendSuccess(
                                            () -> Component.translatable("message.cursed-oath.radius", radius), false);
                            return 1;
                        }));
    }
}
