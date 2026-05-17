package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import eu.kanade.tachiyomi.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat

/**
 * View of the ViewPager that contains a video page. Uses ExoPlayer for playback.
 */
@SuppressLint("ViewConstructor")
class VideoPageHolder(
    readerThemedContext: Context,
    val viewer: PagerViewer,
    val page: ReaderPage,
) : FrameLayout(readerThemedContext), ViewPagerAdapter.PositionableView {

    override val item
        get() = page

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var progressIndicator: ReaderProgressIndicator? = null
    private var errorLayout: ReaderErrorBinding? = null
    private val scope = MainScope()
    private var loadJob: Job? = null

    private var playWhenReady = false

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setBackgroundColor(0xFF000000.toInt())
        loadJob = scope.launch { loadVideoAndProcessStatus() }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loadJob?.cancel()
        loadJob = null
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.stop()
        player?.release()
        player = null
        playerView?.player = null
    }

    private fun initProgressIndicator() {
        if (progressIndicator == null) {
            progressIndicator = ReaderProgressIndicator(context)
            addView(progressIndicator)
        }
    }

    private suspend fun loadVideoAndProcessStatus() {
        val videoUrl = page.url
        if (videoUrl.isBlank() || !videoUrl.isVideoUrl()) {
            setError(IllegalArgumentException("Invalid video URL: $videoUrl"))
            return
        }

        initProgressIndicator()
        progressIndicator?.show()

        try {
            val player = ExoPlayer.Builder(context).build()
            this.player = player

            val playerView = PlayerView(context).apply {
                this.player = player
                useController = true
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }
            this.playerView = playerView

            val mediaItem = MediaItem.fromUri(videoUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = playWhenReady

            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            withUIContext {
                                progressIndicator?.hide()
                                if (playerView?.parent == null) {
                                    addView(playerView, 0)
                                }
                            }
                        }
                        Player.STATE_BUFFERING -> {
                            withUIContext {
                                progressIndicator?.show()
                            }
                        }
                        Player.STATE_ENDED -> {
                            player.seekTo(0)
                            player.playWhenReady = false
                        }
                        Player.STATE_IDLE -> {}
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    withUIContext {
                        setError(error)
                    }
                }
            })

            withUIContext {
                addView(playerView)
            }

        } catch (e: Exception) {
            logcat(e)
            withUIContext {
                setError(e)
            }
        }
    }

    fun pause() {
        playWhenReady = player?.playWhenReady ?: false
        player?.playWhenReady = false
    }

    fun resume() {
        player?.playWhenReady = playWhenReady
    }

    private fun setError(error: Throwable?) {
        progressIndicator?.hide()
        if (errorLayout == null) {
            errorLayout = ReaderErrorBinding.inflate(LayoutInflater.from(context), this, true)
            errorLayout?.actionRetry?.viewer = viewer
            errorLayout?.actionRetry?.setOnClickListener {
                releasePlayer()
                loadJob = scope.launch { loadVideoAndProcessStatus() }
            }
        }
        errorLayout?.errorMessage?.text = error?.message ?: "Video playback error"
        errorLayout?.root?.isVisible = true
    }
}

private fun String.isVideoUrl(): Boolean {
    return endsWith(".mp4", true) ||
        endsWith(".m3u8", true) ||
        endsWith(".ts", true) ||
        endsWith(".mkv", true) ||
        endsWith(".webm", true)
}
