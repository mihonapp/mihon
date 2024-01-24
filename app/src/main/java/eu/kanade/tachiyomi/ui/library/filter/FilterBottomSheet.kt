package eu.kanade.tachiyomi.ui.library.filter

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.databinding.FilterBottomSheetBinding
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.library.LibraryController
import eu.kanade.tachiyomi.ui.library.LibraryGroup
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.hide
import eu.kanade.tachiyomi.util.view.inflate
import eu.kanade.tachiyomi.util.view.isCollapsed
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.isHidden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.math.max
import kotlin.math.roundToInt

class FilterBottomSheet @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs),
    FilterTagGroupListener {

    /**
     * Preferences helper.
     */
    private val preferences: PreferencesHelper by injectLazy()

    private lateinit var binding: FilterBottomSheetBinding

    private val trackManager: TrackManager by injectLazy()

    private val hasTracking
        get() = trackManager.hasLoggedServices()

    private lateinit var downloaded: FilterTagGroup

    private lateinit var unreadProgress: FilterTagGroup

    private lateinit var unread: FilterTagGroup

    private lateinit var completed: FilterTagGroup

    private lateinit var bookmarked: FilterTagGroup

    private lateinit var contentType: FilterTagGroup

    private var tracked: FilterTagGroup? = null

    private var trackers: FilterTagGroup? = null

    private var mangaType: FilterTagGroup? = null

    var sheetBehavior: BottomSheetBehavior<View>? = null

    private var expandedFilterSheet: ExpandedFilterSheet? = null

    private var filterOrder = preferences.filterOrder().get()

    private lateinit var clearButton: Button
    private lateinit var fullFilterButton: ImageView

    private val filterItems: MutableList<FilterTagGroup> by lazy {
        val list = mutableListOf<FilterTagGroup>()
        list.add(unreadProgress)
        list.add(unread)
        list.add(downloaded)
        list.add(completed)
        list.add(bookmarked)
        if (hasTracking) {
            tracked?.let { list.add(it) }
        }
        list.add(contentType)
        list
    }

    var onGroupClicked: (Int) -> Unit = { _ -> }
    private var libraryRecycler: View? = null
    var controller: LibraryController? = null
    private var bottomBarHeight = 0

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FilterBottomSheetBinding.bind(this)
    }

    fun onCreate(controller: LibraryController) {
        clearButton = binding.clearFiltersButton
        binding.filterLayout.removeView(clearButton)
        fullFilterButton = binding.filterButton
        sheetBehavior = BottomSheetBehavior.from(this)
        sheetBehavior?.isHideable = true
        this.controller = controller
        libraryRecycler = controller.binding.libraryGridRecycler.recycler
        libraryRecycler?.post {
            bottomBarHeight =
                controller.activityBinding?.bottomNav?.height
                    ?: controller.activityBinding?.root?.rootWindowInsetsCompat?.getInsets(systemBars())?.bottom
                    ?: 0
        }
        sheetBehavior?.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, progress: Float) {
                    this@FilterBottomSheet.controller?.updateFilterSheetY()
                    binding.pill.alpha = (1 - max(0f, progress)) * 0.25f
                    updateRootPadding(progress)
                }

                override fun onStateChanged(p0: View, state: Int) {
                    this@FilterBottomSheet.controller?.updateFilterSheetY()
                    (this@FilterBottomSheet.controller?.activity as? MainActivity)?.reEnableBackPressedCallBack()
                    stateChanged(state)
                }
            },
        )

        sheetBehavior?.hide()
        binding.expandCategories.setOnClickListener {
            onGroupClicked(ACTION_EXPAND_COLLAPSE_ALL)
        }
        binding.groupBy.setOnClickListener {
            onGroupClicked(ACTION_GROUP_BY)
        }
        binding.viewOptions.setOnClickListener {
            onGroupClicked(ACTION_DISPLAY)
        }

        sheetBehavior?.isGestureInsetBottomIgnored = true

        val activeFilters = hasActiveFiltersFromPref() && !controller.isSubClass
        if (activeFilters && sheetBehavior.isHidden() && sheetBehavior?.skipCollapsed == false) {
            sheetBehavior?.collapse()
            controller.viewScope.launchUI {
                var hasScrolled = false
                binding.filterScroll.setOnScrollChangeListener { _, _, _, _, _ ->
                    hasScrolled = true
                }
                delay(2000)
                if (sheetBehavior.isCollapsed() && !hasScrolled) {
                    sheetBehavior?.hide()
                }
                binding.filterScroll.setOnScrollChangeListener(null)
            }
        }

        post {
            libraryRecycler ?: return@post
            updateRootPadding(
                when (sheetBehavior?.state) {
                    BottomSheetBehavior.STATE_HIDDEN -> -1f
                    BottomSheetBehavior.STATE_EXPANDED -> 1f
                    else -> 0f
                },
            )

            if (binding.secondLayout.width + (binding.groupBy.width * 2) + 20.dpToPx < width) {
                binding.secondLayout.removeView(binding.viewOptions)
                binding.firstLayout.addView(binding.viewOptions)
                binding.secondLayout.isVisible = false
            } else if (binding.viewOptions.parent == binding.firstLayout) {
                binding.firstLayout.removeView(binding.viewOptions)
                binding.secondLayout.addView(binding.viewOptions)
                binding.secondLayout.isVisible = true
            }
        }

        createTags()
        clearButton.setOnClickListener { clearFilters() }
        fullFilterButton.setOnLongClickListener {
            val hadFilters = hasActiveFilters()
            if (hadFilters) {
                clearFilters()
            }
            hadFilters
        }
        fullFilterButton.setOnClickListener { showFullFilterSheet() }

        setExpandText(controller.canCollapseOrExpandCategory(), false)

        preferences.filterOrder().changes()
            .drop(1)
            .onEach {
                filterOrder = it
                clearFilters()
            }
            .launchIn(controller.viewScope)
    }

    private fun showFullFilterSheet() {
        if (expandedFilterSheet != null) return
        val activity = controller?.activity ?: return
        expandedFilterSheet = ExpandedFilterSheet(
            activity,
            filterOrder.toCharArray().distinct().mapNotNull { c ->
                val filter = Filters.filterOf(c)
                val filterTagGroup = mapOfFilters(c)
                if (filter != null && filterTagGroup != null) {
                    return@mapNotNull LibraryFilter(
                        activity.getString(filter.stringRes),
                        filterTagGroup.items,
                        filterTagGroup,
                        filterTagGroup.state + 1,
                    )
                }
                return@mapNotNull null
            },
            trackers?.let {
                LibraryFilter(
                    activity.getString(R.string.trackers),
                    it.items,
                    it,
                    it.state + 1,
                )
            },
            filterCallback = { massUpdateFilters(filterItems) },
            clearFilterCallback = { clearFilters() },
        )
        expandedFilterSheet?.setOnDismissListener {
            expandedFilterSheet = null
        }
        expandedFilterSheet?.show()
    }

    private fun stateChanged(state: Int) {
        controller?.updateHopperY()
        if (state == BottomSheetBehavior.STATE_COLLAPSED) {
            libraryRecycler?.updatePaddingRelative(bottom = sheetBehavior?.peekHeight ?: 0 + 10.dpToPx + bottomBarHeight)
        }
        if (state == BottomSheetBehavior.STATE_EXPANDED) {
            binding.pill.alpha = 0f
        }
        if (state == BottomSheetBehavior.STATE_HIDDEN) {
            onGroupClicked(ACTION_HIDE_FILTER_TIP)
            reSortViews()
            libraryRecycler?.updatePaddingRelative(bottom = 10.dpToPx + bottomBarHeight)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState(state)
        val sheetBehavior = BottomSheetBehavior.from(this)
        stateChanged(sheetBehavior.state)
    }

    fun updateRootPadding(progress: Float? = null) {
        val minHeight = sheetBehavior?.peekHeight ?: 0
        val maxHeight = height
        val trueProgress = progress
            ?: if (sheetBehavior.isExpanded()) 1f else 0f
        val percent = (trueProgress * 100).roundToInt()
        val value = (percent * (maxHeight - minHeight) / 100) + minHeight
        if (trueProgress >= 0) {
            libraryRecycler?.updatePaddingRelative(bottom = value + 10.dpToPx + bottomBarHeight)
        } else {
            libraryRecycler?.updatePaddingRelative(bottom = (minHeight * (1 + trueProgress)).toInt() + bottomBarHeight)
        }
    }

    fun setExpandText(allExpanded: Boolean?, animated: Boolean = true) {
        binding.expandCategories.isVisible = controller?.isSubClass != true && allExpanded != null
        allExpanded ?: return
        binding.expandCategories.setText(
            if (!allExpanded) {
                R.string.expand_all_categories
            } else {
                R.string.collapse_all_categories
            },
        )
        if (animated) {
            binding.expandCategories.icon = AnimatedVectorDrawableCompat.create(
                binding.expandCategories.context,
                if (!allExpanded) {
                    R.drawable.anim_expand_less_to_more
                } else {
                    R.drawable.anim_expand_more_to_less
                },
            )
            (binding.expandCategories.icon as? AnimatedVectorDrawableCompat)?.start()
        } else {
            binding.expandCategories.setIconResource(
                if (!allExpanded) {
                    R.drawable.ic_expand_more_24dp
                } else {
                    R.drawable.ic_expand_less_24dp
                },
            )
        }
    }

    private fun hasActiveFilters() = filterItems.any { it.isActivated }

    private fun hasActiveFiltersFromPref(): Boolean {
        return preferences.filterDownloaded().get() > 0 ||
            preferences.filterUnread().get() > 0 ||
            preferences.filterCompleted().get() > 0 ||
            preferences.filterTracked().get() > 0 ||
            preferences.filterMangaType().get() > 0 ||
            preferences.filterBookmarked().get() > 0 ||
            FILTER_TRACKER.isNotEmpty()
    }

    private fun createTags() {
        downloaded = inflate(R.layout.filter_tag_group) as FilterTagGroup
        downloaded.setup(this, R.string.downloaded, R.string.not_downloaded)

        completed = inflate(R.layout.filter_tag_group) as FilterTagGroup
        completed.setup(this, R.string.completed, R.string.ongoing)

        unreadProgress = inflate(R.layout.filter_tag_group) as FilterTagGroup
        unreadProgress.setup(this, R.string.not_started, R.string.in_progress)

        unread = inflate(R.layout.filter_tag_group) as FilterTagGroup
        unread.setup(this, R.string.unread, R.string.read)

        bookmarked = inflate(R.layout.filter_tag_group) as FilterTagGroup
        bookmarked.setup(this, R.string.bookmarked, R.string.not_bookmarked)

        if (hasTracking) {
            tracked = inflate(R.layout.filter_tag_group) as FilterTagGroup
            tracked?.setup(this, R.string.tracked, R.string.not_tracked)
        }

        contentType = inflate(R.layout.filter_tag_group) as FilterTagGroup
        contentType.setup(this, R.string.sfw, R.string.nsfw)

        reSortViews()

        controller?.viewScope?.launch {
            setFilterStates()
        }
    }

    var checked = false
    suspend fun checkForManhwa(sourceManager: SourceManager) {
        if (checked) return
        withIOContext {
            val libraryManga = controller?.presenter?.allLibraryItems ?: return@withIOContext
            checked = true
            var types = mutableSetOf<Int>()
            libraryManga.forEach {
                when (it.manga.seriesType(sourceManager = sourceManager)) {
                    Manga.TYPE_MANHWA, Manga.TYPE_WEBTOON -> types.add(R.string.manhwa)
                    Manga.TYPE_MANHUA -> types.add(R.string.manhua)
                    Manga.TYPE_COMIC -> types.add(R.string.comic)
                }
                if (types.size == 3) return@forEach
            }
            val sortedTypes = arrayOf(R.string.manhwa, R.string.manhua, R.string.comic)
            types = types.sortedBy { sortedTypes.indexOf(it) }.toMutableSet()
            if (types.isNotEmpty()) {
                launchUI {
                    val mangaType = inflate(R.layout.filter_tag_group) as FilterTagGroup
                    mangaType.setup(
                        this@FilterBottomSheet,
                        R.string.manga,
                        *types.toTypedArray(),
                    )
                    this@FilterBottomSheet.mangaType = mangaType
                    reorderFilters()
                    reSortViews()
                }
            }
            withUIContext {
                mangaType?.setState(
                    when (preferences.filterMangaType().get()) {
                        Manga.TYPE_MANGA -> context.getString(R.string.manga)
                        Manga.TYPE_MANHUA -> context.getString(R.string.manhua)
                        Manga.TYPE_MANHWA -> context.getString(R.string.manhwa)
                        Manga.TYPE_COMIC -> context.getString(R.string.comic)
                        else -> ""
                    },
                )
                reorderFilters()
                reSortViews()
            }

            if (filterItems.contains(tracked)) {
                val loggedServices = Injekt.get<TrackManager>().services.filter { it.isLogged }
                if (loggedServices.size > 1) {
                    val serviceNames = loggedServices.map { context.getString(it.nameRes()) }
                    withUIContext {
                        trackers = inflate(R.layout.filter_tag_group) as FilterTagGroup
                        trackers?.setup(
                            this@FilterBottomSheet,
                            serviceNames.first(),
                            serviceNames.getOrNull(1),
                            serviceNames.getOrNull(2),
                            serviceNames.getOrNull(3),
                            serviceNames.getOrNull(4),
                        )
                        if (tracked?.isActivated == true) {
                            binding.filterLayout.addView(trackers)
                            filterItems.add(trackers!!)
                            trackers?.setState(FILTER_TRACKER)
                            reSortViews()
                        }
                    }
                }
            }
        }
    }

    private suspend fun setFilterStates() {
        withContext(Dispatchers.Main) {
            downloaded.setState(preferences.filterDownloaded())
            completed.setState(preferences.filterCompleted())
            bookmarked.setState(preferences.filterBookmarked())
            val unreadP = preferences.filterUnread().get()
            if (unreadP <= 2) {
                unread.state = unreadP - 1
            } else {
                unreadProgress.state = unreadP - 3
            }
            tracked?.setState(preferences.filterTracked())
            contentType.setState(preferences.filterContentType())
            reorderFilters()
            reSortViews()
        }
    }

    private fun reorderFilters() {
        val array = filterOrder.toCharArray().distinct()
        filterItems.clear()
        for (c in array) {
            mapOfFilters(c)?.let {
                filterItems.add(it)
            }
        }
        listOfNotNull(unreadProgress, unread, downloaded, completed, mangaType, bookmarked, tracked, contentType)
            .forEach {
                if (!filterItems.contains(it)) {
                    filterItems.add(it)
                }
            }
    }

    private fun addForClear(): Int {
        return if (clearButton.parent != null) 1 else 0
    }

    private fun mapOfFilters(char: Char): FilterTagGroup? {
        return when (Filters.filterOf(char)) {
            Filters.UnreadProgress -> unreadProgress
            Filters.Unread -> unread
            Filters.Downloaded -> downloaded
            Filters.Completed -> completed
            Filters.SeriesType -> mangaType
            Filters.Bookmarked -> bookmarked
            Filters.Tracked -> if (hasTracking) tracked else null
            Filters.ContentType -> contentType
            else -> null
        }
    }

    override fun onFilterClicked(view: FilterTagGroup, index: Int, updatePreference: Boolean) {
        if (updatePreference && controller?.isSubClass != true) {
            setFilterPreference(view, index)
            onGroupClicked(ACTION_FILTER)
        }
        val hasFilters = hasActiveFilters()
        if (tracked?.isActivated == true && trackers != null && trackers?.parent == null) {
            binding.filterLayout.addView(trackers, filterItems.indexOf(tracked!!) + 2)
            filterItems.add(filterItems.indexOf(tracked!!) + 1, trackers!!)
        } else if (tracked?.isActivated == false && trackers?.parent != null) {
            binding.filterLayout.removeView(trackers)
            trackers?.reset()
            FILTER_TRACKER = ""
            filterItems.remove(trackers!!)
        }
        if (hasFilters && clearButton.parent == null) {
            binding.filterLayout.addView(clearButton)
        } else if (!hasFilters && clearButton.parent != null) {
            binding.filterLayout.removeView(clearButton)
        }
    }

    private fun setFilterPreference(view: FilterTagGroup, index: Int) {
        when (view) {
            trackers -> {
                FILTER_TRACKER = view.nameOf(index) ?: ""
                null
            }
            unreadProgress -> {
                unread.reset()
                preferences.filterUnread().set(
                    when (index) {
                        in 0..1 -> index + 3
                        else -> 0
                    },
                )
                null
            }
            unread -> {
                unreadProgress.reset()
                preferences.filterUnread()
            }
            downloaded -> preferences.filterDownloaded()
            completed -> preferences.filterCompleted()
            bookmarked -> preferences.filterBookmarked()
            tracked -> preferences.filterTracked()
            mangaType -> {
                val newIndex = when (view.nameOf(index)) {
                    context.getString(R.string.manga) -> Manga.TYPE_MANGA
                    context.getString(R.string.manhua) -> Manga.TYPE_MANHUA
                    context.getString(R.string.manhwa) -> Manga.TYPE_MANHWA
                    context.getString(R.string.comic) -> Manga.TYPE_COMIC
                    else -> 0
                }
                preferences.filterMangaType().set(newIndex)
                null
            }
            contentType -> preferences.filterContentType()
            else -> null
        }?.set(index + 1)
    }

    private fun massUpdateFilters(views: List<FilterTagGroup>) {
        if (controller?.isSubClass != true) {
            var setUnread = false
            views.forEach { view ->
                val index = view.state
                if ((view == unreadProgress || view == unread) && !setUnread && view.isActivated) {
                    (if (view == unreadProgress) unread else unreadProgress).reset()
                    setUnread = true
                    setFilterPreference(view, index)
                } else if (!(view == unreadProgress || view == unread)) {
                    setFilterPreference(view, index)
                }
            }
            if (tracked?.isActivated == false && trackers?.parent != null) {
                binding.filterLayout.removeView(trackers)
                trackers?.reset()
                FILTER_TRACKER = ""
                filterItems.remove(trackers!!)
            }
            reSortViews()
            onGroupClicked(ACTION_FILTER)
        }
    }

    fun updateGroupTypeButton(groupType: Int) {
        binding.groupBy.setIconResource(LibraryGroup.groupTypeDrawableRes(groupType))
    }

    private fun clearFilters() {
        preferences.filterDownloaded().set(0)
        preferences.filterUnread().set(0)
        preferences.filterCompleted().set(0)
        preferences.filterBookmarked().set(0)
        preferences.filterTracked().set(0)
        preferences.filterMangaType().set(0)
        FILTER_TRACKER = ""

        val transition = androidx.transition.AutoTransition()
        transition.duration = 150
        androidx.transition.TransitionManager.beginDelayedTransition(binding.filterLayout, transition)
        reorderFilters()
        filterItems.forEach {
            it.reset()
        }
        trackers?.let {
            filterItems.remove(it)
        }
        reSortViews()
        onGroupClicked(ACTION_FILTER)
    }

    private fun reSortViews() {
        binding.filterLayout.removeAllViews()
        binding.filterLayout.addView(fullFilterButton)
        filterItems.filter { it.isActivated }.forEach {
            binding.filterLayout.addView(it)
        }
        filterItems.filterNot { it.isActivated }.forEach {
            binding.filterLayout.addView(it)
        }
        if (filterItems.any { it.isActivated }) {
            binding.filterLayout.addView(clearButton)
        }
        binding.filterScroll.scrollTo(0, 0)
    }

    companion object {
        const val ACTION_REFRESH = 0
        const val ACTION_FILTER = 1
        const val ACTION_HIDE_FILTER_TIP = 2
        const val ACTION_DISPLAY = 3
        const val ACTION_EXPAND_COLLAPSE_ALL = 4
        const val ACTION_GROUP_BY = 5

        const val STATE_IGNORE = 0
        const val STATE_INCLUDE = 1
        const val STATE_EXCLUDE = 2

        var FILTER_TRACKER = ""
    }

    enum class Filters(val value: Char, @StringRes val stringRes: Int) {
        UnreadProgress('u', R.string.read_progress),
        Unread('r', R.string.unread),
        Downloaded('d', R.string.downloaded),
        Completed('c', R.string.status),
        SeriesType('m', R.string.series_type),
        Bookmarked('b', R.string.bookmarked),
        Tracked('t', R.string.tracking),
        ContentType('s', R.string.content_type);
        ;

        companion object {
            val DEFAULT_ORDER = listOf(
                UnreadProgress,
                Unread,
                Downloaded,
                Completed,
                SeriesType,
                Bookmarked,
                Tracked,
                ContentType,
            ).joinToString("") { it.value.toString() }

            fun filterOf(char: Char) = entries.find { it.value == char }
        }
    }
}
