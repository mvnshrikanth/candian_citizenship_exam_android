package com.mvnsh.citizenship.ui.common

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Puts [gapPx] between rows and nothing before the first or after the last.
 *
 * The design's lists are separated by a gap, not a divider line, and a margin on the item
 * layout would double up between neighbours and add a stray edge at both ends.
 */
class SpacingDecoration(private val gapPx: Int) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        if (parent.getChildAdapterPosition(view) > 0) outRect.top = gapPx
    }
}
