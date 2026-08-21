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
import com.mvnsh.citizenship.data.model.SessionState
import com.mvnsh.citizenship.domain.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultsTest : BaseUiTest() {

    /** Ids 1 and 2 are both Law and Justice, and both answer index 0. */
    private fun finished(
        marks: Map<Int, Int>,
        right: Int,
        goalTarget: Int = 20,
        goalDone: Int = 2,
        streak: Int = 0,
    ) = ProgressState(
        onboarded = true,
        goalTarget = goalTarget, goalDone = goalDone, goalDate = DateUtils.today(), streak = streak,
        session = SessionState(
            mode = "QUICK", label = "Quick practice", ids = listOf(1, 2),
            index = 1, marks = marks, right = right, submitted = true, finalRight = right,
            startedAtEpochMs = System.currentTimeMillis() - 120_000,
        ),
    )

    private fun inResults(state: ProgressState, block: () -> Unit) = withProgress(state) {
        navigateTo(R.id.resultsFragment)
        block()
    }

    @Test
    fun the_score_headline_and_tiles_reflect_the_session() =
        inResults(finished(mapOf(1 to 0, 2 to 1), right = 1)) {
            onView(withText("Quick practice · session complete")).check(matches(isDisplayed()))
            onView(withId(R.id.score)).check(matches(withText("1")))
            onView(withText("of 2 correct")).check(matches(isDisplayed()))
            onView(withId(R.id.stat_accuracy)).check(matches(withText("50%")))
            onView(withId(R.id.stat_missed)).check(matches(withText("1")))
            onView(withId(R.id.stat_time)).check(matches(withText("2m")))
        }

    @Test
    fun a_missed_question_is_listed_with_both_answers() =
        inResults(finished(mapOf(1 to 1, 2 to 0), right = 1)) {
            onView(withText("What you missed")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText("You said: Only individual citizens")).perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(withText("Correct: Everyone, including individuals, corporations, and governments")).perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(withText("Practise the 1 you missed")).check(matches(isDisplayed()))
        }

    @Test
    fun a_question_left_unanswered_is_listed_as_skipped() =
        inResults(finished(mapOf(2 to 0), right = 1)) {
            onView(withText("You said: Skipped")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.stat_missed)).check(matches(withText("1")))
        }

    @Test
    fun a_clean_sweep_swaps_in_the_praise_card() =
        inResults(finished(mapOf(1 to 0, 2 to 0), right = 2)) {
            onView(
                withText(
                    "Every answer correct. Questions you get right twice in a row come back less often.",
                ),
            ).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.practise_missed))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.missed_label))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
        }

    @Test
    fun a_met_goal_is_called_out() = inResults(
        finished(mapOf(1 to 0, 2 to 0), right = 2, goalTarget = 20, goalDone = 20, streak = 5),
    ) {
        onView(withText("Daily goal met · 5-day streak safe")).check(matches(isDisplayed()))
    }

    @Test
    fun an_unmet_goal_leaves_the_callout_out() =
        inResults(finished(mapOf(1 to 0, 2 to 0), right = 2, goalDone = 3)) {
            onView(withId(R.id.goal_met_card))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
        }

    @Test
    fun practising_the_missed_set_starts_a_session_of_exactly_those() =
        inResults(finished(mapOf(1 to 1, 2 to 0), right = 1)) {
            onView(withText("Practise the 1 you missed")).perform(click())
            assertEquals(R.id.quizFragment, currentDestinationId())
            val session = progress().session!!
            assertEquals(listOf(1), session.ids)
            assertEquals("Missed questions", session.label)
        }

    @Test
    fun back_to_home_clears_the_finished_session() =
        inResults(finished(mapOf(1 to 0, 2 to 0), right = 2)) {
            onView(withText("Back to home")).perform(click())
            assertEquals(R.id.homeFragment, currentDestinationId())
            assertNull(progress().session)
            onView(withId(R.id.hero_title)).check(matches(withText("Start studying")))
        }
}
