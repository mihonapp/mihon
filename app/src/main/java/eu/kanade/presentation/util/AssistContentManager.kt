package eu.kanade.presentation.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

interface AssistContentRoute

class AssistContentManager {
    var currentAssistUrl by mutableStateOf<String?>(null)
}

val LocalAssistContentManager = staticCompositionLocalOf { AssistContentManager() }
