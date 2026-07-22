package eu.kanade.domain.source.model

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import mihon.app.di.appGraph
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val Source.icon: ImageBitmap?
    get() {
        return Injekt.get<Context>().appGraph.extensionManager.getAppIconForSource(id)
            ?.toBitmap()
            ?.asImageBitmap()
    }
