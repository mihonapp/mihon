import mihon.buildlogic.configureCompose

plugins {
    id("mihon.code.detekt")
    id("com.android.library")
}

android {
    configureCompose(this)
}
