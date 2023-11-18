package eu.kanade.presentation.category

import android.content.Context
import androidx.compose.runtime.Composable
import tachiyomi.core.i18n.localize
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.localize

val Category.visualName: String
    @Composable
    get() = when {
        isSystemCategory -> localize(MR.strings.label_default)
        else -> name
    }

fun Category.visualName(context: Context): String =
    when {
        isSystemCategory -> context.localize(MR.strings.label_default)
        else -> name
    }
