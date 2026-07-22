package mihon.app.di

import android.content.Context
import mihon.core.metro.metroGraph

val Context.appGraph get() = metroGraph<AppGraph>()
