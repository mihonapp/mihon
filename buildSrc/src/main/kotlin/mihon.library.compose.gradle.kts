import mihon.buildlogic.configureCompose

plugins {
    id("com.android.library")

    id("mihon.code.lint")
}

android {
    configureCompose(this)
}
