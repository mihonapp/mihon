package eu.kanade.tachiyomi.extension.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.domain.extension.interactor.TrustExtension
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.util.lang.Hash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.app.di.appGraph
import mihon.data.dalvik.extensionClassLoader
import mihon.domain.extension.model.ContentWarning
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Class that handles the loading of the extensions. Supports two kinds of extensions:
 *
 * 1. Shared extension: This extension is installed to the system with package
 * installer, so other variants of Tachiyomi and its forks can also use this extension.
 *
 * 2. Private extension: This extension is put inside private data directory of the
 * running app, so this extension can only be used by the running app and not shared
 * with other apps.
 *
 * When both kinds of extensions are installed with a same package name, shared
 * extension will be used unless the version codes are different. In that case the
 * one with higher version code will be used.
 */
internal object ExtensionLoader {

    private const val EXTENSION_FEATURE = "tachiyomi.extension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    private const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
    private const val METADATA_NSFW = "tachiyomi.extension.nsfw"

    private const val METADATA_NAME = "tachiyomix.name"
    private const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
    private const val METADATA_CONTENT_WARNING = "tachiyomix.contentWarning"

    private val SUPPORTED_LIB_VERSIONS = listOf(1.4, 1.6)

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private const val PRIVATE_EXTENSION_EXTENSION = "ext"

    /** One lock per package name, since the staged and target files are named after it. */
    private val packageLocks = ConcurrentHashMap<String, Any>()

    private fun packageLock(pkgName: String): Any {
        // putIfAbsent keeps two concurrent installs of the same package on one lock
        val candidate = Any()
        return packageLocks.putIfAbsent(pkgName, candidate) ?: candidate
    }

    private fun getPrivateExtensionDir(context: Context) = File(context.filesDir, "exts")

    /**
     * A process can die while an install is staged, so leftovers of an interrupted one are cleaned up
     * (or, for a `.previous` file, restored) before the directory is read.
     */
    private fun recoverStagedExtensions(dir: File) {
        dir.listFiles()?.forEach { file ->
            val name = file.name
            when {
                name.endsWith(".$PRIVATE_EXTENSION_EXTENSION.part") -> file.delete()
                name.endsWith(".$PRIVATE_EXTENSION_EXTENSION.previous") -> {
                    val target = File(dir, name.removeSuffix(".previous"))
                    if (target.exists()) {
                        file.delete()
                    } else if (!file.renameTo(target)) {
                        logcat(LogPriority.WARN) { "Unable to recover ${file.name}" }
                    }
                }
            }
        }
    }

    /**
     * Reads what an extension declares without running any of its code: no class loader, no
     * `Class.forName`, no instantiation. That's what makes it safe to call before the user has
     * accepted the signature, which is exactly when the install flow needs it.
     */
    fun inspect(context: Context, file: File): InspectResult {
        if (!file.isFile) return InspectResult.Rejected("The extension file doesn't exist")

        val pkgInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, PACKAGE_FLAGS)
            ?.also { it.applicationInfo?.fixBasePaths(file.absolutePath) }
            ?: return InspectResult.Rejected("The file isn't a readable APK")

        return inspect(context, pkgInfo)
    }

    /**
     * The same checks, for a package that's already installed rather than read from a file.
     */
    private fun inspect(context: Context, pkgInfo: PackageInfo): InspectResult {
        val pkgManager = context.packageManager

        if (!isPackageAnExtension(pkgInfo)) {
            return InspectResult.Rejected("${pkgInfo.packageName} isn't an extension")
        }

        val appInfo = pkgInfo.applicationInfo
            ?: return InspectResult.Rejected("Missing application info for ${pkgInfo.packageName}")
        val metaData = appInfo.metaData
            ?: return InspectResult.Rejected("Missing metadata for ${pkgInfo.packageName}")

        val versionName = pkgInfo.versionName
        if (versionName.isNullOrEmpty()) {
            return InspectResult.Rejected("Missing versionName for ${pkgInfo.packageName}")
        }

        val sourceClasses = metaData.getString(METADATA_SOURCE_CLASS)
            ?.split(";")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.map { if (it.startsWith(".")) pkgInfo.packageName + it else it }
            .orEmpty()
        if (sourceClasses.isEmpty()) {
            return InspectResult.Rejected("Missing source class for ${pkgInfo.packageName}")
        }

        val libVersion = metaData.getFloat(METADATA_EXTENSION_LIB)
            .takeUnless { it == 0.0f }
            ?.toString()
            ?.toDouble()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion !in SUPPORTED_LIB_VERSIONS) {
            return InspectResult.Rejected(
                "Lib version is $libVersion, while only version(s) " +
                    "${SUPPORTED_LIB_VERSIONS.joinToString()} are supported",
            )
        }

        val signatures = getSignatures(pkgInfo).orEmpty()
        if (signatures.isEmpty()) {
            return InspectResult.Rejected("Package ${pkgInfo.packageName} isn't signed")
        }

        return InspectResult.Ok(
            ExtensionInspection(
                pkgName = pkgInfo.packageName,
                name = metaData.getString(METADATA_NAME)
                    ?: pkgManager.getApplicationLabel(appInfo).toString().substringAfter("Tachiyomi: "),
                versionName = versionName,
                versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo),
                libVersion = libVersion,
                signatures = signatures,
                sourceClasses = sourceClasses,
            ),
        )
    }

    /**
     * Installs [file] as a private extension, replacing whatever version is already there.
     *
     * Every check runs against a staged copy instead of the incoming file, so a file swapped between
     * validation and install can't slip through, and the swap itself is a single rename: either the
     * previous extension or the new one, never a half written file in between.
     */
    fun installPrivateExtensionFile(context: Context, file: File) {
        val incoming = when (val result = inspect(context, file)) {
            is InspectResult.Ok -> result.inspection
            is InspectResult.Rejected -> throw IllegalStateException(result.reason)
        }

        // A package name shares its staged and target files, so two installs of it must not overlap
        synchronized(packageLock(incoming.pkgName)) {
            deployPrivateExtensionFile(context, file, incoming)
        }
    }

    private fun deployPrivateExtensionFile(
        context: Context,
        file: File,
        incoming: ExtensionInspection,
    ) {
        val dir = getPrivateExtensionDir(context).also { it.mkdirs() }
        val target = File(dir, "${incoming.pkgName}.$PRIVATE_EXTENSION_EXTENSION")
        val part = File(dir, "${target.name}.part")
        val privateExisted = target.isFile

        // What the user actually runs is the newer of the private file and a copy installed by the
        // system, so both are the baseline for the downgrade and signature checks
        val (installedPkg, installedIsShared) = effectiveInstalled(context, dir, incoming.pkgName)
        val installed = installedPkg?.let { (inspect(context, it) as? InspectResult.Ok)?.inspection }
        if (installed != null) {
            check(incoming.versionCode >= installed.versionCode) {
                if (installedIsShared) {
                    "A newer copy of ${incoming.pkgName} is installed by the system, uninstall it first"
                } else {
                    "The installed version is newer, and downgrading isn't allowed"
                }
            }
            check(incoming.signatures.containsAll(installed.signatures)) {
                "It isn't signed with the same key as the installed extension"
            }
        }

        part.delete()
        try {
            FileOutputStream(part).use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
                output.fd.sync()
            }
            // Just in case, since Android 14+ requires them to be read-only
            if (!part.setReadOnly()) {
                throw IllegalStateException("Unable to make the staged extension read-only")
            }

            validate(context, part, incoming)

            // rename(2) replaces an existing target atomically, so there's no window where neither
            // the old nor the new file is in place
            if (!part.renameTo(target)) {
                throw IllegalStateException("Unable to install the extension")
            }
            target.setReadOnly()
        } finally {
            part.delete()
        }

        if (privateExisted) {
            ExtensionInstallReceiver.notifyReplaced(context, incoming.pkgName)
        } else {
            ExtensionInstallReceiver.notifyAdded(context, incoming.pkgName)
        }
    }

    /**
     * The copy the user actually runs: whatever [selectExtensionPackage] would pick between a private
     * file and a package the system installed, plus which of the two it is.
     */
    private fun effectiveInstalled(
        context: Context,
        dir: File,
        pkgName: String,
    ): Pair<PackageInfo?, Boolean> {
        val privatePkg = File(dir, "$pkgName.$PRIVATE_EXTENSION_EXTENSION")
            .takeIf { it.isFile }
            ?.let { context.packageManager.getPackageArchiveInfo(it.absolutePath, PACKAGE_FLAGS) }
        val sharedPkg = runCatching {
            context.packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
        }.getOrNull()

        return when {
            privatePkg == null -> sharedPkg to true
            sharedPkg == null -> privatePkg to false
            PackageInfoCompat.getLongVersionCode(sharedPkg) >=
                PackageInfoCompat.getLongVersionCode(privatePkg) -> sharedPkg to true
            else -> privatePkg to false
        }
    }

    /**
     * Loads a staged file for real: the dex is read and every declared source class is instantiated,
     * so an extension that can't run is rejected before it gets to replace a working one.
     */
    private fun validate(context: Context, file: File, inspection: ExtensionInspection) {
        val classLoader = extensionClassLoader(file.absolutePath, context.classLoader)
        inspection.sourceClasses.forEach { className ->
            try {
                when (val obj = Class.forName(className, false, classLoader).getDeclaredConstructor().newInstance()) {
                    is Source -> listOf(obj)
                    is SourceFactory -> obj.createSources()
                    else -> throw Exception("Unknown source class type: ${obj.javaClass}")
                }
            } catch (e: Throwable) {
                throw IllegalStateException(
                    "Unable to instantiate extension source $className: ${e.rootMessage}",
                    e,
                )
            }
        }
    }

    fun uninstallPrivateExtension(context: Context, pkgName: String) {
        File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION").delete()
    }

    fun getArchivePackageInfo(context: Context, file: File): PackageInfo? {
        return context.packageManager.getPackageArchiveInfo(file.absolutePath, PACKAGE_FLAGS)
    }

    /**
     * Return a list of all the available extensions initialized concurrently.
     *
     * @param context The application context.
     * @param alreadyLoaded Extensions loaded by an earlier call. Any of these whose apk is unchanged
     * and which still passes every check is returned as is, so its sources keep working and its
     * update status survives. Pass nothing to load every extension from scratch.
     */
    suspend fun loadExtensions(
        context: Context,
        alreadyLoaded: Map<String, Extension.Loaded> = emptyMap(),
    ): List<Extension.Installed> {
        val trustExtension = context.appGraph.trustExtension
        val sourcePreferences = context.appGraph.sourcePreferences
        val stores = context.appGraph.extensionStoreRepository.getAllAsFlow().first()
        val enabledContentWarnings = sourcePreferences.enabledContentWarnings.get()
        val applyContentWarningsToInstalled = sourcePreferences.applyContentWarningsToInstalled.get()
        val disabledExtensions = sourcePreferences.disabledExtensions.get()

        val pkgManager = context.packageManager

        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }

        val sharedExtPkgs = installedPkgs
            .asSequence()
            .filter { isPackageAnExtension(it) }
            .map { ExtensionInfo(packageInfo = it, isShared = true) }

        val privateDir = getPrivateExtensionDir(context)
        recoverStagedExtensions(privateDir)

        val privateExtPkgs = privateDir
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == PRIVATE_EXTENSION_EXTENSION }
            ?.mapNotNull {
                // Just in case, since Android 14+ requires them to be read-only
                if (it.canWrite()) {
                    it.setReadOnly()
                }

                val path = it.absolutePath
                pkgManager.getPackageArchiveInfo(path, PACKAGE_FLAGS)
                    ?.also { pkg -> pkg.applicationInfo?.fixBasePaths(path) }
            }
            ?.filter { isPackageAnExtension(it) }
            ?.map { ExtensionInfo(packageInfo = it, isShared = false) }
            ?.toList()
            .orEmpty()
        val privateExtPkgsByName = privateExtPkgs.groupBy { it.packageInfo.packageName }

        val extPkgs = (sharedExtPkgs + privateExtPkgs)
            // Remove duplicates. Shared takes priority than private by default
            .distinctBy { it.packageInfo.packageName }
            // Compare version number
            .mapNotNull { sharedPkg ->
                val privatePkg = privateExtPkgsByName[sharedPkg.packageInfo.packageName]?.singleOrNull()
                selectExtensionPackage(sharedPkg, privatePkg)
            }
            .toList()

        if (extPkgs.isEmpty()) return emptyList()

        // Load each extension concurrently and wait for completion
        return withContext(Dispatchers.IO) {
            extPkgs
                .map {
                    async {
                        loadExtensionCatching(
                            context = context,
                            extensionInfo = it,
                            trustExtension = trustExtension,
                            stores = stores,
                            enabledContentWarnings = enabledContentWarnings,
                            applyContentWarningsToInstalled = applyContentWarningsToInstalled,
                            disabledExtensions = disabledExtensions,
                            alreadyLoaded = alreadyLoaded[it.packageInfo.packageName],
                        )
                    }
                }
                .awaitAll()
        }
    }

    /**
     * Attempts to load an extension from the given package name. It checks if the extension
     * contains the required feature flag before trying to load it.
     */
    suspend fun loadExtensionFromPkgName(context: Context, pkgName: String): Extension.Installed? {
        val extensionPackage = getExtensionInfoFromPkgName(context, pkgName)
        if (extensionPackage == null) {
            logcat(LogPriority.ERROR) { "Extension package is not found ($pkgName)" }
            return null
        }

        val sourcePreferences = context.appGraph.sourcePreferences
        return loadExtensionCatching(
            context = context,
            extensionInfo = extensionPackage,
            trustExtension = context.appGraph.trustExtension,
            stores = context.appGraph.extensionStoreRepository.getAll(),
            enabledContentWarnings = sourcePreferences.enabledContentWarnings.get(),
            applyContentWarningsToInstalled = sourcePreferences.applyContentWarningsToInstalled.get(),
            disabledExtensions = sourcePreferences.disabledExtensions.get(),
        )
    }

    fun getExtensionPackageInfoFromPkgName(context: Context, pkgName: String): PackageInfo? {
        return getExtensionInfoFromPkgName(context, pkgName)?.packageInfo
    }

    private fun getExtensionInfoFromPkgName(context: Context, pkgName: String): ExtensionInfo? {
        val privateExtensionFile = File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION")
        val privatePkg = if (privateExtensionFile.isFile) {
            context.packageManager.getPackageArchiveInfo(privateExtensionFile.absolutePath, PACKAGE_FLAGS)
                ?.takeIf { isPackageAnExtension(it) }
                ?.let {
                    it.applicationInfo?.fixBasePaths(privateExtensionFile.absolutePath)
                    ExtensionInfo(
                        packageInfo = it,
                        isShared = false,
                    )
                }
        } else {
            null
        }

        val sharedPkg = try {
            context.packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
                .takeIf { isPackageAnExtension(it) }
                ?.let {
                    ExtensionInfo(
                        packageInfo = it,
                        isShared = true,
                    )
                }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

        return selectExtensionPackage(sharedPkg, privatePkg)
    }

    /**
     * [loadExtension] reports the failures it knows how to name, but an apk can be malformed in
     * ways it doesn't check for. Keep anything unforeseen to the extension that caused it instead of
     * letting it take down the load of every other extension.
     */
    private suspend fun loadExtensionCatching(
        context: Context,
        extensionInfo: ExtensionInfo,
        trustExtension: TrustExtension,
        stores: List<ExtensionStore>,
        enabledContentWarnings: Set<ContentWarning>,
        applyContentWarningsToInstalled: Boolean,
        disabledExtensions: Set<String>,
        alreadyLoaded: Extension.Loaded? = null,
    ): Extension.Installed {
        return try {
            loadExtension(
                context = context,
                extensionInfo = extensionInfo,
                trustExtension = trustExtension,
                stores = stores,
                enabledContentWarnings = enabledContentWarnings,
                applyContentWarningsToInstalled = applyContentWarningsToInstalled,
                disabledExtensions = disabledExtensions,
                alreadyLoaded = alreadyLoaded,
            )
        } catch (e: Throwable) {
            val pkgInfo = extensionInfo.packageInfo
            logcat(LogPriority.ERROR, e) { "Extension load error: ${pkgInfo.packageName}" }
            val signatures = getSignatures(pkgInfo).orEmpty()
            Extension.NotLoaded(
                name = pkgInfo.packageName,
                pkgName = pkgInfo.packageName,
                versionName = pkgInfo.versionName.orEmpty(),
                versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo),
                isShared = extensionInfo.isShared,
                contentWarning = ContentWarning.SAFE,
                signatures = signatures,
                store = stores.firstOrNull { it.signingKey in signatures },
                reason = Extension.NotLoaded.Reason.Failed(e.rootMessage, e.stackTraceToString()),
            )
        }
    }

    /**
     * Loads an extension
     *
     * @param context The application context.
     * @param extensionInfo The extension to load.
     */
    private suspend fun loadExtension(
        context: Context,
        extensionInfo: ExtensionInfo,
        trustExtension: TrustExtension,
        stores: List<ExtensionStore>,
        enabledContentWarnings: Set<ContentWarning>,
        applyContentWarningsToInstalled: Boolean,
        disabledExtensions: Set<String>,
        alreadyLoaded: Extension.Loaded? = null,
    ): Extension.Installed {
        val pkgManager = context.packageManager
        val pkgInfo = extensionInfo.packageInfo
        val appInfo = pkgInfo.applicationInfo
        val metaData = appInfo?.metaData
        val pkgName = pkgInfo.packageName

        val extName = metaData?.getString(METADATA_NAME)
            ?: appInfo?.let { pkgManager.getApplicationLabel(it).toString().substringAfter("Tachiyomi: ") }
            ?: pkgName
        val versionName = pkgInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
        val signatures = getSignatures(pkgInfo).orEmpty()
        val store = stores.firstOrNull { it.signingKey in signatures }
        val contentWarning = when {
            metaData == null -> ContentWarning.SAFE
            metaData.containsKey(METADATA_CONTENT_WARNING) -> {
                when (metaData.getInt(METADATA_CONTENT_WARNING)) {
                    1 -> ContentWarning.MIXED
                    2 -> ContentWarning.NSFW
                    else -> ContentWarning.SAFE
                }
            }
            metaData.getInt(METADATA_NSFW) == 1 -> ContentWarning.NSFW
            else -> ContentWarning.SAFE
        }

        val isEnabled = pkgName !in disabledExtensions

        fun notLoaded(
            reason: Extension.NotLoaded.Reason,
            libVersion: Double? = null,
        ) = Extension.NotLoaded(
            name = extName,
            pkgName = pkgName,
            versionName = versionName.orEmpty(),
            versionCode = versionCode,
            isShared = extensionInfo.isShared,
            contentWarning = contentWarning,
            signatures = signatures,
            store = store,
            libVersion = libVersion,
            isEnabled = isEnabled,
            reason = reason,
        )

        if (appInfo == null || metaData == null) {
            logcat(LogPriority.WARN) { "Missing application info for extension $extName" }
            return notLoaded(Extension.NotLoaded.Reason.Malformed)
        }

        if (versionName.isNullOrEmpty()) {
            logcat(LogPriority.WARN) { "Missing versionName for extension $extName" }
            return notLoaded(Extension.NotLoaded.Reason.Malformed)
        }

        // Validate lib version
        val libVersion = metaData.getFloat(METADATA_EXTENSION_LIB)
            .takeUnless { it == 0.0f }
            ?.toString()
            ?.toDouble()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion !in SUPPORTED_LIB_VERSIONS) {
            logcat(LogPriority.WARN) {
                "Lib version is $libVersion, while only version(s) ${SUPPORTED_LIB_VERSIONS.joinToString()} are supported"
            }
            return notLoaded(Extension.NotLoaded.Reason.UnsupportedLibVersion, libVersion)
        }

        if (signatures.isEmpty()) {
            logcat(LogPriority.WARN) { "Package $pkgName isn't signed" }
            return notLoaded(Extension.NotLoaded.Reason.Unsigned, libVersion)
        } else if (!trustExtension.isTrusted(pkgInfo, signatures, stores)) {
            logcat(LogPriority.WARN) { "Extension $pkgName isn't trusted" }
            return notLoaded(Extension.NotLoaded.Reason.Untrusted(signatures.last()), libVersion)
        }

        if (applyContentWarningsToInstalled && contentWarning !in enabledContentWarnings) {
            logcat(LogPriority.WARN) { "Extension $pkgName with $contentWarning not allowed" }
            return notLoaded(Extension.NotLoaded.Reason.Filtered, libVersion)
        }

        // A switched off extension is deployed but deliberately not run: stop here, so neither the
        // class loader nor any of its source instances are created while it stays off
        if (!isEnabled) {
            return notLoaded(Extension.NotLoaded.Reason.Disabled, libVersion)
        }

        // Everything above is cheap to check again, everything below isn't. Nothing about this apk
        // changed and it still passes, so keep the sources that are already registered for it.
        if (alreadyLoaded != null &&
            alreadyLoaded.versionCode == versionCode &&
            alreadyLoaded.isShared == extensionInfo.isShared
        ) {
            return alreadyLoaded.copy(store = store)
        }

        val classLoader = try {
            extensionClassLoader(appInfo.sourceDir, context.classLoader)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($pkgName)" }
            return notLoaded(Extension.NotLoaded.Reason.Failed(e.rootMessage, e.stackTraceToString()), libVersion)
        }

        val sourceClasses = metaData.getString(METADATA_SOURCE_CLASS)
        if (sourceClasses.isNullOrBlank()) {
            logcat(LogPriority.WARN) { "Missing source class for extension $extName" }
            return notLoaded(Extension.NotLoaded.Reason.Malformed, libVersion)
        }

        val sources = sourceClasses
            .split(";")
            .map {
                val sourceClass = it.trim()
                if (sourceClass.startsWith(".")) {
                    pkgInfo.packageName + sourceClass
                } else {
                    sourceClass
                }
            }
            .flatMap {
                try {
                    when (val obj = Class.forName(it, false, classLoader).getDeclaredConstructor().newInstance()) {
                        is Source -> listOf(obj)
                        is SourceFactory -> obj.createSources()
                        else -> throw Exception("Unknown source class type: ${obj.javaClass}")
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($it)" }
                    return notLoaded(
                        Extension.NotLoaded.Reason.Failed(e.rootMessage, e.stackTraceToString()),
                        libVersion,
                    )
                }
            }

        val langs = sources.map { it.lang }.toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        return Extension.Loaded(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            contentWarning = contentWarning,
            sources = sources,
            pkgFactory = metaData.getString(METADATA_SOURCE_FACTORY),
            isShared = extensionInfo.isShared,
            isEnabled = isEnabled,
            signatures = signatures,
            store = store,
        )
    }

    /**
     * Choose which extension package to use based on version code
     *
     * @param shared extension installed to system
     * @param private extension installed to data directory
     */
    private fun selectExtensionPackage(shared: ExtensionInfo?, private: ExtensionInfo?): ExtensionInfo? {
        when {
            private == null && shared != null -> return shared
            shared == null && private != null -> return private
            shared == null && private == null -> return null
        }

        return if (PackageInfoCompat.getLongVersionCode(shared!!.packageInfo) >=
            PackageInfoCompat.getLongVersionCode(private!!.packageInfo)
        ) {
            shared
        } else {
            private
        }
    }

    /**
     * Returns true if the given package is an extension.
     *
     * @param pkgInfo The package info of the application.
     */
    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
    }

    /**
     * Returns the signatures of the package or null if it's not signed.
     *
     * @param pkgInfo The package info of the application.
     * @return List SHA256 digest of the signatures
     */
    fun getSignatures(pkgInfo: PackageInfo): List<String>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo
            when {
                signingInfo == null -> null
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                else -> signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }
            ?.map { Hash.sha256(it.toByteArray()) }
            ?.toList()
    }

    /**
     * On Android 13+ the ApplicationInfo generated by getPackageArchiveInfo doesn't
     * have sourceDir which breaks assets loading (used for getting icon here).
     */
    private fun ApplicationInfo.fixBasePaths(apkPath: String) {
        if (sourceDir == null) {
            sourceDir = apkPath
        }
        if (publicSourceDir == null) {
            publicSourceDir = apkPath
        }
    }

    private data class ExtensionInfo(
        val packageInfo: PackageInfo,
        val isShared: Boolean,
    )
}

/**
 * What an extension declares in its manifest, read without running any of its code.
 */
internal data class ExtensionInspection(
    val pkgName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val libVersion: Double,
    /** SHA-256 digests of the certificates its apk is signed with. */
    val signatures: List<String>,
    val sourceClasses: List<String>,
)

internal sealed interface InspectResult {
    data class Ok(val inspection: ExtensionInspection) : InspectResult

    /** Why the file can't be installed, phrased for the user who picked it. */
    data class Rejected(val reason: String) : InspectResult
}

/**
 * The message of the deepest cause, which is the one that actually says what went wrong.
 */
private val Throwable.rootMessage: String
    get() {
        val root = generateSequence(this) { it.cause }.last()
        return listOfNotNull(root::class.simpleName, root.message).joinToString(": ")
    }
