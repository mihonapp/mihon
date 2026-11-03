package mihon.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.view.setComposeContent
import tachiyomi.core.common.profile.Profile
import tachiyomi.core.common.profile.ProfileManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * "Who's reading?" screen. Choosing another profile restarts the app into it, because the database and the
 * preferences are opened once per process.
 */
class ProfilePickerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setComposeContent {
            var profiles by remember { mutableStateOf(ProfileManager.profiles(this)) }
            var managing by remember { mutableStateOf(false) }
            var editing by remember { mutableStateOf<Profile?>(null) }
            var creating by remember { mutableStateOf(false) }
            var deleting by remember { mutableStateOf<Profile?>(null) }
            val activeId = ProfileManager.activeId(this)

            fun refresh() {
                profiles = ProfileManager.profiles(this)
            }

            BackHandler(enabled = managing) { managing = false }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(
                        if (managing) MR.strings.profile_manage_title else MR.strings.profile_picker_title,
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(32.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        ProfileTile(
                            name = profile.name,
                            color = avatarColor(profile.color),
                            label = profile.name.firstOrNull()?.uppercase().orEmpty(),
                            badge = managing,
                            onClick = {
                                if (managing) editing = profile else choose(profile)
                            },
                        )
                    }
                    if (profiles.size < ProfileManager.MAX_PROFILES) {
                        item(key = "add") {
                            ProfileTile(
                                name = stringResource(MR.strings.profile_add),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                label = "+",
                                onClick = { creating = true },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
                TextButton(onClick = { managing = !managing }) {
                    Text(
                        stringResource(if (managing) MR.strings.profile_done else MR.strings.profile_manage),
                    )
                }
            }

            if (creating) {
                ProfileDialog(
                    initial = null,
                    isActive = false,
                    onDismiss = { creating = false },
                    onConfirm = { name, color ->
                        ProfileManager.add(this, name, color)
                        refresh()
                        creating = false
                    },
                )
            }
            editing?.let { profile ->
                ProfileDialog(
                    initial = profile,
                    isActive = profile.id == activeId,
                    onDismiss = { editing = null },
                    onConfirm = { name, color ->
                        ProfileManager.update(this, profile.id, name, color)
                        refresh()
                        editing = null
                    },
                    onDelete = if (profile.id != ProfileManager.DEFAULT_ID && profile.id != activeId) {
                        {
                            editing = null
                            deleting = profile
                        }
                    } else {
                        null
                    },
                )
            }
            deleting?.let { profile ->
                AlertDialog(
                    onDismissRequest = { deleting = null },
                    title = { Text(stringResource(MR.strings.profile_delete)) },
                    text = { Text(stringResource(MR.strings.profile_delete_confirm, profile.name)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                ProfileManager.delete(this, profile.id)
                                refresh()
                                deleting = null
                            },
                        ) { Text(stringResource(MR.strings.action_delete)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleting = null }) { Text(stringResource(MR.strings.action_cancel)) }
                    },
                )
            }
        }
    }

    private fun choose(profile: Profile) {
        if (profile.id == ProfileManager.activeId(this)) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        } else {
            ProfileManager.setActive(this, profile.id)
            restartApp(this)
        }
    }

    companion object {
        fun show(context: Context) {
            context.startActivity(Intent(context, ProfilePickerActivity::class.java))
        }

        /** Starts the app again in a fresh process, which opens the newly selected profile. */
        fun restartApp(context: Context) {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
            Process.killProcess(Process.myPid())
        }
    }
}

private val AvatarColors = listOf(
    Color(0xFFE50914),
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFFFB8C00),
    Color(0xFF8E24AA),
    Color(0xFF00ACC1),
)

private fun avatarColor(index: Int) = AvatarColors[index.mod(AvatarColors.size)]

@Composable
private fun ProfileTile(
    name: String,
    color: Color,
    label: String,
    onClick: () -> Unit,
    badge: Boolean = false,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            if (badge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) { Text("✎", color = Color.White, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(96.dp),
        )
    }
}

@Composable
private fun ProfileDialog(
    initial: Profile?,
    isActive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: Int) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) MR.strings.profile_add else MR.strings.profile_edit))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text(stringResource(MR.strings.profile_name)) },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarColors.forEachIndexed { index, c ->
                        Box(
                            modifier = Modifier
                                .size(if (index == color) 36.dp else 28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .clickable { color = index },
                        )
                    }
                }
                if (isActive) {
                    Text(
                        stringResource(MR.strings.profile_active_note),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(MR.strings.profile_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, color) }, enabled = name.isNotBlank()) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}
