package eu.kanade.tachiyomi.ui.reader.viewer.book

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The sheet being turned, bound at one edge and turned from the other. It travels to the left when [towardLeft],
 * as pages of a book read left to right do, so its bound edge is its left one.
 */
internal class Leaf(val rect: Rect, val towardLeft: Boolean) {
    val spineX: Float get() = if (towardLeft) rect.left else rect.right
    val freeX: Float get() = if (towardLeft) rect.right else rect.left
    val width: Float get() = rect.width

    /** +1 when the free edge is right of the spine, -1 when left. */
    val direction: Float get() = if (towardLeft) 1f else -1f
}

/** A layer drawn with its top left corner at [rect]'s. */
internal class LayerAt(val layer: GraphicsLayer, val rect: Rect)

/**
 * What a page turn from one spread to the next draws.
 *
 * In a [bookStyle] turn the leaf is a single page: [stationary] is the page facing it, which stays put, and [back] is
 * the next spread's page printed on the leaf's other side, which lands where [stationary] was. Otherwise the whole of
 * [front] is turned like a single sheet with blank paper behind it.
 */
internal class TurnScene(
    val leaf: Leaf,
    val bookStyle: Boolean,
    val stationary: List<LayerAt>,
    val front: List<LayerAt>,
    val under: List<LayerAt>,
    val back: List<LayerAt>,
    /** Facing pages to shade at the spine, left one first. */
    val gutters: List<Pair<Rect, Boolean>>,
)

/**
 * Where the turned corner of [leaf] is when the turn is at [progress], 0 lying flat and 1 turned over, moved [dy]
 * up or down by the finger. The corner lifts on the way over like paper does, and never pulls further from the spine
 * than the leaf reaches.
 */
internal fun cornerPosition(leaf: Leaf, progress: Float, dy: Float, fromTop: Boolean): Offset {
    val r = leaf.rect
    val cornerY = if (fromTop) r.top else r.bottom
    val lift = sin(PI * progress).toFloat() * r.height * CORNER_LIFT
    val x = leaf.freeX - leaf.direction * 2f * leaf.width * progress
    val y = cornerY + dy + if (fromTop) lift else -lift
    val nearSpine = Offset(leaf.spineX, cornerY)
    val farSpine = Offset(leaf.spineX, if (fromTop) r.bottom else r.top)
    return Offset(x, y)
        .limitTo(nearSpine, leaf.width)
        .limitTo(farSpine, hypot(leaf.width, r.height))
}

/**
 * The fold made by moving a leaf's [corner] to [point]: the line halfway between them, perpendicular to them.
 */
private class Fold(corner: Offset, point: Offset) {
    val mid = (corner + point) / 2f
    val normal: Offset
    val length = (corner - point).getDistance()

    init {
        val d = corner - point
        normal = if (length > 0f) d / length else Offset(1f, 0f)
    }

    /** How far [q] is past the fold, toward the corner. */
    fun side(q: Offset): Float = (q.x - mid.x) * normal.x + (q.y - mid.y) * normal.y

    fun reflect(q: Offset): Offset = q - normal * (2f * side(q))

    /** [reflect] as a matrix. */
    fun reflection(): Matrix {
        val (a, b, d) = reflectionTerms()
        val k = 2f * (mid.x * normal.x + mid.y * normal.y)
        return affine(a, b, b, d, k * normal.x, k * normal.y)
    }

    /**
     * Puts content laid out across the spine at [spineX] onto the back of the folded part: mirrored over the spine,
     * as the leaf's other side is, then over the fold. Once turned over the fold is the spine, and this is identity.
     */
    fun backOfLeaf(spineX: Float): Matrix {
        val (a, b, d) = reflectionTerms()
        val k = 2f * (mid.x * normal.x + mid.y * normal.y)
        return affine(-a, b, -b, d, 2f * a * spineX + k * normal.x, 2f * b * spineX + k * normal.y)
    }

    private fun reflectionTerms(): Triple<Float, Float, Float> {
        val nx = normal.x
        val ny = normal.y
        return Triple(1f - 2f * nx * nx, -2f * nx * ny, 1f - 2f * ny * ny)
    }
}

/**
 * Reusable paint for the shadow the turned part casts, blurred where the platform blurs hardware drawing.
 */
internal class CurlPaints {
    private var blurRadius = -1f
    private val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    fun castShadow(radius: Float, color: Color): android.graphics.Paint {
        if (radius != blurRadius) {
            blurRadius = radius
            shadowPaint.maskFilter = if (radius > 0f) BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL) else null
        }
        shadowPaint.color = color.toArgb()
        return shadowPaint
    }
}

/**
 * Draws [scene] with the leaf's corner at [corner] and the turn at [progress].
 */
internal fun DrawScope.drawTurn(
    scene: TurnScene,
    corner: Offset,
    fromTop: Boolean,
    progress: Float,
    paper: Color,
    shadows: Boolean,
    paints: CurlPaints,
) {
    val leaf = scene.leaf
    val r = leaf.rect
    val restingCorner = Offset(leaf.freeX, if (fromTop) r.top else r.bottom)
    // Strongest mid-turn, gone when the leaf lies flat on either side.
    val strength = if (shadows) sqrt(sin(PI * progress.coerceIn(0f, 1f)).toFloat()) else 0f

    scene.stationary.forEach { drawAt(it) }

    val fold = Fold(restingCorner, corner)
    if (fold.length < 0.5f) {
        scene.front.forEach { drawAt(it) }
        drawGutters(scene.gutters, shadows)
        return
    }

    val leafPolygon = listOf(r.topLeft, r.topRight, r.bottomRight, r.bottomLeft)
    val lifted = clipPolygon(leafPolygon, fold::side, keepPositive = true)
    val resting = clipPolygon(leafPolygon, fold::side, keepPositive = false)
    val shadowLength = (fold.length * 0.35f).coerceIn(12f * density, leaf.width * 0.35f)

    if (lifted.size >= 3) {
        clipPath(lifted.toPath()) {
            scene.under.forEach { drawAt(it) }
            if (strength > 0f) {
                drawRect(
                    Brush.linearGradient(
                        0f to Color.Black.copy(alpha = 0.36f * strength),
                        1f to Color.Transparent,
                        start = fold.mid,
                        end = fold.mid + fold.normal * shadowLength,
                    ),
                )
            }
        }
    }
    if (resting.size >= 3) {
        clipPath(resting.toPath()) {
            scene.front.forEach { drawAt(it) }
            if (strength > 0f) {
                // The paper starts curling up just before the fold.
                drawRect(
                    Brush.linearGradient(
                        0f to Color.Black.copy(alpha = 0.2f * strength),
                        1f to Color.Transparent,
                        start = fold.mid,
                        end = fold.mid - fold.normal * (shadowLength * 0.4f),
                    ),
                )
            }
        }
    }
    drawGutters(scene.gutters, shadows)

    val flap = lifted.map(fold::reflect)
    if (flap.size < 3) return
    val flapPath = flap.toPath()

    if (strength > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val paint = paints.castShadow(10f * density, Color.Black.copy(alpha = 0.4f * strength))
        drawIntoCanvas { it.nativeCanvas.drawPath(flapPath.asAndroidPath(), paint) }
    }

    clipPath(flapPath) {
        drawRect(paper)
        if (scene.bookStyle) {
            withTransform({ transform(fold.backOfLeaf(leaf.spineX)) }) {
                scene.back.forEach { drawAt(it) }
            }
            // Landing flat, the page curves into the spine like the one it covers.
            drawGutters(scene.gutters.filter { it.second == leaf.towardLeft }, shadows, alpha = progress)
        } else {
            // A sheet printed on one side: its page shows faintly through the paper, mirrored.
            withTransform({ transform(fold.reflection()) }) {
                scene.front.forEach { drawAt(it) }
            }
            drawRect(paper.copy(alpha = 0.86f))
        }
        if (strength > 0f) {
            // Where the leaf bends at the fold it darkens, then catches the light.
            drawRect(
                Brush.linearGradient(
                    0f to Color.Black.copy(alpha = 0.32f * strength),
                    0.18f to Color.White.copy(alpha = 0.12f * strength),
                    1f to Color.Transparent,
                    start = fold.mid,
                    end = fold.mid - fold.normal * (shadowLength * 1.4f),
                ),
            )
        }
    }
}

/**
 * Shades facing pages toward the spine, where a bound book curves away. Each rect is paired with whether it's the
 * left page.
 */
internal fun DrawScope.drawGutters(gutters: List<Pair<Rect, Boolean>>, shadows: Boolean, alpha: Float = 1f) {
    if (!shadows || alpha <= 0f) return
    for ((rect, isLeft) in gutters) {
        val width = rect.width * GUTTER_WIDTH
        val spineX = if (isLeft) rect.right else rect.left
        val brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.22f * alpha),
            startX = if (isLeft) spineX - width else spineX + width,
            endX = spineX,
        )
        val shade = if (isLeft) {
            Rect(spineX - width, rect.top, spineX, rect.bottom)
        } else {
            Rect(spineX, rect.top, spineX + width, rect.bottom)
        }
        drawRect(brush, topLeft = shade.topLeft, size = shade.size)
    }
}

internal fun DrawScope.drawAt(item: LayerAt) {
    translate(item.rect.left, item.rect.top) {
        drawLayer(item.layer)
    }
}

/**
 * The part of the convex [polygon] on one side of a line, given by [side]: where it's positive when [keepPositive].
 */
private fun clipPolygon(
    polygon: List<Offset>,
    side: (Offset) -> Float,
    keepPositive: Boolean,
): List<Offset> {
    val sign = if (keepPositive) 1f else -1f
    val result = ArrayList<Offset>(polygon.size + 2)
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        val sa = side(a) * sign
        val sb = side(b) * sign
        if (sa >= 0f) result += a
        if ((sa >= 0f) != (sb >= 0f)) {
            val t = sa / (sa - sb)
            result += a + (b - a) * t
        }
    }
    return result
}

private fun List<Offset>.toPath(): Path = Path().apply {
    moveTo(first().x, first().y)
    for (i in 1..lastIndex) lineTo(this@toPath[i].x, this@toPath[i].y)
    close()
}

private fun Offset.limitTo(center: Offset, radius: Float): Offset {
    val d = this - center
    val distance = d.getDistance()
    return if (distance <= radius || distance == 0f) this else center + d * (radius / distance)
}

/**
 * The 2D affine transform x' = a x + b y + tx, y' = c x + d y + ty.
 */
private fun affine(a: Float, b: Float, c: Float, d: Float, tx: Float, ty: Float): Matrix = Matrix().apply {
    values[Matrix.ScaleX] = a
    values[Matrix.SkewX] = b
    values[Matrix.TranslateX] = tx
    values[Matrix.SkewY] = c
    values[Matrix.ScaleY] = d
    values[Matrix.TranslateY] = ty
}

/** How high a turned corner lifts mid-turn, of the leaf's height. */
private const val CORNER_LIFT = 0.12f

/** How far the spine shading reaches into a page, of its width. */
private const val GUTTER_WIDTH = 0.07f
