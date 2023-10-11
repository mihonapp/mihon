plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(androidxLibs.gradle)
    implementation(kotlinLibs.gradle)
    implementation(libs.ktlint)
    implementation(gradleApi())
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
}
