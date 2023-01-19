dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
        create("androidxLibs") {
            from(files("../gradle/androidx.versions.toml"))
        }
        create("kotlinLibs") {
            from(files("../gradle/kotlinx.versions.toml"))
        }
    }
}
