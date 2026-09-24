package io.github.romeoahmed.cursedoath

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import io.github.romeoahmed.cursedoath.technique.Technique
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalizationTest {
    @Test
    fun `all locales have unique nonempty keys and matching indexed placeholders`() {
        val locales =
            listOf("en_us", "zh_cn", "ja_jp").associateWith { locale ->
                val values = linkedMapOf<String, String>()
                val resource = assertNotNull(javaClass.getResourceAsStream("/assets/cursed-oath/lang/$locale.json"))
                JsonReader(resource.reader()).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        assertFalse(key in values, "$locale: duplicate $key")
                        assertEquals(JsonToken.STRING, reader.peek(), "$locale: $key must be text")
                        val value = reader.nextString()
                        assertTrue(value.isNotBlank(), "$locale: blank $key")
                        values[key] = value
                    }
                    reader.endObject()
                    assertEquals(JsonToken.END_DOCUMENT, reader.peek())
                }
                values
            }
        val english = locales.getValue("en_us")
        val placeholders = Regex("%(\\d+)\\\$s")
        for ((locale, values) in locales) {
            assertEquals(english.keys, values.keys, "$locale key set")
            for ((key, text) in values) {
                val literal = placeholders.replace(text, "").replace("%%", "")
                assertFalse('%' in literal, "$locale: $key must use indexed %1\$s placeholders or %%")
                assertEquals(
                    placeholders
                        .findAll(english.getValue(key))
                        .map { it.value }
                        .sorted()
                        .toList(),
                    placeholders
                        .findAll(text)
                        .map { it.value }
                        .sorted()
                        .toList(),
                    "$locale: $key placeholders",
                )
            }
            for (technique in Technique.entries) assertNotNull(values[technique.translationKey])
        }
    }
}
