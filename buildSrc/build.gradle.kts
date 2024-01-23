plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(androidxLibs.gradle)
    implementation(kotlinLibs.gradle)
    implementation(libs.detekt.gradlePlugin)
    implementation(gradleApi())
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
}
