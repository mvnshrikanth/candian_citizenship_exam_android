package com.mvnsh.citizenship.ui.common

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.View
import android.view.animation.PathInterpolator

/** The Quiet Card spec's motion curve and its two named keyframe sets. */
object Motion {

    /** cubic-bezier(0.2, 0, 0, 1) - the only easing the design uses. */
    val STANDARD = PathInterpolator(0.2f, 0f, 0f, 1f)

    const val RISE_MS = 220L
    const val RISE_SHEET_MS = 260L
    const val POP_MS = 260L
    const val FADE_MS = 160L
    const val BAR_QUIZ_MS = 320L
    const val BAR_GOAL_MS = 420L

    /** `rise`: 10dp up plus a fade in. Used on screen and panel entry. */
    fun rise(view: View, durationMs: Long = RISE_MS) {
        val dy = 10f * view.resources.displayMetrics.density
        view.translationY = dy
        view.alpha = 0f
        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(STANDARD)
            .start()
    }

    /** `pop`: 0.94 -> 1.02 -> 1.0. Used when a correct/incorrect marker is revealed. */
    fun pop(view: View) {
        val sx = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.94f, 1.02f, 1f)
        val sy = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.94f, 1.02f, 1f)
        ObjectAnimator.ofPropertyValuesHolder(view, sx, sy).apply {
            duration = POP_MS
            interpolator = STANDARD
        }.start()
    }
}
