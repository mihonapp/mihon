import mihon.buildlogic.configureAndroid
import mihon.buildlogic.configureTest

plugins {
    id("com.android.library")

    id("mihon.code.lint")
}

android {
    configureAndroid(this)
    configureTest()
}
