package eu.kanade.tachiyomi.ui.more.licenses

import android.annotation.SuppressLint
import android.view.View
import com.mikepenz.aboutlibraries.entity.Library
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.databinding.LicensesItemBinding

class LicensesHolder(view: View, adapter: FlexibleAdapter<*>) :
    FlexibleViewHolder(view, adapter) {

    private val binding = LicensesItemBinding.bind(view)

    @SuppressLint("SetTextI18n")
    fun bind(library: Library) {
        binding.name.text = "${library.libraryName} ${library.libraryVersion}"
        binding.artifactId.text = library.libraryArtifactId
        binding.license.text = library.licenses?.joinToString { it.licenseName }
    }
}
