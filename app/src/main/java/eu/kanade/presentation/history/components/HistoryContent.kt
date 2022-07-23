package eu.kanade.presentation.history.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.items
import eu.kanade.domain.history.model.HistoryWithRelations
import eu.kanade.presentation.components.RelativeDateHeader
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.presentation.util.bottomNavPaddingValues
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.shimmerGradient
import eu.kanade.presentation.util.topPaddingValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DateFormat

@Composable
fun HistoryContent(
    history: LazyPagingItems<HistoryUiModel>,
    contentPadding: PaddingValues,
    onClickCover: (HistoryWithRelations) -> Unit,
    onClickResume: (HistoryWithRelations) -> Unit,
    onClickDelete: (HistoryWithRelations) -> Unit,
    preferences: PreferencesHelper = Injekt.get(),
) {
    val relativeTime: Int = remember { preferences.relativeTime().get() }
    val dateFormat: DateFormat = remember { preferences.dateFormat() }

    ScrollbarLazyColumn(
        contentPadding = contentPadding + bottomNavPaddingValues + topPaddingValues,
        state = rememberLazyListState(),
    ) {
        items(history) { item ->
            when (item) {
                is HistoryUiModel.Header -> {
                    RelativeDateHeader(
                        modifier = Modifier
                            .animateItemPlacement(),
                        date = item.date,
                        relativeTime = relativeTime,
                        dateFormat = dateFormat,
                    )
                }
                is HistoryUiModel.Item -> {
                    val value = item.item
                    HistoryItem(
                        modifier = Modifier.animateItemPlacement(),
                        history = value,
                        onClickCover = { onClickCover(value) },
                        onClickResume = { onClickResume(value) },
                        onClickDelete = { onClickDelete(value) },
                    )
                }
                null -> {
                    val transition = rememberInfiniteTransition()
                    val translateAnimation = transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1000f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 1000,
                                easing = LinearEasing,
                            ),
                        ),
                    )

                    val brush = remember {
                        Brush.linearGradient(
                            colors = shimmerGradient,
                            start = Offset(0f, 0f),
                            end = Offset(
                                x = translateAnimation.value,
                                y = 00f,
                            ),
                        )
                    }
                    HistoryItemShimmer(brush = brush)
                }
            }
        }
    }
}
