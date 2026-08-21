package com.mvnsh.citizenship.ui.quiz

import androidx.annotation.ColorRes
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.model.Question
import com.mvnsh.citizenship.domain.StudyEngine

/** The four fills an option row can take. */
enum class OptionStyle { PLAIN, SELECTED, CORRECT, WRONG }

/** What sits in the row's 28dp leading circle. */
enum class Marker { LETTER, LETTER_SELECTED, CHECK, CROSS }

/** The trailing label, shown only once a practice question has been graded. */
enum class Badge { NONE, CORRECT, YOURS }

/**
 * One option as the screen should draw it.
 *
 * [optionIndex] is the index into the question's own option list, never the display
 * position: the two differ whenever the question rotates, and answering is keyed on the
 * source index.
 */
data class OptionRow(
    val optionIndex: Int,
    val letter: String,
    val text: String,
    val marker: Marker,
    val badge: Badge,
    val style: OptionStyle,
)

@ColorRes
fun OptionStyle.fillColor(): Int = when (this) {
    OptionStyle.PLAIN -> R.color.md_surface_container_lowest
    OptionStyle.SELECTED -> R.color.md_primary_container
    OptionStyle.CORRECT -> R.color.md_secondary_container
    OptionStyle.WRONG -> R.color.md_error_container
}

@ColorRes
fun OptionStyle.strokeColor(): Int = when (this) {
    OptionStyle.PLAIN -> R.color.md_outline_variant
    OptionStyle.SELECTED -> R.color.md_primary
    OptionStyle.CORRECT -> R.color.md_secondary
    OptionStyle.WRONG -> R.color.md_error
}

@ColorRes
fun OptionStyle.textColor(): Int = when (this) {
    OptionStyle.PLAIN -> R.color.md_on_surface
    OptionStyle.SELECTED -> R.color.md_on_primary_container
    OptionStyle.CORRECT -> R.color.md_on_secondary_container_strong
    OptionStyle.WRONG -> R.color.md_on_error_container
}

/** Rides the variable font's wght axis; a graded row is heavier than an untouched one. */
fun OptionStyle.textWeight(): Int = when (this) {
    OptionStyle.PLAIN -> 400
    OptionStyle.SELECTED -> 600
    OptionStyle.CORRECT -> 600
    OptionStyle.WRONG -> 500
}

/** An unrevealed row keeps the design's hairline; a graded one takes a 1.5dp edge. */
fun OptionStyle.strokeWidthDp(): Float = if (this == OptionStyle.PLAIN) 1f else 1.5f

object OptionRows {

    private const val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    /**
     * Builds the rows for one question.
     *
     * [pickedIndex] is a source index. [revealed] is honoured only outside a mock: a mock
     * gives no feedback until it is submitted, and submitting flips the session's own
     * revealed-ish flags, so the mock branch refuses to grade regardless of what it is
     * passed.
     */
    fun build(
        q: Question,
        pickedIndex: Int?,
        revealed: Boolean,
        mock: Boolean,
    ): List<OptionRow> = StudyEngine.optionOrder(q).mapIndexed { position, sourceIndex ->
        val isPick = pickedIndex == sourceIndex
        val isAnswer = sourceIndex == q.answer
        val graded = revealed && !mock

        val style = when {
            graded && isAnswer -> OptionStyle.CORRECT
            graded && isPick -> OptionStyle.WRONG
            !graded && isPick -> OptionStyle.SELECTED
            else -> OptionStyle.PLAIN
        }

        OptionRow(
            optionIndex = sourceIndex,
            letter = LETTERS.getOrNull(position)?.toString() ?: (position + 1).toString(),
            text = q.options.getOrElse(sourceIndex) { "" },
            marker = when (style) {
                OptionStyle.CORRECT -> Marker.CHECK
                OptionStyle.WRONG -> Marker.CROSS
                OptionStyle.SELECTED -> Marker.LETTER_SELECTED
                OptionStyle.PLAIN -> Marker.LETTER
            },
            badge = when {
                !graded -> Badge.NONE
                isAnswer -> Badge.CORRECT
                isPick -> Badge.YOURS
                else -> Badge.NONE
            },
            style = style,
        )
    }
}
