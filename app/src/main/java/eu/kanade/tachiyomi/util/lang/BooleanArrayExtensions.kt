package eu.kanade.tachiyomi.util.lang

import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor

fun <T : Any> T.asBooleanArray(): BooleanArray {
    return this::class.declaredMemberProperties
        .filterIsInstance<KProperty1<T, Boolean>>()
        .map { it.get(this) }
        .toBooleanArray()
}

inline fun <reified T : Any> BooleanArray.asDataClass(): T {
    val properties = T::class.declaredMemberProperties.filterIsInstance<KProperty1<T, Boolean>>()
    require(properties.size == this.size) { "Boolean array size does not match data class property count" }
    return T::class.primaryConstructor!!.call(*this.toTypedArray())
}
