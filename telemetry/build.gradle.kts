import mihon.buildlogic.Config

plugins {
    id("mihon.library")
    kotlin("android")
}

android {
    namespace = "mihon.telemetry"

    sourceSets {
        getByName("main") {
            val dir = if (Config.includeAnalytics) "firebase" else "noop"
            kotlin.srcDirs("src/$dir/kotlin")
            manifest.srcFile("src/$dir/AndroidManifext.xml")
        }
    }
}

dependencies {
    if (Config.includeAnalytics) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.analytics)
        implementation(libs.firebase.crashlytics)
    }
}
