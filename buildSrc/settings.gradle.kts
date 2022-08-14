dependencyResolutionManagement {
    versionCatalogs {
        create("kotlinLibs") {
            from(files("../gradle/kotlinx.versions.toml"))
        }
    }
}
