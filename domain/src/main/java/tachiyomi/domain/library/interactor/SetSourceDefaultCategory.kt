package tachiyomi.domain.library.interactor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import tachiyomi.domain.library.service.LibraryPreferences

class SetSourceDefaultCategory(
    private val libraryPreferences: LibraryPreferences,
) {

    fun await(sourceId: Long, categoryId: Long?) {
        val json = libraryPreferences.sourceDefaultCategories.get()
        val currentMap = try {
            Json.parseToJsonElement(json).jsonObject.toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }

        if (categoryId != null) {
            currentMap[sourceId.toString()] = JsonPrimitive(categoryId)
        } else {
            currentMap.remove(sourceId.toString())
        }

        libraryPreferences.sourceDefaultCategories.set(JsonObject(currentMap).toString())
    }
}
