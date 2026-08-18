package com.mvnsh.citizenship.ui.common

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import com.mvnsh.citizenship.R

/**
 * The design's snackbar: a dark rounded pill inset from both edges and floated above the
 * bottom navigation, rather than Material's default full-bleed bar.
 */
fun showDesignSnackbar(anchor: View, message: String) {
    val ctx = anchor.context
    val density = ctx.resources.displayMetrics.density
    val side = (16 * density).toInt()
    val bottom = (88 * density).toInt()

    val bar = Snackbar.make(anchor, message, Snackbar.LENGTH_LONG)
    bar.view.apply {
        background = MaterialShapeDrawable(
            ShapeAppearanceModel.builder()
                .setAllCornerSizes(ctx.resources.getDimension(R.dimen.radius_xs))
                .build(),
        ).apply {
            fillColor = ContextCompat.getColorStateList(ctx, R.color.md_inverse_surface)
        }
        updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
            leftMargin = side
            rightMargin = side
            bottomMargin = bottom
        }
    }
    bar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.apply {
        setTextAppearance(R.style.Text_Body)
        setTextColor(ContextCompat.getColor(ctx, R.color.md_inverse_on_surface))
        maxLines = 3
    }
    bar.show()
}
