package eu.kanade.domain.source.model

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import mihon.app.di.appGraph
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

suspend fun Source.icon(): ImageBitmap? = withIOContext {
    Injekt.get<Context>().appGraph.extensionManager.getAppIconForSource(id)
        ?.toBitmap()
        ?.asImageBitmap()
}
