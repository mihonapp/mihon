package tachiyomi.domain.library.interactor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomi.domain.library.service.LibraryPreferences

class GetSourceDefaultCategory(
    private val libraryPreferences: LibraryPreferences,
) {

    fun await(sourceId: Long): Long {
        if (!libraryPreferences.perSourceDefaultCategory.get()) {
            return libraryPreferences.defaultCategory.get().toLong()
        }

        val json = libraryPreferences.sourceDefaultCategories.get()
        return try {
            val element = Json.parseToJsonElement(json).jsonObject[sourceId.toString()]
            element?.jsonPrimitive?.contentOrNull?.toLong() ?: libraryPreferences.defaultCategory.get().toLong()
        } catch (e: Exception) {
            libraryPreferences.defaultCategory.get().toLong()
        }
    }
}
