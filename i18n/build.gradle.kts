import mihon.buildlogic.tasks.getLocalesConfigTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("mihon.library")
    id("dev.icerock.mobile.multiplatform-resources")
    kotlin("multiplatform")
}

kotlin {
    androidTarget()

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.moko.core)
            }
        }

        androidMain {
            dependsOn(commonMain)  // https://github.com/icerockdev/moko-resources/issues/562
        }
    }
}

android {
    namespace = "tachiyomi.i18n"

    sourceSets {
        named("main") {
            res.srcDir("src/commonMain/resources")
        }
    }

    lint {
        disable.addAll(listOf("MissingTranslation", "ExtraTranslation"))
    }
}

multiplatformResources {
    multiplatformResourcesPackage = "tachiyomi.i18n"
}

tasks {
    val localesConfigTask = project.getLocalesConfigTask()
    preBuild {
        dependsOn(localesConfigTask)
    }

    withType<KotlinCompile> {
        compilerOptions.freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
        )
    }
}
