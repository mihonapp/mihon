package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import com.f2prateek.rx.preferences.Preference
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.addTo
import rx.subscriptions.CompositeSubscription
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Configuration used by webtoon viewers.
 */
class WebtoonConfig(preferences: PreferencesHelper = Injekt.get()) {

    private val subscriptions = CompositeSubscription()

    var imagePropertyChangedListener: (() -> Unit)? = null

    var tappingEnabled = true
        private set

    var longTapEnabled = true
        private set

    var volumeKeysEnabled = false
        private set

    var volumeKeysInverted = false
        private set

    var imageCropBorders = false
        private set

    var doubleTapAnimDuration = 500
        private set

    var marginRatio = 0f
        private set

    init {
        preferences.readWithTapping()
            .register({ tappingEnabled = it })

        preferences.readWithLongTap()
            .register({ longTapEnabled = it })

        preferences.cropBordersWebtoon()
            .register({ imageCropBorders = it }, { imagePropertyChangedListener?.invoke() })

        preferences.doubleTapAnimSpeed()
            .register({ doubleTapAnimDuration = it })

        preferences.readWithVolumeKeys()
            .register({ volumeKeysEnabled = it })

        preferences.readWithVolumeKeysInverted()
            .register({ volumeKeysInverted = it })

        preferences.marginRatio()
            .register({ marginFromPreference(it) }, { imagePropertyChangedListener?.invoke() })
    }

    private fun marginFromPreference(position: Int) {
        marginRatio = when (position) {
            1 -> PageMargin.TEN_PERCENT
            2 -> PageMargin.TWENTY_FIVE_PERCENT
            else -> PageMargin.NO_MARGIN
        }
    }

    object PageMargin {
        const val NO_MARGIN = 0f
        const val TEN_PERCENT = 0.1f
        const val TWENTY_FIVE_PERCENT = 0.25f
    }

    fun unsubscribe() {
        subscriptions.unsubscribe()
    }

    private fun <T> Preference<T>.register(
            valueAssignment: (T) -> Unit,
            onChanged: (T) -> Unit = {}
    ) {
        asObservable()
            .doOnNext(valueAssignment)
            .skip(1)
            .distinctUntilChanged()
            .doOnNext(onChanged)
            .subscribe()
            .addTo(subscriptions)
    }

}
