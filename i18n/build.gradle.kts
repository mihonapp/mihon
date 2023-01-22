plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "tachiyomi.i18n"

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
