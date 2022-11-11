package eu.kanade.domain.source.model

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.extension.ExtensionManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class Source(
    val id: Long,
    val lang: String,
    val name: String,
    val supportsLatest: Boolean,
    val isStub: Boolean,
    val pin: Pins = Pins.unpinned,
    val isUsedLast: Boolean = false,
) {

    val visualName: String
        get() = when {
            lang.isEmpty() -> name
            else -> "$name (${lang.uppercase()})"
        }

    val icon: ImageBitmap?
        get() {
            return Injekt.get<ExtensionManager>().getAppIconForSource(id)
                ?.toBitmap()
                ?.asImageBitmap()
        }

    val key: () -> String = {
        when {
            isUsedLast -> "$id-lastused"
            else -> "$id"
        }
    }
}

sealed class Pin(val code: Int) {
    object Unpinned : Pin(0b00)
    object Pinned : Pin(0b01)
    object Actual : Pin(0b10)
}

inline fun Pins(builder: Pins.PinsBuilder.() -> Unit = {}): Pins {
    return Pins.PinsBuilder().apply(builder).flags()
}

fun Pins(vararg pins: Pin) = Pins {
    pins.forEach { +it }
}

data class Pins(val code: Int = Pin.Unpinned.code) {

    operator fun contains(pin: Pin): Boolean = pin.code and code == pin.code

    operator fun plus(pin: Pin): Pins = Pins(code or pin.code)

    operator fun minus(pin: Pin): Pins = Pins(code xor pin.code)

    companion object {
        val unpinned = Pins(Pin.Unpinned)

        val pinned = Pins(Pin.Pinned, Pin.Actual)
    }

    class PinsBuilder(var code: Int = 0) {
        operator fun Pin.unaryPlus() {
            this@PinsBuilder.code = code or this@PinsBuilder.code
        }

        operator fun Pin.unaryMinus() {
            this@PinsBuilder.code = code or this@PinsBuilder.code
        }

        fun flags(): Pins = Pins(code)
    }
}
