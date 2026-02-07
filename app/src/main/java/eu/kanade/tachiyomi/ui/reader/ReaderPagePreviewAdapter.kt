package eu.kanade.tachiyomi.ui.reader

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext

class ReaderPagePreviewAdapter(
    private val onPageClick: (ReaderPage) -> Unit
) : RecyclerView.Adapter<ReaderPagePreviewAdapter.PagePreviewHolder>() {

    private var pages: List<ReaderPage> = emptyList()
    private var currentPage: ReaderPage? = null

    fun submitList(newPages: List<ReaderPage>) {
        if (pages == newPages) return
        pages = newPages
        notifyDataSetChanged()
    }

    fun setCurrentPage(page: ReaderPage) {
        if (currentPage == page) return
        val oldIndex = pages.indexOf(currentPage)
        currentPage = page
        val newIndex = pages.indexOf(currentPage)
        
        if (oldIndex != -1) notifyItemChanged(oldIndex)
        if (newIndex != -1) notifyItemChanged(newIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagePreviewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reader_page_preview, parent, false)
        return PagePreviewHolder(view)
    }

    override fun onBindViewHolder(holder: PagePreviewHolder, position: Int) {
        val page = pages[position]
        holder.bind(page, page == currentPage)
    }

    override fun getItemCount(): Int = pages.size

    inner class PagePreviewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.card_view)
        private val thumbnail: ImageView = itemView.findViewById(R.id.page_thumbnail)
        private val pageNumber: TextView = itemView.findViewById(R.id.page_number)
        private var loadJob: Job? = null
        private val scope = CoroutineScope(Dispatchers.Main)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPageClick(pages[position])
                }
            }
        }

        fun bind(page: ReaderPage, isSelected: Boolean) {
            pageNumber.text = "${page.number}"

            // Highlight selection with foreground drawable
            if (isSelected) {
                val borderDrawable = ContextCompat.getDrawable(itemView.context, R.drawable.selection_border_foreground)
                cardView.foreground = borderDrawable
            } else {
                cardView.foreground = null
            }
            
            loadJob?.cancel()
            thumbnail.setImageDrawable(null) // Reset

            loadJob = scope.launch {
                page.statusFlow.collectLatest { status ->
                    when (status) {
                        Page.State.Ready -> loadThumbnail(page)
                        Page.State.Queue -> {
                            // Trigger load if visible and in queue
                            launchIO {
                                page.chapter.pageLoader?.loadPage(page)
                            }
                        }
                        else -> {
                            // Show placeholder or loading?
                            // For now just keep empty or standard icon
                        }
                    }
                }
            }
        }

        private suspend fun loadThumbnail(page: ReaderPage) {
            val streamFn = page.stream ?: return
            withContext(Dispatchers.IO) {
                try {
                    val stream = streamFn()
                    val bytes = try {
                        stream.readBytes()
                    } finally {
                        stream.close()
                    }

                    // Decode bounds first to downsample
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

                    // Calculate inSampleSize
                    options.inJustDecodeBounds = false
                    options.inSampleSize = calculateInSampleSize(options, 150, 225) // Thumbnail size

                    // Decode actual bitmap
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

                    if (bitmap != null) {
                        withUIContext {
                            thumbnail.setImageBitmap(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors for previews
                }
            }
        }

        private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            val (height: Int, width: Int) = options.run { outHeight to outWidth }
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {
                val halfHeight: Int = height / 2
                val halfWidth: Int = width / 2

                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }
    }
}
