package eu.kanade.tachiyomi.ui.reader.setting

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.FrameLayout
import androidx.annotation.ArrayRes
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.get
import com.tfcporciuncula.flow.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SpinnerPreferenceBinding

class SpinnerPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    private var entries = emptyList<String>()
    private var selectedPosition = 0
    private var pref: Preference<Int>? = null
    private var prefOffset = 0
    private var popup: PopupMenu? = null

    var onItemSelectedListener: ((Int) -> Unit)? = null
        set(value) {
            field = value
            if (value != null) {
                popup = makeSettingsPopup()
                setOnTouchListener(popup?.dragToOpenListener)
                setOnClickListener {
                    popup?.show()
                }
            }
        }

    private val binding = SpinnerPreferenceBinding.inflate(LayoutInflater.from(context), this, false)

    init {
        addView(binding.root)

        val a = context.obtainStyledAttributes(attrs, R.styleable.SpinnerPreference, 0, 0)

        val str = a.getString(R.styleable.SpinnerPreference_title) ?: ""
        binding.title.text = str

        val entries = (a.getTextArray(R.styleable.SpinnerPreference_android_entries) ?: emptyArray()).map { it.toString() }
        this.entries = entries

        binding.details.text = entries.firstOrNull().orEmpty()

        a.recycle()
    }

    fun setSelection(selection: Int) {
        popup?.menu?.get(selectedPosition)?.isCheckable = false
        popup?.menu?.get(selectedPosition)?.isChecked = false
        selectedPosition = selection
        binding.details.text = entries.getOrNull(selection).orEmpty()
        popup?.menu?.get(selectedPosition)?.isCheckable = true
        popup?.menu?.get(selectedPosition)?.isChecked = true
    }

    fun bindToPreference(pref: Preference<Int>, offset: Int = 0, block: ((Int) -> Unit)? = null) {
        setSelection(pref.get() - offset)
        this.pref = pref
        prefOffset = offset
        popup = makeSettingsPopup(pref, prefOffset, block)
        setOnTouchListener(popup?.dragToOpenListener)
        setOnClickListener {
            popup?.show()
        }
    }

    inline fun <reified T : Enum<T>> bindToPreference(pref: Preference<T>) {
        val enumConstants = T::class.java.enumConstants
        enumConstants?.indexOf(pref.get())?.let { setSelection(it) }
        val popup = makeSettingsPopup(pref)
        setOnTouchListener(popup.dragToOpenListener)
        setOnClickListener {
            popup.show()
        }
    }

    fun bindToIntPreference(pref: Preference<Int>, @ArrayRes intValuesResource: Int, block: ((Int) -> Unit)? = null) {
        setSelection(pref.get())
        this.pref = pref
        prefOffset = 0
        val intValues = resources.getStringArray(intValuesResource).map { it.toIntOrNull() }
        popup = makeSettingsPopup(pref, intValues, block)
        setOnTouchListener(popup?.dragToOpenListener)
        setOnClickListener {
            popup?.show()
        }
    }

    inline fun <reified T : Enum<T>> makeSettingsPopup(preference: Preference<T>): PopupMenu {
        val popup = popup()

        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            val pos = popup.menuClicked(menuItem)
            onItemSelectedListener?.invoke(pos)
            true
        }
        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            val enumConstants = T::class.java.enumConstants
            val pos = popup.menuClicked(menuItem)
            enumConstants?.get(pos)?.let { preference.set(it) }
            true
        }
        return popup
    }

    private fun makeSettingsPopup(preference: Preference<Int>, intValues: List<Int?>, block: ((Int) -> Unit)? = null): PopupMenu {
        val popup = popup()
        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            val pos = popup.menuClicked(menuItem)
            preference.set(intValues[pos] ?: 0)
            block?.invoke(pos)
            true
        }
        return popup
    }

    private fun makeSettingsPopup(preference: Preference<Int>, offset: Int = 0, block: ((Int) -> Unit)? = null): PopupMenu {
        val popup = popup()
        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            val pos = popup.menuClicked(menuItem)
            preference.set(pos + offset)
            block?.invoke(pos)
            true
        }
        return popup
    }

    private fun makeSettingsPopup(): PopupMenu {
        val popup = popup()

        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            val pos = popup.menuClicked(menuItem)
            onItemSelectedListener?.invoke(pos)
            true
        }
        return popup
    }

    fun PopupMenu.menuClicked(menuItem: MenuItem): Int {
        val pos = menuItem.itemId
        menu[selectedPosition].isCheckable = false
        menu[selectedPosition].isChecked = false
        setSelection(pos)
        menu[pos].isCheckable = true
        menu[pos].isChecked = true
        return pos
    }

    fun popup(): PopupMenu {
        val popup = PopupMenu(context, this, Gravity.END, R.attr.actionOverflowMenuStyle, 0)
        entries.forEachIndexed { index, entry ->
            popup.menu.add(0, index, 0, entry)
        }
        popup.menu[selectedPosition].isCheckable = true
        popup.menu[selectedPosition].isChecked = true
        return popup
    }
}
