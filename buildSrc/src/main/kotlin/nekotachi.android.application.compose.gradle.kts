import nekotachi.buildlogic.configureCompose

plugins {
    id("com.android.application")
    kotlin("android")

    id("nekotachi.code.lint")
}

android {
    configureCompose(this)
}
