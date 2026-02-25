package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.restore.restorers.CategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionRepoRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class BackupRestorer(
    private val context: Context,
    private val notifier: BackupNotifier,
    private val isSync: Boolean,

    private val categoriesRestorer: CategoriesRestorer = CategoriesRestorer(),
    private val preferenceRestorer: PreferenceRestorer = PreferenceRestorer(context),
    private val extensionRepoRestorer: ExtensionRepoRestorer = ExtensionRepoRestorer(),
    private val mangaRestorer: MangaRestorer = MangaRestorer(),
) {

    private var restoreAmount = 0
    private var restoreProgress = AtomicInteger()
    private val errors = Collections.synchronizedList(mutableListOf<Pair<Date, String>>())
    private val dispatcher = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors(),
    ).asCoroutineDispatcher()

    private val mangaProgressBatch = Runtime.getRuntime().availableProcessors() * 8

    /**
     * Mapping of source ID to source name from backup data
     */
    private var sourceMapping: Map<Long, String> = emptyMap()

    suspend fun restore(uri: Uri, options: RestoreOptions) {
        val startTime = System.currentTimeMillis()

        try {
            restoreFromFile(uri, options)

            val time = System.currentTimeMillis() - startTime

            val logFile = writeErrorLog()

            notifier.showRestoreComplete(
                time,
                errors.size,
                logFile.parent,
                logFile.name,
                isSync,
            )
        } finally {
            try {
                dispatcher.close()
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    private suspend fun restoreFromFile(uri: Uri, options: RestoreOptions) {
        val backup = BackupDecoder(context).decode(uri)

        // Store source mapping for error messages
        val backupMaps = backup.backupSources
        sourceMapping = backupMaps.associate { it.sourceId to it.name }

        if (options.libraryEntries) {
            restoreAmount += backup.backupManga.size
        }
        if (options.categories) {
            restoreAmount += 1
        }
        if (options.appSettings) {
            restoreAmount += 1
        }
        if (options.extensionRepoSettings) {
            restoreAmount += backup.backupExtensionRepo.size
        }
        if (options.sourceSettings) {
            restoreAmount += 1
        }

        coroutineScope {
            if (options.categories) {
                restoreCategories(backup.backupCategories)
            }
            if (options.appSettings) {
                restoreAppPreferences(backup.backupPreferences, backup.backupCategories.takeIf { options.categories })
            }
            if (options.sourceSettings) {
                restoreSourcePreferences(backup.backupSourcePreferences)
            }
            if (options.libraryEntries) {
                restoreManga(backup.backupManga, if (options.categories) backup.backupCategories else emptyList())
            }
            if (options.extensionRepoSettings) {
                restoreExtensionRepos(backup.backupExtensionRepo)
            }

            // TODO: optionally trigger online library + tracker update
        }
    }

    private fun CoroutineScope.restoreCategories(backupCategories: List<BackupCategory>) = launch(dispatcher) {
        ensureActive()
        categoriesRestorer(backupCategories)

        restoreProgress.incrementAndGet()
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.categories),
            restoreProgress.get(),
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreManga(
        backupMangas: List<BackupManga>,
        backupCategories: List<BackupCategory>,
    ) = launch(dispatcher) {
        val sortedMangas = mangaRestorer.sortByNew(backupMangas)
        sortedMangas.map {
            async {
                ensureActive()
                try {
                    mangaRestorer.restore(it, backupCategories)
                } catch (e: Exception) {
                    val sourceName = sourceMapping[it.source] ?: it.source.toString()
                    errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                } finally {
                    val currentProgress = restoreProgress.incrementAndGet()
                    if (currentProgress == restoreAmount || currentProgress % mangaProgressBatch == 0) {
                        notifier.showRestoreProgress(it.title, currentProgress, restoreAmount, isSync)
                    }
                }
            }
        }.awaitAll()

        val finalProgress = restoreProgress.get()
        if (finalProgress < restoreAmount) {
            notifier.showRestoreProgress(
                context.stringResource(MR.strings.restoring_backup),
                finalProgress,
                restoreAmount,
                isSync,
            )
        }
    }

    private fun CoroutineScope.restoreAppPreferences(
        preferences: List<BackupPreference>,
        categories: List<BackupCategory>?,
    ) = launch(dispatcher) {
        ensureActive()
        preferenceRestorer.restoreApp(
            preferences,
            categories,
        )

        restoreProgress.incrementAndGet()
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.app_settings),
            restoreProgress.get(),
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreSourcePreferences(preferences: List<BackupSourcePreferences>) = launch(
        dispatcher,
    ) {
        ensureActive()
        preferenceRestorer.restoreSource(preferences)

        restoreProgress.incrementAndGet()
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.source_settings),
            restoreProgress.get(),
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreExtensionRepos(
        backupExtensionRepo: List<BackupExtensionRepos>,
    ) = launch(dispatcher) {
        backupExtensionRepo
            .forEach {
                ensureActive()

                try {
                    extensionRepoRestorer(it)
                } catch (e: Exception) {
                    errors.add(Date() to "Error Adding Repo: ${it.name} : ${e.message}")
                }

                restoreProgress.incrementAndGet()
                notifier.showRestoreProgress(
                    context.stringResource(MR.strings.extensionRepo_settings),
                    restoreProgress.get(),
                    restoreAmount,
                    isSync,
                )
            }
    }

    private fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("mihon_restore_error.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
                    }
                }
                return file
            }
        } catch (_: Exception) {
            // Empty
        }
        return File("")
    }
}
