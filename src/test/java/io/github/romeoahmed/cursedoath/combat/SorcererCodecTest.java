package io.github.romeoahmed.cursedoath.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

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

    @Test
    void resourceCodecsRoundTripAndRejectInvalidSaves() {
        var resources = new SorcererResources(640, 25, 200);
        var encoded =
                SorcererResources.CODEC.encodeStart(JsonOps.INSTANCE, resources).getOrThrow();
        assertEquals(
                resources,
                SorcererResources.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        for (var invalid : List.of(
                "{\"energy\":-1,\"recovery\":0}",
                "{\"energy\":1001,\"recovery\":0}",
                "{\"energy\":500,\"recovery\":-1}",
                "{\"energy\":500,\"recovery\":1201}",
                "{\"energy\":500,\"recovery\":0,\"burnout\":-1}",
                "{\"energy\":500,\"recovery\":0,\"burnout\":1201}"))
            assertTrue(SorcererResources.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseString(invalid))
                    .isError());
        var legacy = JsonParser.parseString("{\"energy\":640,\"recovery\":25}");
        assertEquals(
                new SorcererResources(640, 25, 0),
                SorcererResources.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow());
        var unknownVersion = JsonParser.parseString("{\"version\":2,\"practice\":true,\"reversal\":true}");
        assertTrue(SorcererProfile.CODEC.parse(JsonOps.INSTANCE, unknownVersion).isError());
    }
}
