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
            onboarded = true,
            // 40 distinct questions over 14 days: 88% accuracy, a 14-day streak, and
            // 535 - 40 unseen.
            seen = seenRecords(answered = 100, correct = 88, days = 14, questions = 40),
        ),
    ) {
        onView(withId(R.id.accuracy)).check(matches(withText("88%")))
        onView(withText("Test ready")).check(matches(isDisplayed()))
        onView(withText("You are scoring above the passing mark on recent questions."))
            .check(matches(isDisplayed()))
        onView(withId(R.id.stat_answered)).check(matches(withText("100")))
        // 535 in the bank, 40 of them seen. The ids are contiguous now, so all 40 are
        // real questions; "not seen" is still counted from the bank rather than from the
        // size of the seen map, which is what keeps a stale record from inflating it.
        onView(withId(R.id.stat_remaining)).check(matches(withText("495")))
        onView(withId(R.id.stat_best)).check(matches(withText("14")))
    }

    @Test
    fun the_verdict_steps_down_with_accuracy() {
        mapOf(90 to "Test ready", 78 to "Nearly there", 40 to "Keep practising")
            .forEach { (correct, verdict) ->
                onProgress(
                    ProgressState(onboarded = true, seen = seenRecords(100, correct)),
                ) {
                    onView(withText(verdict)).check(matches(isDisplayed()))
                }
            }
    }

    @Test
    fun best_streak_is_the_longest_run_on_record_not_the_current_one() = onProgress(
        // A four-day run a fortnight ago, then a single day today. Both are derived from
        // the same day set, so "best" has to look back rather than trust a stored number.
        ProgressState(
            onboarded = true,
            seen = mapOf(
                1 to SeenStat(1, 0, isoDaysAgo(14)),
                2 to SeenStat(1, 0, isoDaysAgo(13)),
                3 to SeenStat(1, 0, isoDaysAgo(12)),
                4 to SeenStat(1, 0, isoDaysAgo(11)),
                5 to SeenStat(1, 0, isoDaysAgo(0)),
            ),
        ),
    ) {
        onView(withId(R.id.stat_best)).check(matches(withText("4")))
    }

    @Test
    fun milestones_show_earned_and_unearned() = onProgress(
        // Ten questions, not sixty: ids 1..10 are only law and rights, so "all 7
        // topics" stays locked and the count is the three this test is about.
        ProgressState(onboarded = true, seen = seenRecords(60, 50, days = 4, questions = 10)),
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
        ProgressState(onboarded = true, seen = mapOf(1 to SeenStat(4, 1, isoDaysAgo(0)))),
    ) {
        onView(withText("By topic")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.topic_accuracy), hasSibling(withText("Economy"))))
            .perform(scrollTo())
            .check(matches(withText("—")))
        onView(allOf(withId(R.id.topic_accuracy), hasSibling(withText("Law and Justice"))))
            .perform(scrollTo())
            .check(matches(withText("75%")))
    }

    @Test
    fun mock_attempts_are_listed_newest_first() = onProgress(
        ProgressState(
            onboarded = true, seen = seenRecords(40, 30),
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
        onProgress(ProgressState(onboarded = true, seen = seenRecords(40, 30))) {
            onView(withId(R.id.mocks_card))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
        }

    @Test
    fun the_week_chart_draws_seven_days_ending_today() = onProgress(
        ProgressState(
            onboarded = true, seen = seenRecords(20, 15, days = 5),
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
