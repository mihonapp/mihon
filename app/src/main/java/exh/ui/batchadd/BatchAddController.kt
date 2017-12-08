package exh.ui.batchadd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import com.jakewharton.rxbinding.view.clicks
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.util.combineLatest
import eu.kanade.tachiyomi.util.plusAssign
import kotlinx.android.synthetic.main.eh_fragment_batch_add.view.*
import rx.android.schedulers.AndroidSchedulers
import rx.subscriptions.CompositeSubscription

/**
 * Batch add screen
 */
class BatchAddController : NucleusController<BatchAddPresenter>() {
    override fun inflateView(inflater: LayoutInflater, container: ViewGroup) =
            inflater.inflate(R.layout.eh_fragment_batch_add, container, false)!!

    override fun getTitle() = "Batch add"

    override fun createPresenter() = BatchAddPresenter()

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        with(view) {
            btn_add_galleries.clicks().subscribeUntilDestroy {
                addGalleries(galleries_box.text.toString())
            }

            progress_dismiss_btn.clicks().subscribeUntilDestroy {
                presenter.currentlyAddingRelay.call(BatchAddPresenter.STATE_PROGRESS_TO_INPUT)
            }

            val progressSubscriptions = CompositeSubscription()

            presenter.currentlyAddingRelay
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeUntilDestroy {
                        progressSubscriptions.clear()
                        if(it == BatchAddPresenter.STATE_INPUT_TO_PROGRESS) {
                            showProgress(this)
                            progressSubscriptions += presenter.progressRelay
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .combineLatest(presenter.progressTotalRelay, { progress, total ->
                                        //Show hide dismiss button
                                        progress_dismiss_btn.visibility =
                                                if(progress == total)
                                                    View.VISIBLE
                                                else View.GONE

                                        formatProgress(progress, total)
                                    }).subscribeUntilDestroy {
                                progress_text.text = it
                            }

                            progressSubscriptions += presenter.progressTotalRelay
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribeUntilDestroy {
                                        progress_bar.max = it
                                    }

                            progressSubscriptions += presenter.progressRelay
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribeUntilDestroy {
                                        progress_bar.progress = it
                                    }

                            presenter.eventRelay
                                    ?.observeOn(AndroidSchedulers.mainThread())
                                    ?.subscribeUntilDestroy {
                                        progress_log.append("$it\n")
                                    }?.let {
                                progressSubscriptions += it
                            }
                        } else if(it == BatchAddPresenter.STATE_PROGRESS_TO_INPUT) {
                            hideProgress(this)
                            presenter.currentlyAddingRelay.call(BatchAddPresenter.STATE_IDLE)
                        }
                    }
        }
    }

    private val View.progressViews
        get() = listOf(
                progress_title_view,
                progress_log_wrapper,
                progress_bar,
                progress_text,
                progress_dismiss_btn
        )

    private val View.inputViews
        get() = listOf(
                input_title_view,
                galleries_box,
                btn_add_galleries
        )

    private var List<View>.visibility: Int
        get() = throw UnsupportedOperationException()
        set(v) { forEach { it.visibility = v } }

    private fun showProgress(target: View? = view) {
        target?.apply {
            progressViews.visibility = View.VISIBLE
            inputViews.visibility = View.GONE
        }?.progress_log?.text = ""
    }

    private fun hideProgress(target: View? = view) {
        target?.apply {
            progressViews.visibility = View.GONE
            inputViews.visibility = View.VISIBLE
        }?.galleries_box?.setText("", TextView.BufferType.EDITABLE)
    }

    private fun formatProgress(progress: Int, total: Int) = "$progress/$total"

    private fun addGalleries(galleries: String) {
        //Check text box has content
        if(galleries.isBlank()) {
            noGalleriesSpecified()
            return
        }

        presenter.addGalleries(galleries)
    }

    private fun noGalleriesSpecified() {
        activity?.let {
            MaterialDialog.Builder(it)
                    .title("No galleries to add!")
                    .content("You must specify at least one gallery to add!")
                    .positiveText("Ok")
                    .onPositive { materialDialog, _ -> materialDialog.dismiss() }
                    .cancelable(true)
                    .canceledOnTouchOutside(true)
                    .show()
        }
    }
}
