package io.github.romeoahmed.cursedoath.client

import com.mojang.blaze3d.platform.NativeLibrariesBootstrap
import com.mojang.renderpearl.api.device.GpuDebugOptions
import com.mojang.renderpearl.api.device.GpuSurface
import com.mojang.renderpearl.backend.vulkan.VulkanBackend
import net.minecraft.SharedConstants
import org.lwjgl.sdl.SDLError
import org.lwjgl.sdl.SDLInit
import org.lwjgl.sdl.SDLVideo

/** Fail without a dialog when Minecraft cannot create a Vulkan device and presentation surface. */
fun main() {
    SharedConstants.tryDetectVersion()
    NativeLibrariesBootstrap.loadLibraries()
    check(SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO)) { SDLError.SDL_GetError() ?: "SDL video initialization failed" }
    val backend = VulkanBackend()
    try {
        backend.loadLibrary()
        val window = backend.createWindow("Vulkan probe", PROBE_SIZE, PROBE_SIZE, SDLVideo.SDL_WINDOW_HIDDEN)
        check(window != 0L) { SDLError.SDL_GetError() ?: "Vulkan window creation failed" }
        try {
            val device = backend.createDevice(GpuDebugOptions(0, false, false, false))
            try {
                device.createSurface(window) { false }.use { surface ->
                    surface.configure(GpuSurface.Configuration(PROBE_SIZE, PROBE_SIZE, GpuSurface.PresentMode.FIFO))
                }
                val info = device.deviceInfo
                println("${info.backendName()} on ${info.name()}: ${info.driverInfo()}")
            } finally {
                device.close()
            }
        } finally {
            SDLVideo.SDL_DestroyWindow(window)
        }
    } finally {
        backend.unloadLibrary()
        SDLInit.SDL_Quit()
    }
}

private const val PROBE_SIZE = 64
