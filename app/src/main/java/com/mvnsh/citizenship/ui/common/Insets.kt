package com.mvnsh.citizenship.ui.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * targetSdk 36 forces edge-to-edge on Android 15+, so every screen places the system
 * bars itself.
 *
 * One entry point on purpose: [ViewCompat.setOnApplyWindowInsetsListener] keeps a single
 * listener per view, so separate top/bottom helpers would silently cancel each other out
 * when a screen needs both. That bug puts content under the status bar, where the system
 * swallows taps.
 *
 * The view's designed padding is captured once, so re-dispatched insets stay correct.
 */
fun View.applyInsets(top: Boolean = false, bottom: Boolean = false) {
    require(top || bottom) { "applyInsets called without an edge" }
    val baseTop = paddingTop
    val baseBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        v.updatePadding(
            top = if (top) baseTop + bars.top else baseTop,
            bottom = if (bottom) baseBottom + maxOf(bars.bottom, ime.bottom) else baseBottom,
        )
        insets
    }
}

fun View.applyTopInset() = applyInsets(top = true)

fun View.applyBottomInset() = applyInsets(bottom = true)
