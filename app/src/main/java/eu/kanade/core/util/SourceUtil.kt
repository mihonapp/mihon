package eu.kanade.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import mihon.app.di.appGraph
import tachiyomi.domain.source.service.SourceManager

@Composable
fun ifSourcesLoaded(): Boolean {
    val context = LocalContext.current
    return remember { context.appGraph.sourceManager.isInitialized }.collectAsState().value
}
