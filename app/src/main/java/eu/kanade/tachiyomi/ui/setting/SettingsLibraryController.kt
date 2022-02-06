package eu.kanade.tachiyomi.ui.setting

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.text.buildSpannedString
import androidx.preference.PreferenceScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.DEVICE_CHARGING
import eu.kanade.tachiyomi.data.preference.DEVICE_ONLY_ON_WIFI
import eu.kanade.tachiyomi.data.preference.MANGA_FULLY_READ
import eu.kanade.tachiyomi.data.preference.MANGA_ONGOING
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.databinding.PrefLibraryColumnsBinding
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.util.preference.bindTo
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.multiSelectListPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.widget.materialdialogs.QuadStateTextView
import eu.kanade.tachiyomi.widget.materialdialogs.setQuadStateMultiChoiceItems
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsLibraryController : SettingsController() {

    private val db: DatabaseHelper = Injekt.get()
    private val trackManager: TrackManager by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_library

        val dbCategories = db.getCategories().executeAsBlocking()
        val categories = listOf(Category.createDefault(context)) + dbCategories

        preferenceCategory {
            titleRes = R.string.pref_category_display

            preference {
                key = "pref_library_columns"
                titleRes = R.string.pref_library_columns
                onClick {
                    LibraryColumnsDialog().showDialog(router)
                }

                fun getColumnValue(value: Int): String {
                    return if (value == 0) {
                        context.getString(R.string.label_default)
                    } else {
                        value.toString()
                    }
                }

                combine(preferences.portraitColumns().asFlow(), preferences.landscapeColumns().asFlow()) { portraitCols, landscapeCols -> Pair(portraitCols, landscapeCols) }
                    .onEach { (portraitCols, landscapeCols) ->
                        val portrait = getColumnValue(portraitCols)
                        val landscape = getColumnValue(landscapeCols)
                        summary = "${context.getString(R.string.portrait)}: $portrait, " +
                            "${context.getString(R.string.landscape)}: $landscape"
                    }
                    .launchIn(viewScope)
            }
            if (!context.isTablet()) {
                switchPreference {
                    key = Keys.jumpToChapters
                    titleRes = R.string.pref_jump_to_chapters
                    defaultValue = false
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.categories

            preference {
                key = "pref_action_edit_categories"
                titleRes = R.string.action_edit_categories

                val catCount = dbCategories.size
                summary = context.resources.getQuantityString(R.plurals.num_categories, catCount, catCount)

                onClick {
                    router.pushController(CategoryController().withFadeTransaction())
                }
            }

            intListPreference {
                key = Keys.defaultCategory
                titleRes = R.string.default_category

                entries = arrayOf(context.getString(R.string.default_category_summary)) +
                    categories.map { it.name }.toTypedArray()
                entryValues = arrayOf("-1") + categories.map { it.id.toString() }.toTypedArray()
                defaultValue = "-1"

                val selectedCategory = categories.find { it.id == preferences.defaultCategory() }
                summary = selectedCategory?.name
                    ?: context.getString(R.string.default_category_summary)
                onChange { newValue ->
                    summary = categories.find {
                        it.id == (newValue as String).toInt()
                    }?.name ?: context.getString(R.string.default_category_summary)
                    true
                }
            }

            switchPreference {
                bindTo(preferences.categorizedDisplaySettings())
                titleRes = R.string.categorized_display_settings
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_library_update

            intListPreference {
                bindTo(preferences.libraryUpdateInterval())
                titleRes = R.string.pref_library_update_interval
                entriesRes = arrayOf(
                    R.string.update_never,
                    R.string.update_12hour,
                    R.string.update_24hour,
                    R.string.update_48hour,
                    R.string.update_72hour,
                    R.string.update_weekly
                )
                entryValues = arrayOf("0", "12", "24", "48", "72", "168")
                summary = "%s"

                onChange { newValue ->
                    val interval = (newValue as String).toInt()
                    LibraryUpdateJob.setupTask(context, interval)
                    true
                }
            }
            multiSelectListPreference {
                bindTo(preferences.libraryUpdateDeviceRestriction())
                titleRes = R.string.pref_library_update_restriction
                entriesRes = arrayOf(R.string.connected_to_wifi, R.string.charging)
                entryValues = arrayOf(DEVICE_ONLY_ON_WIFI, DEVICE_CHARGING)

                visibleIf(preferences.libraryUpdateInterval()) { it > 0 }

                onChange {
                    // Post to event looper to allow the preference to be updated.
                    ContextCompat.getMainExecutor(context).execute { LibraryUpdateJob.setupTask(context) }
                    true
                }

                fun updateSummary() {
                    val restrictions = preferences.libraryUpdateDeviceRestriction().get()
                        .sorted()
                        .map {
                            when (it) {
                                DEVICE_ONLY_ON_WIFI -> context.getString(R.string.connected_to_wifi)
                                DEVICE_CHARGING -> context.getString(R.string.charging)
                                else -> it
                            }
                        }
                    val restrictionsText = if (restrictions.isEmpty()) {
                        context.getString(R.string.none)
                    } else {
                        restrictions.joinToString()
                    }

                    summary = context.getString(R.string.restrictions, restrictionsText)
                }

                preferences.libraryUpdateDeviceRestriction().asFlow()
                    .onEach { updateSummary() }
                    .launchIn(viewScope)
            }
            multiSelectListPreference {
                bindTo(preferences.libraryUpdateMangaRestriction())
                titleRes = R.string.pref_library_update_manga_restriction
                entriesRes = arrayOf(R.string.pref_update_only_completely_read, R.string.pref_update_only_non_completed)
                entryValues = arrayOf(MANGA_FULLY_READ, MANGA_ONGOING)

                fun updateSummary() {
                    val restrictions = preferences.libraryUpdateMangaRestriction().get()
                        .sorted()
                        .map {
                            when (it) {
                                MANGA_ONGOING -> context.getString(R.string.pref_update_only_non_completed)
                                MANGA_FULLY_READ -> context.getString(R.string.pref_update_only_completely_read)
                                else -> it
                            }
                        }
                    val restrictionsText = if (restrictions.isEmpty()) {
                        context.getString(R.string.none)
                    } else {
                        restrictions.joinToString()
                    }

                    summary = context.getString(R.string.only_update_restrictions, restrictionsText)
                }

                preferences.libraryUpdateMangaRestriction().asFlow()
                    .onEach { updateSummary() }
                    .launchIn(viewScope)
            }
            preference {
                bindTo(preferences.libraryUpdateCategories())
                titleRes = R.string.categories

                onClick {
                    LibraryGlobalUpdateCategoriesDialog().showDialog(router)
                }

                fun updateSummary() {
                    val includedCategories = preferences.libraryUpdateCategories().get()
                        .mapNotNull { id -> categories.find { it.id == id.toInt() } }
                        .sortedBy { it.order }

                    val excludedCategories = preferences.libraryUpdateCategoriesExclude().get()
                        .mapNotNull { id -> categories.find { it.id == id.toInt() } }
                        .sortedBy { it.order }

                    val includedItemsText = if (includedCategories.isEmpty()) {
                        context.getString(R.string.none)
                    } else {
                        if (includedCategories.size == categories.size) context.getString(R.string.all)
                        else includedCategories.joinToString { it.name }
                    }

                    val excludedItemsText = if (excludedCategories.isEmpty()) {
                        context.getString(R.string.none)
                    } else {
                        if (excludedCategories.size == categories.size) context.getString(R.string.all)
                        else excludedCategories.joinToString { it.name }
                    }

                    summary = buildSpannedString {
                        append(context.getString(R.string.include, includedItemsText))
                        appendLine()
                        append(context.getString(R.string.exclude, excludedItemsText))
                    }
                }

                preferences.libraryUpdateCategories().asFlow()
                    .onEach { updateSummary() }
                    .launchIn(viewScope)
                preferences.libraryUpdateCategoriesExclude().asFlow()
                    .onEach { updateSummary() }
                    .launchIn(viewScope)
            }
            switchPreference {
                key = Keys.autoUpdateMetadata
                titleRes = R.string.pref_library_update_refresh_metadata
                summaryRes = R.string.pref_library_update_refresh_metadata_summary
                defaultValue = false
            }
            if (trackManager.hasLoggedServices()) {
                switchPreference {
                    key = Keys.autoUpdateTrackers
                    titleRes = R.string.pref_library_update_refresh_trackers
                    summaryRes = R.string.pref_library_update_refresh_trackers_summary
                    defaultValue = false
                }
            }
        }
    }

    class LibraryColumnsDialog : DialogController() {

        private val preferences: PreferencesHelper = Injekt.get()

        private var portrait = preferences.portraitColumns().get()
        private var landscape = preferences.landscapeColumns().get()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val binding = PrefLibraryColumnsBinding.inflate(LayoutInflater.from(activity!!))
            onViewCreated(binding)
            return MaterialAlertDialogBuilder(activity!!)
                .setTitle(R.string.pref_library_columns)
                .setView(binding.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    preferences.portraitColumns().set(portrait)
                    preferences.landscapeColumns().set(landscape)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }

        fun onViewCreated(binding: PrefLibraryColumnsBinding) {
            with(binding.portraitColumns) {
                displayedValues = arrayOf(context.getString(R.string.label_default)) +
                    IntRange(1, 10).map(Int::toString)
                value = portrait

                setOnValueChangedListener { _, _, newValue ->
                    portrait = newValue
                }
            }
            with(binding.landscapeColumns) {
                displayedValues = arrayOf(context.getString(R.string.label_default)) +
                    IntRange(1, 10).map(Int::toString)
                value = landscape

                setOnValueChangedListener { _, _, newValue ->
                    landscape = newValue
                }
            }
        }
    }

    class LibraryGlobalUpdateCategoriesDialog : DialogController() {

        private val preferences: PreferencesHelper = Injekt.get()
        private val db: DatabaseHelper = Injekt.get()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val dbCategories = db.getCategories().executeAsBlocking()
            val categories = listOf(Category.createDefault(activity!!)) + dbCategories

            val items = categories.map { it.name }
            var selected = categories
                .map {
                    when (it.id.toString()) {
                        in preferences.libraryUpdateCategories().get() -> QuadStateTextView.State.CHECKED.ordinal
                        in preferences.libraryUpdateCategoriesExclude().get() -> QuadStateTextView.State.INVERSED.ordinal
                        else -> QuadStateTextView.State.UNCHECKED.ordinal
                    }
                }
                .toIntArray()

            return MaterialAlertDialogBuilder(activity!!)
                .setTitle(R.string.categories)
                .setQuadStateMultiChoiceItems(
                    message = R.string.pref_library_update_categories_details,
                    items = items,
                    initialSelected = selected
                ) { selections ->
                    selected = selections
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val included = selected
                        .mapIndexed { index, value -> if (value == QuadStateTextView.State.CHECKED.ordinal) index else null }
                        .filterNotNull()
                        .map { categories[it].id.toString() }
                        .toSet()
                    val excluded = selected
                        .mapIndexed { index, value -> if (value == QuadStateTextView.State.INVERSED.ordinal) index else null }
                        .filterNotNull()
                        .map { categories[it].id.toString() }
                        .toSet()

                    preferences.libraryUpdateCategories().set(included)
                    preferences.libraryUpdateCategoriesExclude().set(excluded)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }
    }
}
