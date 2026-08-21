package com.mvnsh.citizenship.ui

import com.mvnsh.citizenship.data.model.Question
import com.mvnsh.citizenship.ui.quiz.Badge
import com.mvnsh.citizenship.ui.quiz.Marker
import com.mvnsh.citizenship.ui.quiz.OptionRows
import com.mvnsh.citizenship.ui.quiz.OptionStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The design gives an option four visual states across two modes, and getting one wrong
 * is silent - the screen still renders, it just lies about the answer. So row
 * construction is a pure function and every state is pinned here rather than on a device.
 */
class OptionStyleTest {

    // id 4 with four options rotates by 0, so display order matches source order.
    private val q = Question(
        id = 4, topic = "rights", question = "In what year?",
        options = listOf("1867", "1921", "1982", "2015"), answer = 2,
    )

    @Test
    fun before_answering_every_row_is_plain_and_lettered() {
        val rows = OptionRows.build(q, pickedIndex = null, revealed = false, mock = false)
        assertEquals(4, rows.size)
        assertEquals(listOf("A", "B", "C", "D"), rows.map { it.letter })
        assertEquals(List(4) { OptionStyle.PLAIN }, rows.map { it.style })
        assertEquals(List(4) { Marker.LETTER }, rows.map { it.marker })
        assertEquals(List(4) { Badge.NONE }, rows.map { it.badge })
    }

    @Test
    fun a_correct_reveal_marks_the_answer_and_nothing_else() {
        val rows = OptionRows.build(q, pickedIndex = 2, revealed = true, mock = false)
        val correct = rows.first { it.optionIndex == 2 }
        assertEquals(OptionStyle.CORRECT, correct.style)
        assertEquals(Marker.CHECK, correct.marker)
        assertEquals(Badge.CORRECT, correct.badge)
        rows.filter { it.optionIndex != 2 }.forEach {
            assertEquals(OptionStyle.PLAIN, it.style)
            assertEquals(Marker.LETTER, it.marker)
            assertEquals(Badge.NONE, it.badge)
        }
    }

    @Test
    fun a_wrong_reveal_marks_both_the_pick_and_the_answer() {
        val rows = OptionRows.build(q, pickedIndex = 0, revealed = true, mock = false)
        val yours = rows.first { it.optionIndex == 0 }
        val answer = rows.first { it.optionIndex == 2 }
        assertEquals(OptionStyle.WRONG, yours.style)
        assertEquals(Marker.CROSS, yours.marker)
        assertEquals(Badge.YOURS, yours.badge)
        assertEquals(OptionStyle.CORRECT, answer.style)
        assertEquals(Marker.CHECK, answer.marker)
        assertEquals(Badge.CORRECT, answer.badge)
        // The two untouched rows stay quiet.
        assertEquals(
            List(2) { OptionStyle.PLAIN },
            rows.filter { it.optionIndex != 0 && it.optionIndex != 2 }.map { it.style },
        )
    }

    @Test
    fun a_skipped_question_still_reveals_the_answer_without_marking_a_pick() {
        val rows = OptionRows.build(q, pickedIndex = null, revealed = true, mock = false)
        assertEquals(OptionStyle.CORRECT, rows.first { it.optionIndex == 2 }.style)
        assertEquals(
            "nothing was picked, so nothing is wrong",
            emptyList<OptionStyle>(),
            rows.map { it.style }.filter { it == OptionStyle.WRONG },
        )
    }

    @Test
    fun in_a_mock_a_pick_is_only_highlighted_never_graded() {
        val rows = OptionRows.build(q, pickedIndex = 0, revealed = false, mock = true)
        val picked = rows.first { it.optionIndex == 0 }
        assertEquals(OptionStyle.SELECTED, picked.style)
        assertEquals(Marker.LETTER_SELECTED, picked.marker)
        assertEquals("a mock never reveals the answer", Badge.NONE, picked.badge)
        rows.filter { it.optionIndex != 0 }.forEach {
            assertEquals(OptionStyle.PLAIN, it.style)
            assertEquals(Badge.NONE, it.badge)
        }
    }

    @Test
    fun a_mock_never_grades_even_if_asked_to_reveal() {
        // Defence in depth: submitting flips revealed on the session, and the run screen
        // must not start showing answers because of it.
        val rows = OptionRows.build(q, pickedIndex = 0, revealed = true, mock = true)
        assertEquals(OptionStyle.SELECTED, rows.first { it.optionIndex == 0 }.style)
        assertEquals(OptionStyle.PLAIN, rows.first { it.optionIndex == 2 }.style)
    }

    @Test
    fun rows_are_presented_in_the_rotated_order_with_letters_following_position() {
        // id 1 rotates by 1, so source order 0,1,2,3 displays as 1,2,3,0.
        val rotated = q.copy(id = 1)
        val rows = OptionRows.build(rotated, pickedIndex = null, revealed = false, mock = false)
        assertEquals(listOf(1, 2, 3, 0), rows.map { it.optionIndex })
        assertEquals(listOf("A", "B", "C", "D"), rows.map { it.letter })
        assertEquals(listOf("1921", "1982", "2015", "1867"), rows.map { it.text })
    }

    @Test
    fun a_rotated_question_still_marks_the_right_source_index() {
        val rotated = q.copy(id = 1)
        val rows = OptionRows.build(rotated, pickedIndex = 0, revealed = true, mock = false)
        // Source index 2 is "1982", the answer, and it now sits in position B.
        val correct = rows.first { it.style == OptionStyle.CORRECT }
        assertEquals(2, correct.optionIndex)
        assertEquals("B", correct.letter)
        assertEquals("1982", correct.text)

        val yours = rows.first { it.style == OptionStyle.WRONG }
        assertEquals(0, yours.optionIndex)
        assertEquals("D", yours.letter)
    }

    @Test
    fun a_question_with_more_options_than_letters_falls_back_to_a_number() {
        val many = q.copy(options = List(30) { "option $it" }, answer = 0)
        val rows = OptionRows.build(many, pickedIndex = null, revealed = false, mock = false)
        assertEquals(30, rows.size)
        assertEquals("Z", rows[25].letter)
        assertEquals("27", rows[26].letter)
    }
}
