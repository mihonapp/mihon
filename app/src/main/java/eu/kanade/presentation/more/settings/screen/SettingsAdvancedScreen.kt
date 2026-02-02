package eu.kanade.presentation.more.settings.screen

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.interactor.TrustExtension
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.advanced.ClearDatabaseScreen
import eu.kanade.presentation.more.settings.screen.debug.DebugInfoScreen
import tachiyomi.domain.manga.model.MangaUpdate
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.library.MetadataUpdateJob
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.PREF_DOH_360
import eu.kanade.tachiyomi.network.PREF_DOH_ADGUARD
import eu.kanade.tachiyomi.network.PREF_DOH_ALIDNS
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.network.PREF_DOH_CONTROLD
import eu.kanade.tachiyomi.network.PREF_DOH_DNSPOD
import eu.kanade.tachiyomi.network.PREF_DOH_GOOGLE
import eu.kanade.tachiyomi.network.PREF_DOH_MULLVAD
import eu.kanade.tachiyomi.network.PREF_DOH_NJALLA
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD101
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD9
import eu.kanade.tachiyomi.network.PREF_DOH_SHECAN
import eu.kanade.tachiyomi.ui.more.OnboardingScreen
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.system.GLUtil
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.isReleaseBuildType
import eu.kanade.tachiyomi.util.system.isShizukuInstalled
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import logcat.LogPriority
import okhttp3.Headers
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.ResetViewerFlags
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.translation.repository.TranslatedChapterRepository
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

object SettingsAdvancedScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_advanced

    @Composable
    override fun getPreferences(): List<Preference> {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val basePreferences = remember { Injekt.get<BasePreferences>() }
        val networkPreferences = remember { Injekt.get<NetworkPreferences>() }
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }

        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_dump_crash_logs),
                subtitle = stringResource(MR.strings.pref_dump_crash_logs_summary),
                onClick = {
                    scope.launch {
                        CrashLogUtil(context).dumpLogs()
                    }
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = networkPreferences.verboseLogging(),
                title = stringResource(MR.strings.pref_verbose_logging),
                subtitle = stringResource(MR.strings.pref_verbose_logging_summary),
                onValueChanged = {
                    context.toast(MR.strings.requires_app_restart)
                    true
                },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_debug_info),
                onClick = { navigator.push(DebugInfoScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_onboarding_guide),
                onClick = { navigator.push(OnboardingScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_manage_notifications),
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                },
            ),
            getBackgroundActivityGroup(),
            getDataGroup(),
            getNetworkGroup(networkPreferences = networkPreferences),
            getLibraryGroup(libraryPreferences = libraryPreferences),
            getReaderGroup(basePreferences = basePreferences),
            getExtensionsGroup(basePreferences = basePreferences),
        )
    }

    @Composable
    private fun getBackgroundActivityGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_background_activity),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_disable_battery_optimization),
                    subtitle = stringResource(MR.strings.pref_disable_battery_optimization_summary),
                    onClick = {
                        val packageName: String = context.packageName
                        if (!context.powerManager.isIgnoringBatteryOptimizations(packageName)) {
                            try {
                                @SuppressLint("BatteryLife")
                                val intent = Intent().apply {
                                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                    data = "package:$packageName".toUri()
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                context.toast(MR.strings.battery_optimization_setting_activity_not_found)
                            }
                        } else {
                            context.toast(MR.strings.battery_optimization_disabled)
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Don't kill my app!",
                    subtitle = stringResource(MR.strings.about_dont_kill_my_app),
                    onClick = { uriHandler.openUri("https://dontkillmyapp.com/") },
                ),
            ),
        )
    }

    @Composable
    private fun getDataGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        var showDeleteTranslationsDialog by remember { mutableStateOf(false) }
        var showNormalizeUrlsDialog by remember { mutableStateOf(false) }
        var removeDoubleSlashes by remember { mutableStateOf(true) }
        var duplicateUrls by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
        var showDuplicatesDialog by remember { mutableStateOf(false) }
        var showMoveToCategoryDialog by remember { mutableStateOf(false) }
        var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
        var isDeleting by remember { mutableStateOf(false) }

        // Load categories when dialog is shown
        if (showMoveToCategoryDialog && categories.isEmpty()) {
            scope.launch {
                categories = Injekt.get<GetCategories>().await()
            }
        }

        if (showDeleteTranslationsDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteTranslationsDialog = false },
                title = { Text(text = "Delete all translations") },
                text = {
                    Text(text = "Are you sure you want to delete all downloaded translations? This cannot be undone.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                Injekt.get<TranslatedChapterRepository>().deleteAll()
                                context.toast("All translations deleted")
                                showDeleteTranslationsDialog = false
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteTranslationsDialog = false }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        // Move to category dialog
        if (showMoveToCategoryDialog && categories.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { showMoveToCategoryDialog = false },
                title = { Text(text = "Move Duplicates to Category") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(text = "Select a category to move ${duplicateUrls.size} duplicates to:")
                        Spacer(modifier = Modifier.height(8.dp))
                        categories.forEach { category ->
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val mangaRepo = Injekt.get<MangaRepository>()
                                        val setCategories = Injekt.get<SetMangaCategories>()
                                        
                                        // Get all favorites once to avoid n+1 queries
                                        val favorites = mangaRepo.getFavorites()
                                        val urlToManga = favorites.associateBy { it.url }
                                        
                                        // Collect all manga IDs to move
                                        val mangaIds = duplicateUrls.mapNotNull { (_, oldUrl, _) ->
                                            urlToManga[oldUrl]?.id
                                        }
                                        
                                        // Batch set categories (if supported) or do sequentially
                                        mangaIds.forEach { mangaId ->
                                            setCategories.await(mangaId, listOf(category.id))
                                        }
                                        
                                        context.toast("Moved ${mangaIds.size} novels to ${category.name}")
                                        showMoveToCategoryDialog = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = category.name)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showMoveToCategoryDialog = false }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        if (showDuplicatesDialog && duplicateUrls.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { showDuplicatesDialog = false },
                title = { Text(text = "Duplicate URLs Found (${duplicateUrls.size})") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(text = "The following novels would have duplicate URLs after normalization:")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        isDeleting = true
                                        val mangaRepo = Injekt.get<MangaRepository>()
                                        // Get all favorites once to avoid n+1 queries
                                        val favorites = mangaRepo.getFavorites()
                                        val urlToManga = favorites.associateBy { it.url }
                                        
                                        // Collect all updates first
                                        val updates = duplicateUrls.mapNotNull { (_, oldUrl, _) ->
                                            urlToManga[oldUrl]?.let { manga ->
                                                MangaUpdate(id = manga.id, favorite = false)
                                            }
                                        }
                                        
                                        // Batch update all at once
                                        if (updates.isNotEmpty()) {
                                            mangaRepo.updateAll(updates)
                                        }
                                        
                                        context.toast("Deleted ${updates.size} duplicate novels")
                                        isDeleting = false
                                        duplicateUrls = emptyList()
                                        showDuplicatesDialog = false
                                    }
                                },
                                enabled = !isDeleting,
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                                Text(text = "Delete All")
                            }
                            TextButton(
                                onClick = { showMoveToCategoryDialog = true },
                                enabled = !isDeleting,
                            ) {
                                Icon(Icons.Filled.DriveFileMove, contentDescription = null)
                                Text(text = "Move to Category")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        duplicateUrls.take(20).forEach { (title, oldUrl, newUrl) ->
                            Text(
                                text = "• $title\n  $oldUrl → $newUrl",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (duplicateUrls.size > 20) {
                            Text(text = "... and ${duplicateUrls.size - 20} more")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDuplicatesDialog = false }) {
                        Text(text = "OK")
                    }
                },
            )
        }

        if (showNormalizeUrlsDialog) {
            AlertDialog(
                onDismissRequest = { showNormalizeUrlsDialog = false },
                title = { Text(text = "Normalize manga URLs") },
                text = {
                    Column {
                        Text(text = "This will clean up manga URLs in your library:")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "• Remove trailing slashes")
                        Text(text = "• Remove URL fragments (#...)")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = removeDoubleSlashes,
                                onCheckedChange = { removeDoubleSlashes = it },
                            )
                            Text(text = "Remove double slashes (//)")
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val result = Injekt.get<tachiyomi.domain.manga.repository.MangaRepository>()
                                    .normalizeAllUrlsAdvanced(removeDoubleSlashes)
                                duplicateUrls = result.second
                                if (duplicateUrls.isNotEmpty()) {
                                    showDuplicatesDialog = true
                                }
                                context.toast("Normalized ${result.first} manga URLs")
                                showNormalizeUrlsDialog = false
                            }
                        },
                    ) {
                        Text(text = "Normalize")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNormalizeUrlsDialog = false }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_data),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_invalidate_download_cache),
                    subtitle = stringResource(MR.strings.pref_invalidate_download_cache_summary),
                    onClick = {
                        Injekt.get<DownloadCache>().invalidateCache()
                        context.toast(MR.strings.download_cache_invalidated)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_database),
                    subtitle = stringResource(MR.strings.pref_clear_database_summary),
                    onClick = { navigator.push(ClearDatabaseScreen()) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Database statistics",
                    subtitle = "Show detailed database size breakdown",
                    onClick = {
                        scope.launch {
                            try {
                                val handler = Injekt.get<DatabaseHandler>() as? AndroidDatabaseHandler
                                    ?: throw Exception("DatabaseHandler is not AndroidDatabaseHandler")
                                val stats = handler.getDetailedDatabaseStats()
                                
                                val totalSize = (stats["total_size_bytes"] as? Long) ?: 0L
                                val freelistSize = (stats["freelist_size_bytes"] as? Long) ?: 0L
                                @Suppress("UNCHECKED_CAST")
                                val tableCounts = (stats["table_row_counts"] as? Map<String, Long>) ?: emptyMap()
                                @Suppress("UNCHECKED_CAST")
                                val tableSizes = (stats["table_sizes_bytes"] as? Map<String, Long>) ?: emptyMap()
                                @Suppress("UNCHECKED_CAST")
                                val indexSizes = (stats["index_sizes_bytes"] as? Map<String, Long>) ?: emptyMap()
                                val avgChapterBytes = (stats["avg_chapter_text_bytes"] as? Double) ?: 0.0
                                val avgDescBytes = (stats["avg_manga_description_bytes"] as? Double) ?: 0.0
                                
                                fun formatSize(bytes: Long): String = when {
                                    bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
                                    bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
                                    bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
                                    else -> "$bytes B"
                                }
                                
                                val report = buildString {
                                    appendLine("=== Database Statistics ===")
                                    appendLine("Total size: ${formatSize(totalSize)}")
                                    appendLine("Freelist (reclaimable): ${formatSize(freelistSize)}")
                                    appendLine()
                                    appendLine("--- Row Counts ---")
                                    tableCounts.entries.sortedByDescending { it.value }.forEach { (table, count) ->
                                        appendLine("$table: ${"%,d".format(count)}")
                                    }
                                    if (tableSizes.isNotEmpty()) {
                                        appendLine()
                                        appendLine("--- Table Sizes ---")
                                        tableSizes.entries.sortedByDescending { it.value }.forEach { (table, size) ->
                                            appendLine("$table: ${formatSize(size)}")
                                        }
                                    }
                                    if (indexSizes.isNotEmpty()) {
                                        appendLine()
                                        appendLine("--- Index Sizes ---")
                                        val totalIndexSize = indexSizes.values.sum()
                                        appendLine("Total indexes: ${formatSize(totalIndexSize)}")
                                        indexSizes.entries.sortedByDescending { it.value }.take(10).forEach { (idx, size) ->
                                            appendLine("$idx: ${formatSize(size)}")
                                        }
                                    }
                                    appendLine()
                                    appendLine("--- Averages ---")
                                    appendLine("Avg chapter text: %.1f bytes".format(avgChapterBytes))
                                    appendLine("Avg manga description: %.1f bytes".format(avgDescBytes))
                                    
                                    // Size estimation breakdown
                                    val chapterCount = tableCounts["chapters"] ?: 0L
                                    val mangaCount = tableCounts["mangas"] ?: 0L
                                    if (chapterCount > 0) {
                                        appendLine()
                                        appendLine("--- Estimated Breakdown ---")
                                        // Each chapter row ~= fixed columns (~80 bytes) + text data
                                        val estChapterRowSize = 80 + avgChapterBytes
                                        val estChapterTableSize = (chapterCount * estChapterRowSize).toLong()
                                        appendLine("Chapters data: ~${formatSize(estChapterTableSize)}")
                                        
                                        // 4 indexes on chapters table, each ~16-40 bytes per row
                                        val estChapterIndexSize = chapterCount * 100 // ~100 bytes per row for all indexes
                                        appendLine("Chapter indexes: ~${formatSize(estChapterIndexSize)}")
                                        
                                        if (avgDescBytes > 0 && mangaCount > 0) {
                                            val estMangaSize = (mangaCount * (200 + avgDescBytes)).toLong()
                                            appendLine("Manga data: ~${formatSize(estMangaSize)}")
                                        }
                                    }
                                }
                                
                                withUIContext {
                                    context.copyToClipboard("Database Stats", report)
                                    context.toast("Statistics copied to clipboard")
                                }
                                logcat(LogPriority.INFO) { report }
                            } catch (e: Exception) {
                                logcat(LogPriority.ERROR, e) { "Failed to get database stats" }
                                withUIContext {
                                    context.toast("Error: ${e.message}")
                                }
                            }
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Database maintenance",
                    subtitle = "VACUUM, REINDEX, and ANALYZE database",
                    onClick = {
                        scope.launch {
                            try {
                                val handler = Injekt.get<tachiyomi.data.DatabaseHandler>()
                                
                                // Get stats before
                                val statsBefore = handler.getDatabaseStats()
                                val sizeBefore = statsBefore["total_size_bytes"] ?: 0L
                                
                                withUIContext { context.toast("Running ANALYZE...") }
                                handler.analyze()
                                
                                withUIContext { context.toast("Running REINDEX...") }
                                handler.reindex()
                                
                                withUIContext { context.toast("Running VACUUM (this may take a while)...") }
                                handler.vacuum()
                                
                                // Get stats after
                                val statsAfter = handler.getDatabaseStats()
                                val sizeAfter = statsAfter["total_size_bytes"] ?: 0L
                                val saved = sizeBefore - sizeAfter
                                
                                val savedStr = when {
                                    saved >= 1024 * 1024 -> "%.2f MB".format(saved / (1024.0 * 1024.0))
                                    saved >= 1024 -> "%.2f KB".format(saved / 1024.0)
                                    else -> "$saved bytes"
                                }
                                
                                withUIContext {
                                    if (saved > 0) {
                                        context.toast("Database optimized! Saved $savedStr")
                                    } else {
                                        context.toast("Database optimized!")
                                    }
                                }
                            } catch (e: Exception) {
                                logcat(LogPriority.ERROR, e) { "Database maintenance failed" }
                                withUIContext {
                                    context.toast("Database maintenance failed: ${e.message}")
                                }
                            }
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Normalize manga URLs",
                    subtitle = "Removes trailing slashes from all manga URLs in database",
                    onClick = { showNormalizeUrlsDialog = true },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Delete all translations",
                    subtitle = "Deletes all downloaded translations",
                    onClick = { showDeleteTranslationsDialog = true },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Clear temp files",
                    subtitle = "Clears temporary files from app cache (epub exports, network cache, etc.)",
                    onClick = {
                        scope.launch {
                            var clearedSize = 0L
                            try {
                                // Clear network cache
                                val networkCacheDir = File(context.cacheDir, "network_cache")
                                if (networkCacheDir.exists()) {
                                    clearedSize += networkCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                                    networkCacheDir.deleteRecursively()
                                }
                                
                                // Clear epub export temp files
                                context.cacheDir.listFiles()?.filter { it.name.startsWith("epub_export_") }?.forEach {
                                    clearedSize += it.walkTopDown().filter { f -> f.isFile }.sumOf { f -> f.length() }
                                    it.deleteRecursively()
                                }
                                
                                // Clear mass import temp files
                                context.cacheDir.listFiles()?.filter { it.name.startsWith("mass_import_") }?.forEach {
                                    clearedSize += it.length()
                                    it.delete()
                                }
                                
                                // Clear font temp files
                                context.cacheDir.listFiles()?.filter { it.name.startsWith("font_") && it.extension == "ttf" }?.forEach {
                                    clearedSize += it.length()
                                    it.delete()
                                }
                                
                                // Clear update error files
                                context.cacheDir.listFiles()?.filter { it.name.contains("update_errors") }?.forEach {
                                    clearedSize += it.length()
                                    it.delete()
                                }
                                
                                val sizeString = when {
                                    clearedSize >= 1024 * 1024 -> "%.2f MB".format(clearedSize / (1024.0 * 1024.0))
                                    clearedSize >= 1024 -> "%.2f KB".format(clearedSize / 1024.0)
                                    else -> "$clearedSize bytes"
                                }
                                withUIContext {
                                    context.toast("Cleared $sizeString of temp files")
                                }
                            } catch (e: Exception) {
                                logcat(LogPriority.ERROR, e)
                                withUIContext {
                                    context.toast("Error clearing temp files")
                                }
                            }
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Normalize tags",
                    subtitle = "Trims whitespace and removes duplicate tags (case-insensitive)",
                    onClick = {
                        scope.launch {
                            val count = Injekt.get<MangaRepository>().normalizeAllTags()
                            withUIContext {
                                context.toast("Normalized tags for $count novels")
                            }
                        }
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getNetworkGroup(
        networkPreferences: NetworkPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val networkHelper = remember { Injekt.get<NetworkHelper>() }

        val userAgentPref = networkPreferences.defaultUserAgent()
        val userAgent by userAgentPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_network),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_cookies),
                    onClick = {
                        networkHelper.cookieJar.removeAll()
                        context.toast(MR.strings.cookies_cleared)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_clear_webview_data),
                    onClick = {
                        try {
                            WebView(context).run {
                                setDefaultSettings()
                                clearCache(true)
                                clearFormData()
                                clearHistory()
                                clearSslPreferences()
                            }
                            WebStorage.getInstance().deleteAllData()
                            context.applicationInfo?.dataDir?.let { File("$it/app_webview/").deleteRecursively() }
                            context.toast(MR.strings.webview_data_deleted)
                        } catch (e: Throwable) {
                            logcat(LogPriority.ERROR, e)
                            context.toast(MR.strings.cache_delete_error)
                        }
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = networkPreferences.dohProvider(),
                    entries = persistentMapOf(
                        -1 to stringResource(MR.strings.disabled),
                        PREF_DOH_CLOUDFLARE to "Cloudflare",
                        PREF_DOH_GOOGLE to "Google",
                        PREF_DOH_ADGUARD to "AdGuard",
                        PREF_DOH_QUAD9 to "Quad9",
                        PREF_DOH_ALIDNS to "AliDNS",
                        PREF_DOH_DNSPOD to "DNSPod",
                        PREF_DOH_360 to "360",
                        PREF_DOH_QUAD101 to "Quad 101",
                        PREF_DOH_MULLVAD to "Mullvad",
                        PREF_DOH_CONTROLD to "Control D",
                        PREF_DOH_NJALLA to "Njalla",
                        PREF_DOH_SHECAN to "Shecan",
                    ),
                    title = stringResource(MR.strings.pref_dns_over_https),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = userAgentPref,
                    title = stringResource(MR.strings.pref_user_agent_string),
                    onValueChanged = {
                        try {
                            // OkHttp checks for valid values internally
                            Headers.Builder().add("User-Agent", it)
                            context.toast(MR.strings.requires_app_restart)
                        } catch (_: IllegalArgumentException) {
                            context.toast(MR.strings.error_user_agent_string_invalid)
                            return@EditTextPreference false
                        }
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_reset_user_agent_string),
                    enabled = remember(userAgent) { userAgent != userAgentPref.defaultValue() },
                    onClick = {
                        userAgentPref.delete()
                        context.toast(MR.strings.requires_app_restart)
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getLibraryGroup(
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_library),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_refresh_library_covers),
                    onClick = { MetadataUpdateJob.startNow(context) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_reset_viewer_flags),
                    subtitle = stringResource(MR.strings.pref_reset_viewer_flags_summary),
                    onClick = {
                        scope.launchNonCancellable {
                            val success = Injekt.get<ResetViewerFlags>().await()
                            withUIContext {
                                val message = if (success) {
                                    MR.strings.pref_reset_viewer_flags_success
                                } else {
                                    MR.strings.pref_reset_viewer_flags_error
                                }
                                context.toast(message)
                            }
                        }
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.updateMangaTitles(),
                    title = stringResource(MR.strings.pref_update_library_manga_titles),
                    subtitle = stringResource(MR.strings.pref_update_library_manga_titles_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.disallowNonAsciiFilenames(),
                    title = stringResource(MR.strings.pref_disallow_non_ascii_filenames),
                    subtitle = stringResource(MR.strings.pref_disallow_non_ascii_filenames_details),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.autoRefreshLibrary(),
                    title = "Auto-refresh library",
                    subtitle = "Automatically refresh library when database changes. Disable for better performance on large libraries (requires manual refresh).",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.verifyCacheOnStartup(),
                    title = "Verify cache on startup",
                    subtitle = "Check library cache integrity on app startup. Disable for faster startup (cache may become stale).",
                ),
            ),
        )
    }

    @Composable
    private fun getReaderGroup(
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val chooseColorProfile = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                basePreferences.displayProfile().set(uri.toString())
            }
        }
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_reader),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = basePreferences.hardwareBitmapThreshold(),
                    entries = GLUtil.CUSTOM_TEXTURE_LIMIT_OPTIONS
                        .mapIndexed { index, option ->
                            val display = if (index == 0) {
                                stringResource(MR.strings.pref_hardware_bitmap_threshold_default, option)
                            } else {
                                option.toString()
                            }
                            option to display
                        }
                        .toMap()
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_hardware_bitmap_threshold),
                    subtitleProvider = { value, options ->
                        stringResource(MR.strings.pref_hardware_bitmap_threshold_summary, options[value].orEmpty())
                    },
                    enabled = !ImageUtil.HARDWARE_BITMAP_UNSUPPORTED &&
                        GLUtil.DEVICE_TEXTURE_LIMIT > GLUtil.SAFE_TEXTURE_LIMIT,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = basePreferences.alwaysDecodeLongStripWithSSIV(),
                    title = stringResource(MR.strings.pref_always_decode_long_strip_with_ssiv_2),
                    subtitle = stringResource(MR.strings.pref_always_decode_long_strip_with_ssiv_summary),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_display_profile),
                    subtitle = basePreferences.displayProfile().get(),
                    onClick = {
                        chooseColorProfile.launch(arrayOf("*/*"))
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getExtensionsGroup(
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val extensionInstallerPref = basePreferences.extensionInstaller()
        var shizukuMissing by rememberSaveable { mutableStateOf(false) }
        val trustExtension = remember { Injekt.get<TrustExtension>() }

        if (shizukuMissing) {
            val dismiss = { shizukuMissing = false }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(text = stringResource(MR.strings.ext_installer_shizuku)) },
                text = { Text(text = stringResource(MR.strings.ext_installer_shizuku_unavailable_dialog)) },
                dismissButton = {
                    TextButton(onClick = dismiss) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dismiss()
                            uriHandler.openUri("https://shizuku.rikka.app/download")
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
            )
        }
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_extensions),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = extensionInstallerPref,
                    entries = extensionInstallerPref.entries
                        .filter {
                            // TODO: allow private option in stable versions once URL handling is more fleshed out
                            if (isReleaseBuildType) {
                                it != BasePreferences.ExtensionInstaller.PRIVATE
                            } else {
                                true
                            }
                        }
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.ext_installer_pref),
                    onValueChanged = {
                        if (it == BasePreferences.ExtensionInstaller.SHIZUKU &&
                            !context.isShizukuInstalled
                        ) {
                            shizukuMissing = true
                            false
                        } else {
                            true
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.ext_revoke_trust),
                    onClick = {
                        trustExtension.revokeAll()
                        context.toast(MR.strings.requires_app_restart)
                    },
                ),
            ),
        )
    }
}
