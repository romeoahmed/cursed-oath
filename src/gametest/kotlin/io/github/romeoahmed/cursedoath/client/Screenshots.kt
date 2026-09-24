package io.github.romeoahmed.cursedoath.client

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonAlgorithm
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonOptions
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions
import org.joml.Vector2i
import java.nio.file.Files
import java.nio.file.Path

private const val WIDTH = 960
private const val HEIGHT = 540
private const val REGION_WIDTH = WIDTH / 3
private const val REGION_HEIGHT = HEIGHT / 3
private const val MAX_DIFFERENCE = 0.0001f
private val matching = TestScreenshotComparisonAlgorithm.meanSquaredDifference(MAX_DIFFERENCE)
private val different =
    TestScreenshotComparisonAlgorithm { actual, expected ->
        if (matching.findColor(actual, expected) == null) Vector2i() else null
    }

internal fun ClientGameTestContext.prepareScreenshots() {
    runOnClient<RuntimeException> {
        val backend = RenderSystem.getDevice().deviceInfo.backendName()
        check(backend == "Vulkan") { "Client tests require Vulkan; Minecraft selected $backend" }
    }
    input.resizeWindow(WIDTH, HEIGHT)
}

internal fun ClientGameTestContext.capture(name: String): Path =
    takeScreenshot(TestScreenshotOptions.of(name).withDeltaTicks(0f))

/** Compare against this run's baseline, without maintaining cross-GPU golden images. */
internal fun ClientGameTestContext.checkEffect(
    baseline: Path,
    name: String,
    visible: Boolean,
) {
    NativeImage.read(Files.readAllBytes(baseline)).use { full ->
        NativeImage(REGION_WIDTH, REGION_HEIGHT, false).use { region ->
            full.resizeSubRectTo(REGION_WIDTH, REGION_HEIGHT, REGION_WIDTH, REGION_HEIGHT, region)
            assertScreenshotEquals(
                TestScreenshotComparisonOptions
                    .of(region)
                    .withRegion(REGION_WIDTH, REGION_HEIGHT, REGION_WIDTH, REGION_HEIGHT)
                    .withDeltaTicks(0f)
                    .withAlgorithm(if (visible) different else matching)
                    .saveWithFileName(name),
            )
        }
    }
}
