package com.mvnsh.citizenship.ui.quiz

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.databinding.ItemOptionBinding
import com.mvnsh.citizenship.ui.common.Motion

/**
 * Binds option rows into a fixed [LinearLayout] rather than a RecyclerView.
 *
 * A question never has more than a handful of options, so there is nothing to recycle,
 * and keeping one View per position for the life of the screen is what lets the fill
 * animate from its old colour to its new one. A recycled row would arrive already
 * carrying somebody else's colour and the transition would read as a flicker.
 */
object OptionViews {

    private const val FILL_ANIM_MS = 180L

    fun bind(
        container: LinearLayout,
        rows: List<OptionRow>,
        enabled: Boolean,
        onPick: (Int) -> Unit,
    ) {
        syncChildCount(container, rows.size)
        rows.forEachIndexed { position, row ->
            val view = container.getChildAt(position)
            bindRow(ItemOptionBinding.bind(view), row, enabled, onPick)
        }
    }

    /** Adds or removes rows so the container matches [count], keeping existing views. */
    private fun syncChildCount(container: LinearLayout, count: Int) {
        val inflater = LayoutInflater.from(container.context)
        while (container.childCount < count) {
            val view = inflater.inflate(R.layout.item_option, container, false)
            container.addView(view)
            if (container.childCount > 1) {
                view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = container.resources.getDimensionPixelSize(R.dimen.gap_list_wide)
                }
            }
        }
        while (container.childCount > count) {
            container.removeViewAt(container.childCount - 1)
        }
    }

    private fun bindRow(
        binding: ItemOptionBinding,
        row: OptionRow,
        enabled: Boolean,
        onPick: (Int) -> Unit,
    ) = with(binding) {
        val ctx = root.context
        optionText.text = row.text
        optionText.setTextColor(ContextCompat.getColor(ctx, row.style.textColor()))
        optionText.fontVariationSettings = "'wght' ${row.style.textWeight()}"

        animateFill(binding, ContextCompat.getColor(ctx, row.style.fillColor()))
        optionRow.strokeColor = ContextCompat.getColor(ctx, row.style.strokeColor())
        optionRow.strokeWidth =
            (row.style.strokeWidthDp() * ctx.resources.displayMetrics.density).toInt()

        bindMarker(binding, row)
        bindBadge(binding, row)

        optionRow.isClickable = enabled
        optionRow.setOnClickListener(if (enabled) View.OnClickListener { onPick(row.optionIndex) } else null)
    }

    private fun bindMarker(binding: ItemOptionBinding, row: OptionRow) = with(binding) {
        val ctx = root.context
        val filled = row.marker != Marker.LETTER
        marker.setBackgroundResource(
            if (filled) R.drawable.bg_marker_fill else R.drawable.bg_marker_ring,
        )
        marker.backgroundTintList = when (row.marker) {
            Marker.LETTER -> null
            Marker.LETTER_SELECTED -> tint(ctx, R.color.md_primary)
            Marker.CHECK -> tint(ctx, R.color.md_secondary)
            Marker.CROSS -> tint(ctx, R.color.md_error)
        }

        val showsIcon = row.marker == Marker.CHECK || row.marker == Marker.CROSS
        markerLetter.isVisible = !showsIcon
        markerIcon.isVisible = showsIcon

        if (showsIcon) {
            val wasHidden = markerIcon.tag != row.marker
            markerIcon.setImageResource(
                if (row.marker == Marker.CHECK) R.drawable.ic_check else R.drawable.ic_close,
            )
            markerIcon.imageTintList = tint(
                ctx,
                if (row.marker == Marker.CHECK) R.color.md_on_secondary else R.color.md_on_error,
            )
            // Pop once on the reveal, not on every re-render of an already-marked row.
            if (wasHidden) Motion.pop(marker)
        } else {
            markerLetter.text = row.letter
            markerLetter.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (row.marker == Marker.LETTER_SELECTED) {
                        R.color.md_on_primary
                    } else {
                        R.color.md_on_surface_variant
                    },
                ),
            )
        }
        markerIcon.tag = row.marker
    }

    private fun bindBadge(binding: ItemOptionBinding, row: OptionRow) = with(binding) {
        val ctx = root.context
        optionBadge.isVisible = row.badge != Badge.NONE
        when (row.badge) {
            Badge.NONE -> Unit
            Badge.CORRECT -> {
                optionBadge.setText(R.string.badge_correct)
                optionBadge.setTextColor(
                    ContextCompat.getColor(ctx, R.color.md_on_secondary_container),
                )
            }

            Badge.YOURS -> {
                optionBadge.setText(R.string.badge_yours)
                optionBadge.setTextColor(
                    ContextCompat.getColor(ctx, R.color.md_on_error_container),
                )
            }
        }
    }

    /**
     * The design animates the option's background over 180ms when it is graded.
     *
     * The previous colour is remembered on the view rather than read back from it: mid
     * animation the card reports an interpolated colour, and a first bind has no previous
     * state at all, which would otherwise animate in from the theme's card default.
     */
    private fun animateFill(binding: ItemOptionBinding, target: Int) {
        val card = binding.optionRow
        (card.getTag(R.id.tag_fill_animator) as? ValueAnimator)?.cancel()

        val previous = card.getTag(R.id.tag_fill_colour) as? Int
        card.setTag(R.id.tag_fill_colour, target)

        if (previous == null) {
            card.setCardBackgroundColor(target)
            return
        }
        if (previous == target) return

        val animator = ValueAnimator.ofObject(ArgbEvaluator(), previous, target).apply {
            duration = FILL_ANIM_MS
            interpolator = Motion.STANDARD
            addUpdateListener { card.setCardBackgroundColor(it.animatedValue as Int) }
        }
        card.setTag(R.id.tag_fill_animator, animator)
        animator.start()
    }

    private fun tint(ctx: android.content.Context, colorRes: Int) =
        ColorStateList.valueOf(ContextCompat.getColor(ctx, colorRes))
}
