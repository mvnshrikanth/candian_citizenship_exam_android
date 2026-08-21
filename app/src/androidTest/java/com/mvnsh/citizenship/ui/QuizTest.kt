package com.mvnsh.citizenship.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SeenStat
import com.mvnsh.citizenship.data.model.SessionState
import org.hamcrest.Matchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuizTest : BaseUiTest() {

    /**
     * Seeds a running practice session. Question 1's answer is "Both A and B"; question
     * 4 is the one with no authored explanation.
     */
    private fun startedQuiz(vararg ids: Int) = ProgressState(
        onboarded = true,
        session = SessionState(
            mode = "ALL", label = "All questions", ids = ids.toList(),
            startedAtEpochMs = System.currentTimeMillis(),
        ),
    )

    private fun inQuiz(state: ProgressState, block: () -> Unit) = withProgress(state) {
        navigateTo(R.id.quizFragment)
        block()
    }

    @Test
    fun the_header_shows_topic_counter_and_progress() = inQuiz(startedQuiz(1, 2, 3)) {
        onView(withId(R.id.q_counter)).check(matches(withText("1/3")))
        onView(withId(R.id.q_topic)).check(matches(withText("Rights & Responsibilities")))
        onView(withText("Who is regulated by laws in Canada?")).check(matches(isDisplayed()))
        onView(withId(R.id.skip)).check(matches(isDisplayed()))
    }

    @Test
    fun a_correct_answer_reveals_the_explanation_and_the_next_button() =
        inQuiz(startedQuiz(1, 2)) {
            onView(withText("Both A and B")).perform(click())
            onView(withText("Why")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText(containsString("The rule of law means everyone is regulated by law")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(withText("Next question")).check(matches(isDisplayed()))
            onView(withId(R.id.skip))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
            assertEquals(1, progress().correct)
            assertEquals(1, progress().answered)
        }

    @Test
    fun a_wrong_answer_is_recorded_and_the_answer_is_shown() = inQuiz(startedQuiz(1, 2)) {
        onView(withText("Individuals")).perform(click())
        onView(withText("Your pick")).check(matches(isDisplayed()))
        onView(withText("Correct")).check(matches(isDisplayed()))
        val p = progress()
        assertEquals(1, p.answered)
        assertEquals(0, p.correct)
        assertEquals(1, p.seen[1]!!.m)
    }

    @Test
    fun tapping_a_second_option_after_revealing_changes_nothing() = inQuiz(startedQuiz(1, 2)) {
        onView(withText("Individuals")).perform(click())
        onView(withText("Governments")).perform(click())
        assertEquals("a revealed question is locked", 1, progress().answered)
        assertEquals("and counted exactly once", 1, progress().seen[1]!!.s)
    }

    @Test
    fun a_question_with_no_authored_explanation_shows_the_answer_panel_instead() =
        inQuiz(startedQuiz(4)) {
            onView(withText("1982")).perform(click())
            onView(withText("Answer")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(
                withText(
                    containsString("An explanation for this question hasn't been written yet"),
                ),
            ).perform(scrollTo()).check(matches(isDisplayed()))
        }

    @Test
    fun the_last_question_offers_results_and_reaches_them() = inQuiz(startedQuiz(1)) {
        onView(withText("Both A and B")).perform(click())
        onView(withText("See results")).perform(click())
        assertEquals(R.id.resultsFragment, currentDestinationId())
        val session = progress().session!!
        assertEquals(true, session.submitted)
        assertEquals(1, session.finalRight)
    }

    @Test
    fun skip_advances_without_recording_an_answer() = inQuiz(startedQuiz(1, 2)) {
        onView(withId(R.id.skip)).perform(click())
        onView(withId(R.id.q_counter)).check(matches(withText("2/2")))
        assertEquals(0, progress().answered)
    }

    @Test
    fun the_seen_line_reports_the_history_before_the_answer_is_given() = inQuiz(
        startedQuiz(1, 2).copy(seen = mapOf(1 to SeenStat(4, 2))),
    ) {
        onView(withId(R.id.seen_line)).check(matches(withText("Seen 4× · missed 2×")))
    }

    @Test
    fun the_flag_toggles_the_bookmark_for_this_question() = inQuiz(startedQuiz(1, 2)) {
        onView(withId(R.id.bookmark)).perform(click())
        assertEquals(listOf(1), progress().bookmarks)
        onView(withId(R.id.bookmark)).perform(click())
        assertEquals(emptyList<Int>(), progress().bookmarks)
    }

    @Test
    fun leaving_offers_to_keep_the_session() = inQuiz(startedQuiz(1, 2, 3)) {
        onView(withId(R.id.exit)).perform(click())
        onView(withText("Leave this session?")).check(matches(isDisplayed()))
        onView(withText("Save and exit")).perform(click())
        assertEquals(R.id.homeFragment, currentDestinationId())
        assertEquals("the session is kept for home to resume", 3, progress().session!!.ids.size)
    }

    @Test
    fun leaving_can_also_discard_the_session() = inQuiz(startedQuiz(1, 2, 3)) {
        onView(withId(R.id.exit)).perform(click())
        onView(withText("Discard the session")).perform(click())
        assertEquals(R.id.homeFragment, currentDestinationId())
        assertNull(progress().session)
    }

    @Test
    fun keep_studying_closes_the_dialog_and_stays_put() = inQuiz(startedQuiz(1, 2, 3)) {
        onView(withId(R.id.exit)).perform(click())
        onView(withText("Keep studying")).perform(click())
        assertEquals(R.id.quizFragment, currentDestinationId())
        onView(withId(R.id.q_counter)).check(matches(withText("1/3")))
    }
}
