package eu.kanade.tachiyomi

/** Version identity used by source request headers; never impersonates an Android release. */
object AppInfo {
    fun getVersionName(): String =
        System.getProperty("mihon.version") ?: AppInfo::class.java.`package`.implementationVersion ?: "MihonW"
    fun getVersionCode(): Int = System.getProperty("mihon.versionCode")?.toIntOrNull() ?: 0
}
