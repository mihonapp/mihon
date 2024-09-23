import nekotachi.buildlogic.configureAndroid
import nekotachi.buildlogic.configureTest

plugins {
    id("com.android.test")
    kotlin("android")

    id("nekotachi.code.lint")
}

android {
    configureAndroid(this)
    configureTest()
}
