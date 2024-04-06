import mihon.buildlogic.AndroidConfig
import mihon.buildlogic.configureCompose

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    configureCompose(this)
}
