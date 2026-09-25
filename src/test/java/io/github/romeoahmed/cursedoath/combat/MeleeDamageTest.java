package io.github.romeoahmed.cursedoath.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class MeleeDamageTest {
    @Test
    void blackFlashUsesPowerGrowthAndStrengthensBareHands() {
        assertEquals(32f, MeleeDamage.resolve(4f, true));
        assertEquals(129.64181, MeleeDamage.resolve(7f, true), 0.0001);
        assertEquals(5.656854f, MeleeDamage.resolve(1f, true), 0.000001f);
    }

    @Test
    void ordinaryReinforcementAndZeroDamageStaySeparateFromBlackFlash() {
        assertEquals(4.6f, MeleeDamage.resolve(4f, false));
        for (var blackFlash : List.of(false, true)) {
            assertEquals(0f, MeleeDamage.resolve(0f, blackFlash));
            assertEquals(0f, MeleeDamage.resolve(-1f, blackFlash));
        }
    }
}
