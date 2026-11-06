package mihon.feature.mekuri.shelf

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Mekuri: bookshelf preferences that aren't tied to Mihon's library settings: pinned spine colours, smart shelves
 * on/off, and whether the first-run shelf demo has played.
 */
object ShelfPrefs {

    private val _pinnedColors = MutableStateFlow<Map<Long, Int>>(emptyMap())

    /** Category id → ARGB colour the user pinned to that spine. */
    val pinnedColors: StateFlow<Map<Long, Int>> = _pinnedColors.asStateFlow()

    private val _smartShelves = MutableStateFlow(true)
    val smartShelves: StateFlow<Boolean> = _smartShelves.asStateFlow()

    private val _demoDone = MutableStateFlow(true)
    val demoDone: StateFlow<Boolean> = _demoDone.asStateFlow()

    private var colorsPref: Preference<Set<String>>? = null
    private var smartPref: Preference<Boolean>? = null
    private var demoPref: Preference<Boolean>? = null

    val smartShelvesPreference: Preference<Boolean>? get() = smartPref

    fun init(preferenceStore: PreferenceStore, scope: CoroutineScope) {
        val colors = preferenceStore.getStringSet("mekuri_shelf_colors", emptySet())
        val smart = preferenceStore.getBoolean("mekuri_smart_shelves", true)
        val demo = preferenceStore.getBoolean("mekuri_shelf_demo_done", false)
        colorsPref = colors
        smartPref = smart
        demoPref = demo
        colors.changes()
            .onEach { set ->
                _pinnedColors.value = set.mapNotNull { entry ->
                    val (id, argb) = entry.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                    (id.toLongOrNull() ?: return@mapNotNull null) to (argb.toIntOrNull() ?: return@mapNotNull null)
                }.toMap()
            }
            .launchIn(scope)
        smart.changes().onEach { _smartShelves.value = it }.launchIn(scope)
        demo.changes().onEach { _demoDone.value = it }.launchIn(scope)
    }

    /** Pins [argb] to the shelf, or goes back to the cover colour when null. */
    fun setPinnedColor(categoryId: Long, argb: Int?) {
        val pref = colorsPref ?: return
        val others = pref.get().filterNot { it.startsWith("$categoryId:") }.toSet()
        pref.set(if (argb == null) others else others + "$categoryId:$argb")
    }

    fun markDemoDone() {
        demoPref?.set(true)
    }
}
