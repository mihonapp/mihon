package eu.kanade.domain.source.model

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.app.di.appGraph
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

suspend fun Source.icon(): ImageBitmap? = withContext(Dispatchers.IO) {
    Injekt.get<Context>().appGraph.extensionManager.getAppIconForSource(id)
        ?.toBitmap()
        ?.asImageBitmap()
}
