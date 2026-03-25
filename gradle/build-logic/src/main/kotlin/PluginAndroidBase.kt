import mihon.gradle.configurations.configureAndroid
import mihon.gradle.configurations.configureKotlin
import mihon.gradle.extensions.alias
import mihon.gradle.extensions.configureTest
import mihon.gradle.extensions.libs
import mihon.gradle.extensions.plugins
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("UNUSED")
class PluginAndroidBase : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.kotlin.android)
        }

        configureKotlin()
        configureTest()
        configureAndroid()
    }
}
