package exh.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

abstract class BaseExhController<VB : ViewBinding>(bundle: Bundle? = null) : BaseController<VB>(bundle), CoroutineScope {
    abstract val layoutId: Int
        @LayoutRes get

    override val coroutineContext: CoroutineContext = Job() + Dispatchers.Default

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(layoutId, container, false)
    }

    override fun onDestroy() {
        super.onDestroy()

        cancel()
    }
}
