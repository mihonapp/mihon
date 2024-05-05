package mihon.buildlogic

import org.gradle.api.JavaVersion as GradleJavaVersion

object AndroidConfig {
    const val COMPILE_SDK = 34
    const val TARGET_SDK = 34
    const val MIN_SDK = 26
    const val NDK = "26.1.10909125"
    val JavaVersion = GradleJavaVersion.VERSION_17
}
