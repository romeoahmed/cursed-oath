package io.github.romeoahmed.cursedoath.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonAlgorithm;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonOptions;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import org.joml.Vector2i;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class Screenshots {
    private static final int WIDTH = 960, HEIGHT = 540, REGION_WIDTH = WIDTH / 3, REGION_HEIGHT = HEIGHT / 3;
    private static final TestScreenshotComparisonAlgorithm MATCHING =
            TestScreenshotComparisonAlgorithm.meanSquaredDifference(0.0001f);
    private static final TestScreenshotComparisonAlgorithm DIFFERENT =
            (actual, expected) -> MATCHING.findColor(actual, expected) == null ? new Vector2i() : null;

    private Screenshots() {}

    static void prepareScreenshots(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var backend = RenderSystem.getDevice().getDeviceInfo().backendName();
            if (!backend.equals("Vulkan"))
                throw new AssertionError("Client tests require Vulkan; Minecraft selected " + backend);
        });
        context.getInput().resizeWindow(WIDTH, HEIGHT);
    }

    static Path capture(ClientGameTestContext context, String name) {
        // Fixture commands must not obscure the HUD, wheel, or pose being inspected.
        context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
        return context.takeScreenshot(TestScreenshotOptions.of(name).withDeltaTicks(0f));
    }
    /// Checks the center region against a same-run baseline without requiring cross-GPU pixel identity.
    ///
    /// @param context running client test
    /// @param baseline capture taken without the effect at the same camera position
    /// @param name output capture name
    /// @param visible whether the region should differ from the baseline
    /// @throws UncheckedIOException if the baseline cannot be read
    static void checkEffect(ClientGameTestContext context, Path baseline, String name, boolean visible) {
        try (var full = NativeImage.read(Files.readAllBytes(baseline));
                var region = new NativeImage(REGION_WIDTH, REGION_HEIGHT, false)) {
            full.resizeSubRectTo(REGION_WIDTH, REGION_HEIGHT, REGION_WIDTH, REGION_HEIGHT, region);
            context.assertScreenshotEquals(TestScreenshotComparisonOptions.of(region)
                    .withRegion(REGION_WIDTH, REGION_HEIGHT, REGION_WIDTH, REGION_HEIGHT)
                    .withDeltaTicks(0f)
                    .withAlgorithm(visible ? DIFFERENT : MATCHING)
                    .saveWithFileName(name));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
