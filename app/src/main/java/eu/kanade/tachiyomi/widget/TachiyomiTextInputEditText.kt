package eu.kanade.tachiyomi.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import androidx.core.view.inputmethod.EditorInfoCompat
import com.google.android.material.textfield.TextInputEditText
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.widget.TachiyomiTextInputEditText.Companion.setIncognito
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A custom [TextInputEditText] that sets [EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING] to imeOptions
 * if [BasePreferences.incognitoMode] is true. Some IMEs may not respect this flag.
 *
 * @see setIncognito
 */
class TachiyomiTextInputEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.editTextStyle,
) : TextInputEditText(context, attrs, defStyleAttr) {

    private var scope: CoroutineScope? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        setIncognito(scope!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope?.cancel()
        scope = null
    }

    companion object {
        /**
         * Sets Flow to this [EditText] that sets [EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING] to imeOptions
         * if [BasePreferences.incognitoMode] is true. Some IMEs may not respect this flag.
         */
        fun EditText.setIncognito(viewScope: CoroutineScope) {
            Injekt.get<BasePreferences>().incognitoMode().changes()
                .onEach {
                    imeOptions = if (it) {
                        imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
                    } else {
                        imeOptions and EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING.inv()
                    }
                }
                .launchIn(viewScope)
        }
    }
}
