import mihon.buildlogic.configureAndroid
import mihon.buildlogic.configureTest

plugins {
    id("mihon.code.detekt")
    id("com.android.library")
}

android {
    configureAndroid(this)
    configureTest()
}
