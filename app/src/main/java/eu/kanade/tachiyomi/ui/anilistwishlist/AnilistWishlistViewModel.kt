package eu.kanade.tachiyomi.ui.anilistwishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.anilist.service.AnilistWishlistPreferences
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.presentation.more.anilistwishlist.AnilistWishlistScreenState
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.anilist.model.AnilistWishlistMapping
import tachiyomi.domain.anilist.repository.AnilistWishlistRepository
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnilistWishlistViewModel(
    private val trackerManager: TrackerManager,
    private val mangaRepository: MangaRepository,
    private val anilistWishlistRepository: AnilistWishlistRepository,
    private val anilistWishlistPreferences: AnilistWishlistPreferences,
    private val getCategories: GetCategories,
    private val createCategoryWithName: CreateCategoryWithName,
    private val updateManga: UpdateManga,
    private val networkToLocalManga: NetworkToLocalManga,
) : ViewModel() {

    private val _state = MutableStateFlow<AnilistWishlistScreenState>(
        AnilistWishlistScreenState.Loading,
    )

    val state: StateFlow<AnilistWishlistScreenState> = _state

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private val importMutex = Mutex()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = AnilistWishlistScreenState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val aniList = trackerManager.aniList

                if (!aniList.isLoggedIn) {
                    _state.value = AnilistWishlistScreenState.NotLoggedIn
                    return@launch
                }

                val entries = aniList.getPlanToRead()

                try {
                    reconcileMappings(entries.map { it.mediaId }.toSet())
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _events.emit("Wishlist loaded, but local cleanup failed: ${e.message}")
                }

                _state.value = AnilistWishlistScreenState.Success(entries)
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                _state.value = AnilistWishlistScreenState.Error(
                    e.message ?: "Failed to load AniList Plan to Read",
                )
            }
        }
    }

    private suspend fun reconcileMappings(activeMediaIds: Set<Long>) {
        importMutex.withLock {
            val mappings = anilistWishlistRepository.getAll()
            val allCategories = getCategories.await()
            val savedCategoryId = anilistWishlistPreferences.categoryId.get()
            val category = allCategories.firstOrNull {
                it.id == savedCategoryId && savedCategoryId > 0L
            } ?: allCategories.firstOrNull { it.name == CATEGORY_NAME }

            for (mapping in mappings) {
                if (mapping.anilistMediaId in activeMediaIds) continue

                try {
                    if (category != null) {
                        val categoryIds = getCategories.await(mapping.mangaId)
                            .map { it.id }

                        if (category.id in categoryIds) {
                            mangaRepository.setMangaCategories(
                                mangaId = mapping.mangaId,
                                categoryIds = categoryIds.filterNot { it == category.id },
                            )
                        }
                    }

                    anilistWishlistRepository.delete(mapping.anilistMediaId)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _events.emit(
                        "Could not clean up an AniList Wishlist entry: ${e.message}",
                    )
                }
            }
        }
    }

    fun importManga(mediaId: Long, selectedManga: Manga) {
        viewModelScope.launch(Dispatchers.IO) {
            importMutex.withLock {
                try {
                    val existingMapping =
                        anilistWishlistRepository.getByAniListMediaId(mediaId)

                    val manga = if (existingMapping != null) {
                        val mappedManga =
                            mangaRepository.getMangaById(existingMapping.mangaId)

                        if (
                            mappedManga.url != selectedManga.url ||
                            mappedManga.source != selectedManga.source
                        ) {
                            throw IllegalStateException(
                                "This AniList entry is already linked to another manga.",
                            )
                        }

                        mappedManga
                    } else {
                        mangaRepository.getMangaByUrlAndSourceId(
                            selectedManga.url,
                            selectedManga.source,
                        ) ?: networkToLocalManga(selectedManga)
                    }

                    val mangaMapping =
                        anilistWishlistRepository.getByMangaId(manga.id)

                    if (
                        mangaMapping != null &&
                        mangaMapping.anilistMediaId != mediaId
                    ) {
                        throw IllegalStateException(
                            "This Mihon manga is already linked to another AniList entry.",
                        )
                    }

                    if (!manga.favorite) {
                        val updated = updateManga.awaitUpdateFavorite(
                            mangaId = manga.id,
                            favorite = true,
                        )

                        if (!updated) {
                            throw IllegalStateException(
                                "Could not add manga to Mihon Library.",
                            )
                        }
                    }

                    val allCategories = getCategories.await()
                    val savedCategoryId = anilistWishlistPreferences.categoryId.get()

                    val category = allCategories
                        .firstOrNull { it.id == savedCategoryId && savedCategoryId > 0L }
                        ?: allCategories.firstOrNull { it.name == CATEGORY_NAME }
                        ?: run {
                            when (
                                val result = createCategoryWithName.await(CATEGORY_NAME)
                            ) {
                                CreateCategoryWithName.Result.Success -> Unit
                                is CreateCategoryWithName.Result.InternalError -> {
                                    val categoryAfterRetry = getCategories.await()
                                        .firstOrNull { it.name == CATEGORY_NAME }

                                    if (categoryAfterRetry == null) {
                                        throw result.error
                                    }
                                }
                            }

                            getCategories.await()
                                .firstOrNull { it.name == CATEGORY_NAME }
                                ?: error("Could not create AniList Plan to Read category.")
                        }

                    if (savedCategoryId != category.id) {
                        anilistWishlistPreferences.categoryId.set(category.id)
                    }

                    val existingCategoryIds = getCategories.await(manga.id)
                        .map { it.id }

                    if (category.id !in existingCategoryIds) {
                        mangaRepository.setMangaCategories(
                            mangaId = manga.id,
                            categoryIds = existingCategoryIds + category.id,
                        )
                    }

                    anilistWishlistRepository.upsert(
                        AnilistWishlistMapping(
                            anilistMediaId = mediaId,
                            mangaId = manga.id,
                        ),
                    )

                    _events.emit("Added to Mihon Library: ${manga.title}")
                } catch (e: Exception) {
                    if (e is CancellationException) throw e

                    _events.emit(
                        e.message ?: "Failed to add manga to Mihon Library.",
                    )
                }
            }
        }
    }

    private companion object {
        const val CATEGORY_NAME = "AniList Plan to Read"
    }
}

