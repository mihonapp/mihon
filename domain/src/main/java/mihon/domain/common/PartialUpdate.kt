package mihon.domain.common

import kotlin.properties.Delegates
import kotlin.reflect.KProperty

abstract class PartialUpdate {

    private val assigned = mutableSetOf<String>()

    fun isSet(property: KProperty<*>): Boolean = property.name in assigned

    protected fun <T> field(initial: T) = Delegates.observable(initial) { property, _, _ ->
        assigned += property.name
    }
}
