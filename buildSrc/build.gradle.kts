plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlinLibs.versions.kotlin.version.get()}")

    implementation(gradleApi())
}

repositories {
    mavenCentral()
    google()
}
