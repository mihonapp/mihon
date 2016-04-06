package eu.kanade.tachiyomi.util

import java.util.*

/**
 * This singleton is used to share some objects within the application, useful to communicate
 * different parts of the app.
 *
 * It stores the objects in a map using the type of the object as key, so that only one object per
 * class is stored at once.
 */
object SharedData {

    /**
     * Map where the objects are saved.
     */
    private val map = HashMap<Class<*>, Any>()

    /**
     * Publish an object to the shared data.
     *
     * @param data the object to put.
     */
    fun <T : Any> put(data: T) {
        map.put(data.javaClass, data)
    }

    /**
     * Retrieves an object from the shared data.
     *
     * @param classType the class of the object to retrieve.
     * @return an object of type T or null if it's not found.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(classType: Class<T>) = map[classType] as? T

    /**
     * Removes an object from the shared data.
     *
     * @param classType the class of the object to remove.
     * @return the object removed, null otherwise.
     */
    fun <T : Any> remove(classType: Class<T>) = get(classType)?.apply { map.remove(classType) }

}
