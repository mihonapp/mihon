import com.diffplug.gradle.spotless.SpotlessExtension
import mihon.gradle.extensions.alias
import mihon.gradle.extensions.libs
import mihon.gradle.extensions.plugins
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

@Suppress("UNUSED")
class PluginSpotless : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.spotless)
        }

        // Configuration should be synced with [/gradle/build-logic/build.gradle.kts]
        val ktlintVersion = libs.ktlint.bom.get().version
        spotless {
            kotlin {
                target("src/**/*.kt")
                ktlint(ktlintVersion)
                trimTrailingWhitespace()
                endWithNewline()
                forbidRegex(
                    "materialSymbolsRImport",
                    "(?m)^import mihon\\.icons\\.materialsymbols\\.R(?! as MaterialSymbolsR$)\\b.*$",
                    "Import mihon.icons.materialsymbols.R as MaterialSymbolsR",
                )
                forbidRegex(
                    "materialSymbolsRQualified",
                    "(?m)^(?!import ).*\\bmihon\\.icons\\.materialsymbols\\.R\\.",
                    "Use the MaterialSymbolsR import alias instead of the qualified name",
                )
                forbidRegex(
                    "customMaterialSymbolsRImport",
                    "(?m)^import mihon\\.icons\\.custommaterialsymbols\\.R(?! as CustomMaterialSymbolsR$)\\b.*$",
                    "Import mihon.icons.custommaterialsymbols.R as CustomMaterialSymbolsR",
                )
                forbidRegex(
                    "customMaterialSymbolsRQualified",
                    "(?m)^(?!import ).*\\bmihon\\.icons\\.custommaterialsymbols\\.R\\.",
                    "Use the CustomMaterialSymbolsR import alias instead of the qualified name",
                )
            }

            kotlinGradle {
                target("*.kts")
                ktlint(ktlintVersion)
                trimTrailingWhitespace()
                endWithNewline()
            }

            format("xml") {
                target("src/**/*.xml")
                trimTrailingWhitespace()
                endWithNewline()
            }
        }
    }
}

private fun Project.spotless(block: SpotlessExtension.() -> Unit) {
    extensions.configure(block)
}
