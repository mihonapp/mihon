package eu.kanade.tachiyomi.ui.browse.extension.details

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ExtensionDetailHeaderBinding
import eu.kanade.tachiyomi.ui.browse.extension.getApplicationIcon
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks

class ExtensionDetailsHeaderAdapter(private val presenter: ExtensionDetailsPresenter) :
    RecyclerView.Adapter<ExtensionDetailsHeaderAdapter.HeaderViewHolder>() {

    private lateinit var binding: ExtensionDetailHeaderBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        binding = ExtensionDetailHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HeaderViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind()
    }

    inner class HeaderViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            val extension = presenter.extension ?: return
            val context = view.context

            extension.getApplicationIcon(context)?.let { binding.icon.setImageDrawable(it) }
            binding.title.text = extension.name
            binding.version.text = context.getString(R.string.ext_version_info, extension.versionName)
            binding.lang.text = context.getString(R.string.ext_language_info, LocaleHelper.getSourceDisplayName(extension.lang, context))
            binding.nsfw.isVisible = extension.isNsfw
            binding.pkgname.text = extension.pkgName

            binding.btnUninstall.clicks()
                .onEach { presenter.uninstallExtension() }
                .launchIn(presenter.presenterScope)
            binding.btnAppInfo.clicks()
                .onEach { presenter.openInSettings() }
                .launchIn(presenter.presenterScope)

            if (extension.isObsolete) {
                binding.warningBanner.isVisible = true
                binding.warningBanner.setText(R.string.obsolete_extension_message)
            }

            if (extension.isUnofficial) {
                binding.warningBanner.isVisible = true
                binding.warningBanner.setText(R.string.unofficial_extension_message)
            }
        }
    }
}
