package io.github.romeoahmed.cursedoath.client.render.limitless;

import static org.junit.jupiter.api.Assertions.*;

import io.github.romeoahmed.cursedoath.technique.Technique;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/// Geometry contracts run without a graphics context; color blending is covered by client captures.
@NullMarked
class LimitlessEffectsTest {
    private static final double EPSILON = 1e-6;

    @ParameterizedTest
    @CsvSource({"0, 0, 1", "0, 1, 0", "0.6, 0, 0.8"})
    void fusionStartsWithReadableStationaryEqualSources(double x, double y, double z) {
        var direction = new Vec3(x, y, z);
        var initial = body(LimitlessEffects.charge(Technique.PURPLE, 0, direction), Technique.BLUE);
        for (float progress : new float[] {0, 0.04f, 0.08f, 0.12f}) {
            var forms = LimitlessEffects.charge(Technique.PURPLE, progress, direction);
            var blue = body(forms, Technique.BLUE);
            var red = body(forms, Technique.RED);
            assertEquals(initial.center(), blue.center(), "Give the separated sources time to read");
            assertEquals(blue.radius(), red.radius(), EPSILON, "Sources have equal size");
            assertEquals(blue.center().y, red.center().y, EPSILON, "Sources meet on a horizontal line");
            assertTrue(blue.center().distanceTo(red.center()) > blue.radius() + red.radius());
            assertTrue(forms.stream().noneMatch(form -> form.technique() == Technique.PURPLE));
        }
        assertTrue(sourceOffset(0.3f) < sourceOffset(0), "The hold must lead into movement");
    }

    @Test
    void fusionAcceleratesGentlyAndDoesNotStopAtContact() {
        int frames = Technique.PURPLE.preparation() * 3; // Sample at 60 fps across the full preparation.
        float frame = 1f / frames;
        double previousOffset = sourceOffset(0), previousStep = 0;
        boolean crossedContact = false;
        double radius = body(LimitlessEffects.charge(Technique.PURPLE, 0, new Vec3(0, 0, 1)), Technique.BLUE)
                .radius();
        for (int sample = 1; sample <= frames; sample++) {
            float progress = sample * frame;
            double offset = sourceOffset(progress), step = previousOffset - offset;
            assertTrue(step >= -EPSILON, "Fusion never retracts");
            assertTrue(step < radius * 0.2, "No visible position jump between rendered frames");
            assertTrue(Math.abs(step - previousStep) < radius * 0.02, "Velocity changes smoothly");
            if (previousOffset >= radius && offset < radius) {
                assertTrue(step > radius * 0.02, "Contact must not restart a stopped animation");
                crossedContact = true;
            }
            previousOffset = offset;
            previousStep = step;
        }
        assertTrue(crossedContact);
        double early = sourceOffset(0.20f) - sourceOffset(0.20f + frame);
        double middle = sourceOffset(0.28f) - sourceOffset(0.28f + frame);
        double later = sourceOffset(0.36f) - sourceOffset(0.36f + frame);
        assertTrue(early > 0 && middle > early && later > middle, "Approach builds speed after the hold");
    }

    private static double sourceOffset(float progress) {
        return LimitlessEffects.charge(Technique.PURPLE, progress, new Vec3(0, 0, 1)).stream()
                .filter(form -> form.technique() == Technique.BLUE)
                .mapToDouble(form -> form.center().length())
                .findFirst()
                .orElse(0);
    }

    @ParameterizedTest
    @EnumSource(
            value = Technique.class,
            names = {"BLUE", "RED", "PURPLE"})
    void releasePreservesTheBodyAndNetworkDelayCannotEnlargeIt(Technique technique) {
        var direction = new Vec3(0, 0, 1);
        var ready = body(LimitlessEffects.charge(technique, 1, direction), technique);
        for (int age : new int[] {0, 1, 3}) {
            var flight = body(LimitlessEffects.flight(technique, age, direction, 0), technique);
            assertEquals(ready.center(), flight.center(), "Release keeps the prepared center");
            assertEquals(ready.direction(), flight.direction(), "Release keeps the aiming direction");
            assertEquals(ready.radius(), flight.radius(), EPSILON, "Age alone must not enlarge the body");
        }
    }

    @ParameterizedTest
    @CsvSource({"0, 0, 1", "0, 1, 0", "0.6, 0, 0.8"})
    void fusionRemainsVisibleAndOverlapsWithoutShrinkingTheSources(double x, double y, double z) {
        var direction = new Vec3(x, y, z);
        var initial = LimitlessEffects.charge(Technique.PURPLE, 0, direction);
        double sourceRadius = body(initial, Technique.BLUE).radius();
        double previousSeparation = body(initial, Technique.BLUE)
                .center()
                .distanceTo(body(initial, Technique.RED).center());
        boolean overlapped = false;
        for (int sample = 0; sample <= 100; sample++) {
            float progress = sample / 100f;
            var forms = LimitlessEffects.charge(Technique.PURPLE, progress, direction);
            assertFalse(forms.isEmpty(), "Fusion must never disappear between stages");
            for (var form : forms) {
                assertTrue(Double.isFinite(form.radius()) && form.radius() > 0);
                assertTrue(Double.isFinite(form.center().lengthSqr()));
            }
            var blue = forms.stream()
                    .filter(form -> form.technique() == Technique.BLUE)
                    .findFirst();
            var red = forms.stream()
                    .filter(form -> form.technique() == Technique.RED)
                    .findFirst();
            assertEquals(blue.isPresent(), red.isPresent(), "Both sources take part in the fusion");
            if (blue.isPresent() && red.isPresent()) {
                assertEquals(sourceRadius, blue.get().radius(), EPSILON);
                assertEquals(sourceRadius, red.get().radius(), EPSILON);
                double separation = blue.get().center().distanceTo(red.get().center());
                assertTrue(separation <= previousSeparation + EPSILON);
                overlapped |= separation < sourceRadius * 2 - EPSILON;
                previousSeparation = separation;
            }
        }
        assertTrue(overlapped, "Sources must visibly overlap before fusion completes");
        var ready = LimitlessEffects.charge(Technique.PURPLE, 1, direction);
        assertTrue(ready.stream().allMatch(form -> form.technique() == Technique.PURPLE));
        assertEquals(Vec3.ZERO, body(ready, Technique.PURPLE).center());
    }

    private static LimitlessEffects.Form body(List<LimitlessEffects.Form> forms, Technique technique) {
        return forms.stream()
                .filter(form -> form.technique() == technique)
                .findFirst()
                .orElseThrow();
    }
}
