import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

plugins {
    id("com.android.application")
    id("com.mikepenz.aboutlibraries.plugin")
    kotlin("android")
    kotlin("plugin.serialization")
    id("com.github.zellius.shortcut-helper")
}

if (gradle.startParameter.taskRequests.toString().contains("Standard")) {
    apply(plugin = "com.google.gms.google-services")
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
        versionCode = 72
        versionName = "0.12.3"

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
    implementation(kotlin("reflect", version = BuildPluginsVersion.KOTLIN))

    val coroutinesVersion = "1.6.0"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")

    // Source models and interfaces from Tachiyomi 1.x
    implementation("org.tachiyomi:source-api:1.1")

    // AndroidX libraries
    implementation("androidx.annotation:annotation:1.4.0-alpha01")
    implementation("androidx.appcompat:appcompat:1.4.1")
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha04")
    implementation("androidx.browser:browser:1.4.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.3")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.core:core-ktx:1.8.0-alpha02")
    implementation("androidx.core:core-splashscreen:1.0.0-alpha02")
    implementation("androidx.recyclerview:recyclerview:1.3.0-alpha01")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0-alpha01")
    implementation("androidx.viewpager:viewpager:1.1.0-alpha01")

    val lifecycleVersion = "2.4.0"
    implementation("androidx.lifecycle:lifecycle-common:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")

    // Job scheduling
    implementation("androidx.work:work-runtime-ktx:2.6.0")

    // RX
    implementation("io.reactivex:rxandroid:1.2.1")
    implementation("io.reactivex:rxjava:1.3.8")
    implementation("com.jakewharton.rxrelay:rxrelay:1.2.0")
    implementation("ru.beryukhov:flowreactivenetwork:1.0.4")

    // Network client
    val okhttpVersion = "4.9.1"
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:$okhttpVersion")
    implementation("com.squareup.okio:okio:3.0.0")

    // TLS 1.3 support for Android < 10
    implementation("org.conscrypt:conscrypt-android:2.5.2")

    // Data serialization (JSON, protobuf)
    val kotlinSerializationVersion = "1.3.2"
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinSerializationVersion")

    // JavaScript engine
    implementation("app.cash.quickjs:quickjs-android:0.9.2")
    // TODO: remove Duktape once all extensions are using QuickJS
    implementation("com.squareup.duktape:duktape-android:1.4.0")

    // HTML parser
    implementation("org.jsoup:jsoup:1.14.3")

    // Disk
    implementation("com.jakewharton:disklrucache:2.0.2")
    implementation("com.github.tachiyomiorg:unifile:17bec43")
    implementation("com.github.junrar:junrar:7.4.0")

    // Database
    implementation("androidx.sqlite:sqlite-ktx:2.2.0")
    implementation("com.github.inorichi.storio:storio-common:8be19de@aar")
    implementation("com.github.inorichi.storio:storio-sqlite:8be19de@aar")
    implementation("com.github.requery:sqlite-android:3.36.0")

    // Preferences
    implementation("androidx.preference:preference-ktx:1.2.0-rc01")
    implementation("com.github.tfcporciuncula.flow-preferences:flow-preferences:1.4.0")

    // Model View Presenter
    val nucleusVersion = "3.0.0"
    implementation("info.android15.nucleus:nucleus:$nucleusVersion")
    implementation("info.android15.nucleus:nucleus-support-v7:$nucleusVersion")

    // Dependency injection
    implementation("com.github.inorichi.injekt:injekt-core:65b0440")

    // Image loading
    val coilVersion = "1.4.0"
    implementation("io.coil-kt:coil:$coilVersion")
    implementation("io.coil-kt:coil-gif:$coilVersion")

    implementation("com.github.tachiyomiorg:subsampling-scale-image-view:846abe0") {
        exclude(module = "image-decoder")
    }
    implementation("com.github.tachiyomiorg:image-decoder:7481a4a")

    // Sort
    implementation("com.github.gpanther:java-nat-sort:natural-comparator-1.1")

    // UI libraries
    implementation("com.google.android.material:material:1.6.0-alpha02")
    implementation("com.github.dmytrodanylyk.android-process-button:library:1.0.4")
    implementation("com.github.arkon.FlexibleAdapter:flexible-adapter:c8013533")
    implementation("com.github.arkon.FlexibleAdapter:flexible-adapter-ui:c8013533")
    implementation("com.nightlynexus.viewstatepageradapter:viewstatepageradapter:1.1.0")
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    implementation("com.github.tachiyomiorg:DirectionalViewPager:1.0.0") {
        exclude(group = "androidx.viewpager", module = "viewpager")
    }
    implementation("dev.chrisbanes.insetter:insetter:0.6.1")

    // Conductor
    val conductorVersion = "3.1.2"
    implementation("com.bluelinelabs:conductor:$conductorVersion")
    implementation("com.bluelinelabs:conductor-viewpager:$conductorVersion")
    implementation("com.github.tachiyomiorg:conductor-support-preference:$conductorVersion")

    // FlowBinding
    val flowbindingVersion = "1.2.0"
    implementation("io.github.reactivecircus.flowbinding:flowbinding-android:$flowbindingVersion")
    implementation("io.github.reactivecircus.flowbinding:flowbinding-appcompat:$flowbindingVersion")
    implementation("io.github.reactivecircus.flowbinding:flowbinding-recyclerview:$flowbindingVersion")
    implementation("io.github.reactivecircus.flowbinding:flowbinding-swiperefreshlayout:$flowbindingVersion")
    implementation("io.github.reactivecircus.flowbinding:flowbinding-viewpager:$flowbindingVersion")

    // Logging
    implementation("com.squareup.logcat:logcat:0.1")

    // Crash reports/analytics
    implementation("ch.acra:acra-http:5.8.4")
    "standardImplementation"("com.google.firebase:firebase-analytics-ktx:20.0.2")

    // Licenses
    implementation("com.mikepenz:aboutlibraries-core:${BuildPluginsVersion.ABOUTLIB_PLUGIN}")

    // Shizuku
    val shizukuVersion = "12.1.0"
    implementation("dev.rikka.shizuku:api:$shizukuVersion")
    implementation("dev.rikka.shizuku:provider:$shizukuVersion")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.assertj:assertj-core:3.16.1")
    testImplementation("org.mockito:mockito-core:1.10.19")

    val robolectricVersion = "3.1.4"
    testImplementation("org.robolectric:robolectric:$robolectricVersion")
    testImplementation("org.robolectric:shadows-play-services:$robolectricVersion")

    // For detecting memory leaks; see https://square.github.io/leakcanary/
    // debugImplementation("com.squareup.leakcanary:leakcanary-android:2.7")
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
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(kotlin("gradle-plugin", version = BuildPluginsVersion.KOTLIN))
    }
}


// Git is needed in your system PATH for these commands to work.
// If it's not installed, you can return a random value as a workaround
fun getCommitCount(): String {
    return runCommand("git rev-list --count HEAD")
    // return "1"
}

fun getGitSha(): String {
    return runCommand("git rev-parse --short HEAD")
    // return "1"
}

fun getBuildTime(): String {
    val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'")
    df.timeZone = TimeZone.getTimeZone("UTC")
    return df.format(Date())
}

fun runCommand(command: String): String {
    val byteOut = ByteArrayOutputStream()
    project.exec {
        commandLine = command.split(" ")
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim()
}
