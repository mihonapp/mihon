package eu.kanade.tachiyomi.util.system

import eu.kanade.tachiyomi.BuildConfig

val isDevFlavor: Boolean
    get() = BuildConfig.FLAVOR == "dev"

val isPreviewBuildType: Boolean
    get() = BuildConfig.BUILD_TYPE == "preview"

val isReleaseBuildType: Boolean
    get() = BuildConfig.BUILD_TYPE == "release"
