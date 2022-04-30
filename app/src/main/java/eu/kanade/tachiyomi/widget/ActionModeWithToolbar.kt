package eu.kanade.tachiyomi.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.view.isVisible
import dev.chrisbanes.insetter.applyInsetter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ActionToolbarBinding
import eu.kanade.tachiyomi.util.system.applySystemAnimatorScale
import eu.kanade.tachiyomi.widget.ActionModeWithToolbar.Callback
import eu.kanade.tachiyomi.widget.listener.SimpleAnimationListener

/**
 * A toolbar holding only menu items. This view is supposed to be paired with [AppCompatActivity]'s [ActionMode].
 *
 * @see Callback
 */
class ActionModeWithToolbar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    init {
        clipToPadding = false
        applyInsetter {
            type(navigationBars = true) {
                padding(bottom = true, horizontal = true)
            }
        }
    }

    private val binding = ActionToolbarBinding.inflate(LayoutInflater.from(context), this, true)

    private var callback: Callback? = null

    private var actionMode: ActionMode? = null
    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            callback?.onCreateActionToolbar(mode.menuInflater, binding.menu.menu)
            binding.menu.setOnMenuItemClickListener { onActionItemClicked(mode, it) }
            binding.root.isVisible = true
            val bottomAnimation = AnimationUtils.loadAnimation(context, R.anim.bottom_sheet_slide_in)
            bottomAnimation.applySystemAnimatorScale(context)
            binding.root.startAnimation(bottomAnimation)

            return callback?.onCreateActionMode(mode, menu) ?: false
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            callback?.onPrepareActionToolbar(this@ActionModeWithToolbar, binding.menu.menu)
            return callback?.onPrepareActionMode(mode, menu) ?: false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return callback?.onActionItemClicked(mode, item) ?: false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            callback?.onDestroyActionMode(mode)

            val bottomAnimation = AnimationUtils.loadAnimation(context, R.anim.bottom_sheet_slide_out).apply {
                applySystemAnimatorScale(context)
                setAnimationListener(
                    object : SimpleAnimationListener() {
                        override fun onAnimationEnd(animation: Animation) {
                            binding.root.isVisible = false
                            binding.menu.menu.clear()
                            binding.menu.setOnMenuItemClickListener(null)

                            callback?.onDestroyActionToolbar()
                            callback = null
                            actionMode = null
                        }
                    },
                )
            }
            binding.root.startAnimation(bottomAnimation)
        }
    }

    fun start(callback: Callback) {
        val context = context
        if (context !is AppCompatActivity) {
            throw IllegalStateException("AppCompatActivity is needed to start this view")
        }
        if (actionMode == null) {
            this.callback = callback
            actionMode = context.startSupportActionMode(actionModeCallback)
        }
    }

    fun finish() {
        actionMode?.finish()
    }

    /**
     * Gets a menu item if found.
     */
    fun findToolbarItem(@IdRes itemId: Int): MenuItem? {
        return binding.menu.menu.findItem(itemId)
    }

    override fun invalidate() {
        super.invalidate()
        actionMode?.invalidate()
    }

    interface Callback {
        /**
         * Called when action mode is first created. The menu supplied will be used to
         * generate action buttons for the action mode.
         *
         * @param mode ActionMode being created
         * @param menu Menu used to populate action buttons
         * @return true if the action mode should be created, false if entering this
         * mode should be aborted.
         */
        fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean

        /**
         * [onCreateActionMode] but for the bottom toolbar
         */
        fun onCreateActionToolbar(menuInflater: MenuInflater, menu: Menu)

        /**
         * Called to refresh an action mode's action menu whenever it is invalidated.
         *
         * @param mode ActionMode being prepared
         * @param menu Menu used to populate action buttons
         * @return true if the menu or action mode was updated, false otherwise.
         */
        fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean

        /**
         * [onPrepareActionMode] but for the bottom toolbar
         */
        fun onPrepareActionToolbar(toolbar: ActionModeWithToolbar, menu: Menu)

        /**
         * Called to report a user click on an action button.
         *
         * @param mode The current ActionMode
         * @param item The item that was clicked
         * @return true if this callback handled the event, false if the standard MenuItem
         * invocation should continue.
         */
        fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean

        /**
         * Called when an action mode is about to be exited and destroyed.
         *
         * @param mode The current ActionMode being destroyed
         */
        fun onDestroyActionMode(mode: ActionMode)

        /**
         * Called when the action toolbar is finished exiting
         */
        fun onDestroyActionToolbar() {}
    }
}
