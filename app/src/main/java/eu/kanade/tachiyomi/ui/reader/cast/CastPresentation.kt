package eu.kanade.tachiyomi.ui.reader.cast

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR

/**
 * Full screen window on an external display that renders the cast state with a [CastPageView].
 *
 * It is created with the application context rather than an activity: presentation windows are
 * allowed on public presentation displays without an activity token, which keeps the cast alive
 * while the reader activity is recreated.
 */
class CastPresentation(
    context: Context,
    display: Display,
    private val controller: CastController,
) : Presentation(context, display, android.R.style.Theme_Material_NoActionBar_Fullscreen) {

    private var pageView: CastPageView? = null
    private var scope: CoroutineScope? = null
    private var decodeListener: ((CastPageInfo) -> Unit)? = null
    private var released = false
    private var started = false

    init {
        setOnDismissListener {
            // Only the system dismisses us without going through release(): the display is gone.
            if (!released) controller.stopCasting(MR.strings.cast_display_disconnected)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.let(::setUpWindow)
        val view = CastPageView(context, controller)
        setContentView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        pageView = view
    }

    override fun onStart() {
        super.onStart()
        started = true
        val view = pageView ?: return
        stopCollecting()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        this.scope = scope
        val listener: (CastPageInfo) -> Unit = { view.postInvalidate() }
        decodeListener = listener
        controller.images.onImageDecoded = listener
        controller.state
            .onEach { view.setState(it) }
            .launchIn(scope)
    }

    override fun onStop() {
        started = false
        stopCollecting()
        super.onStop()
    }

    /**
     * Dismisses the presentation on behalf of the controller, i.e. without reporting a lost
     * display. Also cleans up when [show] failed before the window was attached.
     */
    fun release() {
        released = true
        stopCollecting()
        when {
            isShowing -> dismiss()
            // show() failed after onStart(): dismiss() is a no-op, but the framework's display
            // listener registered in Presentation.onStart() still has to be released.
            started -> onStop()
        }
    }

    private fun stopCollecting() {
        scope?.cancel()
        scope = null
        val listener = decodeListener ?: return
        decodeListener = null
        if (controller.images.onImageDecoded === listener) {
            controller.images.onImageDecoded = null
        }
    }

    private fun setUpWindow(window: Window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "Unable to hide the system bars of the cast presentation" }
        }
    }
}
