package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import eu.kanade.tachiyomi.ui.reader.translator.Translator

class TranslationOverlayView(context: Context) : View(context) {

    private var translations: List<Translator.TranslationResult> = emptyList()
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
    }
    private val backgroundPaint = Paint().apply {
        color = Color.BLACK
        alpha = 180
    }

    private var scale = 1f
    private var centerX = 0f
    private var centerY = 0f

    fun setTranslations(translations: List<Translator.TranslationResult>) {
        this.translations = translations
        invalidate()
    }

    fun updateTransform(scale: Float, centerX: Float, centerY: Float) {
        this.scale = scale
        this.centerX = centerX
        this.centerY = centerY
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(-centerX, -centerY)

        translations.forEach { result ->
            val rect = result.boundingBox
            canvas.drawRect(rect, backgroundPaint)

            textPaint.textSize = rect.height() * 0.8f
            val textLayout = StaticLayout.Builder.obtain(
                result.translated,
                0,
                result.translated.length,
                textPaint,
                rect.width(),
            ).build()

            canvas.save()
            val textX = rect.left + (rect.width() - textLayout.width) / 2f
            val textY = rect.top + (rect.height() - textLayout.height) / 2f
            canvas.translate(textX, textY)
            textLayout.draw(canvas)
            canvas.restore()
        }

        canvas.restore()
    }
}
