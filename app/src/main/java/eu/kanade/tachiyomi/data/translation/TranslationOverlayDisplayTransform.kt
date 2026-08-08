package eu.kanade.tachiyomi.data.translation

import tachiyomi.data.Translation_boxes
import kotlin.math.max
import kotlin.math.min

enum class TranslationOverlayRotation {
    None,
    Clockwise90,
    CounterClockwise90,
}

enum class TranslationOverlaySourceHalf {
    Left,
    Right,
}

data class TranslationOverlayDisplayTransform(
    val rotation: TranslationOverlayRotation = TranslationOverlayRotation.None,
    val reflowTopSourceHalf: TranslationOverlaySourceHalf? = null,
) {
    init {
        require(rotation == TranslationOverlayRotation.None || reflowTopSourceHalf == null) {
            "Rotation and reflow transforms are mutually exclusive"
        }
    }

    companion object {
        val Identity = TranslationOverlayDisplayTransform()
        val Clockwise90 = TranslationOverlayDisplayTransform(rotation = TranslationOverlayRotation.Clockwise90)
        val CounterClockwise90 = TranslationOverlayDisplayTransform(rotation = TranslationOverlayRotation.CounterClockwise90)

        fun reflowed(topSourceHalf: TranslationOverlaySourceHalf): TranslationOverlayDisplayTransform {
            return TranslationOverlayDisplayTransform(reflowTopSourceHalf = topSourceHalf)
        }
    }
}

object TranslationOverlayDisplayTransformResolver {
    fun forWebtoon(
        isWideImage: Boolean,
        rotateToFit: Boolean,
        rotateToFitInverted: Boolean,
        splitDoublePage: Boolean,
        splitDoublePageInverted: Boolean,
    ): TranslationOverlayDisplayTransform {
        if (!isWideImage) return TranslationOverlayDisplayTransform.Identity

        return when {
            rotateToFit -> {
                if (rotateToFitInverted) {
                    TranslationOverlayDisplayTransform.CounterClockwise90
                } else {
                    TranslationOverlayDisplayTransform.Clockwise90
                }
            }
            splitDoublePage -> {
                val topSourceHalf = if (splitDoublePageInverted) {
                    TranslationOverlaySourceHalf.Left
                } else {
                    TranslationOverlaySourceHalf.Right
                }
                TranslationOverlayDisplayTransform.reflowed(topSourceHalf)
            }
            else -> TranslationOverlayDisplayTransform.Identity
        }
    }
}

data class TranslationOverlaySourceBox(
    val sourceBoxId: Long,
    val sourcePageId: Long,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val originalText: String,
    val translatedText: String,
    val textType: String,
    val confidence: Double?,
    val styleJson: String?,
)

data class TranslationOverlayDisplayBox(
    val sourceBoxId: Long,
    val sourcePageId: Long,
    val sourceX: Float,
    val sourceY: Float,
    val sourceWidth: Float,
    val sourceHeight: Float,
    val displayX: Float,
    val displayY: Float,
    val displayWidth: Float,
    val displayHeight: Float,
    val originalText: String,
    val translatedText: String,
    val textType: String,
    val confidence: Double?,
    val styleJson: String?,
    val fragmentOrdinal: Int = 0,
    val fragmentCount: Int = 1,
    val drawTranslatedText: Boolean = true,
)

data class TranslationOverlayRenderCacheKey(
    val displayTransform: TranslationOverlayDisplayTransform,
    val displayBoxes: List<TranslationOverlayDisplayBox>,
    val pageViewReady: Boolean,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val imageLeftBits: Int,
    val imageTopBits: Int,
    val imageWidthBits: Int,
    val imageHeightBits: Int,
    val viewWidth: Int,
    val viewHeight: Int,
) {
    val displayBoxesHash: Int
        get() = displayBoxes.hashCode()

    companion object {
        fun create(
            displayBoxes: List<TranslationOverlayDisplayBox>,
            displayTransform: TranslationOverlayDisplayTransform,
            pageViewReady: Boolean,
            sourceWidth: Int,
            sourceHeight: Int,
            imageLeft: Float,
            imageTop: Float,
            imageWidth: Float,
            imageHeight: Float,
            viewWidth: Int,
            viewHeight: Int,
        ): TranslationOverlayRenderCacheKey {
            return TranslationOverlayRenderCacheKey(
                displayTransform = displayTransform,
                displayBoxes = displayBoxes,
                pageViewReady = pageViewReady,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                imageLeftBits = imageLeft.toBits(),
                imageTopBits = imageTop.toBits(),
                imageWidthBits = imageWidth.toBits(),
                imageHeightBits = imageHeight.toBits(),
                viewWidth = viewWidth,
                viewHeight = viewHeight,
            )
        }
    }
}

object TranslationOverlayDisplayTransformer {
    fun transform(
        boxes: List<TranslationOverlaySourceBox>,
        transform: TranslationOverlayDisplayTransform,
    ): List<TranslationOverlayDisplayBox> {
        return boxes.flatMap { box ->
            when {
                transform.reflowTopSourceHalf != null -> reflow(box, transform.reflowTopSourceHalf)
                transform.rotation == TranslationOverlayRotation.Clockwise90 -> rotateClockwise(box)
                transform.rotation == TranslationOverlayRotation.CounterClockwise90 -> rotateCounterClockwise(box)
                else -> identity(box)
            }
        }
    }

    fun fromPersisted(
        boxes: List<Translation_boxes>,
        transform: TranslationOverlayDisplayTransform,
    ): List<TranslationOverlayDisplayBox> {
        return transform(
            boxes = boxes.map { it.toSourceBox() },
            transform = transform,
        )
    }

    private fun identity(box: TranslationOverlaySourceBox): List<TranslationOverlayDisplayBox> {
        return listOfNotNull(
            createClampedDisplayBox(
                box = box,
                displayX = box.x,
                displayY = box.y,
                displayWidth = box.width,
                displayHeight = box.height,
            ),
        )
    }

    private fun rotateClockwise(box: TranslationOverlaySourceBox): List<TranslationOverlayDisplayBox> {
        return listOfNotNull(
            createClampedDisplayBox(
                box = box,
                displayX = 1f - (box.y + box.height),
                displayY = box.x,
                displayWidth = box.height,
                displayHeight = box.width,
            ),
        )
    }

    private fun rotateCounterClockwise(box: TranslationOverlaySourceBox): List<TranslationOverlayDisplayBox> {
        return listOfNotNull(
            createClampedDisplayBox(
                box = box,
                displayX = box.y,
                displayY = 1f - (box.x + box.width),
                displayWidth = box.height,
                displayHeight = box.width,
            ),
        )
    }

    private fun reflow(
        box: TranslationOverlaySourceBox,
        topSourceHalf: TranslationOverlaySourceHalf,
    ): List<TranslationOverlayDisplayBox> {
        val fragments = buildList {
            val leftWidth = min(box.x + box.width, 0.5f) - box.x
            if (leftWidth > 0f) {
                add(
                    Fragment(
                        sourceX = box.x,
                        sourceY = box.y,
                        sourceWidth = leftWidth,
                        sourceHeight = box.height,
                        sourceHalf = TranslationOverlaySourceHalf.Left,
                    ),
                )
            }

            val rightStart = max(box.x, 0.5f)
            val rightWidth = (box.x + box.width) - rightStart
            if (rightWidth > 0f) {
                add(
                    Fragment(
                        sourceX = rightStart,
                        sourceY = box.y,
                        sourceWidth = rightWidth,
                        sourceHeight = box.height,
                        sourceHalf = TranslationOverlaySourceHalf.Right,
                    ),
                )
            }
        }.mapNotNull { fragment ->
            val isTopHalf = fragment.sourceHalf == topSourceHalf
            val displayX = when (fragment.sourceHalf) {
                TranslationOverlaySourceHalf.Left -> fragment.sourceX * 2f
                TranslationOverlaySourceHalf.Right -> fragment.sourceX * 2f - 1f
            }
            val displayY = if (isTopHalf) {
                fragment.sourceY / 2f
            } else {
                0.5f + fragment.sourceY / 2f
            }
            val displayWidth = fragment.sourceWidth * 2f
            val displayHeight = fragment.sourceHeight / 2f
            createClampedDisplayBox(
                box = box,
                displayX = displayX,
                displayY = displayY,
                displayWidth = displayWidth,
                displayHeight = displayHeight,
            )
        }

        if (fragments.isEmpty()) return emptyList()
        val largestIndex = fragments.indices.maxByOrNull { index ->
            fragments[index].displayWidth * fragments[index].displayHeight
        } ?: 0
        return fragments.mapIndexed { index, fragment ->
            fragment.copy(
                fragmentOrdinal = index,
                fragmentCount = fragments.size,
                drawTranslatedText = index == largestIndex,
            )
        }
    }

    private fun createClampedDisplayBox(
        box: TranslationOverlaySourceBox,
        displayX: Float,
        displayY: Float,
        displayWidth: Float,
        displayHeight: Float,
    ): TranslationOverlayDisplayBox? {
        val left = max(0f, min(displayX, displayX + displayWidth))
        val top = max(0f, min(displayY, displayY + displayHeight))
        val right = min(1f, max(displayX, displayX + displayWidth))
        val bottom = min(1f, max(displayY, displayY + displayHeight))
        val clampedWidth = right - left
        val clampedHeight = bottom - top
        if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) return null
        if (clampedWidth <= 0f || clampedHeight <= 0f) return null
        return createDisplayBox(
            box = box,
            displayX = left,
            displayY = top,
            displayWidth = clampedWidth,
            displayHeight = clampedHeight,
        )
    }

    private fun createDisplayBox(
        box: TranslationOverlaySourceBox,
        displayX: Float,
        displayY: Float,
        displayWidth: Float,
        displayHeight: Float,
        fragmentOrdinal: Int = 0,
        fragmentCount: Int = 1,
        drawTranslatedText: Boolean = true,
    ): TranslationOverlayDisplayBox {
        return TranslationOverlayDisplayBox(
            sourceBoxId = box.sourceBoxId,
            sourcePageId = box.sourcePageId,
            sourceX = box.x,
            sourceY = box.y,
            sourceWidth = box.width,
            sourceHeight = box.height,
            displayX = displayX,
            displayY = displayY,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            originalText = box.originalText,
            translatedText = box.translatedText,
            textType = box.textType,
            confidence = box.confidence,
            styleJson = box.styleJson,
            fragmentOrdinal = fragmentOrdinal,
            fragmentCount = fragmentCount,
            drawTranslatedText = drawTranslatedText,
        )
    }

    private data class Fragment(
        val sourceX: Float,
        val sourceY: Float,
        val sourceWidth: Float,
        val sourceHeight: Float,
        val sourceHalf: TranslationOverlaySourceHalf,
    )

    private fun Translation_boxes.toSourceBox(): TranslationOverlaySourceBox {
        return TranslationOverlaySourceBox(
            sourceBoxId = _id,
            sourcePageId = page_id,
            x = x.toFloat(),
            y = y.toFloat(),
            width = width.toFloat(),
            height = height.toFloat(),
            originalText = original_text,
            translatedText = translated_text,
            textType = text_type,
            confidence = confidence,
            styleJson = style_json,
        )
    }
}
