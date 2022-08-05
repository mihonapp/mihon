package eu.kanade.presentation.category

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.kanade.domain.category.model.Category
import eu.kanade.tachiyomi.R

val Category.visualName: String
    @Composable
    get() = when (id) {
        Category.UNCATEGORIZED_ID -> stringResource(id = R.string.label_default)
        else -> name
    }

fun Category.visualName(context: Context): String =
    when (id) {
        Category.UNCATEGORIZED_ID -> context.getString(R.string.label_default)
        else -> name
    }
