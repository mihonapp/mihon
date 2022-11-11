import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("com.mikepenz.aboutlibraries.plugin")
    kotlin("android")
    kotlin("plugin.serialization")
    id("com.github.zellius.shortcut-helper")
    id("com.squareup.sqldelight")
}

if (gradle.startParameter.taskRequests.toString().contains("Standard")) {
    apply<com.google.gms.googleservices.GoogleServicesPlugin>()
}

shortcutHelper.setFilePath("./shortcuts.xml")

val SUPPORTED_ABIS = setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

android {
    namespace = "eu.kanade.tachiyomi"
    compileSdk = AndroidConfig.compileSdk
    ndkVersion = AndroidConfig.ndk

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi"
        minSdk = AndroidConfig.minSdk
        targetSdk = AndroidConfig.targetSdk
        versionCode = 91
        versionName = "0.14.2"

        buildConfigField("String", "COMMIT_COUNT", "\"${getCommitCount()}\"")
        buildConfigField("String", "COMMIT_SHA", "\"${getGitSha()}\"")
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTime()}\"")
        buildConfigField("boolean", "INCLUDE_UPDATER", "false")
        buildConfigField("boolean", "PREVIEW", "false")

        // Please disable ACRA or use your own instance in forked versions of the project
        buildConfigField("String", "ACRA_URI", "\"https://tachiyomi.kanade.eu/crash_report\"")

        ndk {
            abiFilters += SUPPORTED_ABIS
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include(*SUPPORTED_ABIS.toTypedArray())
            isUniversalApk = true
        }
    }

    buildTypes {
        named("debug") {
            versionNameSuffix = "-${getCommitCount()}"
            applicationIdSuffix = ".debug"
            isPseudoLocalesEnabled = true
        }
        named("release") {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")
        }
        create("preview") {
            initWith(getByName("release"))
            buildConfigField("boolean", "PREVIEW", "true")

            val debugType = getByName("debug")
            signingConfig = debugType.signingConfig
            versionNameSuffix = debugType.versionNameSuffix
            applicationIdSuffix = debugType.applicationIdSuffix
            matchingFallbacks.add("release")
        }
        create("benchmark") {
            initWith(getByName("release"))

            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks.add("release")
            isDebuggable = false
            versionNameSuffix = "-benchmark"
            applicationIdSuffix = ".benchmark"
        }
    }

    sourceSets {
        getByName("preview").res.srcDirs("src/debug/res")
        getByName("benchmark").res.srcDirs("src/debug/res")
    }

    flavorDimensions.add("default")

    productFlavors {
        create("standard") {
            buildConfigField("boolean", "INCLUDE_UPDATER", "true")
            dimension = "default"
        }
        create("dev") {
            // Include pseudolocales: https://developer.android.com/guide/topics/resources/pseudolocales
            resourceConfigurations.addAll(listOf("en", "en_XA", "ar_XB", "xxhdpi"))
            dimension = "default"
        }
    }

    packagingOptions {
        resources.excludes.addAll(listOf(
            "META-INF/DEPENDENCIES",
            "LICENSE.txt",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/README.md",
            "META-INF/NOTICE",
            "META-INF/*.kotlin_module",
        ))
    }

    dependenciesInfo {
        includeInApk = false
    }

    buildFeatures {
        viewBinding = true
        compose = true

        // Disable some unused things
        aidl = false
        renderScript = false
        shaders = false
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    composeOptions {
        kotlinCompilerExtensionVersion = compose.versions.compiler.get()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8

        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }

    sqldelight {
        database("Database") {
            packageName = "eu.kanade.tachiyomi"
            dialect = "sqlite:3.24"
        }
    }
}

dependencies {
    implementation(project(":i18n"))
    implementation(project(":core"))
    implementation(project(":source-api"))

    coreLibraryDesugaring(libs.desugar)

    // Compose
    implementation(platform(compose.bom))
    implementation(compose.activity)
    implementation(compose.foundation)
    implementation(compose.material3.core)
    implementation(compose.material3.adapter)
    implementation(compose.material.core)
    implementation(compose.material.icons)
    implementation(compose.animation)
    implementation(compose.animation.graphics)
    implementation(compose.ui.tooling)
    implementation(compose.ui.util)
    implementation(compose.accompanist.webview)
    implementation(compose.accompanist.swiperefresh)
    implementation(compose.accompanist.flowlayout)
    implementation(compose.accompanist.permissions)

    implementation(androidx.paging.runtime)
    implementation(androidx.paging.compose)

    implementation(libs.bundles.sqlite)
    implementation(androidx.sqlite)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.sqldelight.android.paging)

    implementation(kotlinx.reflect)

    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)

    // AndroidX libraries
    implementation(androidx.annotation)
    implementation(androidx.appcompat)
    implementation(androidx.biometricktx)
    implementation(androidx.constraintlayout)
    implementation(androidx.coordinatorlayout)
    implementation(androidx.corektx)
    implementation(androidx.splashscreen)
    implementation(androidx.recyclerview)
    implementation(androidx.viewpager)
    implementation(androidx.glance)
    implementation(androidx.profileinstaller)

    implementation(androidx.bundles.lifecycle)

    // Job scheduling
    implementation(androidx.bundles.workmanager)

    // RX
    implementation(libs.bundles.reactivex)
    implementation(libs.flowreactivenetwork)

    // Network client
    implementation(libs.bundles.okhttp)
    implementation(libs.okio)

    // TLS 1.3 support for Android < 10
    implementation(libs.conscrypt.android)

    // Data serialization (JSON, protobuf)
    implementation(kotlinx.bundles.serialization)

    // HTML parser
    implementation(libs.jsoup)

    // Disk
    implementation(libs.disklrucache)
    implementation(libs.unifile)
    implementation(libs.junrar)

    // Preferences
    implementation(libs.preferencektx)

    // Model View Presenter
    implementation(libs.bundles.nucleus)

    // Dependency injection
    implementation(libs.injekt.core)

    // Image loading
    implementation(libs.bundles.coil)

    implementation(libs.subsamplingscaleimageview) {
        exclude(module = "image-decoder")
    }
    implementation(libs.image.decoder)

    // Sort
    implementation(libs.natural.comparator)

    // UI libraries
    implementation(libs.material)
    implementation(libs.flexible.adapter.core)
    implementation(libs.flexible.adapter.ui)
    implementation(libs.photoview)
    implementation(libs.directionalviewpager) {
        exclude(group = "androidx.viewpager", module = "viewpager")
    }
    implementation(libs.insetter)
    implementation(libs.markwon)
    implementation(libs.aboutLibraries.compose)
    implementation(libs.cascade)
    implementation(libs.bundles.voyager)
    implementation(libs.wheelpicker)

    // Conductor
    implementation(libs.bundles.conductor)

    // FlowBinding
    implementation(libs.bundles.flowbinding)

    // Logging
    implementation(libs.logcat)

    // Crash reports/analytics
    implementation(libs.acra.http)
    "standardImplementation"(libs.firebase.analytics)

    // Shizuku
    implementation(libs.bundles.shizuku)

    // Tests
    testImplementation(libs.junit)

    // For detecting memory leaks; see https://square.github.io/leakcanary/
    // debugImplementation(libs.leakcanary.android)
    implementation(libs.leakcanary.plumber)
}

androidComponents {
    beforeVariants { variantBuilder ->
        // Disables standardBenchmark
        if (variantBuilder.buildType == "benchmark") {
            variantBuilder.enable = variantBuilder.productFlavors.containsAll(listOf("default" to "dev"))
        }
    }
    onVariants(selector().withFlavor("default" to "standard")) {
        // Only excluding in standard flavor because this breaks
        // Layout Inspector's Compose tree
        it.packaging.resources.excludes.add("META-INF/*.version")
    }
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
    }

    withType<org.jmailen.gradle.kotlinter.tasks.LintTask>().configureEach {
        exclude { it.file.path.contains("generated[\\\\/]".toRegex()) }
    }

    // See https://kotlinlang.org/docs/reference/experimental.html#experimental-status-of-experimental-api(-markers)
    withType<KotlinCompile> {
        kotlinOptions.freeCompilerArgs += listOf(
            "-opt-in=coil.annotation.ExperimentalCoilApi",
            "-opt-in=com.google.accompanist.permissions.ExperimentalPermissionsApi",
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )

        if (project.findProperty("tachiyomi.enableComposeCompilerMetrics") == "true") {
            kotlinOptions.freeCompilerArgs += listOf(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" +
                    project.buildDir.absolutePath + "/compose_metrics"
            )
            kotlinOptions.freeCompilerArgs += listOf(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=" +
                    project.buildDir.absolutePath + "/compose_metrics"
            )
        }
    }

    preBuild {
        val ktlintTask = if (System.getenv("GITHUB_BASE_REF") == null) formatKotlin else lintKotlin
        dependsOn(ktlintTask)
    }
}

buildscript {
    dependencies {
        classpath(kotlinx.gradle)
    }
}
