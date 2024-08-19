import mihon.buildlogic.configureAndroid
import mihon.buildlogic.configureTest

plugins {
    id("com.android.library")
}

android {
    configureAndroid(this)
    configureTest()
}
