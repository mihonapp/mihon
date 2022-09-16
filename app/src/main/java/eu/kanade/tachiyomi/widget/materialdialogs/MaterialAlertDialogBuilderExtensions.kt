package eu.kanade.tachiyomi.widget.materialdialogs

import android.view.LayoutInflater
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.databinding.DialogStubQuadstatemultichoiceBinding
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Sets a list of items with checkboxes that supports 4 states.
 *
 * @see eu.kanade.tachiyomi.widget.materialdialogs.QuadStateTextView
 */
fun MaterialAlertDialogBuilder.setQuadStateMultiChoiceItems(
    @StringRes message: Int? = null,
    isActionList: Boolean = true,
    items: List<CharSequence>,
    initialSelected: IntArray,
    disabledIndices: IntArray? = null,
    selection: QuadStateMultiChoiceListener,
): MaterialAlertDialogBuilder {
    val binding = DialogStubQuadstatemultichoiceBinding.inflate(LayoutInflater.from(context))
    binding.list.layoutManager = LinearLayoutManager(context)
    binding.list.adapter = QuadStateMultiChoiceDialogAdapter(
        items = items,
        disabledItems = disabledIndices,
        initialSelected = initialSelected,
        isActionList = isActionList,
        listener = selection,
    )
    val updateScrollIndicators = {
        binding.scrollIndicatorUp.isVisible = binding.list.canScrollVertically(-1)
        binding.scrollIndicatorDown.isVisible = binding.list.canScrollVertically(1)
    }
    binding.list.setOnScrollChangeListener { _, _, _, _, _ ->
        updateScrollIndicators()
    }
    binding.list.post {
        updateScrollIndicators()
    }

    if (message != null) {
        binding.message.setText(message)
        binding.message.isVisible = true
    }
    return setView(binding.root)
}

suspend fun MaterialAlertDialogBuilder.await(
    @StringRes positiveLabelId: Int,
    @StringRes negativeLabelId: Int,
    @StringRes neutralLabelId: Int? = null,
) = suspendCancellableCoroutine { cont ->
    setPositiveButton(positiveLabelId) { _, _ -> cont.resume(AlertDialog.BUTTON_POSITIVE) }
    setNegativeButton(negativeLabelId) { _, _ -> cont.resume(AlertDialog.BUTTON_NEGATIVE) }
    if (neutralLabelId != null) {
        setNeutralButton(neutralLabelId) { _, _ -> cont.resume(AlertDialog.BUTTON_NEUTRAL) }
    }
    setOnDismissListener { cont.cancel() }

    val dialog = show()
    cont.invokeOnCancellation { dialog.dismiss() }
}
