package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.library.model.LibraryManga
import java.util.Locale

data class DuplicateMangaCandidate(
    val id: Long,
    val title: String,
    val sourceId: Long,
    val thumbnailUrl: String?,
    val chapterCount: Long,
)

data class DuplicateMangaDialogState(
    val target: DuplicateMangaCandidate,
    val candidates: List<DuplicateMangaCandidate>,
    val groupKey: String = "",
)

/**
 * Normalizes a title for duplicate detection. Case, whitespace and punctuation are ignored,
 * while letters (including CJK) and digits are preserved.
 */
fun normalizeDuplicateTitle(title: String): String = title
    .lowercase(Locale.ROOT)
    .filter { it.isLetterOrDigit() }

/**
 * Returns duplicate groups keyed by normalized title. Only titles that appear under more than
 * one source are considered likely duplicates.
 */
fun findDuplicateGroups(items: List<LibraryManga>): Map<String, List<LibraryManga>> = items
    .groupBy { normalizeDuplicateTitle(it.title) }
    .filterKeys { it.isNotEmpty() }
    .filterValues { members -> members.map { it.sourceId }.distinct().size > 1 }

fun LibraryManga.toDuplicateCandidate(): DuplicateMangaCandidate = DuplicateMangaCandidate(
    id = id,
    title = title,
    sourceId = sourceId,
    thumbnailUrl = thumbnailUrl,
    chapterCount = chapterCount,
)

@Composable
fun DuplicateMangaDialog(
    state: DuplicateMangaDialogState,
    sourceNameFor: (Long) -> String,
    onDismissRequest: () -> Unit,
    onAddAnyway: () -> Unit,
    onOpenManga: (Long) -> Unit,
    onMigrate: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag("duplicate-manga-dialog"),
        title = { Text("Possible duplicates") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "The manga you added may already be in your library from a different source.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Added", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = state.target.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = sourceNameFor(state.target.sourceId),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.candidates.forEach { candidate ->
                    DuplicateCandidateRow(
                        candidate = candidate,
                        sourceName = sourceNameFor(candidate.sourceId),
                        onOpen = { onOpenManga(candidate.id) },
                        onMigrate = { onMigrate(candidate.id) },
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag("duplicate-cancel"),
            ) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAddAnyway,
                modifier = Modifier.testTag("duplicate-add-anyway"),
            ) {
                Text("Add anyway")
            }
        },
    )
}

@Composable
private fun DuplicateCandidateRow(
    candidate: DuplicateMangaCandidate,
    sourceName: String,
    onOpen: () -> Unit,
    onMigrate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("duplicate-candidate-${candidate.id}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mihon.desktop.ui.common.MangaCover(
            thumbnailUrl = candidate.thumbnailUrl,
            mangaId = candidate.id,
            contentDescription = candidate.title,
            modifier = Modifier
                .width(54.dp)
                .height(76.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sourceName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${candidate.chapterCount} ${if (candidate.chapterCount == 1L) "chapter" else "chapters"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(
                onClick = onOpen,
                modifier = Modifier.testTag("duplicate-open-${candidate.id}"),
            ) {
                Text("Open")
            }
            Button(
                onClick = onMigrate,
                modifier = Modifier.testTag("duplicate-migrate-${candidate.id}"),
            ) {
                Text("Migrate")
            }
        }
    }
}
