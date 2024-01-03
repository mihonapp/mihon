package eu.kanade.tachiyomi.ui.browse.migration.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import kotlinx.coroutines.flow.update
import tachiyomi.core.preference.Preference
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.toChapterUpdate
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

@Composable
internal fun MigrateDialog(
    oldManga: Manga,
    newManga: Manga,
    screenModel: MigrateDialogScreenModel,
    onDismissRequest: () -> Unit,
    onClickTitle: () -> Unit,
    onPopScreen: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state by screenModel.state.collectAsState()

    val flags = remember { MigrationFlags.getFlags(oldManga, screenModel.migrateFlags.get()) }
    val selectedFlags = remember { flags.map { it.isDefaultSelected }.toMutableStateList() }

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    flags.forEachIndexed { index, flag ->
                        LabeledCheckbox(
                            label = stringResource(flag.titleId),
                            checked = selectedFlags[index],
                            onCheckedChange = { selectedFlags[index] = it },
                        )
                    }
                }
            },
            confirmButton = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onClickTitle()
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_show_manga))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = {
                            scope.launchIO {
                                screenModel.migrateManga(
                                    oldManga,
                                    newManga,
                                    false,
                                    MigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
                                )
                                withUIContext { onPopScreen() }
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.copy))
                    }
                    TextButton(
                        onClick = {
                            scope.launchIO {
                                screenModel.migrateManga(
                                    oldManga,
                                    newManga,
                                    true,
                                    MigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
                                )

                                withUIContext { onPopScreen() }
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.migrate))
                    }
                }
            },
        )
    }
}

internal class MigrateDialogScreenModel(
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) : StateScreenModel<MigrateDialogScreenModel.State>(State()) {

    val migrateFlags: Preference<Int> by lazy {
        preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)
    }

    private val enhancedServices by lazy {
        Injekt.get<TrackerManager>().trackers.filterIsInstance<EnhancedTracker>()
    }

    suspend fun migrateManga(
        oldManga: Manga,
        newManga: Manga,
        replace: Boolean,
        flags: Int,
    ) {
        migrateFlags.set(flags)
        val source = sourceManager.get(newManga.source) ?: return
        val prevSource = sourceManager.get(oldManga.source)

        mutableState.update { it.copy(isMigrating = true) }

        try {
            val chapters = source.getChapterList(newManga.toSManga())

            migrateMangaInternal(
                oldSource = prevSource,
                newSource = source,
                oldManga = oldManga,
                newManga = newManga,
                sourceChapters = chapters,
                replace = replace,
                flags = flags,
            )
        } catch (_: Throwable) {
            // Explicitly stop if an error occurred; the dialog normally gets popped at the end
            // anyway
            mutableState.update { it.copy(isMigrating = false) }
        }
    }

    private suspend fun migrateMangaInternal(
        oldSource: Source?,
        newSource: Source,
        oldManga: Manga,
        newManga: Manga,
        sourceChapters: List<SChapter>,
        replace: Boolean,
        flags: Int,
    ) {
        val migrateChapters = MigrationFlags.hasChapters(flags)
        val migrateCategories = MigrationFlags.hasCategories(flags)
        val migrateCustomCover = MigrationFlags.hasCustomCover(flags)
        val deleteDownloaded = MigrationFlags.hasDeleteDownloaded(flags)

        try {
            syncChaptersWithSource.await(sourceChapters, newManga, newSource)
        } catch (_: Exception) {
            // Worst case, chapters won't be synced
        }

        // Update chapters read, bookmark and dateFetch
        if (migrateChapters) {
            val prevMangaChapters = getChaptersByMangaId.await(oldManga.id)
            val mangaChapters = getChaptersByMangaId.await(newManga.id)

            val maxChapterRead = prevMangaChapters
                .filter { it.read }
                .maxOfOrNull { it.chapterNumber }

            val updatedMangaChapters = mangaChapters.map { mangaChapter ->
                var updatedChapter = mangaChapter
                if (updatedChapter.isRecognizedNumber) {
                    val prevChapter = prevMangaChapters
                        .find { it.isRecognizedNumber && it.chapterNumber == updatedChapter.chapterNumber }

                    if (prevChapter != null) {
                        updatedChapter = updatedChapter.copy(
                            dateFetch = prevChapter.dateFetch,
                            bookmark = prevChapter.bookmark,
                        )
                    }

                    if (maxChapterRead != null && updatedChapter.chapterNumber <= maxChapterRead) {
                        updatedChapter = updatedChapter.copy(read = true)
                    }
                }

                updatedChapter
            }

            val chapterUpdates = updatedMangaChapters.map { it.toChapterUpdate() }
            updateChapter.awaitAll(chapterUpdates)
        }

        // Update categories
        if (migrateCategories) {
            val categoryIds = getCategories.await(oldManga.id).map { it.id }
            setMangaCategories.await(newManga.id, categoryIds)
        }

        // Update track
        getTracks.await(oldManga.id).mapNotNull { track ->
            val updatedTrack = track.copy(mangaId = newManga.id)

            val service = enhancedServices
                .firstOrNull { it.isTrackFrom(updatedTrack, oldManga, oldSource) }

            if (service != null) {
                service.migrateTrack(updatedTrack, newManga, newSource)
            } else {
                updatedTrack
            }
        }
            .takeIf { it.isNotEmpty() }
            ?.let { insertTrack.awaitAll(it) }

        // Delete downloaded
        if (deleteDownloaded) {
            if (oldSource != null) {
                downloadManager.deleteManga(oldManga, oldSource)
            }
        }

        if (replace) {
            updateManga.await(MangaUpdate(oldManga.id, favorite = false, dateAdded = 0))
        }

        // Update custom cover (recheck if custom cover exists)
        if (migrateCustomCover && oldManga.hasCustomCover()) {
            coverCache.setCustomCoverToCache(newManga, coverCache.getCustomCoverFile(oldManga.id).inputStream())
        }

        updateManga.await(
            MangaUpdate(
                id = newManga.id,
                favorite = true,
                chapterFlags = oldManga.chapterFlags,
                viewerFlags = oldManga.viewerFlags,
                dateAdded = if (replace) oldManga.dateAdded else Instant.now().toEpochMilli(),
            ),
        )
    }

    @Immutable
    data class State(
        val isMigrating: Boolean = false,
    )
}
