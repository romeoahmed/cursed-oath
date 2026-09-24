package io.github.romeoahmed.cursedoath.combat

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SorcererDataTest {
    @Test
    fun `persistent resources survive codec round trips and reject invalid saves`() {
        val resources = SorcererResources(640, 25)
        val encoded = SorcererResources.CODEC.encodeStart(JsonOps.INSTANCE, resources).getOrThrow()
        assertEquals(resources, SorcererResources.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow())
        for (invalid in listOf(
            """{"energy":-1,"recovery":0}""",
            """{"energy":1001,"recovery":0}""",
            """{"energy":500,"recovery":-1}""",
            """{"energy":500,"recovery":1201}""",
        )) {
            assertTrue(SorcererResources.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(invalid)).isError)
        }
        val unknownVersion = JsonParser.parseString("""{"version":2,"practice":true,"reversal":true}""")
        assertTrue(SorcererProfile.CODEC.parse(JsonOps.INSTANCE, unknownVersion).isError)
    }
}
