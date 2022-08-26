package eu.kanade.presentation.category

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.kanade.domain.category.model.Category
import eu.kanade.tachiyomi.R

val Category.visualName: String
    @Composable
    get() = when {
        isSystemCategory -> stringResource(R.string.label_default)
        else -> name
    }

fun Category.visualName(context: Context): String =
    when {
        isSystemCategory -> context.getString(R.string.label_default)
        else -> name
    }
