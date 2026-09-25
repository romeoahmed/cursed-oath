package io.github.romeoahmed.cursedoath.combat

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SorcererDataTest {
    @Test
    fun `legacy practice profiles retain their granted qualifications`() {
        val old = JsonParser.parseString("""{"version":1,"practice":true,"reversal":true}""")
        val migrated = SorcererProfile.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow()
        assertTrue(migrated.barriers && migrated.selfHealing)
        val explicit = migrated.copy(barriers = false, selfHealing = false, domainRadius = 200.0)
        val encoded = SorcererProfile.CODEC.encodeStart(JsonOps.INSTANCE, explicit).getOrThrow()
        assertEquals(explicit, SorcererProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow())
    }

    @Test
    fun `persistent resources survive codec round trips and reject invalid saves`() {
        val resources = SorcererResources(640, 25, 200)
        val encoded = SorcererResources.CODEC.encodeStart(JsonOps.INSTANCE, resources).getOrThrow()
        assertEquals(resources, SorcererResources.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow())
        for (invalid in listOf(
            """{"energy":-1,"recovery":0}""",
            """{"energy":1001,"recovery":0}""",
            """{"energy":500,"recovery":-1}""",
            """{"energy":500,"recovery":1201}""",
            """{"energy":500,"recovery":0,"burnout":-1}""",
            """{"energy":500,"recovery":0,"burnout":1201}""",
        )) {
            assertTrue(SorcererResources.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(invalid)).isError)
        }
        val legacy = JsonParser.parseString("""{"energy":640,"recovery":25}""")
        assertEquals(
            SorcererResources(640, 25, 0),
            SorcererResources.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow(),
        )
        val unknownVersion = JsonParser.parseString("""{"version":2,"practice":true,"reversal":true}""")
        assertTrue(SorcererProfile.CODEC.parse(JsonOps.INSTANCE, unknownVersion).isError)
    }
}
