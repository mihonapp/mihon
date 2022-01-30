package eu.kanade.tachiyomi.widget

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.Animatable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.MangaSummaryBinding
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.view.setChips
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class MangaSummaryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val binding = MangaSummaryBinding.inflate(LayoutInflater.from(context), this, true)

    private var animatorSet: AnimatorSet? = null

    private var recalculateHeights = false
    private var descExpandedHeight = -1
    private var descShrunkHeight = -1

    var expanded = false
        set(value) {
            if (field != value) {
                field = value
                updateExpandState()
            }
        }

    var description: CharSequence? = null
        set(value) {
            if (field != value) {
                field = if (value.isNullOrBlank()) {
                    context.getString(R.string.unknown)
                } else {
                    value
                }
                binding.descriptionText.text = field
                recalculateHeights = true
                doOnNextLayout {
                    updateExpandState()
                }
                if (!isInLayout) {
                    requestLayout()
                }
            }
        }

    fun setTags(items: List<String>?, onClick: (item: String) -> Unit) {
        listOfNotNull(binding.tagChipsShrunk, binding.tagChipsExpanded).forEach { chips ->
            chips.setChips(items, onClick) { tag -> context.copyToClipboard(tag, tag) }
        }
    }

    private fun updateExpandState() = binding.apply {
        val initialSetup = descriptionText.maxHeight < 0

        val maxHeightTarget = if (expanded) descExpandedHeight else descShrunkHeight
        val maxHeightStart = if (initialSetup) maxHeightTarget else descriptionText.maxHeight
        val descMaxHeightAnimator = ValueAnimator().apply {
            setIntValues(maxHeightStart, maxHeightTarget)
            addUpdateListener {
                descriptionText.maxHeight = it.animatedValue as Int
            }
        }

        val toggleDrawable = ContextCompat.getDrawable(
            context,
            if (expanded) R.drawable.anim_caret_up else R.drawable.anim_caret_down
        )
        toggleMore.setImageDrawable(toggleDrawable)

        var pastHalf = false
        val toggleTarget = if (expanded) 1F else 0F
        val toggleStart = if (initialSetup) {
            toggleTarget
        } else {
            toggleMore.translationY / toggleMore.height
        }
        val toggleAnimator = ValueAnimator().apply {
            setFloatValues(toggleStart, toggleTarget)
            addUpdateListener {
                val value = it.animatedValue as Float

                toggleMore.translationY = toggleMore.height * value
                descriptionScrim.translationY = toggleMore.translationY
                toggleMoreScrim.translationY = toggleMore.translationY
                tagChipsShrunkContainer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    topMargin = toggleMore.translationY.roundToInt()
                }
                tagChipsExpanded.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    topMargin = toggleMore.translationY.roundToInt()
                }

                // Update non-animatable objects mid-animation makes it feel less abrupt
                if (it.animatedFraction >= 0.5F && !pastHalf) {
                    pastHalf = true
                    descriptionText.text = trimWhenNeeded(description)
                    tagChipsShrunkContainer.scrollX = 0
                    tagChipsShrunkContainer.isVisible = !expanded
                    tagChipsExpanded.isVisible = expanded
                }
            }
        }

        animatorSet?.cancel()
        animatorSet = AnimatorSet().apply {
            interpolator = FastOutSlowInInterpolator()
            duration = (TOGGLE_ANIM_DURATION * context.animatorDurationScale).roundToLong()
            playTogether(toggleAnimator, descMaxHeightAnimator)
            start()
        }
        (toggleDrawable as? Animatable)?.start()
    }

    private fun trimWhenNeeded(text: CharSequence?): CharSequence? {
        return if (!expanded) {
            text
                ?.replace(Regex(" +\$", setOf(RegexOption.MULTILINE)), "")
                ?.replace(Regex("[\\r\\n]{2,}", setOf(RegexOption.MULTILINE)), "\n")
        } else {
            text
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Wait until parent view has determined the exact width
        // because this affect the description line count
        val measureWidthFreely = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY
        if (!recalculateHeights || measureWidthFreely) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        recalculateHeights = false

        // Measure with expanded lines
        binding.descriptionText.maxLines = Int.MAX_VALUE
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        descExpandedHeight = binding.descriptionText.measuredHeight

        // Measure with shrunk lines
        binding.descriptionText.maxLines = SHRUNK_DESC_MAX_LINES
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        descShrunkHeight = binding.descriptionText.measuredHeight
    }

    init {
        binding.descriptionText.apply {
            // So that 1 line of text won't be hidden by scrim
            minLines = DESC_MIN_LINES

            setOnLongClickListener {
                context.copyToClipboard(
                    context.getString(R.string.description),
                    text.toString()
                )
                true
            }
        }

        arrayOf(
            binding.descriptionText,
            binding.descriptionScrim,
            binding.toggleMoreScrim,
            binding.toggleMore
        ).forEach {
            it.setOnClickListener { expanded = !expanded }
        }
    }
}

private const val TOGGLE_ANIM_DURATION = 300L

private const val DESC_MIN_LINES = 2
private const val SHRUNK_DESC_MAX_LINES = 3
