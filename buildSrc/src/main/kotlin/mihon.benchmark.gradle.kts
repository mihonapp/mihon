import mihon.buildlogic.configureAndroid
import mihon.buildlogic.configureTest

plugins {
    id("mihon.code.detekt")
    id("com.android.test")
    kotlin("android")
}

android {
    configureAndroid(this)
    configureTest()
}
