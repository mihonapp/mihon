package eu.kanade.tachiyomi.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.profile.ProfileScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.library.LibraryViewModel
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Person
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

data object ProfileTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 1u,
            title = stringResource(MR.strings.profile_title),
            icon = rememberVectorPainter(MaterialSymbols.Rounded.Person),
        )

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val tabNavigator = LocalTabNavigator.current
        val profilePreferences = remember(context) { ProfilePreferences(context) }
        var avatarUri by remember { mutableStateOf(profilePreferences.avatarUri) }
        var bannerUri by remember { mutableStateOf(profilePreferences.bannerUri) }
        var username by remember { mutableStateOf(profilePreferences.username) }
        var bio by remember { mutableStateOf(profilePreferences.bio) }

        val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                profilePreferences.setAvatarUri(uri)
                avatarUri = uri.toString()
            }
        }
        val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                profilePreferences.setBannerUri(uri)
                bannerUri = uri.toString()
            }
        }
        val pickImage = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        val viewModel = metroViewModel<LibraryViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        ProfileScreen(
            items = state.libraryData.favorites,
            avatarUri = avatarUri,
            bannerUri = bannerUri,
            username = username,
            bio = bio,
            onPickAvatar = { avatarPicker.launch(pickImage) },
            onPickBanner = { bannerPicker.launch(pickImage) },
            onSaveIdentity = { newUsername, newBio ->
                profilePreferences.setIdentity(newUsername, newBio)
                username = profilePreferences.username
                bio = profilePreferences.bio
            },
            onOpenUpdates = { tabNavigator.current = UpdatesTab },
            onOpenHistory = { tabNavigator.current = HistoryTab },
            onOpenSettings = { tabNavigator.current = MoreTab },
            onOpenManga = { navigator.push(MangaScreen(it)) },
        )

        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) (context as? MainActivity)?.ready = true
        }
    }
}
