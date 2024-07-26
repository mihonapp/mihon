package eu.kanade.presentation.util

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier

// https://issuetracker.google.com/352584409
context(LazyItemScope)
fun Modifier.animateItemFastScroll() = this.animateItem(fadeInSpec = null, fadeOutSpec = null)
