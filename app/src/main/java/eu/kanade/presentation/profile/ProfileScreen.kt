package eu.kanade.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AfragaNeon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.NeonPill
import eu.kanade.presentation.components.SplitColorHeader
import eu.kanade.presentation.components.glassSurface
import eu.kanade.tachiyomi.ui.library.LibraryItem
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.rounded.Schedule
import mihon.icons.materialsymbols.rounded.Settings
import mihon.icons.materialsymbols.rounded.Edit
import mihon.icons.materialsymbols.rounded.Photo
import mihon.icons.materialsymbols.rounded.Person
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ProfileScreen(
    items: List<LibraryItem>,
    avatarUri: String?,
    bannerUri: String?,
    username: String,
    bio: String,
    onPickAvatar: () -> Unit,
    onPickBanner: () -> Unit,
    onSaveIdentity: (String, String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    onOpenUpdates: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenManga: (Long) -> Unit,
) {
    var showIdentityEditor by remember { mutableStateOf(false) }
    val readingItems = items.filter { it.libraryManga.hasStarted && it.unreadCount > 0 }
    val notStartedItems = items.filter { !it.libraryManga.hasStarted && it.unreadCount > 0 }
    val completedCount = items.count { it.libraryManga.manga.status == SManga.COMPLETED.toLong() }
    val unreadCount = items.sumOf { it.unreadCount }

    Scaffold(
        topBar = {
            AppBar(title = stringResource(MR.strings.profile_title))
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                ProfileHero(
                    featured = items.firstOrNull(),
                    avatarUri = avatarUri,
                    bannerUri = bannerUri,
                    username = username,
                    bio = bio,
                    seriesCount = items.size,
                    unreadCount = unreadCount,
                    onPickAvatar = onPickAvatar,
                    onPickBanner = onPickBanner,
                    onEditProfile = { showIdentityEditor = true },
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NeonPill(
                        text = stringResource(MR.strings.profile_saved_count, items.size),
                        tone = AfragaNeon.Purple,
                        modifier = Modifier.weight(1f),
                    )
                    NeonPill(
                        text = stringResource(MR.strings.profile_reading_count, readingItems.size),
                        tone = AfragaNeon.Emerald,
                        modifier = Modifier.weight(1f),
                    )
                    NeonPill(
                        text = stringResource(MR.strings.profile_completed_count, completedCount),
                        tone = AfragaNeon.Cyan,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (readingItems.isNotEmpty() || notStartedItems.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BentoFolder(
                            title = "Currently reading",
                            items = readingItems,
                            modifier = Modifier.weight(1f),
                            onOpenManga = onOpenManga,
                        )
                        BentoFolder(
                            title = "Not started",
                            items = notStartedItems,
                            modifier = Modifier.weight(1f),
                            onOpenManga = onOpenManga,
                        )
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SplitColorHeader(
                        firstWord = stringResource(MR.strings.profile_activity_first),
                        secondWord = stringResource(MR.strings.profile_activity_second),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeonPill(
                            text = stringResource(MR.strings.label_recent_updates),
                            tone = AfragaNeon.Amber,
                            icon = MaterialSymbols.Rounded.Refresh,
                            onClick = onOpenUpdates,
                        )
                        NeonPill(
                            text = stringResource(MR.strings.history),
                            tone = AfragaNeon.Cyan,
                            icon = MaterialSymbols.Rounded.Schedule,
                            onClick = onOpenHistory,
                        )
                        NeonPill(
                            text = stringResource(MR.strings.label_settings),
                            tone = AfragaNeon.Purple,
                            icon = MaterialSymbols.Rounded.Settings,
                            onClick = onOpenSettings,
                        )
                    }
                }
            }
        }
    }
    if (showIdentityEditor) {
        var editedUsername by remember(username) { mutableStateOf(username) }
        var editedBio by remember(bio) { mutableStateOf(bio) }
        AlertDialog(
            onDismissRequest = { showIdentityEditor = false },
            title = { Text("Edit profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editedUsername,
                        onValueChange = { editedUsername = it },
                        label = { Text("Display name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = editedBio,
                        onValueChange = { editedBio = it },
                        label = { Text("Bio") },
                        minLines = 2,
                        maxLines = 3,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveIdentity(editedUsername, editedBio)
                        showIdentityEditor = false
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showIdentityEditor = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProfileHero(
    featured: LibraryItem?,
    avatarUri: String?,
    bannerUri: String?,
    username: String,
    bio: String,
    seriesCount: Int,
    unreadCount: Long,
    onPickAvatar: () -> Unit,
    onPickBanner: () -> Unit,
    onEditProfile: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(286.dp)
            .clip(shape)
            .background(Color(0xFF0B0D14))
            .clickable(onClick = onPickBanner),
    ) {
        val bannerModel = bannerUri ?: featured?.libraryManga?.manga?.let { manga ->
            MangaCover(
                mangaId = manga.id,
                sourceId = manga.source,
                isMangaFavorite = manga.favorite,
                url = manga.thumbnailUrl,
                lastModified = manga.coverLastModified,
            )
        }
        if (bannerModel != null) {
            AsyncImage(
                model = bannerModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onPickBanner)
                    .blur(4.dp)
                    .alpha(0.56f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x260B0D14),
                        1f to Color(0xEE0B0D14),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp)
                .glassSurface(
                    shape = RoundedCornerShape(22.dp),
                    tint = Color(0x99191E30),
                    glow = Color(0xFF00B0FF),
                    cornerRadius = 22.dp,
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF182035))
                        .clickable(onClick = onPickAvatar),
                ) {
                    if (avatarUri != null) {
                        AsyncImage(
                            model = avatarUri,
                            contentDescription = "Choose profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        androidx.compose.material3.Icon(
                            imageVector = MaterialSymbols.Rounded.Person,
                            contentDescription = "Choose profile photo",
                            tint = Color(0xFFB388FF),
                            modifier = Modifier.align(Alignment.Center).size(38.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF007BFF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = MaterialSymbols.Rounded.Photo,
                            contentDescription = "Edit profile photo",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                NeonPill(
                    text = "Edit profile",
                    tone = AfragaNeon.Cyan,
                    icon = MaterialSymbols.Rounded.Edit,
                    onClick = onEditProfile,
                )
            }
            Text(
                text = stringResource(MR.strings.profile_local_label).uppercase(),
                color = Color(0xFF40C4FF),
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.5.sp,
            )
            Text(
                text = username,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = bio.ifBlank { stringResource(MR.strings.profile_hero_title) },
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(MR.strings.profile_hero_subtitle, seriesCount, unreadCount),
                color = Color.White.copy(alpha = 0.64f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun BentoFolder(
    title: String,
    items: List<LibraryItem>,
    modifier: Modifier = Modifier,
    onOpenManga: (Long) -> Unit,
) {
    Column(
        modifier = modifier
            .height(206.dp)
            .glassSurface(
                shape = RoundedCornerShape(20.dp),
                tint = Color(0xB2161F33),
                border = Color.White.copy(alpha = 0.13f),
                cornerRadius = 20.dp,
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(MR.strings.information_no_entries_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        } else {
            items.take(4).chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { item ->
                        val manga = item.libraryManga.manga
                        AsyncImage(
                            model = MangaCover(
                                mangaId = manga.id,
                                sourceId = manga.source,
                                isMangaFavorite = manga.favorite,
                                url = manga.thumbnailUrl,
                                lastModified = manga.coverLastModified,
                            ),
                            contentDescription = manga.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenManga(item.id) },
                        )
                    }
                    if (rowItems.size == 1) {
                        Box(modifier = Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
    }
}
