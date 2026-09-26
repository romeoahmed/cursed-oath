package io.github.romeoahmed.cursedoath.client;

import com.mojang.blaze3d.platform.NativeLibrariesBootstrap;
import com.mojang.renderpearl.api.device.GpuDebugOptions;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.backend.vulkan.VulkanBackend;
import java.util.Objects;
import net.minecraft.SharedConstants;
import org.jspecify.annotations.NullMarked;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLVideo;

/// Fails without a dialog when a Vulkan device or presentation surface cannot be created.
@NullMarked
public final class VulkanProbe {
    private static final int PROBE_SIZE = 64;

    private VulkanProbe() {}

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        NativeLibrariesBootstrap.loadLibraries();
        if (!SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO))
            throw new IllegalStateException(
                    Objects.requireNonNullElse(SDLError.SDL_GetError(), "SDL video initialization failed"));
        var backend = new VulkanBackend();
        try {
            backend.loadLibrary();
            var window = backend.createWindow("Vulkan probe", PROBE_SIZE, PROBE_SIZE, SDLVideo.SDL_WINDOW_HIDDEN);
            if (window == 0)
                throw new IllegalStateException(
                        Objects.requireNonNullElse(SDLError.SDL_GetError(), "Vulkan window creation failed"));
            try {
                var device = backend.createDevice(new GpuDebugOptions(0, false, false, false));
                try {
                    try (var surface = device.createSurface(window, () -> false)) {
                        surface.configure(
                                new GpuSurface.Configuration(PROBE_SIZE, PROBE_SIZE, GpuSurface.PresentMode.FIFO));
                    }
                    var info = device.getDeviceInfo();
                    System.out.println(info.backendName() + " on " + info.name() + ": " + info.driverInfo());
                } finally {
                    device.close();
                }
            } finally {
                SDLVideo.SDL_DestroyWindow(window);
            }
        } finally {
            backend.unloadLibrary();
            SDLInit.SDL_Quit();
        }
    }
}
