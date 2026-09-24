package eu.kanade.presentation.more.storage.data

import androidx.compose.ui.graphics.Color
import tachiyomi.domain.manga.model.Manga

data class StorageData(
    val manga: Manga,
    val categories: List<Long>,
    val size: Long,
    val chapterCount: Int,
    val color: Color,
)
