package mihon.desktop.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

@Composable
internal fun rememberReaderPointerIcon(visible: Boolean): PointerIcon {
    val hidden = remember {
        runCatching {
            PointerIcon(
                Toolkit.getDefaultToolkit().createCustomCursor(
                    BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                    Point(0, 0),
                    "mihon-reader-hidden",
                ),
            )
        }.getOrDefault(PointerIcon.Default)
    }
    return if (visible) PointerIcon.Default else hidden
}
