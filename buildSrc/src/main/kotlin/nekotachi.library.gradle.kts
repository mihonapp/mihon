import nekotachi.buildlogic.configureAndroid
import nekotachi.buildlogic.configureTest

plugins {
    id("com.android.library")

    id("nekotachi.code.lint")
}

android {
    configureAndroid(this)
    configureTest()
}
