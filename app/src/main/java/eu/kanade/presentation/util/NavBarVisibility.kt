package eu.kanade.presentation.util

enum class NavBarVisibility {
    SHOW,
    HIDE
}

fun NavBarVisibility.toBoolean(): Boolean {
    return when (this) {
        NavBarVisibility.SHOW -> true
        NavBarVisibility.HIDE -> false
    }
}
