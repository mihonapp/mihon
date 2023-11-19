plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("dev.icerock.mobile.multiplatform-resources")
}

kotlin {
    androidTarget()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.moko.core)
            }
        }
        val androidMain by getting {
            dependsOn(commonMain) // https://github.com/icerockdev/moko-resources/issues/562
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
    val localesConfigTask = registerLocalesConfigTask(project)
    preBuild {
        dependsOn(localesConfigTask)
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.freeCompilerArgs += listOf(
            "-Xexpect-actual-classes",
        )
    }
}
