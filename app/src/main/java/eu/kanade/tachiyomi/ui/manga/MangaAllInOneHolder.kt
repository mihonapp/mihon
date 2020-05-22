package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.Gson
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.data.glide.toMangaThumbnail
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.browse.source.SourceController
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.util.view.visibleIf
import exh.MERGED_SOURCE_ID
import exh.util.setChipsExtended
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date
import kotlinx.android.synthetic.main.manga_all_in_one_header.backdrop
import kotlinx.android.synthetic.main.manga_all_in_one_header.btn_categories
import kotlinx.android.synthetic.main.manga_all_in_one_header.btn_favorite
import kotlinx.android.synthetic.main.manga_all_in_one_header.btn_migrate
import kotlinx.android.synthetic.main.manga_all_in_one_header.btn_share
import kotlinx.android.synthetic.main.manga_all_in_one_header.btn_smart_search
import kotlinx.android.synthetic.main.manga_all_in_one_header.btn_tracking
import kotlinx.android.synthetic.main.manga_all_in_one_header.btn_webview
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_artist
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_artist_label
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_author
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_author_label
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_chapters
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_cover
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_full_title
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_genres_tags_compact
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_genres_tags_compact_chips
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_genres_tags_full_chips
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_genres_tags_wrapper
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_info_toggle
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_last_update
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_source
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_source_label
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_status
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_summary
import kotlinx.android.synthetic.main.manga_all_in_one_header.manga_summary_label
import kotlinx.android.synthetic.main.manga_all_in_one_header.merge_btn
import kotlinx.android.synthetic.main.manga_all_in_one_header.recommend_btn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.android.view.longClicks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class MangaAllInOneHolder(
    view: View,
    private val adapter: MangaAllInOneAdapter,
    smartSearchConfig: SourceController.SmartSearchConfig? = null
) : BaseFlexibleViewHolder(view, adapter) {

    private val preferences: PreferencesHelper by injectLazy()

    private val gson: Gson by injectLazy()

    private val dateFormat: DateFormat by lazy {
        preferences.dateFormat()
    }

    private val sourceManager: SourceManager by injectLazy()

    init {
        val presenter = adapter.delegate.mangaPresenter()

        // Setting this via XML doesn't work
        manga_cover.clipToOutline = true

        btn_favorite.clicks()
            .onEach { adapter.delegate.onFavoriteClick() }
            .launchIn(adapter.delegate.controllerScope)

        if ((Injekt.get<TrackManager>().hasLoggedServices()) && presenter.manga.favorite) {
            btn_tracking.visible()
        }

        adapter.delegate.controllerScope.launch(Dispatchers.IO) {
            if (Injekt.get<DatabaseHelper>().getTracks(presenter.manga).executeAsBlocking().any {
                val status = Injekt.get<TrackManager>().getService(it.sync_id)?.getStatus(it.status)
                status != null
            }
            ) {
                withContext(Dispatchers.Main) {
                    btn_tracking.icon = itemView.context.getDrawable(R.drawable.ic_cloud_white_24dp)
                }
            }
        }

        btn_tracking.clicks()
            .onEach { adapter.delegate.openTracking() }
            .launchIn(adapter.delegate.controllerScope)

        if (presenter.manga.favorite && presenter.getCategories().isNotEmpty()) {
            btn_categories.visible()
        }
        btn_categories.clicks()
            .onEach { adapter.delegate.onCategoriesClick() }
            .launchIn(adapter.delegate.controllerScope)

        if (presenter.source is HttpSource) {
            btn_webview.visible()
            btn_share.visible()

            btn_webview.clicks()
                .onEach { adapter.delegate.openInWebView() }
                .launchIn(adapter.delegate.controllerScope)
            btn_share.clicks()
                .onEach { adapter.delegate.shareManga() }
                .launchIn(adapter.delegate.controllerScope)
        }

        if (presenter.manga.favorite) {
            btn_migrate.visible()
            btn_smart_search.visible()
        }

        btn_migrate.clicks()
            .onEach {
                adapter.delegate.migrateManga()
            }
            .launchIn(adapter.delegate.controllerScope)

        btn_smart_search.clicks()
            .onEach { adapter.delegate.openSmartSearch() }
            .launchIn(adapter.delegate.controllerScope)

        manga_full_title.longClicks()
            .onEach {
                adapter.delegate.copyToClipboard(view.context.getString(R.string.title), manga_full_title.text.toString())
            }
            .launchIn(adapter.delegate.controllerScope)

        manga_full_title.clicks()
            .onEach {
                adapter.delegate.performGlobalSearch(manga_full_title.text.toString())
            }
            .launchIn(adapter.delegate.controllerScope)

        manga_artist.longClicks()
            .onEach {
                adapter.delegate.copyToClipboard(manga_artist_label.text.toString(), manga_artist.text.toString())
            }
            .launchIn(adapter.delegate.controllerScope)

        manga_artist.clicks()
            .onEach {
                var text = manga_artist.text.toString()
                if (adapter.delegate.isEHentaiBasedSource()) {
                    text = adapter.delegate.wrapTag("artist", text)
                }
                adapter.delegate.performGlobalSearch(text)
            }
            .launchIn(adapter.delegate.controllerScope)

        manga_author.longClicks()
            .onEach {
                // EXH Special case E-Hentai/ExHentai to ignore author field (unused)
                if (!adapter.delegate.isEHentaiBasedSource()) {
                    adapter.delegate.copyToClipboard(manga_author_label.text.toString(), manga_author.text.toString())
                }
            }
            .launchIn(adapter.delegate.controllerScope)

        manga_author.clicks()
            .onEach {
                // EXH Special case E-Hentai/ExHentai to ignore author field (unused)
                if (!adapter.delegate.isEHentaiBasedSource()) {
                    adapter.delegate.performGlobalSearch(manga_author.text.toString())
                }
            }
            .launchIn(adapter.delegate.controllerScope)

        manga_summary.longClicks()
            .onEach {
                adapter.delegate.copyToClipboard(view.context.getString(R.string.description), manga_summary.text.toString())
            }
            .launchIn(adapter.delegate.controllerScope)

        manga_cover.longClicks()
            .onEach {
                adapter.delegate.copyToClipboard(view.context.getString(R.string.title), presenter.manga.title)
            }
            .launchIn(adapter.delegate.controllerScope)

        // EXH -->
        if (smartSearchConfig == null) {
            recommend_btn.visible()
            recommend_btn.clicks()
                .onEach { adapter.delegate.openRecommends() }
                .launchIn(adapter.delegate.controllerScope)
        }
        smartSearchConfig?.let { smartSearchConfig ->
            if (smartSearchConfig.origMangaId != null) { merge_btn.visible() }
            merge_btn.clicks()
                .onEach {
                    adapter.delegate.mergeWithAnother()
                }

                .launchIn(adapter.delegate.controllerScope)
        }
        // EXH <--
    }

    fun bind(item: MangaAllInOneHeaderItem, manga: Manga, source: Source?) {
        val presenter = adapter.delegate.mangaPresenter()

        manga_full_title.text = if (manga.title.isBlank()) {
            itemView.context.getString(R.string.unknown)
        } else {
            manga.title
        }

        // Update artist TextView.
        manga_artist.text = if (manga.artist.isNullOrBlank()) {
            itemView.context.getString(R.string.unknown)
        } else {
            manga.artist
        }

        // Update author TextView.
        manga_author.text = if (manga.author.isNullOrBlank()) {
            itemView.context.getString(R.string.unknown)
        } else {
            manga.author
        }

        // If manga source is known update source TextView.
        val mangaSource = source?.toString()
        with(manga_source) {
            // EXH -->
            if (mangaSource == null) {
                text = itemView.context.getString(R.string.unknown)
            } else if (source.id == MERGED_SOURCE_ID) {
                text = eu.kanade.tachiyomi.source.online.all.MergedSource.MangaConfig.readFromUrl(gson, manga.url).children.map {
                    sourceManager.getOrStub(it.source).toString()
                }.distinct().joinToString()
            } else {
                text = mangaSource
                setOnClickListener {
                    val sourceManager = Injekt.get<SourceManager>()
                    adapter.delegate.performSearch(sourceManager.getOrStub(source.id).name)
                }
            }
            // EXH <--
        }

        // EXH -->
        if (source?.id == MERGED_SOURCE_ID) {
            manga_source_label.text = "Sources"
        } else {
            manga_source_label.setText(R.string.manga_info_source_label)
        }
        // EXH <--

        // Update status TextView.
        manga_status.setText(
            when (manga.status) {
                SManga.ONGOING -> R.string.ongoing
                SManga.COMPLETED -> R.string.completed
                SManga.LICENSED -> R.string.licensed
                else -> R.string.unknown
            }
        )

        setChapterCount(0F)
        setLastUpdateDate(Date(0L))

        // Set the favorite drawable to the correct one.
        setFavoriteButtonState(manga.favorite)

        // Set cover if it wasn't already.
        val mangaThumbnail = manga.toMangaThumbnail()

        GlideApp.with(itemView.context)
            .load(mangaThumbnail)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()
            .into(manga_cover)

        backdrop?.let {
            GlideApp.with(itemView.context)
                .load(mangaThumbnail)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .centerCrop()
                .into(it)
        }

        // Manga info section
        if (manga.description.isNullOrBlank() && manga.genre.isNullOrBlank()) {
            hideMangaInfo()
        } else {
            // Update description TextView.
            manga_summary.text = if (manga.description.isNullOrBlank()) {
                itemView.context.getString(R.string.unknown)
            } else {
                manga.description
            }

            // Update genres list
            if (!manga.genre.isNullOrBlank()) {
                manga_genres_tags_compact_chips.setChipsExtended(manga.getGenres(), this::performSearch, this::performGlobalSearch, manga.source)
                manga_genres_tags_full_chips.setChipsExtended(manga.getGenres(), this::performSearch, this::performGlobalSearch, manga.source)
            } else {
                manga_genres_tags_wrapper.gone()
            }

            // Handle showing more or less info
            manga_summary.clicks()
                .onEach { toggleMangaInfo(itemView.context) }
                .launchIn(adapter.delegate.controllerScope)
            manga_info_toggle.clicks()
                .onEach { toggleMangaInfo(itemView.context) }
                .launchIn(adapter.delegate.controllerScope)

            // Expand manga info if navigated from source listing
            if (adapter.delegate.isInitialLoadAndFromSource()) {
                adapter.delegate.removeInitialLoad()
                toggleMangaInfo(itemView.context)
            }
        }
    }

    private fun hideMangaInfo() {
        manga_summary_label.gone()
        manga_summary.gone()
        manga_genres_tags_wrapper.gone()
        manga_info_toggle.gone()
    }

    fun toggleMangaInfo(context: Context) {
        val isExpanded = manga_info_toggle.text == context.getString(R.string.manga_info_collapse)

        manga_info_toggle.text =
            if (isExpanded) {
                context.getString(R.string.manga_info_expand)
            } else {
                context.getString(R.string.manga_info_collapse)
            }

        with(manga_summary) {
            maxLines =
                if (isExpanded) {
                    3
                } else {
                    Int.MAX_VALUE
                }

            ellipsize =
                if (isExpanded) {
                    android.text.TextUtils.TruncateAt.END
                } else {
                    null
                }
        }

        manga_genres_tags_compact.visibleIf { isExpanded }
        manga_genres_tags_full_chips.visibleIf { !isExpanded }
    }

    /**
     * Update chapter count TextView.
     *
     * @param count number of chapters.
     */
    fun setChapterCount(count: Float) {
        if (count > 0f) {
            manga_chapters.text = DecimalFormat("#.#").format(count)
        } else {
            manga_chapters.text = itemView.context.getString(R.string.unknown)
        }
    }

    fun setLastUpdateDate(date: Date) {
        if (date.time != 0L) {
            manga_last_update.text = dateFormat.format(date)
        } else {
            manga_last_update.text = itemView.context.getString(R.string.unknown)
        }
    }

    /**
     * Toggles the favorite status and asks for confirmation to delete downloaded chapters.
     */
    fun toggleFavorite() {
        val presenter = adapter.delegate.mangaPresenter()

        val isNowFavorite = presenter.toggleFavorite()
        if (itemView != null && !isNowFavorite && presenter.hasDownloads()) {
            itemView.snack(itemView.context.getString(R.string.delete_downloads_for_manga)) {
                setAction(R.string.action_delete) {
                    presenter.deleteDownloads()
                }
            }
        }

        btn_categories.visibleIf { isNowFavorite && presenter.getCategories().isNotEmpty() }
        if (isNowFavorite) {
            btn_smart_search.visible()
            btn_migrate.visible()
        } else {
            btn_smart_search.gone()
            btn_migrate.gone()
        }
    }

    /**
     * Update favorite button with correct drawable and text.
     *
     * @param isFavorite determines if manga is favorite or not.
     */
    fun setFavoriteButtonState(isFavorite: Boolean) {
        // Set the Favorite drawable to the correct one.
        // Border drawable if false, filled drawable if true.
        btn_favorite.apply {
            icon = ContextCompat.getDrawable(context, if (isFavorite) R.drawable.ic_favorite_24dp else R.drawable.ic_favorite_border_24dp)
            text = context.getString(if (isFavorite) R.string.in_library else R.string.add_to_library)
            isChecked = isFavorite
        }
    }

    private fun performSearch(query: String) {
        adapter.delegate.performSearch(query)
    }

    private fun performGlobalSearch(query: String) {
        adapter.delegate.performGlobalSearch(query)
    }
}
