import mihon.buildlogic.configureAndroid
import mihon.buildlogic.configureTest

plugins {
    id("com.android.test")
    kotlin("android")
}

android {
    configureAndroid(this)
    configureTest()
}
