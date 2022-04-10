import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("com.mikepenz.aboutlibraries.plugin")
    kotlin("android")
    kotlin("plugin.serialization")
    id("com.github.zellius.shortcut-helper")
}

if (gradle.startParameter.taskRequests.toString().contains("Standard")) {
    apply<com.google.gms.googleservices.GoogleServicesPlugin>()
}

shortcutHelper.setFilePath("./shortcuts.xml")

val SUPPORTED_ABIS = setOf("armeabi-v7a", "arm64-v8a", "x86")

android {
    compileSdk = AndroidConfig.compileSdk
    ndkVersion = AndroidConfig.ndk

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi"
        minSdk = AndroidConfig.minSdk
        targetSdk = AndroidConfig.targetSdk
        versionCode = 78
        versionName = "0.13.2"

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
        }
    }

    sourceSets {
        getByName("preview").res.srcDirs("src/debug/res")
    }

    flavorDimensions.add("default")

    productFlavors {
        create("standard") {
            buildConfigField("boolean", "INCLUDE_UPDATER", "true")
            dimension = "default"
        }
        create("dev") {
            resourceConfigurations.addAll(listOf("en", "xxhdpi"))
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
            "META-INF/*.version",
        ))
    }

    dependenciesInfo {
        includeInApk = false
    }

    buildFeatures {
        viewBinding = true

        // Disable some unused things
        aidl = false
        renderScript = false
        shaders = false
    }

    lint {
        disable.addAll(listOf("MissingTranslation", "ExtraTranslation"))
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}

dependencies {
    implementation(kotlinx.reflect)

    implementation(kotlinx.bundles.coroutines)

    // Source models and interfaces from Tachiyomi 1.x
    implementation(libs.tachiyomi.api)

    // AndroidX libraries
    implementation(androidx.annotation)
    implementation(androidx.appcompat)
    implementation(androidx.biometricktx)
    implementation(androidx.constraintlayout)
    implementation(androidx.coordinatorlayout)
    implementation(androidx.corektx)
    implementation(androidx.splashscreen)
    implementation(androidx.recyclerview)
    implementation(androidx.swiperefreshlayout)
    implementation(androidx.viewpager)

    implementation(androidx.bundles.lifecycle)

    // Job scheduling
    implementation(androidx.work.runtime)

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

    // JavaScript engine
    implementation(libs.bundles.js.engine)

    // HTML parser
    implementation(libs.jsoup)

    // Disk
    implementation(libs.disklrucache)
    implementation(libs.unifile)
    implementation(libs.junrar)

    // Database
    implementation(libs.bundles.sqlite)
    implementation("com.github.inorichi.storio:storio-common:8be19de@aar")
    implementation("com.github.inorichi.storio:storio-sqlite:8be19de@aar")

    // Preferences
    implementation(libs.preferencektx)
    implementation(libs.flowpreferences)

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
    implementation(libs.androidprocessbutton)
    implementation(libs.flexible.adapter.core)
    implementation(libs.flexible.adapter.ui)
    implementation(libs.viewstatepageradapter)
    implementation(libs.photoview)
    implementation(libs.directionalviewpager) {
        exclude(group = "androidx.viewpager", module = "viewpager")
    }
    implementation(libs.insetter)

    // Conductor
    implementation(libs.bundles.conductor)

    // FlowBinding
    implementation(libs.bundles.flowbinding)

    // Logging
    implementation(libs.logcat)

    // Crash reports/analytics
    implementation(libs.acra.http)
    "standardImplementation"(libs.firebase.analytics)

    // Licenses
    implementation(libs.aboutlibraries.core)

    // Shizuku
    implementation(libs.bundles.shizuku)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)

    testImplementation(libs.bundles.robolectric)

    // For detecting memory leaks; see https://square.github.io/leakcanary/
    // debugImplementation(libs.leakcanary.android)
}

tasks {
    // See https://kotlinlang.org/docs/reference/experimental.html#experimental-status-of-experimental-api(-markers)
    withType<KotlinCompile> {
        kotlinOptions.freeCompilerArgs += listOf(
            "-Xopt-in=kotlin.Experimental",
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xopt-in=kotlin.ExperimentalStdlibApi",
            "-Xopt-in=kotlinx.coroutines.FlowPreview",
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xopt-in=kotlinx.coroutines.InternalCoroutinesApi",
            "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-Xopt-in=coil.annotation.ExperimentalCoilApi",
        )
    }

    // Duplicating Hebrew string assets due to some locale code issues on different devices
    val copyHebrewStrings = task("copyHebrewStrings", type = Copy::class) {
        from("./src/main/res/values-he")
        into("./src/main/res/values-iw")
        include("**/*")
    }

    preBuild {
        dependsOn(formatKotlin, copyHebrewStrings)
    }
}

buildscript {
    dependencies {
        classpath(kotlinx.gradle)
    }
}
