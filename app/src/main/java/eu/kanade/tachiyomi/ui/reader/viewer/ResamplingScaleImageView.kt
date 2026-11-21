// Adapted from https://github.com/mpreg-ca/webgpuviewer
// Copyright (c) 2026 w, MIT License

package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlin.math.roundToInt

/**
 * A [SubsamplingScaleImageView] that, when [resampling], enlarges pages the way the high quality renderer does
 * instead of bilinearly: in linear light, with Catmull-Rom. Below a page's own size, or while the page moves, it
 * stays bilinear: the shader is too slow to pan with.
 *
 * The parent draws every bitmap through one `drawBitmap` call but keeps its paint and canvas to itself, so it
 * draws on a [ResamplingCanvas] here. Needs Android 13 for [RuntimeShader], and a hardware canvas to run it on.
 */
class ResamplingScaleImageView(context: Context) : SubsamplingScaleImageView(context) {

    private val resamplingCanvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ResamplingCanvas()
    } else {
        null
    }

    private var resampling = false

    private val lastOrigin = PointF()
    private var lastScale = 0f
    private var lastMoveMs = 0L
    private val settle = Runnable { invalidate() }

    /**
     * Call before the image, as this changes what resolution it's decoded at.
     *
     * @param shrunk whether the page is shown below its size, where decoding stays as it is without resampling.
     */
    fun setResampling(enabled: Boolean, shrunk: Boolean) {
        resampling = enabled && resamplingCanvas != null
        // Decoding below the screen's resolution would leave nothing for the shader to work with.
        setMinimumTileDpi(if (resampling && !shrunk) Int.MAX_VALUE else DEFAULT_TILE_DPI)
        invalidate()
    }

    /** Whether the page has moved within [SETTLE_MS]. A last frame comes once it stops. */
    private fun isMoving(origin: PointF): Boolean {
        val now = SystemClock.uptimeMillis()
        if (lastScale > 0f && lastScale == scale && lastOrigin == origin) {
            return now - lastMoveMs < SETTLE_MS
        }
        val moved = lastScale > 0f
        lastScale = scale
        lastOrigin.set(origin)
        if (moved) {
            lastMoveMs = now
            removeCallbacks(settle)
            postDelayed(settle, SETTLE_MS)
        }
        return moved
    }

    override fun onDraw(canvas: Canvas) {
        val resamplingCanvas = resamplingCanvas
        val origin = resamplingCanvas?.imageOrigin
        if (origin != null && resampling && isReady) sourceToViewCoord(0f, 0f, origin)
        val moving = origin != null && resampling && isReady && isMoving(origin)
        if (resampling && resamplingCanvas != null && canvas.isHardwareAccelerated && scale >= 1f && !moving) {
            resamplingCanvas.target = canvas
            resamplingCanvas.imageScale = scale
            resamplingCanvas.imageWidth = sWidth
            resamplingCanvas.imageHeight = sHeight
            super.onDraw(resamplingCanvas)
            resamplingCanvas.target = null
        } else {
            super.onDraw(canvas)
        }
    }

    companion object {
        const val DEFAULT_TILE_DPI = 180
        private const val SETTLE_MS = 150L
    }
}

/** Forwards what [SubsamplingScaleImageView] draws to [target], its bitmaps through [AGSL]. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ResamplingCanvas : Canvas() {

    var target: Canvas? = null

    /** Where the view really puts the image, which the matrices it hands over only roughly follow. */
    var imageScale = 0f
    var imageWidth = 0
    var imageHeight = 0
    val imageOrigin = PointF()

    private val shader = RuntimeShader(AGSL)
    private val shaderPaint = Paint()
    private val matrixValues = FloatArray(9)
    private val bounds = RectF()
    private val exactMatrix = Matrix()

    override fun drawBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint?) {
        val target = target ?: return
        bounds.set(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        matrix.mapRect(bounds)
        val exact = exactMatrixFor(bitmap, matrix)
        shader.setInputShader(
            "image",
            BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_NEAREST
            },
        )
        shader.setLocalMatrix(exact)
        shaderPaint.shader = shader
        target.drawRect(bounds, shaderPaint)
    }

    /**
     * The parent rounds where a tile lands to whole pixels, which stretches it slightly. Keeps that rectangle
     * as what's drawn, so tiles still meet, but samples the bitmap from where it really is. Rotated images
     * keep the given matrix.
     */
    private fun exactMatrixFor(bitmap: Bitmap, matrix: Matrix): Matrix {
        matrix.getValues(matrixValues)
        val tileScale = matrixValues[Matrix.MSCALE_X]
        if (imageScale <= 0f || tileScale <= 0f || matrixValues[Matrix.MSKEW_X] != 0f) return matrix
        val sampleSize = (imageScale / tileScale).roundToInt().coerceAtLeast(1)
        val sourceX = tileStart(matrixValues[Matrix.MTRANS_X] - imageOrigin.x, bitmap.width * sampleSize, imageWidth)
        val sourceY = tileStart(matrixValues[Matrix.MTRANS_Y] - imageOrigin.y, bitmap.height * sampleSize, imageHeight)
        exactMatrix.setScale(imageScale * sampleSize, imageScale * sampleSize)
        exactMatrix.postTranslate(imageOrigin.x + sourceX * imageScale, imageOrigin.y + sourceY * imageScale)
        return exactMatrix
    }

    /** Which of the parent's evenly divided tiles lands [viewOffset] from the image's edge, in source pixels. */
    private fun tileStart(viewOffset: Float, tileLength: Int, imageLength: Int): Int {
        val tiles = (imageLength.toFloat() / tileLength).roundToInt().coerceAtLeast(1)
        val length = imageLength / tiles
        return length * (viewOffset / (imageScale * length)).roundToInt()
    }

    override fun drawRect(rect: RectF, paint: Paint) {
        target?.drawRect(rect, paint)
    }

    override fun getMaximumBitmapWidth(): Int = target?.maximumBitmapWidth ?: super.getMaximumBitmapWidth()

    override fun getMaximumBitmapHeight(): Int = target?.maximumBitmapHeight ?: super.getMaximumBitmapHeight()
}

/**
 * The high quality renderer's `catmull_rom`, for one destination pixel at `p` in bitmap texels.
 */
private const val AGSL = """
uniform shader image;

vec4 toLinear(vec4 c) {
    vec3 lo = c.rgb / 12.92;
    vec3 hi = pow((max(c.rgb, vec3(0.0)) + 0.055) / 1.055, vec3(2.4));
    return vec4(mix(hi, lo, step(c.rgb, vec3(0.04045))), c.a);
}

vec4 toSrgb(vec4 c) {
    vec3 rgb = max(c.rgb, vec3(0.0));
    vec3 lo = rgb * 12.92;
    vec3 hi = 1.055 * pow(rgb, vec3(1.0 / 2.4)) - 0.055;
    return clamp(vec4(mix(hi, lo, step(rgb, vec3(0.0031308))), c.a), vec4(0.0), vec4(1.0));
}

vec4 load(vec2 texel) {
    return toLinear(image.eval(texel + 0.5));
}

vec4 catmullRomWeights(float t) {
    float t2 = t * t;
    float t3 = t2 * t;
    return vec4(
        -0.5 * t3 + t2 - 0.5 * t,
        1.5 * t3 - 2.5 * t2 + 1.0,
        -1.5 * t3 + 2.0 * t2 + 0.5 * t,
        0.5 * t3 - 0.5 * t2
    );
}

vec4 catmullRom(vec2 pos) {
    vec2 pixel = pos - 0.5;
    vec2 base = floor(pixel);
    vec2 f = pixel - base;
    vec4 wx = catmullRomWeights(f.x);
    vec4 wy = catmullRomWeights(f.y);
    vec4 sum = vec4(0.0);
    for (int j = 0; j < 4; j++) {
        vec4 row = vec4(0.0);
        for (int i = 0; i < 4; i++) {
            row += load(base + vec2(float(i - 1), float(j - 1))) * wx[i];
        }
        sum += row * wy[j];
    }
    return toSrgb(sum);
}

vec4 main(vec2 p) {
    return catmullRom(p);
}
"""
