package eu.kanade.tachiyomi.ui.base.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.view.hideKeyboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

abstract class BaseController<VB : ViewBinding>(bundle: Bundle? = null) : Controller(bundle) {

    protected lateinit var binding: VB
        private set

    lateinit var viewScope: CoroutineScope

    init {
        retainViewMode = RetainViewMode.RETAIN_DETACH

        addLifecycleListener(
            object : LifecycleListener() {
                override fun postCreateView(controller: Controller, view: View) {
                    onViewCreated(view)
                }

                override fun preCreateView(controller: Controller) {
                    viewScope = MainScope()
                    logcat { "Create view for ${controller.instance()}" }
                }

                override fun preAttach(controller: Controller, view: View) {
                    logcat { "Attach view for ${controller.instance()}" }
                }

                override fun preDetach(controller: Controller, view: View) {
                    logcat { "Detach view for ${controller.instance()}" }
                }

                override fun preDestroyView(controller: Controller, view: View) {
                    viewScope.cancel()
                    logcat { "Destroy view for ${controller.instance()}" }
                }
            },
        )
    }

    abstract fun createBinding(inflater: LayoutInflater): VB

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup, savedViewState: Bundle?): View {
        binding = createBinding(inflater)
        return binding.root
    }

    open fun onViewCreated(view: View) {}

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        view?.hideKeyboard()

        if (type.isEnter) {
            setTitle()
            setHasOptionsMenu(true)
        }

        super.onChangeStarted(handler, type)
    }

    open fun getTitle(): String? {
        return null
    }

    fun setTitle(title: String? = null) {
        (activity as? AppCompatActivity)?.supportActionBar?.title = title ?: getTitle()
    }

    private fun Controller.instance(): String {
        return "${javaClass.simpleName}@${Integer.toHexString(hashCode())}"
    }
}
