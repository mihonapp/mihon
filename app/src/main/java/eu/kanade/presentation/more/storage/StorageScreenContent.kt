package eu.kanade.presentation.more.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.more.storage.components.CumulativeStorage
import eu.kanade.presentation.more.storage.components.SelectStorageCategory
import eu.kanade.presentation.more.storage.components.StorageItem
import eu.kanade.presentation.more.storage.data.StorageData
import eu.kanade.presentation.util.isTabletUi
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun StorageScreenContent(
    state: StorageScreenState.Success,
    selectedCategory: Category,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues,
    onCategorySelected: (Category) -> Unit,
    onDelete: (StorageData, Boolean) -> Unit,
    onClickCover: (StorageData) -> Unit,
) {
    Row(
        modifier = modifier
            .padding(horizontal = MaterialTheme.padding.small)
            .padding(paddingValues),
        content = {
            if (isTabletUi()) {
                Info(
                    state = state,
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected,
                    modifier = Modifier
                        .weight(2f)
                        .padding(end = MaterialTheme.padding.extraLarge)
                        .fillMaxHeight(),
                )
            }

            if (state.items.isEmpty()) {
                Column(
                    modifier = Modifier.weight(3f),
                ) {
                    Spacer(Modifier.height(MaterialTheme.padding.small))

                    if (!isTabletUi()) {
                        Info(
                            state = state,
                            selectedCategory = selectedCategory,
                            onCategorySelected = onCategorySelected,
                            showStorage = false,
                        )
                    }

                    EmptyScreen(
                        stringResource(MR.strings.storage_overview_no_items),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(3f),
                    content = {
                        item {
                            Spacer(Modifier.height(MaterialTheme.padding.small))
                        }
                        item {
                            if (!isTabletUi()) {
                                Info(
                                    state = state,
                                    selectedCategory = selectedCategory,
                                    onCategorySelected = onCategorySelected,
                                )
                            }
                        }

                        items(
                            items = state.items,
                            key = { "storage-${it.manga.id}" },
                        ) { item ->
                            StorageItem(
                                modifier = Modifier.animateItem(),
                                item = item,
                                onDelete = { onDelete(item, it) },
                                onClickCover = { onClickCover(item) },
                            )
                            Spacer(Modifier.height(MaterialTheme.padding.medium))
                        }
                    },
                )
            }
        },
    )
}

@Composable
private fun Info(
    state: StorageScreenState.Success,
    selectedCategory: Category,
    onCategorySelected: (Category) -> Unit,
    modifier: Modifier = Modifier,
    showStorage: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = {
            SelectStorageCategory(
                selectedCategory = selectedCategory,
                categories = state.categories,
                onCategorySelected = onCategorySelected,
            )
            if (showStorage) {
                CumulativeStorage(
                    modifier = Modifier
                        .padding(
                            horizontal = MaterialTheme.padding.small,
                            vertical = MaterialTheme.padding.medium,
                        )
                        .run {
                            if (isTabletUi()) {
                                this
                            } else {
                                padding(bottom = MaterialTheme.padding.medium)
                            }
                        },
                    items = state.items,
                )
            }
        },
    )
}
