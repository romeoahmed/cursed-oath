package io.github.romeoahmed.cursedoath;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.github.romeoahmed.cursedoath.technique.Technique;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class LocalizationTest {
    @Test
    void localesHaveUniqueNonemptyKeysAndMatchingIndexedPlaceholders() throws IOException {
        var locales = new LinkedHashMap<String, Map<String, String>>();
        for (var locale : List.of("en_us", "zh_cn", "ja_jp")) {
            var values = new LinkedHashMap<String, String>();
            var resource = getClass().getResourceAsStream("/assets/cursed-oath/lang/" + locale + ".json");
            assertNotNull(resource);
            try (var reader = new JsonReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    var key = reader.nextName();
                    assertFalse(values.containsKey(key), locale + ": duplicate " + key);
                    assertEquals(JsonToken.STRING, reader.peek(), locale + ": " + key + " must be text");
                    var value = reader.nextString();
                    assertFalse(value.isBlank(), locale + ": blank " + key);
                    values.put(key, value);
                }
                reader.endObject();
                assertEquals(JsonToken.END_DOCUMENT, reader.peek());
            }
            locales.put(locale, values);
        }
        var english = locales.get("en_us");
        assertNotNull(english);
        var placeholders = Pattern.compile("%(\\d+)\\$s");
        for (var entry : locales.entrySet()) {
            var locale = entry.getKey();
            var values = entry.getValue();
            assertEquals(english.keySet(), values.keySet(), locale + " key set");
            for (var translation : values.entrySet()) {
                var key = translation.getKey();
                var text = translation.getValue();
                var literal = placeholders.matcher(text).replaceAll("").replace("%%", "");
                assertFalse(literal.contains("%"), locale + ": " + key + " must use indexed %1$s placeholders or %%");
                assertEquals(
                        placeholders
                                .matcher(english.get(key))
                                .results()
                                .map(MatchResult::group)
                                .sorted()
                                .toList(),
                        placeholders
                                .matcher(text)
                                .results()
                                .map(MatchResult::group)
                                .sorted()
                                .toList(),
                        locale + ": " + key + " placeholders");
            }
            for (var technique : Technique.values()) {
                assertNotNull(values.get(technique.translationKey()), locale + ": " + technique.path() + " full name");
                assertNotNull(
                        values.get("wheel.cursed-oath." + technique.path()),
                        locale + ": " + technique.path() + " wheel name");
            }
        }
    }
}
