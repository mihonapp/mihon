import nekotachi.buildlogic.configureCompose

plugins {
    id("com.android.library")

    id("nekotachi.code.lint")
}

android {
    configureCompose(this)
}
