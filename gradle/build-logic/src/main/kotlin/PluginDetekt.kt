import dev.detekt.gradle.extensions.DetektExtension
import mihon.gradle.extensions.alias
import mihon.gradle.extensions.libs
import mihon.gradle.extensions.plugins
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

@Suppress("UNUSED")
class PluginDetekt : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.detekt)
        }

        // Configuration should be synced with [/gradle/build-logic/build.gradle.kts]
        val detektVersion = libs.detekt.gradle.get().version
        val detektConfig = gradle.includedBuild("build-logic").projectDir
            .resolve("config/detekt/detekt.yaml")

        detekt {
            toolVersion.set(detektVersion)
            config.setFrom(detektConfig)
            buildUponDefaultConfig.set(false)
        }
    }
}

private fun Project.detekt(block: DetektExtension.() -> Unit) {
    extensions.configure(block)
}
