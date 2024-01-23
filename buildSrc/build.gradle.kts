plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(androidxLibs.gradle)
    implementation(kotlinLibs.gradle)
    implementation(gradleApi())
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
}
