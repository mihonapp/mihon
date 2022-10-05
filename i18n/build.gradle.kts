plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "eu.kanade.tachiyomi.i18n"
    compileSdk = AndroidConfig.compileSdk

    defaultConfig {
        minSdk = AndroidConfig.minSdk
        targetSdk = AndroidConfig.targetSdk
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }

    lint {
        disable.addAll(listOf("MissingTranslation", "ExtraTranslation"))
    }
}

tasks {
    val localesConfigTask = registerLocalesConfigTask(project)

    // Duplicating Hebrew string assets due to some locale code issues on different devices
    val copyHebrewStrings by registering(Copy::class) {
        from("./src/main/res/values-he")
        into("./src/main/res/values-iw")
        include("**/*")
    }

    preBuild {
        dependsOn(copyHebrewStrings, localesConfigTask)
    }
}
