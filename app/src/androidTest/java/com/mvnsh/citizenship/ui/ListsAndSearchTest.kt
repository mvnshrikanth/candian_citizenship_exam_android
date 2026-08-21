package com.mvnsh.citizenship.ui

import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SeenStat
import org.hamcrest.Matchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListsAndSearchTest : BaseUiTest() {

    // ---- weak list ------------------------------------------------------

    @Test
    fun the_weak_list_headlines_the_count_and_offers_practice() = withProgress(
        ProgressState(onboarded = true, seen = mapOf(1 to SeenStat(4, 2), 3 to SeenStat(3, 3))),
    ) {
        navigateTo(R.id.weakFragment)
        onView(withText("You have 2 questions you keep missing.")).check(matches(isDisplayed()))
        onView(withText("Missed 2×")).check(matches(isDisplayed()))
        onView(withText("Practise these now")).perform(click())
        assertEquals(R.id.quizFragment, currentDestinationId())
        assertEquals(listOf(1, 3), progress().session!!.ids)
    }

    @Test
    fun i_know_this_now_removes_a_question_from_the_weak_list() = withProgress(
        ProgressState(onboarded = true, seen = mapOf(1 to SeenStat(4, 2))),
    ) {
        navigateTo(R.id.weakFragment)
        onView(withText("I know this now")).perform(click())
        onView(withText("Nothing to review")).check(matches(isDisplayed()))
        assertEquals(0, progress().seen[1]!!.m)
        assertEquals("the attempt history is kept", 4, progress().seen[1]!!.s)
    }

    @Test
    fun bookmarking_from_the_weak_list_keeps_the_question_there() = withProgress(
        ProgressState(onboarded = true, seen = mapOf(1 to SeenStat(4, 2))),
    ) {
        navigateTo(R.id.weakFragment)
        onView(withText("Bookmark")).perform(click())
        assertEquals(listOf(1), progress().bookmarks)
        onView(withText("Missed 2×")).check(matches(isDisplayed()))
    }

    @Test
    fun an_empty_weak_list_explains_how_it_fills() =
        withProgress(ProgressState(onboarded = true)) {
            navigateTo(R.id.weakFragment)
            onView(withText("Nothing to review")).check(matches(isDisplayed()))
            onView(withText(containsString("Questions land here after you miss them twice")))
                .check(matches(isDisplayed()))
            onView(withText("Quick practice")).perform(click())
            assertEquals(R.id.quizFragment, currentDestinationId())
            assertEquals(10, progress().session!!.ids.size)
        }

    // ---- bookmarks ------------------------------------------------------

    @Test
    fun bookmarks_list_and_unbookmark() =
        withProgress(ProgressState(onboarded = true, bookmarks = listOf(2, 7))) {
            navigateTo(R.id.bookmarksFragment)
            onView(withText("Practise bookmarked")).check(matches(isDisplayed()))
            onView(firstWithId(R.id.remove)).perform(click())
            assertEquals(listOf(7), progress().bookmarks)
        }

    @Test
    fun practising_bookmarks_keeps_the_saved_order() =
        withProgress(ProgressState(onboarded = true, bookmarks = listOf(7, 2))) {
            navigateTo(R.id.bookmarksFragment)
            onView(withText("Practise bookmarked")).perform(click())
            assertEquals(listOf(7, 2), progress().session!!.ids)
        }

    @Test
    fun an_empty_bookmark_list_explains_the_flag() =
        withProgress(ProgressState(onboarded = true)) {
            navigateTo(R.id.bookmarksFragment)
            onView(withText("No bookmarks yet")).check(matches(isDisplayed()))
            onView(withText(containsString("Tap the flag on any question")))
                .check(matches(isDisplayed()))
        }

    @Test
    fun a_bookmark_the_bank_no_longer_carries_is_skipped_rather_than_crashing() =
        withProgress(ProgressState(onboarded = true, bookmarks = listOf(2, 99_999))) {
            navigateTo(R.id.bookmarksFragment)
            onView(withId(R.id.bookmarks_list)).check(matches(isDisplayed()))
            onView(withText("Practise bookmarked")).perform(click())
            assertEquals("only real ids reach a session", listOf(2), progress().session!!.ids)
        }

    // ---- search ---------------------------------------------------------

    @Test
    fun search_needs_two_characters_then_matches_question_and_option_text() =
        withProgress(ProgressState(onboarded = true)) {
            navigateTo(R.id.searchFragment)
            onView(withText(containsString("Search the whole bank by wording or by answer")))
                .check(matches(isDisplayed()))

            onView(withId(R.id.query)).perform(replaceText("a"))
            onView(withText(containsString("Search the whole bank")))
                .check(matches(isDisplayed()))

            onView(withId(R.id.query)).perform(replaceText("habeas corpus"))
            closeSoftKeyboard()
            onView(withText(containsString("Habeas Corpus"))).check(matches(isDisplayed()))
        }

    @Test
    fun the_search_hint_quotes_the_real_bank_size() =
        withProgress(ProgressState(onboarded = true)) {
            navigateTo(R.id.searchFragment)
            onView(withId(R.id.query)).check(matches(withHint("Search 501 questions")))
        }

    @Test
    fun search_caps_at_thirty_hits_and_marks_the_overflow() =
        withProgress(ProgressState(onboarded = true)) {
            navigateTo(R.id.searchFragment)
            onView(withId(R.id.query)).perform(replaceText("the"))
            closeSoftKeyboard()
            onView(withId(R.id.hit_count)).check(matches(withText("30+ results")))
        }

    @Test
    fun a_search_with_no_match_offers_the_topic_list() =
        withProgress(ProgressState(onboarded = true)) {
            navigateTo(R.id.searchFragment)
            onView(withId(R.id.query)).perform(replaceText("zzzzqqq"))
            closeSoftKeyboard()
            onView(withText("No question matches that")).check(matches(isDisplayed()))
            onView(withText("Browse topics")).perform(click())
            assertEquals(R.id.topicsFragment, currentDestinationId())
        }

    @Test
    fun clearing_the_query_returns_to_the_idle_hint() =
        withProgress(ProgressState(onboarded = true)) {
            navigateTo(R.id.searchFragment)
            onView(withId(R.id.query)).perform(replaceText("Charter"))
            closeSoftKeyboard()
            onView(withId(R.id.clear)).perform(click())
            onView(withId(R.id.query)).check(matches(withText("")))
            onView(withText(containsString("Search the whole bank")))
                .check(matches(isDisplayed()))
        }

    @Test
    fun a_search_hit_can_be_bookmarked_from_the_result() =
        withProgress(ProgressState(onboarded = true)) {
            navigateTo(R.id.searchFragment)
            onView(withId(R.id.query)).perform(replaceText("habeas corpus"))
            closeSoftKeyboard()
            onView(firstWithId(R.id.flag)).perform(click())
            assertEquals(1, progress().bookmarks.size)
        }

    private fun withHint(expected: String) =
        androidx.test.espresso.matcher.ViewMatchers.withHint(expected)
}
