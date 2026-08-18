package com.mvnsh.citizenship.ui.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * targetSdk 36 forces edge-to-edge on Android 15+, so every screen has to place the
 * system bars itself. Both helpers capture the view's designed padding once and add the
 * inset on top, so they stay correct if the listener fires more than once.
 */
fun View.applyTopInset() {
    val base = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        v.updatePadding(top = base + insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
        insets
    }
}

fun View.applyBottomInset() {
    val base = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        v.updatePadding(bottom = base + maxOf(bars, ime))
        insets
    }
}
