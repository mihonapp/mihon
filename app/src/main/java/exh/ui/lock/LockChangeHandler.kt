package exh.ui.lock

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.changehandler.AnimatorChangeHandler
import java.util.*

class LockChangeHandler : AnimatorChangeHandler {
    constructor(): super()

    constructor(removesFromViewOnPush: Boolean) : super(removesFromViewOnPush)

    constructor(duration: Long) : super(duration)

    constructor(duration: Long, removesFromViewOnPush: Boolean) : super(duration, removesFromViewOnPush)

    override fun getAnimator(container: ViewGroup, from: View?, to: View?, isPush: Boolean, toAddedToContainer: Boolean): Animator {
        val animator = AnimatorSet()
        val viewAnimators = ArrayList<Animator>()

        if (!isPush && from != null) {
            viewAnimators.add(ObjectAnimator.ofFloat(from, View.SCALE_X, 3f))
            viewAnimators.add(ObjectAnimator.ofFloat(from, View.SCALE_Y, 3f))
            viewAnimators.add(ObjectAnimator.ofFloat(from, View.ALPHA, 0f))
        }

        animator.playTogether(viewAnimators)
        return animator
    }

    override fun resetFromView(from: View) {}

    override fun copy(): ControllerChangeHandler =
            LockChangeHandler(animationDuration, removesFromViewOnPush())

}

