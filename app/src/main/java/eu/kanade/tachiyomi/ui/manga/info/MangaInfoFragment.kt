package eu.kanade.tachiyomi.ui.manga.info

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.signature.StringSignature
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.source.base.Source
import eu.kanade.tachiyomi.ui.base.fragment.BaseRxFragment
import kotlinx.android.synthetic.main.fragment_manga_info.*
import nucleus.factory.RequiresPresenter

/**
 * Fragment that shows manga information.
 * Uses R.layout.fragment_manga_info.
 * UI related actions should be called from here.
 */
@RequiresPresenter(MangaInfoPresenter::class)
class MangaInfoFragment : BaseRxFragment<MangaInfoPresenter>() {

    companion object {
        /**
         * Create new instance of MangaInfoFragment.
         *
         * @return MangaInfoFragment.
         */
        fun newInstance(): MangaInfoFragment {
            return MangaInfoFragment()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_manga_info, container, false)
    }

    override fun onViewCreated(view: View?, savedState: Bundle?) {
        // Set onclickListener to toggle favorite when FAB clicked.
        fab_favorite.setOnClickListener { presenter.toggleFavorite() }

        // Set SwipeRefresh to refresh manga data.
        swipe_refresh.setOnRefreshListener { fetchMangaFromSource() }
    }

    /**
     * Check if manga is initialized.
     * If true update view with manga information,
     * if false fetch manga information
     *
     * @param manga  manga object containing information about manga.
     * @param source the source of the manga.
     */
    fun onNextManga(manga: Manga, source: Source) {
        if (manga.initialized) {
            // Update view.
            setMangaInfo(manga, source)
        } else {
            // Initialize manga.
            fetchMangaFromSource()
        }
    }

    /**
     * Update the view with manga information.
     *
     * @param manga manga object containing information about manga.
     * @param source the source of the manga.
     */
    private fun setMangaInfo(manga: Manga, source: Source?) {
        // Update artist TextView.
        manga_artist.text = manga.artist

        // Update author TextView.
        manga_author.text = manga.author

        // If manga source is known update source TextView.
        if (source != null) {
            manga_source.text = source.visibleName
        }

        // Update genres TextView.
        manga_genres.text = manga.genre

        // Update status TextView.
        manga_status.text = manga.getStatus(activity)

        // Update description TextView.
        manga_summary.text = manga.description

        // Set the favorite drawable to the correct one.
        setFavoriteDrawable(manga.favorite)

        // Initialize CoverCache and Glide headers to retrieve cover information.
        val coverCache = presenter.coverCache
        val headers = presenter.source.glideHeaders

        // Check if thumbnail_url is given.
        manga.thumbnail_url?.let { url ->
            if (manga.favorite) {
                coverCache.saveOrLoadFromCache(url, headers) {
                    if (isResumed) {
                        Glide.with(context)
                                .load(it)
                                .diskCacheStrategy(DiskCacheStrategy.RESULT)
                                .centerCrop()
                                .signature(StringSignature(it.lastModified().toString()))
                                .into(manga_cover)

                        Glide.with(context)
                                .load(it)
                                .diskCacheStrategy(DiskCacheStrategy.RESULT)
                                .centerCrop()
                                .signature(StringSignature(it.lastModified().toString()))
                                .into(backdrop)
                    }
                }
            } else {
                Glide.with(context)
                        .load(if (headers != null) GlideUrl(url, headers) else url)
                        .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                        .centerCrop()
                        .into(manga_cover)

                Glide.with(context)
                        .load(if (headers != null) GlideUrl(url, headers) else url)
                        .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                        .centerCrop()
                        .into(backdrop)
            }
        }
    }

    /**
     * Update chapter count TextView.
     *
     * @param count number of chapters.
     */
    fun setChapterCount(count: Int) {
        manga_chapters.text = count.toString()
    }

    /**
     * Update FAB with correct drawable.
     *
     * @param isFavorite determines if manga is favorite or not.
     */
    private fun setFavoriteDrawable(isFavorite: Boolean) {
        // Set the Favorite drawable to the correct one.
        // Border drawable if false, filled drawable if true.
        fab_favorite.setImageResource(if (isFavorite)
            R.drawable.ic_bookmark_white_24dp
        else
            R.drawable.ic_bookmark_border_white_24dp)
    }

    /**
     * Start fetching manga information from source.
     */
    private fun fetchMangaFromSource() {
        setRefreshing(true)
        // Call presenter and start fetching manga information
        presenter.fetchMangaFromSource()
    }


    /**
     * Update swipe refresh to stop showing refresh in progress spinner.
     */
    fun onFetchMangaDone() {
        setRefreshing(false)
    }

    /**
     * Update swipe refresh to start showing refresh in progress spinner.
     */
    fun onFetchMangaError() {
        setRefreshing(false)
    }

    /**
     * Set swipe refresh status.
     *
     * @param value whether it should be refreshing or not.
     */
    private fun setRefreshing(value: Boolean) {
        swipe_refresh.isRefreshing = value
    }

}
