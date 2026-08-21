package com.mvnsh.citizenship.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.model.MockAttempt
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SessionState
import com.mvnsh.citizenship.domain.DateUtils
import com.mvnsh.citizenship.domain.Stats
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class MockTest : BaseUiTest() {

    /** A mock in progress. Ids 1-3 are Law and Justice; q1 and q2 both answer index 0. */
    private fun mock(
        ids: List<Int>,
        marks: Map<Int, Int> = emptyMap(),
        secondsLeft: Int = 1800,
    ): ProgressState {
        val now = System.currentTimeMillis()
        return ProgressState(
            onboarded = true,
            session = SessionState(
                mode = "MOCK", label = "Mock test", ids = ids, marks = marks, timed = true,
                startedAtEpochMs = now, deadlineEpochMs = now + secondsLeft * 1000L,
            ),
        )
    }

    /**
     * A full-size mock built from the real bank, with [unanswered] questions left
     * blank, then [correct] answered right, then the rest answered wrong.
     *
     * Blanks come first so a review assertion lands on a row the list has actually laid
     * out - the review list is a RecyclerView, so a row twenty deep does not exist yet
     * and ViewActions.scrollTo cannot reach it.
     *
     * The pass rule is the design's literal "15 correct", so a banner can only be
     * asserted against a mock of the length the app actually produces - a two-question
     * mock can never reach the bar and would always render as a near miss.
     */
    private fun mock20(correct: Int, unanswered: Int = 0): ProgressState {
        val loaded = runBlocking { app().bankRepository.load() }
        val ids = (1..20).filter { loaded.byId.containsKey(it) }
        check(ids.size == 20) { "ids 1..20 are expected to all be in the bank" }

        val marks = buildMap {
            ids.forEachIndexed { index, id ->
                val q = loaded.byId.getValue(id)
                when {
                    index < unanswered -> Unit
                    index < unanswered + correct -> put(id, q.answer)
                    else -> put(id, (q.answer + 1) % q.options.size)
                }
            }
        }
        return mock(ids, marks)
    }

    private fun inMock(state: ProgressState, block: () -> Unit) = withProgress(state) {
        navigateTo(R.id.mockFragment)
        block()
    }

    // ---- intro ----------------------------------------------------------

    @Test
    fun the_intro_states_the_rules() = withProgress(ProgressState(onboarded = true)) {
        navigateTo(R.id.mockIntroFragment)
        onView(withText("Same shape as the real test")).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.fact_value), hasSibling(withText("Questions"))))
            .check(matches(withText("20")))
        onView(allOf(withId(R.id.fact_value), hasSibling(withText("Time limit"))))
            .check(matches(withText("30 minutes")))
        onView(allOf(withId(R.id.fact_value), hasSibling(withText("To pass"))))
            .check(matches(withText("15 correct")))
        onView(allOf(withId(R.id.fact_value), hasSibling(withText("Answers"))))
            .check(matches(withText("Changeable until you submit")))
    }

    @Test
    fun previous_attempts_are_listed_newest_first_and_numbered_by_when_they_happened() =
        withProgress(
            ProgressState(
                onboarded = true,
                mocks = listOf(
                    MockAttempt(70, DateUtils.shiftDay(-9)),
                    MockAttempt(85, DateUtils.shiftDay(-1)),
                ),
            ),
        ) {
            navigateTo(R.id.mockIntroFragment)
            onView(withText("Your last attempts")).perform(scrollTo())
                .check(matches(isDisplayed()))
            // The newest sits at the top but keeps its original number.
            onView(allOf(withId(R.id.pct), hasSibling(withText("Mock test 2"))))
                .perform(scrollTo())
                .check(matches(withText("85%")))
            onView(allOf(withId(R.id.pct), hasSibling(withText("Mock test 1"))))
                .perform(scrollTo())
                .check(matches(withText("70%")))
        }

    @Test
    fun starting_a_test_draws_twenty_questions_and_shows_the_timer() =
        withProgress(ProgressState(onboarded = true)) {
            navigateTo(R.id.mockIntroFragment)
            onView(withText("Start the test")).perform(click())
            assertEquals(R.id.mockFragment, currentDestinationId())
            onView(withId(R.id.q_counter)).check(matches(withText("1/20")))
            onView(withId(R.id.timer)).check(matches(withText(startsWith("29:"))))
            val session = progress().session!!
            assertEquals(20, session.ids.size)
            assertEquals(20, session.ids.toSet().size)
        }

    // ---- the run --------------------------------------------------------

    @Test
    fun a_mock_gives_no_feedback_and_the_answer_stays_changeable() = inMock(mock(listOf(1, 2, 3))) {
        onView(withText("Only individual citizens")).perform(scrollTo(), click())
        onView(withText("Why")).check(doesNotExist())
        onView(withText("Correct")).check(doesNotExist())

        onView(withText("Only government officials and politicians")).perform(scrollTo(), click())
        assertEquals(2, progress().session!!.marks[1])
        assertEquals("a mock records nothing until submit", 0, Stats.answered(progress()))
    }

    @Test
    fun the_navigator_shows_answered_state_and_jumps() =
        inMock(mock(listOf(1, 2, 3), marks = mapOf(1 to 0))) {
            onView(withId(R.id.navigator)).perform(click())
            onView(withText("Jump to question")).check(matches(isDisplayed()))
            onView(withText("1 of 3 answered")).check(matches(isDisplayed()))
            onView(withText("3")).perform(click())
            onView(withId(R.id.q_counter)).check(matches(withText("3/3")))
        }

    @Test
    fun the_submit_button_counts_unanswered_questions() =
        inMock(mock(listOf(1, 2), marks = mapOf(1 to 0))) {
            onView(withId(R.id.navigator)).perform(click())
            onView(withText("Submit (1/2)")).check(matches(isDisplayed()))
            onView(withText("Keep going")).perform(click())

            onView(withText("Magna Carta")).check(doesNotExist()) // still on question 1
            onView(withId(R.id.mock_next)).perform(click())
            onView(withText("Magna Carta")).perform(click())

            onView(withId(R.id.navigator)).perform(click())
            onView(withText("Submit test")).check(matches(isDisplayed()))
        }

    @Test
    fun back_moves_to_the_previous_question_and_keeps_the_answer() =
        inMock(mock(listOf(1, 2), marks = mapOf(1 to 0))) {
            onView(withId(R.id.mock_next)).perform(click())
            onView(withId(R.id.q_counter)).check(matches(withText("2/2")))
            onView(withId(R.id.mock_back)).perform(click())
            onView(withId(R.id.q_counter)).check(matches(withText("1/2")))
            assertEquals(0, progress().session!!.marks[1])
        }

    // ---- results --------------------------------------------------------

    @Test
    fun a_passing_result_shows_the_pass_banner_and_records_the_attempt() =
        inMock(mock20(correct = 17)) {
            onView(withId(R.id.navigator)).perform(click())
            onView(withText("Submit test")).perform(click())
            assertEquals(R.id.mockResultsFragment, currentDestinationId())
            onView(withText("You would have passed")).check(matches(isDisplayed()))
            onView(withId(R.id.score)).check(matches(withText("17")))
            onView(withId(R.id.score_of)).check(matches(withText("of 20 · 85%")))
            // The attempt is stamped with an instant, matching what the web app writes,
            // so assert the score and the day rather than a formatted string.
            val attempt = progress().mocks.single()
            assertEquals(85, attempt.pct)
            assertEquals(LocalDate.now(), DateUtils.localDayOf(attempt.date))
        }

    @Test
    fun exactly_fifteen_correct_is_a_pass() = inMock(mock20(correct = 15)) {
        onView(withId(R.id.navigator)).perform(click())
        onView(withText("Submit test")).perform(click())
        onView(withText("You would have passed")).check(matches(isDisplayed()))
    }

    @Test
    fun a_failing_result_says_how_many_more_were_needed() = inMock(mock20(correct = 12)) {
        onView(withId(R.id.navigator)).perform(click())
        onView(withText("Submit test")).perform(click())
        onView(withText("Close — 15 of 20 is the bar")).check(matches(isDisplayed()))
        onView(withText(containsString("You needed 3 more"))).check(matches(isDisplayed()))
    }

    @Test
    fun submitting_folds_every_answer_into_the_running_totals() =
        inMock(mock20(correct = 12, unanswered = 2)) {
            onView(withId(R.id.navigator)).perform(click())
            onView(withText(startsWith("Submit ("))).perform(click())
            val p = progress()
            assertEquals(20, Stats.answered(p))
            assertEquals(12, Stats.correct(p))
            // An unanswered question is marked wrong, as the real test would.
            assertEquals(8, p.seen.values.count { it.m > 0 })
        }

    @Test
    fun review_lists_every_question_with_its_outcome() =
        inMock(mock20(correct = 18, unanswered = 1)) {
            onView(withId(R.id.navigator)).perform(click())
            onView(withText(startsWith("Submit"))).perform(click())
            onView(withText("Review all 20 answers")).perform(click())
            assertEquals(R.id.reviewFragment, currentDestinationId())
            onView(withText("Review · 18 of 20")).check(matches(isDisplayed()))
            onView(withText("You said: Not answered")).check(matches(isDisplayed()))
        }

    @Test
    fun an_expired_deadline_auto_submits() =
        inMock(mock20(correct = 17).let { state ->
            val now = System.currentTimeMillis()
            state.copy(session = state.session!!.copy(deadlineEpochMs = now + 1_000))
        }) {
            // The clock is the only thing that ends the test here; nothing is tapped.
            assertEquals(R.id.mockResultsFragment, currentDestinationId())
            onView(withId(R.id.score)).check(matches(withText("17")))
            assertEquals(1, progress().mocks.size)
        }

    @Test
    fun retaking_from_the_results_starts_a_fresh_timed_test() = inMock(mock20(correct = 17)) {
        onView(withId(R.id.navigator)).perform(click())
        onView(withText("Submit test")).perform(click())
        onView(withText("Retake")).perform(click())
        assertEquals(R.id.mockFragment, currentDestinationId())
        val session = progress().session!!
        assertEquals(20, session.ids.size)
        assertEquals(false, session.submitted)
    }
}
