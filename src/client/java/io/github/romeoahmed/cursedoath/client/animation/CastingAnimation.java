package io.github.romeoahmed.cursedoath.client.animation;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;
import io.github.romeoahmed.cursedoath.CursedOath;
import io.github.romeoahmed.cursedoath.technique.Technique;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import org.jspecify.annotations.Nullable;

/// PAL playback follows confirmed cast stages; it does not drive simulation.
public final class CastingAnimation {
    private static final int LAYER_PRIORITY = 1500;
    private static final Identifier LAYER = CursedOath.id("casting");

    private CastingAnimation() {}

    public static void initialize() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, LAYER_PRIORITY, player -> {
            var controller = new PlayerAnimationController(player, (state, animation, time) -> PlayState.STOP);
            controller.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
            return controller;
        });
    }

    public static void start(Avatar player, Technique technique, boolean release, float elapsedTicks) {
        String name = switch (technique) {
            case BLUE -> "attract";
            case RED -> "repel";
            case PURPLE -> "fusion";
            case DISMANTLE -> "dismantle";
            case CLEAVE -> "cleave";
            case HEAL -> "seal";
            case UNLIMITED_VOID -> "void";
            case MALEVOLENT_SHRINE -> "shrine";
            case INFINITY, SIMPLE_DOMAIN, AMPLIFICATION -> null;
        };
        if (name == null) return;
        var controller = controller(player);
        if (controller != null)
            controller.triggerAnimation(CursedOath.id(release ? name + "_release" : name), elapsedTicks);
    }

    public static void stop(Avatar player) {
        var controller = controller(player);
        if (controller != null) controller.stopTriggeredAnimation();
    }

    private static @Nullable PlayerAnimationController controller(Avatar player) {
        var layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER);
        return layer instanceof PlayerAnimationController controller ? controller : null;
    }
}
