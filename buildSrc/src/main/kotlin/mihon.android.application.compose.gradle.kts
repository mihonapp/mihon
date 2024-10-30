import mihon.buildlogic.configureCompose

plugins {
    id("com.android.application")
    kotlin("android")

    id("mihon.code.lint")
}

android {
    configureCompose(this)
}
