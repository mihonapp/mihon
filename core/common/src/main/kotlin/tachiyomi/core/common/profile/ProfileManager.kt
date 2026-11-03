package tachiyomi.core.common.profile

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class Profile(
    val id: String,
    val name: String,
    /** Index into the avatar color palette shown on the profile picker. */
    val color: Int = 0,
)

/**
 * Keeps track of the user profiles. Every profile has its own database, preferences, covers and storage folder, so
 * profiles are completely separate, like on a streaming service.
 *
 * The default profile keeps the file names the app always used, so existing installs need no migration.
 *
 * Deliberately not part of the dependency graph: the database and preference store need the active profile while the
 * graph is built. Switching profiles restarts the process, so the active profile never changes while running.
 */
object ProfileManager {

    const val DEFAULT_ID = "default"
    const val MAX_PROFILES = 6

    private const val FILE = "mihon_profiles"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_ACTIVE = "active"
    private const val KEY_SKIP_PICKER = "skip_picker_once"

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var activeIdCache: String? = null

    @Volatile
    private var pickedThisProcess = false

    private fun store(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    fun profiles(context: Context): List<Profile> {
        val raw = store(context).getString(KEY_PROFILES, null)
        val stored = raw?.let { runCatching { json.decodeFromString<List<Profile>>(it) }.getOrNull() }.orEmpty()
        return if (stored.any { it.id == DEFAULT_ID }) stored else listOf(Profile(DEFAULT_ID, "Default")) + stored
    }

    private fun save(context: Context, profiles: List<Profile>) {
        store(context).edit(commit = true) { putString(KEY_PROFILES, json.encodeToString(profiles)) }
    }

    fun activeId(context: Context): String {
        activeIdCache?.let { return it }
        val id = store(context).getString(KEY_ACTIVE, DEFAULT_ID).orEmpty()
        val valid = if (profiles(context).any { it.id == id }) id else DEFAULT_ID
        activeIdCache = valid
        return valid
    }

    fun active(context: Context): Profile {
        val id = activeId(context)
        return profiles(context).first { it.id == id }
    }

    fun isDefault(context: Context): Boolean = activeId(context) == DEFAULT_ID

    fun add(context: Context, name: String, color: Int): Profile? {
        val current = profiles(context)
        if (current.size >= MAX_PROFILES) return null
        val profile = Profile(UUID.randomUUID().toString().take(8), name.trim().ifEmpty { "Profile" }, color)
        save(context, current + profile)
        return profile
    }

    fun update(context: Context, id: String, name: String, color: Int) {
        save(
            context,
            profiles(context).map {
                if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }, color = color) else it
            },
        )
    }

    /** Removes a profile and every file it owns. The default and the active profile can't be deleted. */
    fun delete(context: Context, id: String): Boolean {
        if (id == DEFAULT_ID || id == activeId(context)) return false
        val app = context.applicationContext
        save(app, profiles(app).filterNot { it.id == id })
        app.deleteDatabase(databaseName(id))
        File(app.getDatabasePath(databaseName(id)).path + "-wal").delete()
        File(app.getDatabasePath(databaseName(id)).path + "-shm").delete()
        app.deleteSharedPreferences(preferencesName(id))
        PROFILE_SCOPED_PREFS.forEach { app.deleteSharedPreferences("${it}_$id") }
        File(app.filesDir, "profiles/$id").deleteRecursively()
        app.getExternalFilesDir(null)?.let { File(it, "profiles/$id").deleteRecursively() }
        return true
    }

    /** Makes [id] the active profile on the next process start. */
    fun setActive(context: Context, id: String) {
        // commit = true: the process is killed right after this, so the write has to reach the disk first
        store(context).edit(commit = true) {
            putString(KEY_ACTIVE, id)
            putBoolean(KEY_SKIP_PICKER, true)
        }
    }

    /**
     * Whether the profile picker should be shown now. True once per process, and only when there is a choice to make.
     * A process restarted by a profile switch skips the picker, since the profile was just chosen.
     */
    fun shouldShowPicker(context: Context): Boolean {
        if (pickedThisProcess) return false
        pickedThisProcess = true
        val store = store(context)
        if (store.getBoolean(KEY_SKIP_PICKER, false)) {
            store.edit(commit = true) { remove(KEY_SKIP_PICKER) }
            return false
        }
        return profiles(context).size > 1
    }

    // Names of the files that belong to a profile

    fun databaseName(context: Context): String = databaseName(activeId(context))

    const val DEFAULT_DATABASE_NAME = "tachiyomi.db"

    private fun databaseName(id: String) = if (id == DEFAULT_ID) DEFAULT_DATABASE_NAME else "tachiyomi_$id.db"

    private fun preferencesName(id: String) = "profile_${id}_preferences"

    /** The app's main preferences for the active profile. */
    fun preferences(context: Context): SharedPreferences {
        val id = activeId(context)
        return if (id == DEFAULT_ID) {
            PreferenceManager.getDefaultSharedPreferences(context)
        } else {
            context.applicationContext.getSharedPreferences(preferencesName(id), Context.MODE_PRIVATE)
        }
    }

    /** The main preferences of any profile, not only the active one. */
    fun preferencesOf(context: Context, id: String): SharedPreferences {
        return if (id == DEFAULT_ID) {
            PreferenceManager.getDefaultSharedPreferences(context)
        } else {
            context.applicationContext.getSharedPreferences(preferencesName(id), Context.MODE_PRIVATE)
        }
    }

    /** Whether a profile other than the active one has [pkgName] in its list of extensions. */
    fun otherProfileHasExtension(context: Context, pkgName: String): Boolean {
        val active = activeId(context)
        return profiles(context).any { profile ->
            profile.id != active &&
                preferencesOf(context, profile.id).getStringSet("profile_extensions", null)?.contains(pkgName) == true
        }
    }

    /**
     * Preferences shared by every profile. Extensions are installed on the whole device, so which repositories and
     * signatures are trusted can't differ per profile. This is the file the default profile uses.
     */
    fun devicePreferences(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    /** Name for an extra preferences file that has to be kept per profile. */
    fun scopedPreferencesName(context: Context, base: String): String {
        val id = activeId(context)
        return if (id == DEFAULT_ID) base else "${base}_$id"
    }

    /** Folder name for data kept under the app's own directories, for example the covers. */
    fun scopedDirectory(context: Context, dir: String): String {
        val id = activeId(context)
        return if (id == DEFAULT_ID) dir else "profiles/$id/$dir"
    }

    // Extra preference files that are scoped through scopedPreferencesName
    private val PROFILE_SCOPED_PREFS = listOf("active_downloads", "chapters_to_delete", "tracking_queue")
}
