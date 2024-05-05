plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(files(androidx.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(files(compose.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(files(kotlinx.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(androidx.gradle)
    implementation(kotlinx.gradle)
    implementation(libs.detekt.gradlePlugin)
    implementation(gradleApi())
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
}
