package eu.kanade.tachiyomi.util.system

import eu.kanade.tachiyomi.BuildConfig

val isDevFlavor: Boolean
    get() = BuildConfig.FLAVOR == "dev"
