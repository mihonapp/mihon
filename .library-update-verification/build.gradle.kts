plugins {
    kotlin("jvm") version "2.4.21"
}

repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        main {
            kotlin.setSrcDirs(listOf("../app/src/main/java", "../core/common/src/main/kotlin"))
            kotlin.include(
                "eu/kanade/tachiyomi/data/library/LibraryUpdateRetry.kt",
                "eu/kanade/tachiyomi/network/HttpException.kt",
            )
        }
        test {
            kotlin.setSrcDirs(listOf("../app/src/test/java"))
            kotlin.include("eu/kanade/tachiyomi/data/library/LibraryUpdateRetryTest.kt")
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}

tasks.test {
    useJUnitPlatform()
}