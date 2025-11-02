package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.browse.source.RecommendationsScreenModel
import tachiyomi.presentation.core.components.ScrollbarLazyColumn

@Composable
fun RecommendationsScreen(
    state: RecommendationsScreenModel.State,
    contentPadding: PaddingValues,
    onGetRecommendations: () -> Unit,
    onClearRecommendations: () -> Unit,
) {
    ScrollbarLazyColumn(
        contentPadding = contentPadding,
    ) {
        // Top content: Buttons and status
        item {
            Column {
                // Button row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isLoadingRecommendations) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    }

                    Button(
                        onClick = onGetRecommendations,
                        enabled = !state.isLoadingRecommendations,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(if (state.aiRecommendations != null) "Refresh" else "Get Recommendations")
                    }

                    if (state.parsedRecommendations.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onClearRecommendations,
                            enabled = !state.isLoadingRecommendations
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = null
                            )
                        }
                    }
                }

                // Recommendation count
                if (state.parsedRecommendations.isNotEmpty()) {
                    Text(
                        text = "${state.parsedRecommendations.size} Recommendations",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Error display
                state.recommendationsError?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Empty state
                if (state.aiRecommendations == null && 
                    state.recommendationsError == null && 
                    !state.isLoadingRecommendations) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "🤖 AI-Powered Recommendations",
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Get personalized manga recommendations based on your library. The AI will analyze your reading preferences and suggest titles you might enjoy.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Main content - show parsed recommendations as copyable list
        if (state.parsedRecommendations.isNotEmpty()) {
            items(
                items = state.parsedRecommendations,
                key = { "recommendation-${it.hashCode()}" },
            ) { title ->
                RecommendationItem(
                    title = title,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun RecommendationItem(
    title: String,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(title))
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy title",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}