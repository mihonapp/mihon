import nekotachi.buildlogic.AndroidConfig
import nekotachi.buildlogic.configureAndroid
import nekotachi.buildlogic.configureTest

plugins {
    id("com.android.application")
    kotlin("android")

    id("nekotachi.code.lint")
}

android {
    defaultConfig {
        targetSdk = AndroidConfig.TARGET_SDK
    }
    configureAndroid(this)
    configureTest()
}
