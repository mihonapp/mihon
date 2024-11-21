package eu.kanade.tachiyomi.util.system

import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import kotlin.math.max

object GLUtil {
    val DEVICE_TEXTURE_LIMIT: Int by lazy {
        // Get EGL Display
        val egl = EGLContext.getEGL() as EGL10
        val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)

        // Initialise
        val version = IntArray(2)
        egl.eglInitialize(display, version)

        // Query total number of configurations
        val totalConfigurations = IntArray(1)
        egl.eglGetConfigs(display, null, 0, totalConfigurations)

        // Query actual list configurations
        val configurationsList = arrayOfNulls<EGLConfig>(totalConfigurations[0])
        egl.eglGetConfigs(display, configurationsList, totalConfigurations[0], totalConfigurations)

        val textureSize = IntArray(1)
        var maximumTextureSize = 0

        // Iterate through all the configurations to located the maximum texture size
        for (i in 0..<totalConfigurations[0]) {
            // Only need to check for width since opengl textures are always squared
            egl.eglGetConfigAttrib(display, configurationsList[i], EGL10.EGL_MAX_PBUFFER_WIDTH, textureSize)

            // Keep track of the maximum texture size
            if (maximumTextureSize < textureSize[0]) maximumTextureSize = textureSize[0]
        }

        // Release
        egl.eglTerminate(display)

        // Return largest texture size found (after making it a multiplier of [Multiplier]), or default
        max(maximumTextureSize, SAFE_TEXTURE_LIMIT)
    }

    const val SAFE_TEXTURE_LIMIT: Int = 2048

    val CUSTOM_TEXTURE_LIMIT_OPTIONS: List<Int> by lazy {
        val steps = DEVICE_TEXTURE_LIMIT / MULTIPLIER
        buildList(steps) {
            add(DEVICE_TEXTURE_LIMIT)
            for (step in steps downTo 2) {
                val value = step * MULTIPLIER
                if (value >= DEVICE_TEXTURE_LIMIT) continue
                add(value)
            }
        }
    }
}

private const val MULTIPLIER: Int = 1024
