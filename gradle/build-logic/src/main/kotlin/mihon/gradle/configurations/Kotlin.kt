package mihon.gradle.configurations

import com.gradleup.tapmoc.configureJavaCompatibility
import mihon.gradle.extensions.mihonx
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions

fun Project.configureKotlin() {
    configureJavaCompatibility(mihonx.versions.java.get().toInt())
}

@Suppress("Unused")
private fun Project.kotlin(block: KotlinBaseExtension.() -> Unit) {
    extensions.configure(block)
}

@Suppress("Unused")
private fun KotlinBaseExtension.compilerOptions(block: KotlinCommonCompilerOptions.() -> Unit) {
    if (this is HasConfigurableKotlinCompilerOptions<*>) compilerOptions(block)
}
