package io.github.romeoahmed.cursedoath.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@NullMarked
class SorcererCodecTest {
    @Test
    void legacyPracticeProfilesRetainGrantedQualifications() {
        var old = JsonParser.parseString("{\"version\":1,\"practice\":true,\"reversal\":true}");
        var migrated = SorcererProfile.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
        assertTrue(migrated.barriers() && migrated.selfHealing());
        var explicit =
                new SorcererProfile(migrated.version(), migrated.practice(), migrated.reversal(), false, false, 200);
        var encoded =
                SorcererProfile.CODEC.encodeStart(JsonOps.INSTANCE, explicit).getOrThrow();
        assertEquals(
                explicit, SorcererProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @ParameterizedTest
    @CsvSource({"0, 0, 0", "640, 25, 200", "1000, 1200, 1200"})
    void resourceCodecsRoundTrip(int energy, int recovery, int burnout) {
        var resources = new SorcererResources(energy, recovery, burnout);
        var encoded =
                SorcererResources.CODEC.encodeStart(JsonOps.INSTANCE, resources).getOrThrow();
        assertEquals(
                resources,
                SorcererResources.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @ParameterizedTest
    @CsvSource({"-1, 0, 0", "1001, 0, 0", "500, -1, 0", "500, 1201, 0", "500, 0, -1", "500, 0, 1201"})
    void resourceCodecsRejectInvalidSaves(int energy, int recovery, int burnout) {
        var invalid = "{\"energy\":%d,\"recovery\":%d,\"burnout\":%d}".formatted(energy, recovery, burnout);
        assertTrue(SorcererResources.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(invalid))
                .isError());
    }

    @Test
    void legacyResourcesDefaultBurnoutToZero() {
        var legacy = JsonParser.parseString("{\"energy\":640,\"recovery\":25}");
        assertEquals(
                new SorcererResources(640, 25, 0),
                SorcererResources.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow());
    }

    @Test
    void unknownProfileVersionsAreRejected() {
        var unknownVersion = JsonParser.parseString("{\"version\":2,\"practice\":true,\"reversal\":true}");
        assertTrue(SorcererProfile.CODEC.parse(JsonOps.INSTANCE, unknownVersion).isError());
    }
}
