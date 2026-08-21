package com.mvnsh.citizenship.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.withStyledAttributes
import com.mvnsh.citizenship.R

/**
 * The design's one bar shape: a pill track with a pill fill, no shadow, no inset.
 *
 * It appears five times over (daily goal, quiz progress, topic coverage, topic accuracy,
 * the week chart), differing only in colour and height, so it is one view rather than a
 * set of layered drawables per screen. Drawing it directly also avoids the usual
 * width-fraction dance: the fill is a rect, not a resized child, so it animates without
 * ever needing the parent's measured width.
 */
class BarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var animator: ValueAnimator? = null

    /** What is drawn right now, 0f..1f. Lags [percent] while the fill animates. */
    private var drawn = 0f

    /** The value the bar represents, 0..100. */
    var percent: Int = 0
        private set

    init {
        context.withStyledAttributes(attrs, R.styleable.BarView) {
            trackPaint.color = getColor(R.styleable.BarView_barTrackColor, 0)
            fillPaint.color = getColor(R.styleable.BarView_barFillColor, 0)
            percent = getInt(R.styleable.BarView_barPercent, 0).coerceIn(0, 100)
        }
        drawn = percent / 100f
    }

    fun setTrackColor(color: Int) {
        trackPaint.color = color
        invalidate()
    }

    fun setFillColor(color: Int) {
        fillPaint.color = color
        invalidate()
    }

    /**
     * Sets the bar, growing into place over [durationMs] when [animate] is true.
     *
     * Re-setting the same value is a no-op so a state re-render does not replay the
     * growth animation on every emission.
     */
    fun setPercent(value: Int, animate: Boolean = true, durationMs: Long = Motion.BAR_GOAL_MS) {
        val next = value.coerceIn(0, 100)
        if (next == percent && animator?.isRunning != true) return
        percent = next

        animator?.cancel()
        val target = next / 100f
        if (!animate || !isAttachedToWindow) {
            drawn = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(drawn, target).apply {
            duration = durationMs
            interpolator = Motion.STANDARD
            addUpdateListener {
                drawn = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val radius = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, trackPaint)

        if (drawn <= 0f) return
        // Never narrower than the pill is round, or a 1% fill draws as a clipped sliver.
        val filled = (width * drawn).coerceAtLeast(height.toFloat())
        rect.set(0f, 0f, filled.coerceAtMost(width.toFloat()), height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    /** Announces the value to TalkBack; a bar drawn on a canvas is otherwise silent. */
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_PERCENT,
            0f,
            100f,
            percent.toFloat(),
        )
    }
}
