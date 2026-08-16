package mihon.gradle.extensions

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.DependencyHandlerScope

fun DependencyHandlerScope.api(dependencyNotation: Provider<MinimalExternalModuleDependency>) {
    add("api", dependencyNotation)
}

fun DependencyHandlerScope.coreLibraryDesugaring(dependencyNotation: Provider<MinimalExternalModuleDependency>) {
    add("coreLibraryDesugaring", dependencyNotation)
}

fun DependencyHandlerScope.debugApi(dependencyNotation: Provider<MinimalExternalModuleDependency>) {
    add("debugApi", dependencyNotation)
}

@JvmName("implementationBundle")
fun DependencyHandlerScope.implementation(dependencyNotation: Provider<ExternalModuleDependencyBundle>) {
    add("implementation", dependencyNotation)
}

fun DependencyHandlerScope.implementation(dependencyNotation: Provider<MinimalExternalModuleDependency>) {
    add("implementation", dependencyNotation)
}

fun DependencyHandlerScope.implementation(dependencyNotation: Project) {
    add("implementation", dependencyNotation)
}
