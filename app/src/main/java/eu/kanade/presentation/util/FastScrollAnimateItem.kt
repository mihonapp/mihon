package eu.kanade.presentation.util

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier

// https://issuetracker.google.com/352584409
context(itemScope: LazyItemScope)
fun Modifier.animateItemFastScroll() = with(itemScope) {
    this@animateItemFastScroll.animateItem(fadeInSpec = null, fadeOutSpec = null)
}
