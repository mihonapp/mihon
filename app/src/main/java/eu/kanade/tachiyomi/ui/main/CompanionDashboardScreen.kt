package eu.kanade.tachiyomi.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.core.dualscreen.DualScreenState
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object CompanionDashboardScreen : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { CompanionDashboardScreenModel() }
        val state = screenModel.state.collectAsState().value

        Box(modifier = Modifier.fillMaxSize()) {
            if (state.lastRead != null) {
                AsyncImage(
                    model = state.lastRead.coverData,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.15f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Column {
                        Text(
                            text = "Companion Dashboard",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Your reading hub",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.lastRead != null) {
                    item {
                        StatsRow(state.dailyReadCount, state.totalReadCount)
                    }

                    if (state.downloadQueue.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Download Queue")
                            DownloadQueueCard(downloads = state.downloadQueue)
                        }
                    }

                    item {
                        SectionHeader(title = "Continue Reading")
                        HeroResumeCard(history = state.lastRead)
                    }
                }

                if (state.recentUpdates.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Recent Updates")
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column {
                                val updates = state.recentUpdates.take(5)
                                for (index in updates.indices) {
                                    val update = updates[index]
                                    UpdateItem(update = update)
                                    if (index < updates.size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 60.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SectionHeader(title: String) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    @Composable
    private fun DownloadQueueCard(downloads: List<eu.kanade.tachiyomi.data.download.model.Download>) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { DualScreenState.openScreen(eu.kanade.tachiyomi.ui.download.DownloadQueueScreen) },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${downloads.size} items in queue",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(downloads.take(10)) { download ->
                        MangaCover.Square(
                            data = download.manga.asMangaCover(),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                    if (downloads.size > 10) {
                        item {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(4.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${downloads.size - 10}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun StatsRow(dailyCount: Int, totalCount: Int) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Read Today",
                value = dailyCount.toString(),
                icon = Icons.Outlined.Schedule
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Total Read",
                value = totalCount.toString(),
                icon = Icons.Outlined.Book
            )
        }
    }

    @Composable
    private fun StatCard(modifier: Modifier = Modifier, title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }

    @Composable
    private fun HeroResumeCard(history: HistoryWithRelations) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { DualScreenState.openScreen(eu.kanade.tachiyomi.ui.manga.MangaScreen(history.mangaId)) },
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.height(140.dp)
            ) {
                MangaCover.Book(
                    data = history.coverData,
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(140.dp)
                )
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = history.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(MR.strings.display_mode_chapter, formatChapterNumber(history.chapterNumber)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = relativeDateText(history.readAt?.time?.toLocalDate()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @Composable
    private fun UpdateItem(update: UpdatesWithRelations) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { DualScreenState.openScreen(eu.kanade.tachiyomi.ui.manga.MangaScreen(update.mangaId)) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MangaCover.Square(
                data = update.coverData,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(
                    text = update.mangaTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = update.chapterName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

class CompanionDashboardScreenModel(
    private val getHistory: GetHistory = Injekt.get(),
    private val getUpdates: GetUpdates = Injekt.get(),
    private val downloadManager: eu.kanade.tachiyomi.data.download.DownloadManager = Injekt.get(),
) : StateScreenModel<CompanionDashboardScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            getHistory.subscribe("")
                .collectLatest { history ->
                    val lastRead = history.firstOrNull()
                    val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val dailyCount = history.count { (it.readAt?.time ?: 0L) >= todayStart }
                    
                    mutableState.update { 
                        it.copy(
                            lastRead = lastRead, 
                            dailyReadCount = dailyCount,
                            totalReadCount = history.size
                        ) 
                    }
                }
        }

        screenModelScope.launch {
            val limit = ZonedDateTime.now().minusDays(7).toInstant()
            getUpdates.subscribe(
                instant = limit,
                unread = null,
                started = null,
                bookmarked = null,
                hideExcludedScanlators = false
            ).collectLatest { updates ->
                mutableState.update { it.copy(recentUpdates = updates.take(10)) }
            }
        }

        screenModelScope.launch {
            downloadManager.queueState.collectLatest { downloads ->
                mutableState.update { it.copy(downloadQueue = downloads) }
            }
        }
    }

    data class State(
        val lastRead: HistoryWithRelations? = null,
        val recentUpdates: List<UpdatesWithRelations> = emptyList(),
        val dailyReadCount: Int = 0,
        val totalReadCount: Int = 0,
        val downloadQueue: List<eu.kanade.tachiyomi.data.download.model.Download> = emptyList()
    )
}