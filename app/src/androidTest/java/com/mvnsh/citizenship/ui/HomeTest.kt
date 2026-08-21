package com.mvnsh.citizenship.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SeenStat
import com.mvnsh.citizenship.data.model.SessionState
import com.mvnsh.citizenship.domain.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeTest : BaseUiTest() {

    @Test
    fun the_goal_card_shows_progress_towards_the_target() = withProgress(
        ProgressState(
            onboarded = true, goalTarget = 20, goalDone = 14, goalDate = DateUtils.today(),
            streak = 12, answered = 340, correct = 279,
        ),
    ) {
        onView(withText("Today's goal")).check(matches(isDisplayed()))
        onView(withId(R.id.goal_count)).check(matches(withText("14")))
        onView(withId(R.id.goal_target)).check(matches(withText("/ 20 questions")))
        onView(withText("6 to go · about 3 minutes")).check(matches(isDisplayed()))
        onView(withText("12 days")).check(matches(isDisplayed()))
        onView(withText("Day 12 of studying")).check(matches(isDisplayed()))
    }

    @Test
    fun a_met_goal_swaps_the_line() = withProgress(
        ProgressState(onboarded = true, goalTarget = 20, goalDone = 20, goalDate = DateUtils.today()),
    ) {
        onView(withText("Goal met · well done")).check(matches(isDisplayed()))
    }

    @Test
    fun a_goal_counted_on_an_earlier_day_reads_as_nothing_done_today() = withProgress(
        // The stored counter is stale until the next answer rolls it, so home must
        // compare the date rather than trust goalDone.
        ProgressState(
            onboarded = true, goalTarget = 20, goalDone = 20, goalDate = DateUtils.shiftDay(-1),
        ),
    ) {
        onView(withId(R.id.goal_count)).check(matches(withText("0")))
        onView(withText("20 to go · about 10 minutes")).check(matches(isDisplayed()))
    }

    @Test
    fun with_no_test_date_the_prompt_replaces_the_countdown() = withProgress(
        ProgressState(onboarded = true, testDate = null),
    ) {
        onView(withText("Add your test date")).check(matches(isDisplayed()))
        onView(withText("We'll pace the practice to it")).check(matches(isDisplayed()))
        onView(withId(R.id.countdown_card))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun with_a_test_date_the_countdown_shows_days_remaining() = withProgress(
        ProgressState(
            onboarded = true, testDate = DateUtils.shiftDay(38), answered = 100, correct = 85,
        ),
    ) {
        onView(withId(R.id.days_left)).check(matches(withText("38")))
        onView(withText("On pace — 85% accuracy")).check(matches(isDisplayed()))
        onView(withId(R.id.no_date_card)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun a_fresh_install_invites_a_first_session() = withProgress(ProgressState(onboarded = true)) {
        onView(withText("Ready when you are")).check(matches(isDisplayed()))
        onView(withId(R.id.hero_title)).check(matches(withText("Start studying")))
        onView(withText("20 questions · about 10 minutes")).check(matches(isDisplayed()))
        // Nothing has been practised, so there is no weakest topic to name.
        onView(withId(R.id.weakest_card)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun an_unfinished_session_becomes_a_resume_prompt() = withProgress(
        ProgressState(
            onboarded = true,
            session = SessionState(
                mode = "QUICK", label = "Quick practice", ids = listOf(1, 2, 3, 4, 5),
                index = 2, startedAtEpochMs = System.currentTimeMillis(),
            ),
        ),
    ) {
        onView(withId(R.id.hero_title)).check(matches(withText("Resume session")))
        onView(withText("Quick practice · question 3 of 5")).check(matches(isDisplayed()))
    }

    @Test
    fun the_hero_cta_starts_a_continue_session_and_opens_the_quiz() =
        withProgress(ProgressState(onboarded = true)) {
            onView(withId(R.id.hero_cta)).perform(click())
            assertEquals(R.id.quizFragment, currentDestinationId())
            val session = progress().session
            assertNotNull("the CTA must build a session before navigating", session)
            assertEquals("Continue", session!!.label)
            assertEquals(20, session.ids.size)
        }

    @Test
    fun resuming_keeps_the_session_it_found_rather_than_starting_a_new_one() = withProgress(
        ProgressState(
            onboarded = true,
            session = SessionState(
                mode = "QUICK", label = "Quick practice", ids = listOf(1, 2, 3, 4, 5),
                index = 2, startedAtEpochMs = System.currentTimeMillis(),
            ),
        ),
    ) {
        onView(withId(R.id.hero_cta)).perform(click())
        assertEquals(R.id.quizFragment, currentDestinationId())
        assertEquals("Quick practice", progress().session!!.label)
        assertEquals(2, progress().session!!.index)
    }

    @Test
    fun the_two_tiles_navigate_to_weak_and_mock() = withProgress(
        ProgressState(
            onboarded = true,
            seen = mapOf(1 to SeenStat(3, 2), 5 to SeenStat(4, 3), 9 to SeenStat(2, 1)),
        ),
    ) {
        // Only ids missed twice or more count as weak, so 9 is excluded.
        onView(withId(R.id.weak_count)).check(matches(withText("2")))
        onView(withId(R.id.weak_tile)).perform(click())
        assertEquals(R.id.weakFragment, currentDestinationId())
        pressBack()
        onView(withId(R.id.mock_tile)).perform(click())
        assertEquals(R.id.mockIntroFragment, currentDestinationId())
    }

    @Test
    fun the_weakest_topic_card_names_the_lowest_scoring_practised_topic() = withProgress(
        // ids 1-3 are Rights & Responsibilities; two of three attempts on id 2 were missed.
        ProgressState(
            onboarded = true,
            seen = mapOf(1 to SeenStat(1, 0), 2 to SeenStat(3, 2)),
        ),
    ) {
        onView(withId(R.id.weakest_card)).check(matches(isDisplayed()))
        onView(withId(R.id.weakest_name)).check(matches(withText("Rights & Responsibilities")))
        onView(withId(R.id.weakest_accuracy)).check(matches(withText("50% accuracy")))
        onView(withId(R.id.weakest_card)).perform(click())
        assertEquals(R.id.topicDetailFragment, currentDestinationId())
    }

    @Test
    fun the_header_buttons_reach_search_and_settings() =
        withProgress(ProgressState(onboarded = true)) {
            onView(withId(R.id.search_button)).perform(click())
            assertEquals(R.id.searchFragment, currentDestinationId())
            pressBack()
            onView(withId(R.id.settings_button)).perform(click())
            assertEquals(R.id.settingsFragment, currentDestinationId())
        }
}
