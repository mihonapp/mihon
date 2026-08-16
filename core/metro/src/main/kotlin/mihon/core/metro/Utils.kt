package mihon.core.metro

import android.content.Context

@Suppress("UNCHECKED_CAST")
fun <T> Context.metroGraph(): T = (applicationContext as GraphProvider<T>).graph
