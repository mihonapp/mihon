package mihon.reader.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mihon.reader.model.FrameId
import mihon.reader.model.PageId

data class AnimationProbe(
    val frameCount: Int,
    val frameDurationsMillis: List<Long>,
) {
    init {
        require(frameCount > 0) { "frameCount must be positive" }
        require(frameDurationsMillis.size == frameCount) { "every frame needs one duration" }
        require(frameDurationsMillis.all { it >= 0L }) { "frame duration must not be negative" }
    }
}

/** Keeps a single current animation frame lease, replacing it atomically as the animation loops. */
class AnimationCoordinator(
    private val scope: CoroutineScope,
    private val loadFrame: suspend (FrameId) -> AutoCloseable?,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) : AutoCloseable {
    private var page: PageId? = null
    private var probe: AnimationProbe? = null
    private var index = 0
    private var foreground = true
    private var visible = true
    private var lease: AutoCloseable? = null
    private var job: Job? = null
    private var closed = false
    private val _selectedFrame = MutableStateFlow<FrameId?>(null)

    val selectedFrame: StateFlow<FrameId?> = _selectedFrame.asStateFlow()

    fun start(pageId: PageId, probe: AnimationProbe) {
        check(!closed) { "animation coordinator is closed" }
        cancelJobAndLease()
        page = pageId
        this.probe = probe
        index = 0
        launchIfActive()
    }

    fun retry() {
        val currentPage = page ?: return
        val currentProbe = probe ?: return
        start(currentPage, currentProbe)
    }

    fun setForeground(foreground: Boolean) {
        this.foreground = foreground
        updateActivity()
    }

    fun setContentVisible(visible: Boolean) {
        this.visible = visible
        updateActivity()
    }

    fun cancelForPageOrChapterChange() = cancelJobAndLease()

    override fun close() {
        if (closed) return
        closed = true
        cancelJobAndLease()
        page = null
        probe = null
    }

    private fun updateActivity() {
        if (!foreground || !visible) {
            cancelJobAndLease()
        } else {
            launchIfActive()
        }
    }

    private fun launchIfActive() {
        if (closed || !foreground || !visible || page == null || probe == null || job != null) return
        job = scope.launch {
            while (!closed && foreground && visible) {
                val pageId = page ?: return@launch
                val animation = probe ?: return@launch
                val frame = FrameId(pageId, index)
                _selectedFrame.value = frame
                val replacement = loadFrame(frame)
                val previous = lease
                lease = replacement
                previous?.close()
                wait(animation.frameDurationsMillis[index].coerceAtLeast(MIN_FRAME_DELAY_MILLIS))
                index = (index + 1) % animation.frameCount
            }
        }
    }

    private fun cancelJobAndLease() {
        job?.cancel()
        job = null
        lease?.close()
        lease = null
        _selectedFrame.value = null
    }

    private companion object {
        const val MIN_FRAME_DELAY_MILLIS = 1L
    }
}
