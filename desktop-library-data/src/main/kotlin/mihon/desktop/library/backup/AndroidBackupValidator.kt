package mihon.desktop.library.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale

class AndroidBackupValidator {
    fun validate(
        backup: AndroidBackup,
        limits: BackupLimits = BackupLimits.DEFAULT,
    ): ValidatedAndroidBackup {
        if (backup.backupManga.size > limits.maxManga) {
            reject("backupManga[${limits.maxManga}]", "manga count exceeds ${limits.maxManga}")
        }
        if (backup.backupCategories.size > limits.maxCategories) {
            reject("backupCategories[${limits.maxCategories}]", "category count exceeds ${limits.maxCategories}")
        }

        val categoryIds = mutableSetOf<Long>()
        val categoryNames = mutableSetOf<String>()
        val categoryOrders = mutableSetOf<Long>()
        backup.backupCategories.forEachIndexed { index, category ->
            val path = "backupCategories[$index]"
            checkString(category.name, "$path.name", limits)
            if (!categoryIds.add(category.id)) reject("$path.id", "duplicate category id")
            if (!categoryNames.add(category.name.lowercase(Locale.ROOT))) {
                reject("$path.name", "duplicate category name")
            }
            if (!categoryOrders.add(category.order)) reject("$path.order", "duplicate category order")
        }

        var chapterCount = 0
        var trackCount = 0
        val mangaIdentities = mutableSetOf<Pair<Long, String>>()
        val mangaMemos = ArrayList<String>(backup.backupManga.size)
        val chapterMemos = ArrayList<List<String>>(backup.backupManga.size)
        backup.backupManga.forEachIndexed { mangaIndex, manga ->
            val path = "backupManga[$mangaIndex]"
            if (!mangaIdentities.add(manga.source to manga.url)) reject(path, "duplicate manga identity")
            checkString(manga.url, "$path.url", limits)
            checkString(manga.title, "$path.title", limits)
            checkNullableString(manga.artist, "$path.artist", limits)
            checkNullableString(manga.author, "$path.author", limits)
            checkNullableString(manga.description, "$path.description", limits)
            manga.genre.forEachIndexed { index, value -> checkString(value, "$path.genre[$index]", limits) }
            checkNullableString(manga.thumbnailUrl, "$path.thumbnailUrl", limits)
            manga.excludedScanlators.forEachIndexed { index, value ->
                checkString(value, "$path.excludedScanlators[$index]", limits)
            }
            checkString(manga.notes, "$path.notes", limits)
            mangaMemos += parseMemo(manga.memo, "$path.memo", limits)

            val chapterUrls = mutableSetOf<String>()
            val perMangaChapterMemos = ArrayList<String>(manga.chapters.size)
            manga.chapters.forEachIndexed { chapterIndex, chapter ->
                val chapterPath = "$path.chapters[$chapterIndex]"
                chapterCount++
                if (chapterCount >
                    limits.maxChapters
                ) {
                    reject(chapterPath, "chapter count exceeds ${limits.maxChapters}")
                }
                checkString(chapter.url, "$chapterPath.url", limits)
                if (!chapterUrls.add(chapter.url)) reject("$chapterPath.url", "duplicate chapter URL")
                checkString(chapter.name, "$chapterPath.name", limits)
                checkNullableString(chapter.scanlator, "$chapterPath.scanlator", limits)
                checkFinite(chapter.chapterNumber, "$chapterPath.chapterNumber")
                perMangaChapterMemos += parseMemo(chapter.memo, "$chapterPath.memo", limits)
            }
            chapterMemos += perMangaChapterMemos

            manga.categories.forEachIndexed { index, categoryOrder ->
                if (categoryOrder !in categoryOrders) reject("$path.categories[$index]", "unknown category order")
            }
            manga.history.forEachIndexed { index, history ->
                val historyPath = "$path.history[$index]"
                checkString(history.url, "$historyPath.url", limits)
                if (history.url !in chapterUrls) reject("$historyPath.url", "history chapter URL is absent")
            }
            val trackingIds = mutableSetOf<Int>()
            manga.tracking.forEachIndexed { index, tracking ->
                val trackingPath = "$path.tracking[$index]"
                trackCount++
                if (trackCount > limits.maxTracks) reject(trackingPath, "tracking count exceeds ${limits.maxTracks}")
                if (!trackingIds.add(tracking.syncId)) reject("$trackingPath.syncId", "duplicate tracking syncId")
                checkString(tracking.trackingUrl, "$trackingPath.trackingUrl", limits)
                checkString(tracking.title, "$trackingPath.title", limits)
                checkFinite(tracking.lastChapterRead, "$trackingPath.lastChapterRead")
                checkFinite(tracking.score, "$trackingPath.score")
            }
        }

        backup.backupSources.forEachIndexed { index, source ->
            checkString(source.name, "backupSources[$index].name", limits)
        }

        var preferenceCount = 0
        backup.backupPreferences.forEachIndexed { index, preference ->
            preferenceCount++
            if (preferenceCount > limits.maxPreferences) {
                reject("backupPreferences[$index]", "preference count exceeds ${limits.maxPreferences}")
            }
            validatePreference(preference, "backupPreferences[$index]", limits)
        }
        backup.backupSourcePreferences.forEachIndexed { sourceIndex, sourcePreferences ->
            val path = "backupSourcePreferences[$sourceIndex]"
            checkString(sourcePreferences.sourceKey, "$path.sourceKey", limits)
            sourcePreferences.prefs.forEachIndexed { preferenceIndex, preference ->
                val preferencePath = "$path.prefs[$preferenceIndex]"
                preferenceCount++
                if (preferenceCount > limits.maxPreferences) {
                    reject(preferencePath, "preference count exceeds ${limits.maxPreferences}")
                }
                validatePreference(preference, preferencePath, limits)
            }
        }

        backup.backupExtensionStores.forEachIndexed { index, store ->
            val path = "backupExtensionStores[$index]"
            checkString(store.indexUrl, "$path.indexUrl", limits)
            checkString(store.name, "$path.name", limits)
            checkNullableString(store.badgeLabel, "$path.badgeLabel", limits)
            checkString(store.contactWebsite, "$path.contactWebsite", limits)
            checkString(store.signingKey, "$path.signingKey", limits)
            checkNullableString(store.contactDiscord, "$path.contactDiscord", limits)
            checkNullableString(store.extensionListUrl, "$path.extensionListUrl", limits)
        }

        return ValidatedAndroidBackup(backup, mangaMemos, chapterMemos)
    }

    private fun validatePreference(preference: AndroidBackupPreference, path: String, limits: BackupLimits) {
        checkString(preference.key, "$path.key", limits)
        when (val value = preference.value) {
            is AndroidStringPreferenceValue -> checkString(value.value, "$path.value", limits)
            is AndroidStringSetPreferenceValue -> value.value.forEachIndexed { index, item ->
                checkString(item, "$path.value[$index]", limits)
            }
            is AndroidFloatPreferenceValue -> checkFinite(value.value, "$path.value")
            is AndroidIntPreferenceValue,
            is AndroidLongPreferenceValue,
            is AndroidBooleanPreferenceValue,
            -> Unit
        }
    }

    private fun checkFinite(value: Float, path: String) {
        if (!value.isFinite()) reject(path, "float must be finite")
    }

    private fun parseMemo(bytes: ByteArray, path: String, limits: BackupLimits): String {
        if (bytes.size > limits.maxStringChars) reject(path, "memo byte count exceeds ${limits.maxStringChars}")
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            reject(path, "memo is not valid UTF-8")
        }
        checkString(text, path, limits)
        val maximumDepth = minOf(limits.maxNestingDepth, MAX_SAFE_JSON_NESTING_DEPTH)
        checkStructuralDepth(text, path, maximumDepth)
        val element = try {
            Json.parseToJsonElement(text)
        } catch (_: Exception) {
            reject(path, "memo is not valid JSON")
        }
        if (element !is JsonObject) reject(path, "memo JSON must be an object")
        checkDepth(element, 1, path, maximumDepth)
        return element.toString()
    }

    private fun checkStructuralDepth(text: String, path: String, maximum: Int) {
        var depth = 0
        var inString = false
        var escaped = false
        text.forEach { character ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
            } else {
                when (character) {
                    '"' -> inString = true
                    '{', '[' -> {
                        depth++
                        if (depth > maximum) reject(path, "memo JSON nesting exceeds $maximum")
                    }
                    '}', ']' -> depth--
                }
            }
        }
    }

    private fun checkDepth(element: JsonElement, depth: Int, path: String, maximum: Int) {
        when (element) {
            is JsonObject -> {
                if (depth > maximum) reject(path, "memo JSON nesting exceeds $maximum")
                element.values.forEach { checkDepth(it, depth + 1, path, maximum) }
            }
            is JsonArray -> {
                if (depth > maximum) reject(path, "memo JSON nesting exceeds $maximum")
                element.forEach { checkDepth(it, depth + 1, path, maximum) }
            }
            else -> Unit
        }
    }

    private fun checkNullableString(value: String?, path: String, limits: BackupLimits) {
        if (value != null) checkString(value, path, limits)
    }

    private fun checkString(value: String, path: String, limits: BackupLimits) {
        if (value.length > limits.maxStringChars) reject(path, "string length exceeds ${limits.maxStringChars}")
    }

    private fun reject(path: String, reason: String): Nothing = throw BackupValidationException(path, reason)

    private companion object {
        const val MAX_SAFE_JSON_NESTING_DEPTH = 64
    }
}

data class ValidatedAndroidBackup(
    val backup: AndroidBackup,
    val mangaMemoJson: List<String>,
    val chapterMemoJson: List<List<String>>,
)

class BackupValidationException(
    val path: String,
    val reason: String,
) : IllegalArgumentException("$path: $reason")
