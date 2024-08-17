import mihon.buildlogic.configureCompose

plugins {
    id("com.android.library")
}

android {
    configureCompose(this)
}
