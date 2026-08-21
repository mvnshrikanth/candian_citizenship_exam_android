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
import com.mvnsh.citizenship.domain.Stats
import org.hamcrest.Matchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuizTest : BaseUiTest() {

    /**
     * Seeds a running practice session. Ids 1-3 are Law and Justice; id 1's answer is
     * option 0 and id 4's is "1982".
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
        onView(withId(R.id.q_topic)).check(matches(withText("Law and Justice")))
        onView(withText("Under the rule of law in Canada, who must obey the law?"))
            .check(matches(isDisplayed()))
        onView(withId(R.id.skip)).check(matches(isDisplayed()))
    }

    @Test
    fun a_correct_answer_reveals_the_explanation_and_the_next_button() =
        inQuiz(startedQuiz(1, 2)) {
            onView(withText("Everyone, including individuals, corporations, and governments")).perform(scrollTo(), click())
            onView(withText("Why")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText(containsString("all individuals, organizations, and governments")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(withText("Next question")).check(matches(isDisplayed()))
            onView(withId(R.id.skip))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
            assertEquals(1, Stats.correct(progress()))
            assertEquals(1, Stats.answered(progress()))
        }

    @Test
    fun a_wrong_answer_is_recorded_and_the_answer_is_shown() = inQuiz(startedQuiz(1, 2)) {
        onView(withText("Only individual citizens")).perform(scrollTo(), click())
        onView(withText("Your pick")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Correct")).check(matches(isDisplayed()))
        val p = progress()
        assertEquals(1, Stats.answered(p))
        assertEquals(0, Stats.correct(p))
        assertEquals(1, p.seen[1]!!.m)
    }

    @Test
    fun tapping_a_second_option_after_revealing_changes_nothing() = inQuiz(startedQuiz(1, 2)) {
        onView(withText("Only individual citizens")).perform(scrollTo(), click())
        onView(withText("Only government officials and politicians")).perform(scrollTo(), click())
        assertEquals("a revealed question is locked", 1, Stats.answered(progress()))
        assertEquals("and counted exactly once", 1, progress().seen[1]!!.s)
    }

    @Test
    fun the_why_panel_carries_the_authored_tip_as_well_as_the_reason() = inQuiz(startedQuiz(4)) {
        // Upstream now authors why/tip for every question, so the "not written yet" panel
        // is unreachable from the shipped assets. The branch stays in QuizFragment because
        // only the asset guarantees that; BankDataTest is what would catch a regression.
        onView(withText("1982")).perform(scrollTo(), click())
        onView(withText("Why")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText(containsString("entrenched in the Constitution in 1982")))
            .perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText(containsString("1867 = Confederation")))
            .perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun the_last_question_offers_results_and_reaches_them() = inQuiz(startedQuiz(1)) {
        onView(withText("Everyone, including individuals, corporations, and governments")).perform(scrollTo(), click())
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
        assertEquals(0, Stats.answered(progress()))
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
