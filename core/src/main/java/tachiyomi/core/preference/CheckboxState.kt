package tachiyomi.core.preference

sealed class CheckboxState<T>(open val value: T) {

    abstract fun next(): CheckboxState<T>

    sealed class State<T>(override val value: T) : CheckboxState<T>(value) {
        data class Checked<T>(override val value: T) : State<T>(value)
        data class None<T>(override val value: T) : State<T>(value)

        val isChecked: Boolean
            get() = this is Checked

        override fun next(): CheckboxState<T> {
            return when (this) {
                is Checked -> None(value)
                is None -> Checked(value)
            }
        }
    }

    sealed class TriState<T>(override val value: T) : CheckboxState<T>(value) {
        data class Include<T>(override val value: T) : TriState<T>(value)
        data class Exclude<T>(override val value: T) : TriState<T>(value)
        data class None<T>(override val value: T) : TriState<T>(value)

        override fun next(): CheckboxState<T> {
            return when (this) {
                is Exclude -> None(value)
                is Include -> Exclude(value)
                is None -> Include(value)
            }
        }
    }
}

inline fun <T> T.asCheckboxState(condition: (T) -> Boolean): CheckboxState.State<T> {
    return if (condition(this)) {
        CheckboxState.State.Checked(this)
    } else {
        CheckboxState.State.None(this)
    }
}

inline fun <T> List<T>.mapAsCheckboxState(condition: (T) -> Boolean): List<CheckboxState.State<T>> {
    return this.map { it.asCheckboxState(condition) }
}
