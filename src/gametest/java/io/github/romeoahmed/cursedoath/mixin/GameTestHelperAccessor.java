package io.github.romeoahmed.cursedoath.mixin;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// Exposes [GameTestInfo] so fixtures can register native completion listeners.
@Mixin(GameTestHelper.class)
public interface GameTestHelperAccessor {
    @Accessor("testInfo")
    GameTestInfo cursedOath$testInfo();
}
