package com.mvnsh.citizenship.ui

import android.view.ViewGroup
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.model.MockAttempt
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SeenStat
import com.mvnsh.citizenship.domain.DateUtils
import com.mvnsh.citizenship.domain.Stats
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressScreenTest : BaseUiTest() {

    private fun onProgress(state: ProgressState, block: () -> Unit) = withProgress(state) {
        onView(withId(R.id.progressFragment)).perform(click())
        block()
    }

    @Test
    fun the_empty_state_invites_a_first_session() =
        onProgress(ProgressState(onboarded = true)) {
            onView(withText("Nothing measured yet")).check(matches(isDisplayed()))
            onView(withText("Start practising")).perform(click())
            assertEquals(R.id.quizFragment, currentDestinationId())
            assertEquals(10, progress().session!!.ids.size)
        }

    @Test
    fun the_hero_card_reports_accuracy_and_a_readiness_verdict() = onProgress(
        ProgressState(
            onboarded = true, answered = 100, correct = 88, best = 14,
            seen = (1..40).associateWith { SeenStat(1, 0) },
        ),
    ) {
        onView(withId(R.id.accuracy)).check(matches(withText("88%")))
        onView(withText("Test ready")).check(matches(isDisplayed()))
        onView(withText("You are scoring above the passing mark on recent questions."))
            .check(matches(isDisplayed()))
        onView(withId(R.id.stat_answered)).check(matches(withText("100")))
        // 501 in the bank. Ids 1..40 are seeded but id 39 is not in the bank, so only 39
        // of them are real questions - "not seen" counts the bank, not the seen map, and
        // 462 rather than 461 is what proves it.
        onView(withId(R.id.stat_remaining)).check(matches(withText("462")))
        onView(withId(R.id.stat_best)).check(matches(withText("14")))
    }

    @Test
    fun the_verdict_steps_down_with_accuracy() {
        mapOf(90 to "Test ready", 78 to "Nearly there", 40 to "Keep practising")
            .forEach { (correct, verdict) ->
                onProgress(ProgressState(onboarded = true, answered = 100, correct = correct)) {
                    onView(withText(verdict)).check(matches(isDisplayed()))
                }
            }
    }

    @Test
    fun a_streak_running_now_counts_as_the_best_even_before_it_is_banked() = onProgress(
        ProgressState(onboarded = true, answered = 10, correct = 8, streak = 9, best = 4),
    ) {
        onView(withId(R.id.stat_best)).check(matches(withText("9")))
    }

    @Test
    fun milestones_show_earned_and_unearned() = onProgress(
        ProgressState(onboarded = true, answered = 60, correct = 50, streak = 4),
    ) {
        onView(withText("Milestones")).perform(scrollTo()).check(matches(isDisplayed()))
        // first, s3 and q50 are reached; the other five are not.
        onView(withId(R.id.miles_count)).check(matches(withText("3 of 8")))
        Stats.MILESTONES.forEach {
            onView(withText(it.label)).perform(scrollTo()).check(matches(isDisplayed()))
        }
    }

    @Test
    fun by_topic_lists_all_seven_with_an_em_dash_for_the_untouched() = onProgress(
        ProgressState(onboarded = true, answered = 5, correct = 4, seen = mapOf(1 to SeenStat(4, 1))),
    ) {
        onView(withText("By topic")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.topic_accuracy), hasSibling(withText("Economy"))))
            .perform(scrollTo())
            .check(matches(withText("—")))
        onView(allOf(withId(R.id.topic_accuracy), hasSibling(withText("Rights & Responsibilities"))))
            .perform(scrollTo())
            .check(matches(withText("75%")))
    }

    @Test
    fun mock_attempts_are_listed_newest_first() = onProgress(
        ProgressState(
            onboarded = true, answered = 40, correct = 30,
            mocks = listOf(
                MockAttempt(70, DateUtils.shiftDay(-9)),
                MockAttempt(85, DateUtils.shiftDay(-1)),
            ),
        ),
    ) {
        onView(withText("Mock tests")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.pct), hasSibling(withText("Mock test 2"))))
            .perform(scrollTo())
            .check(matches(withText("85%")))
    }

    @Test
    fun the_mock_card_is_hidden_until_a_mock_has_been_taken() =
        onProgress(ProgressState(onboarded = true, answered = 40, correct = 30)) {
            onView(withId(R.id.mocks_card))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
        }

    @Test
    fun the_week_chart_draws_seven_days_ending_today() = onProgress(
        ProgressState(
            onboarded = true, answered = 20, correct = 15,
            week = listOf(2, 4, 6, 3, 8, 1, 5), weekDate = DateUtils.today(),
        ),
    ) {
        onView(withText("This week")).perform(scrollTo()).check(matches(isDisplayed()))
        var bars = 0
        rule.onActivity { activity ->
            bars = activity.findViewById<ViewGroup>(R.id.week_bars).childCount
        }
        assertEquals(7, bars)
    }
}
