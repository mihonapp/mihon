package eu.kanade.tachiyomi.ui.customsource

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import tachiyomi.presentation.core.components.material.Scaffold

/**
 * Entry screen for creating a custom novel source via WebView element selection.
 */
class CreateCustomSourceScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current

        var showUrlDialog by remember { mutableStateOf(false) }
        var websiteUrl by remember { mutableStateOf("") }

        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header
                Icon(
                    Icons.Filled.Code,
                    contentDescription = null,
                    modifier = Modifier.height(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Create Custom Source",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Build your own novel source by selecting elements from a website",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Method selection cards
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    onClick = { showUrlDialog = true },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Launch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(modifier = Modifier.weight(0.1f))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Guided WebView Selector",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "Navigate to a novel website and select elements step-by-step",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Alternative: Import JSON config
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    onClick = {
                        // TODO: Import JSON config
                        Toast.makeText(context, "Import feature coming soon", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(modifier = Modifier.weight(0.1f))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Import Configuration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = "Import a JSON configuration file from another user",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Info text
                Text(
                    text = "The guided selector will walk you through:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                listOf(
                    "1. Selecting trending/popular novels section",
                    "2. Selecting new/latest novels section",
                    "3. Setting up search functionality",
                    "4. Identifying pagination patterns",
                    "5. Selecting novel card elements (cover, title)",
                    "6. Selecting novel details (description, tags)",
                    "7. Selecting chapter list elements",
                    "8. Selecting chapter content area",
                ).forEach { step ->
                    Text(
                        text = step,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // URL input dialog
        if (showUrlDialog) {
            AlertDialog(
                onDismissRequest = { showUrlDialog = false },
                title = { Text("Enter Website URL") },
                text = {
                    Column {
                        Text(
                            text = "Enter the URL of the novel website you want to create a source for:",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = websiteUrl,
                            onValueChange = { websiteUrl = it },
                            label = { Text("Website URL") },
                            placeholder = { Text("https://example.com") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (websiteUrl.isNotBlank()) {
                                val url = if (!websiteUrl.startsWith("http")) {
                                    "https://$websiteUrl"
                                } else {
                                    websiteUrl
                                }
                                showUrlDialog = false
                                navigator.push(ElementSelectorVoyagerScreen(url))
                            }
                        },
                        enabled = websiteUrl.isNotBlank(),
                    ) {
                        Text("Start Wizard")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUrlDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

/**
 * Voyager screen wrapper for the Element Selector
 */
class ElementSelectorVoyagerScreen(
    private val initialUrl: String,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { ElementSelectorScreenModel(initialUrl) }
        val state by screenModel.state.collectAsState()

        // Handle success
        LaunchedEffect(state.savedSuccessfully) {
            if (state.savedSuccessfully) {
                Toast.makeText(context, "Custom source created successfully!", Toast.LENGTH_LONG).show()
                navigator.popUntilRoot()
            }
        }

        // Handle error
        LaunchedEffect(state.error) {
            state.error?.let { error ->
                Toast.makeText(context, "Error: $error", Toast.LENGTH_LONG).show()
                screenModel.clearError()
            }
        }

        ElementSelectorScreen(
            initialUrl = initialUrl,
            onNavigateUp = { navigator.pop() },
            onSaveConfig = { config ->
                screenModel.saveConfig(config)
            },
        )
    }
}
