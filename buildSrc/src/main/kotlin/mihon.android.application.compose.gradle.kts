import mihon.buildlogic.configureCompose

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    configureCompose(this)
}
