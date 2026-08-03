import mihon.gradle.extensions.alias
import mihon.gradle.extensions.libs
import mihon.gradle.extensions.mihonx
import mihon.gradle.extensions.plugins
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("UNUSED")
class PluginAndroidApplication : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.android.application)
            alias(mihonx.plugins.android.base)
        }
    }
}
