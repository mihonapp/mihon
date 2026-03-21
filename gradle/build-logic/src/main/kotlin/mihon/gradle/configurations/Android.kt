package mihon.gradle.configurations

import com.android.build.api.dsl.ApplicationDefaultConfig
import mihon.gradle.extensions.android
import mihon.gradle.extensions.coreLibraryDesugaring
import mihon.gradle.extensions.libs
import mihon.gradle.extensions.mihonx
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

fun Project.configureAndroid() {
    android {
        defaultConfig {
            minSdk = mihonx.versions.android.sdk.min.get().toInt()
            if (this is ApplicationDefaultConfig) {
                targetSdk = mihonx.versions.android.sdk.target.get().toInt()
            }

            ndkVersion = mihonx.versions.android.ndk.get()
        }

        compileSdk = mihonx.versions.android.sdk.compile.get().toInt()

        compileOptions {
            isCoreLibraryDesugaringEnabled = true
        }
    }

    dependencies {
        coreLibraryDesugaring(libs.android.desugar)
    }
}
