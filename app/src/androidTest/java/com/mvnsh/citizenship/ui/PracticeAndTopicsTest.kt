package com.mvnsh.citizenship.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SeenStat
import com.mvnsh.citizenship.domain.Topics
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeAndTopicsTest : BaseUiTest() {

    @Test
    fun practice_lists_the_six_modes_with_live_counts() = withProgress(
        ProgressState(
            onboarded = true,
            bookmarks = listOf(2, 7),
            seen = mapOf(1 to SeenStat(3, 2), 5 to SeenStat(2, 2), 9 to SeenStat(1, 0)),
        ),
    ) {
        onView(withId(R.id.practiceFragment)).perform(click())
        onView(withText("Quick practice")).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.meta), hasSibling(withText("Quick practice"))))
            .check(matches(withText("10")))
        onView(allOf(withId(R.id.meta), hasSibling(withText("All questions"))))
            .check(matches(withText("501")))
        onView(allOf(withId(R.id.meta), hasSibling(withText("Weak questions"))))
            .check(matches(withText("2")))
        onView(allOf(withId(R.id.meta), hasSibling(withText("Bookmarked"))))
            .check(matches(withText("2")))
        onView(allOf(withId(R.id.meta), hasSibling(withText("New to you"))))
            .check(matches(withText("498")))
        onView(allOf(withId(R.id.meta), hasSibling(withText("By topic"))))
            .check(matches(withText("7")))
    }

    @Test
    fun quick_practice_starts_a_ten_question_session() =
        withProgress(ProgressState(onboarded = true)) {
            onView(withId(R.id.practiceFragment)).perform(click())
            onView(withText("Quick practice")).perform(click())
            assertEquals(R.id.quizFragment, currentDestinationId())
            val session = progress().session!!
            assertEquals("Quick practice", session.label)
            assertEquals(10, session.ids.size)
        }

    @Test
    fun the_two_list_modes_open_their_lists_rather_than_starting_a_session() = withProgress(
        ProgressState(onboarded = true, bookmarks = listOf(2), seen = mapOf(1 to SeenStat(3, 2))),
    ) {
        onView(withId(R.id.practiceFragment)).perform(click())
        onView(withText("Weak questions")).perform(click())
        assertEquals(R.id.weakFragment, currentDestinationId())
        assertEquals("browsing must not start a session", null, progress().session)

        navigateTo(R.id.practiceFragment)
        onView(withText("Bookmarked")).perform(click())
        assertEquals(R.id.bookmarksFragment, currentDestinationId())
        assertEquals(null, progress().session)
    }

    @Test
    fun the_practice_footer_reaches_the_mock_intro() =
        withProgress(ProgressState(onboarded = true)) {
            onView(withId(R.id.practiceFragment)).perform(click())
            onView(withText("Take a mock test")).perform(click())
            assertEquals(R.id.mockIntroFragment, currentDestinationId())
        }

    @Test
    fun topics_lists_all_seven_in_the_designs_order() =
        withProgress(ProgressState(onboarded = true)) {
            onView(withId(R.id.topicsFragment)).perform(click())
            onView(withText("Seven topics, drawn from the official study guide."))
                .check(matches(isDisplayed()))
            // Seven rows do not fit one screen, so each is scrolled to in turn.
            Topics.all.forEach {
                onView(withText(it.name)).perform(scrollTo()).check(matches(isDisplayed()))
            }
        }

    @Test
    fun an_unpractised_topic_shows_an_em_dash_not_zero_percent() =
        withProgress(ProgressState(onboarded = true)) {
            onView(withId(R.id.topicsFragment)).perform(click())
            onView(allOf(withId(R.id.accuracy), hasSibling(withText("Economy"))))
                .perform(scrollTo())
                .check(matches(withText("—")))
        }

    @Test
    fun topic_detail_shows_counts_and_the_question_list() = withProgress(
        // ids 1 and 2 are both Rights & Responsibilities; id 2 was missed twice.
        ProgressState(onboarded = true, seen = mapOf(1 to SeenStat(2, 0), 2 to SeenStat(3, 2))),
    ) {
        onView(withId(R.id.topicsFragment)).perform(click())
        onView(withText("Rights & Responsibilities")).perform(click())
        onView(withId(R.id.cat_total)).check(matches(withText("43")))
        onView(withId(R.id.cat_done)).check(matches(withText("2")))
        onView(withId(R.id.cat_accuracy)).check(matches(withText("60%")))
        onView(withText("Practise 20 questions")).check(matches(isDisplayed()))
        onView(withText("Missed 2×")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Correct")).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun topic_detail_starts_a_session_of_that_topic_only() =
        withProgress(ProgressState(onboarded = true)) {
            onView(withId(R.id.topicsFragment)).perform(click())
            onView(withText("Economy")).perform(scrollTo(), click())
            // Economy holds only 14 questions, so the cap of 20 does not bite.
            onView(withText("Practise 14 questions")).perform(click())
            assertEquals(R.id.quizFragment, currentDestinationId())
            val session = progress().session!!
            assertEquals("Economy", session.label)
            assertEquals("economy", session.topicKey)
            assertEquals(14, session.ids.size)
        }

    @Test
    fun the_flag_on_a_topic_question_toggles_the_bookmark() =
        withProgress(ProgressState(onboarded = true)) {
            onView(withId(R.id.topicsFragment)).perform(click())
            onView(withText("Economy")).perform(scrollTo(), click())
            // Every row carries a flag, so the matcher has to name one of them.
            onView(firstWithId(R.id.flag)).perform(scrollTo(), click())
            assertEquals(1, progress().bookmarks.size)
            onView(firstWithId(R.id.flag)).perform(click())
            assertEquals(emptyList<Int>(), progress().bookmarks)
        }
}
