import mihon.buildlogic.Config

plugins {
    id("mihon.library")
    kotlin("android")
}

android {
    namespace = "mihon.telemetry"

    sourceSets {
        getByName("main") {
            val dir = if (Config.includeTelemetry) "firebase" else "noop"
            kotlin.srcDirs("src/$dir/kotlin")
            manifest.srcFile("src/$dir/AndroidManifext.xml")
        }
    }
}

dependencies {
    if (Config.includeTelemetry) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.analytics)
        implementation(libs.firebase.crashlytics)
    }
}
