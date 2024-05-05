import mihon.buildlogic.AndroidConfig
import mihon.buildlogic.configureAndroid
import mihon.buildlogic.configureTest

plugins {
    id("mihon.code.detekt")
    id("com.android.application")
    kotlin("android")
}

android {
    defaultConfig {
        targetSdk = AndroidConfig.TARGET_SDK
    }
    configureAndroid(this)
    configureTest()
}
