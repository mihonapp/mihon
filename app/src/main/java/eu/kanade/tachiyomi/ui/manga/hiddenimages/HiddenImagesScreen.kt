package eu.kanade.tachiyomi.ui.manga.hiddenimages

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.manga.interactor.GetHiddenImages
import eu.kanade.domain.manga.interactor.RemoveHiddenImage
import eu.kanade.domain.manga.interactor.UpdateHiddenImageScope
import eu.kanade.domain.manga.model.HiddenImage
import eu.kanade.domain.manga.model.displayNameRes
import eu.kanade.domain.manga.model.next
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import okio.Buffer
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.clickableNoIndication
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView

class HiddenImagesScreen(
    private val mangaId: Long,
    private val mangaTitle: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { Model(mangaId) }
        val state by screenModel.state.collectAsState()
        var showClearDialog by remember { mutableStateOf(false) }
        var previewImage by remember { mutableStateOf<ByteArray?>(null) }

        BackHandler(enabled = state.selection.isNotEmpty()) {
            screenModel.clearSelection()
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text(stringResource(MR.strings.hidden_images_clear_list)) },
                text = { Text(stringResource(MR.strings.hidden_images_clear_list_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearDialog = false
                            screenModel.clearAll()
                        },
                    ) {
                        Text(stringResource(MR.strings.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        previewImage?.let { imageBytes ->
            HiddenImagePreviewDialog(
                imageBytes = imageBytes,
                onDismissRequest = { previewImage = null },
            )
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.hidden_images_menu_title, mangaTitle),
                    navigateUp = {
                        if (state.selection.isNotEmpty()) {
                            screenModel.clearSelection()
                        } else {
                            navigator.pop()
                        }
                    },
                    actionModeCounter = state.selection.size,
                    onCancelActionMode = screenModel::clearSelection,
                    actionModeActions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_select_all),
                                    icon = Icons.Outlined.SelectAll,
                                    onClick = screenModel::selectAll,
                                ),
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_select_inverse),
                                    icon = Icons.Outlined.FlipToBack,
                                    onClick = screenModel::invertSelection,
                                ),
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_remove),
                                    icon = Icons.Outlined.Delete,
                                    onClick = screenModel::removeSelected,
                                ),
                            ),
                        )
                    },
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.OverflowAction(
                                    title = stringResource(MR.strings.hidden_images_clear_list),
                                    onClick = { showClearDialog = true },
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            if (state.hiddenImages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = stringResource(MR.strings.hidden_images_none))
                }
                return@Scaffold
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = paddingValues.calculateTopPadding() + 12.dp,
                    bottom = paddingValues.calculateBottomPadding() + 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.hiddenImages, key = { it.id }) { item ->
                    HiddenImageGridItem(
                        item = item,
                        selected = item.id in state.selection,
                        selectionMode = state.selection.isNotEmpty(),
                        onClick = {
                            if (state.selection.isNotEmpty()) {
                                screenModel.toggleSelection(item.id)
                            } else {
                                previewImage = item.previewImage
                            }
                        },
                        onLongClick = { screenModel.toggleSelection(item.id) },
                        onCycleScope = { screenModel.cycleScope(item) },
                        onRemove = { screenModel.remove(item.id) },
                    )
                }
            }
        }
    }

    @Composable
    private fun HiddenImageGridItem(
        item: HiddenImage,
        selected: Boolean,
        selectionMode: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        onCycleScope: () -> Unit,
        onRemove: () -> Unit,
    ) {
        val imageBitmap = remember(item.previewImage) {
            item.previewImage?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            ),
            border = if (selected) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = stringResource(MR.strings.hidden_images_no_preview))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCycleScope, enabled = !selectionMode) {
                        Text(text = stringResource(item.scope.displayNameRes()))
                    }

                    IconButton(onClick = onRemove, enabled = !selectionMode) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(MR.strings.action_remove),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun HiddenImagePreviewDialog(
        imageBytes: ByteArray,
        onDismissRequest: () -> Unit,
    ) {
        val previewData = remember(imageBytes) {
            val source = Buffer().write(imageBytes)
            HiddenImagePreviewData(
                source = source,
                isAnimated = ImageUtil.isAnimatedAndSupported(source),
            )
        }
        val previewToken = remember(imageBytes) { imageBytes.contentHashCode() }

        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                            .navigationBarsPadding(),
                    ) {
                        ActionsPill {
                            IconButton(onClick = onDismissRequest) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(MR.strings.action_close),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                },
            ) { contentPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f))
                        .clickableNoIndication(onClick = onDismissRequest),
                ) {
                    AndroidView(
                        factory = { context ->
                            ReaderPageImageView(context).apply {
                                onViewClicked = onDismissRequest
                                tag = null
                            }
                        },
                        update = { view ->
                            if (view.tag != previewToken) {
                                view.setImage(
                                    previewData.source,
                                    previewData.isAnimated,
                                    ReaderPageImageView.Config(zoomDuration = 500),
                                )
                                view.tag = previewToken
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = contentPadding.calculateTopPadding(),
                                bottom = contentPadding.calculateBottomPadding(),
                            ),
                    )
                }
            }
        }
    }

    private data class HiddenImagePreviewData(
        val source: Buffer,
        val isAnimated: Boolean,
    )

    @Composable
    private fun ActionsPill(content: @Composable () -> Unit) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
        ) {
            content()
        }
    }

    private class Model(
        private val mangaId: Long,
        private val getHiddenImages: GetHiddenImages = Injekt.get(),
        private val removeHiddenImage: RemoveHiddenImage = Injekt.get(),
        private val updateHiddenImageScope: UpdateHiddenImageScope = Injekt.get(),
    ) : StateScreenModel<State>(State()) {

        init {
            screenModelScope.launchIO {
                getHiddenImages.subscribe(mangaId).collectLatest { hiddenImages ->
                    mutableState.value = state.value.copy(hiddenImages = hiddenImages)
                }
            }
        }

        fun toggleSelection(id: Long) {
            val selection = state.value.selection.toMutableSet().apply {
                if (!add(id)) {
                    remove(id)
                }
            }
            mutableState.value = state.value.copy(selection = selection)
        }

        fun clearSelection() {
            mutableState.value = state.value.copy(selection = emptySet())
        }

        fun selectAll() {
            mutableState.value = state.value.copy(selection = state.value.hiddenImages.map { it.id }.toSet())
        }

        fun invertSelection() {
            val allIds = state.value.hiddenImages.map { it.id }.toSet()
            mutableState.value = state.value.copy(selection = allIds - state.value.selection)
        }

        fun remove(id: Long) {
            screenModelScope.launchIO {
                removeHiddenImage.await(id)
            }
        }

        fun removeSelected() {
            val selected = state.value.selection
            clearSelection()
            screenModelScope.launchIO {
                selected.forEach { id ->
                    removeHiddenImage.await(id)
                }
            }
        }

        fun cycleScope(item: HiddenImage) {
            screenModelScope.launchIO {
                updateHiddenImageScope.await(item.id, item.scope.next())
            }
        }

        fun clearAll() {
            val currentIds = state.value.hiddenImages.map { it.id }
            clearSelection()
            screenModelScope.launchIO {
                currentIds.forEach { id ->
                    removeHiddenImage.await(id)
                }
            }
        }
    }

    @Immutable
    private data class State(
        val hiddenImages: List<HiddenImage> = emptyList(),
        val selection: Set<Long> = emptySet(),
    )
}
