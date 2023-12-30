package tachiyomi.core.util.lang

import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor

fun <T : Any> T.asBooleanArray(): BooleanArray {
    val constructorParams = this::class.primaryConstructor!!.parameters.map { it.name }
    val properties = this::class.declaredMemberProperties
        .filterIsInstance<KProperty1<T, Boolean>>()
    return constructorParams
        .map { param -> properties.find { it.name == param }!!.get(this) }
        .toBooleanArray()
}

inline fun <reified T : Any> BooleanArray.asDataClass(): T {
    val properties = T::class.declaredMemberProperties.filterIsInstance<KProperty1<T, Boolean>>()
    require(properties.size == this.size) { "Boolean array size does not match data class property count" }
    return T::class.primaryConstructor!!.call(*this.toTypedArray())
}
