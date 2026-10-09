import mihon.gradle.tasks.GenerateVectorDrawablesTask

plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.compose)

    alias(mihonx.plugins.spotless)

    alias(libs.plugins.valkyrie)
}

android {
    namespace = "mihon.icons.materialsymbols"
}

spotless {
    format("svg") {
        target("src/**/*.svg")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

valkyrie {
    packageName = "mihon.icons.materialsymbols"
    generateAtSync = true

    imageVector {
        suppressUnusedReceiverWarning = true
    }

    iconPack {
        name = "MaterialSymbols"
        targetSourceSet = "main"

        nested {
            name = "Rounded"
            sourceFolder = "rounded"
            autoMirror = false
        }

        nested {
            name = "RoundedFilled"
            sourceFolder = "roundedFilled"
            autoMirror = false
        }

        // https://github.com/ComposeGears/Valkyrie/pull/1019
        nested {
            name = "AutoMirroredRounded"
            sourceFolder = "autoMirroredRounded"
            autoMirror = true
        }

        nested {
            name = "AutoMirroredRoundedFilled"
            sourceFolder = "autoMirroredRoundedFilled"
            autoMirror = true
        }
    }
}

androidComponents {
    onVariants { variant ->
        val resSource = variant.sources.res ?: return@onVariants

        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val task = tasks.register<GenerateVectorDrawablesTask>("generate${variantName}VectorDrawables") {
            svgFiles.from(
                listOf(
                    "rounded/book",
                    "rounded/check",
                    "rounded/close",
                    "rounded/drag_handle",
                    "rounded/more_vert",
                    "rounded/refresh",
                    "rounded/share",
                    "roundedFilled/extension",
                    "roundedFilled/folder",
                    "roundedFilled/pause",
                    "roundedFilled/photo",
                    "roundedFilled/play_arrow",
                    "roundedFilled/warning",
                ).map { layout.projectDirectory.file("src/main/valkyrieResources/$it.svg") },
            )
        }
        resSource.addGeneratedSourceDirectory(task) { it.outputDir }
    }
}

dependencies {
    api(libs.androidx.compose.ui)
}
