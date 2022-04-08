package eu.kanade.tachiyomi.widget.materialdialogs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.databinding.DialogQuadstatemultichoiceItemBinding

private object CheckPayload
private object InverseCheckPayload
private object UncheckPayload
private object IndeterminatePayload

typealias QuadStateMultiChoiceListener = (indices: IntArray) -> Unit

// isAction state: Uncheck-> Check-> Invert else Uncheck-> Indeterminate (only if initial so)-> Check
// isAction for list of action to operate on like filter include, exclude
internal class QuadStateMultiChoiceDialogAdapter(
    internal var items: List<CharSequence>,
    disabledItems: IntArray?,
    private var initialSelected: IntArray,
    internal var listener: QuadStateMultiChoiceListener,
    val isActionList: Boolean = true,
) : RecyclerView.Adapter<QuadStateMultiChoiceViewHolder>() {

    private val states = QuadStateTextView.State.values()

    private var currentSelection: IntArray = initialSelected
        set(value) {
            val previousSelection = field
            field = value
            previousSelection.forEachIndexed { index, previous ->
                val current = value[index]
                when {
                    current == QuadStateTextView.State.CHECKED.ordinal && previous != QuadStateTextView.State.CHECKED.ordinal -> {
                        // This value was selected
                        notifyItemChanged(index, CheckPayload)
                    }
                    current == QuadStateTextView.State.INVERSED.ordinal && previous != QuadStateTextView.State.INVERSED.ordinal -> {
                        // This value was inverse selected
                        notifyItemChanged(index, InverseCheckPayload)
                    }
                    current == QuadStateTextView.State.UNCHECKED.ordinal && previous != QuadStateTextView.State.UNCHECKED.ordinal -> {
                        // This value was unselected
                        notifyItemChanged(index, UncheckPayload)
                    }
                    current == QuadStateTextView.State.INDETERMINATE.ordinal && previous != QuadStateTextView.State.INDETERMINATE.ordinal -> {
                        // This value was set back to Indeterminate
                        notifyItemChanged(index, IndeterminatePayload)
                    }
                }
            }
        }
    private var disabledIndices: IntArray = disabledItems ?: IntArray(0)
    internal fun itemActionClicked(index: Int) {
        val newSelection = this.currentSelection.toMutableList()
        newSelection[index] = when (currentSelection[index]) {
            QuadStateTextView.State.CHECKED.ordinal -> QuadStateTextView.State.INVERSED.ordinal
            QuadStateTextView.State.INVERSED.ordinal -> QuadStateTextView.State.UNCHECKED.ordinal
            // INDETERMINATE or UNCHECKED
            else -> QuadStateTextView.State.CHECKED.ordinal
        }
        this.currentSelection = newSelection.toIntArray()
        listener(currentSelection)
    }

    internal fun itemDisplayClicked(index: Int) {
        val newSelection = this.currentSelection.toMutableList()
        newSelection[index] = when (currentSelection[index]) {
            QuadStateTextView.State.UNCHECKED.ordinal -> QuadStateTextView.State.CHECKED.ordinal
            QuadStateTextView.State.CHECKED.ordinal -> when (initialSelected[index]) {
                QuadStateTextView.State.INDETERMINATE.ordinal -> QuadStateTextView.State.INDETERMINATE.ordinal
                else -> QuadStateTextView.State.UNCHECKED.ordinal
            }
            // INDETERMINATE or UNCHECKED
            else -> QuadStateTextView.State.UNCHECKED.ordinal
        }
        this.currentSelection = newSelection.toIntArray()
        listener(currentSelection)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): QuadStateMultiChoiceViewHolder {
        return QuadStateMultiChoiceViewHolder(
            itemBinding = DialogQuadstatemultichoiceItemBinding
                .inflate(LayoutInflater.from(parent.context), parent, false),
            adapter = this,
        )
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(
        holder: QuadStateMultiChoiceViewHolder,
        position: Int,
    ) {
        holder.isEnabled = !disabledIndices.contains(position)
        holder.controlView.state = states[currentSelection[position]]
        holder.controlView.text = items[position]
    }

    override fun onBindViewHolder(
        holder: QuadStateMultiChoiceViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        when (payloads.firstOrNull()) {
            CheckPayload -> {
                holder.controlView.state = QuadStateTextView.State.CHECKED
                return
            }
            InverseCheckPayload -> {
                holder.controlView.state = QuadStateTextView.State.INVERSED
                return
            }
            UncheckPayload -> {
                holder.controlView.state = QuadStateTextView.State.UNCHECKED
                return
            }
            IndeterminatePayload -> {
                holder.controlView.state = QuadStateTextView.State.INDETERMINATE
                return
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }
}
