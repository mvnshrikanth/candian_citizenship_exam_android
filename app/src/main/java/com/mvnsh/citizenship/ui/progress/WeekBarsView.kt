package com.mvnsh.citizenship.ui.progress

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.domain.Stats
import kotlin.math.max

/**
 * Seven day columns, each a bar over its day letter.
 *
 * A fixed seven children need no recycling, so this is a LinearLayout rather than a
 * RecyclerView. The labels come from the dates in [Stats.DayCount], not a fixed
 * "M T W T F S S" string - the window rolls, so the letters have to roll with it.
 */
class WeekBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        orientation = HORIZONTAL
        gravity = Gravity.BOTTOM
    }

    fun setDays(days: List<Stats.DayCount>) {
        removeAllViews()
        val busiest = max(1, days.maxOfOrNull { it.count } ?: 0)
        val density = resources.displayMetrics.density

        days.forEach { day ->
            addView(
                buildColumn(day, busiest, density),
                LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
            )
        }
    }

    private fun buildColumn(day: Stats.DayCount, busiest: Int, density: Float): View {
        val column = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            // A day with nothing on it still shows a sliver, so the row reads as a chart
            // rather than as a gap.
            val fraction = day.count.toFloat() / busiest
            val height = (MIN_BAR_DP + (MAX_BAR_DP - MIN_BAR_DP) * fraction) * density

            addView(
                View(context).apply {
                    setBackgroundResource(R.drawable.bg_week_bar)
                    backgroundTintList = ContextCompat.getColorStateList(
                        context, R.color.md_primary_container,
                    )
                },
                LayoutParams(LayoutParams.MATCH_PARENT, height.toInt()).apply {
                    marginStart = (BAR_GAP_DP * density).toInt()
                    marginEnd = (BAR_GAP_DP * density).toInt()
                },
            )

            addView(
                TextView(context).apply {
                    text = day.label
                    textSize = LABEL_SP
                    setTextColor(ContextCompat.getColor(context, R.color.md_text_muted))
                    gravity = Gravity.CENTER_HORIZONTAL
                },
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (6 * density).toInt()
                },
            )
        }
        column.contentDescription =
            context.getString(R.string.cd_week_bar, day.date, day.count)
        return column
    }

    private companion object {
        const val MIN_BAR_DP = 4f
        const val MAX_BAR_DP = 64f
        const val BAR_GAP_DP = 4f
        const val LABEL_SP = 10.5f
    }
}
