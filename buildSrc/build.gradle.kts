plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(androidxLibs.gradle)
    implementation(kotlinLibs.gradle)
    implementation(libs.kotlinter)
    implementation(gradleApi())
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
}
