package mihon.feature.mekuri.shelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.launch
import mihon.feature.mekuri.vault.MekuriVault
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.RenameCategory
import tachiyomi.domain.category.interactor.ReorderCategory

/**
 * Mekuri: actions for one shelf, opened from the ⋯ next to its name on the bookshelf.
 * Rename, spine colour, move left/right, and move into / out of the vault.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfMenuSheet(
    shelf: Shelf,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onDismiss: () -> Unit,
    actions: ShelfActionsViewModel,
) {
    val isUserCategory = shelf.key > 0
    var name by remember(shelf.key) { mutableStateOf(shelf.name) }
    val pinned = ShelfPrefs.pinnedColors.value[shelf.key]

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(text = shelf.name, style = MaterialTheme.typography.headlineSmall)

            if (isUserCategory) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = name.isNotBlank() && name != shelf.name,
                        onClick = {
                            actions.rename(shelf.key, name.trim())
                            onDismiss()
                        },
                    ) { Text("Save") }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Spine colour", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(onClick = { ShelfPrefs.setPinnedColor(shelf.key, null) }) {
                        Text(if (pinned == null) "From cover ✓" else "From cover")
                    }
                    ShelfPalette.forEach { color ->
                        val selected = pinned == color.toArgb()
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface),
                                            CircleShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { ShelfPrefs.setPinnedColor(shelf.key, color.toArgb()) }
                                .semantics { contentDescription = if (selected) "Selected colour" else "Colour" },
                        )
                    }
                }
            }

            if (isUserCategory) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(enabled = canMoveLeft, onClick = { actions.move(shelf.key, -1) }) {
                        Text("← Move left")
                    }
                    OutlinedButton(enabled = canMoveRight, onClick = { actions.move(shelf.key, 1) }) {
                        Text("Move right →")
                    }
                }
                val inVault = shelf.kind == Shelf.Kind.Hidden
                OutlinedButton(
                    onClick = {
                        MekuriVault.setCategoryInVault(shelf.key, !inVault)
                        onDismiss()
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (inVault) "Take out of vault" else "Move to vault",
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class ShelfActionsViewModel(
    private val renameCategory: RenameCategory,
    private val reorderCategory: ReorderCategory,
    private val getCategories: GetCategories,
) : ViewModel() {

    fun rename(categoryId: Long, name: String) {
        viewModelScope.launch { renameCategory.await(categoryId, name) }
    }

    /** Moves a shelf one place left (-1) or right (+1) among the user's categories. */
    fun move(categoryId: Long, direction: Int) {
        viewModelScope.launch {
            val categories = getCategories.await().filterNot { it.isSystemCategory }
            val index = categories.indexOfFirst { it.id == categoryId }
            if (index == -1) return@launch
            val target = (index + direction).coerceIn(0, categories.lastIndex)
            if (target != index) reorderCategory.await(categories[index], target)
        }
    }
}
