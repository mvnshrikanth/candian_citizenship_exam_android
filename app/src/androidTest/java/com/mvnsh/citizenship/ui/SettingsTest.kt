package com.mvnsh.citizenship.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.model.MockAttempt
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SeenStat
import com.mvnsh.citizenship.domain.DateUtils
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsTest : BaseUiTest() {

    /**
     * Turning a reminder on asks for POST_NOTIFICATIONS, and a real system dialog pauses
     * the activity - which fails not just that test but every test queued behind it, with
     * a NoActivityResumedException that says nothing about notifications. Pre-granting
     * makes the launcher return without UI.
     */
    @get:Rule
    val notifications: GrantPermissionRule =
        GrantPermissionRule.grant("android.permission.POST_NOTIFICATIONS")

    /**
     * A grouped-list row addressed by the row itself rather than by its label.
     *
     * The label is a non-clickable child, so clicking it relies on the touch bubbling to
     * the row; naming the row removes the question of which view actually received it.
     */
    private fun settingRow(label: String) =
        allOf(withId(R.id.setting_row), hasDescendant(withText(label)))

    private fun inSettings(state: ProgressState, block: () -> Unit) = withProgress(state) {
        navigateTo(R.id.settingsFragment)
        block()
    }

    @After
    fun restoreSystemTheme() {
        // The night mode is process-wide, so a theme test would otherwise leak into
        // whatever runs next. This must not touch `rule`: withProgress has already closed
        // the scenario by the time @After runs, and setDefaultNightMode needs no activity.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    @Test
    fun the_theme_selector_persists_and_applies() =
        inSettings(ProgressState(onboarded = true)) {
            onView(withId(R.id.theme_dark)).perform(click())
            assertEquals("Dark", progress().theme)
            assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
        }

    @Test
    fun choosing_light_switches_back() =
        inSettings(ProgressState(onboarded = true, theme = "Dark")) {
            onView(withId(R.id.theme_light)).perform(click())
            assertEquals("Light", progress().theme)
            assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())
        }

    @Test
    fun the_goal_chips_persist_the_target() =
        inSettings(ProgressState(onboarded = true, goalTarget = 20)) {
            onView(withId(R.id.goal_30)).perform(click())
            assertEquals(30, progress().goalTarget)
            onView(withId(R.id.goal_10)).perform(click())
            assertEquals(10, progress().goalTarget)
        }

    @Test
    fun the_test_date_row_toggles_between_set_and_unset() =
        inSettings(ProgressState(onboarded = true, testDate = null)) {
            onView(allOf(withId(R.id.subtitle), hasSibling(withText("Test date"))))
                .check(matches(withText("Not set")))
            onView(withId(R.id.test_date_row)).perform(click())
            onView(allOf(withId(R.id.subtitle), hasSibling(withText("Test date"))))
                .check(matches(withText(containsString("38 days"))))
            assertEquals(DateUtils.shiftDay(38), progress().testDate)

            onView(withId(R.id.test_date_row)).perform(click())
            assertEquals(null, progress().testDate)
        }

    @Test
    fun each_reminder_switch_persists_independently() =
        inSettings(ProgressState(onboarded = true)) {
            onView(withText("Streak at risk")).perform(scrollTo(), click())
            onView(withText("Test date countdown")).perform(scrollTo(), click())
            val on = progress().notif
            assertTrue("daily is on by default", on.daily)
            assertTrue(on.streak)
            assertTrue(on.test)

            onView(withText("Daily study reminder")).perform(scrollTo(), click())
            val after = progress().notif
            assertFalse(after.daily)
            assertTrue("the other two are untouched", after.streak && after.test)
        }

    @Test
    fun reset_clears_progress_but_keeps_bookmarks() = inSettings(
        ProgressState(
            onboarded = true, answered = 340, correct = 279, streak = 12,
            seen = mapOf(1 to SeenStat(3, 2)), bookmarks = listOf(2, 7),
            mocks = listOf(MockAttempt(85, DateUtils.today())),
        ),
    ) {
        onView(settingRow("Reset all progress")).perform(scrollTo(), click())
        onView(withText("Reset all progress?")).check(matches(isDisplayed()))
        onView(withText(containsString("Bookmarks are kept"))).check(matches(isDisplayed()))
        onView(withText("Reset everything")).perform(click())

        awaitProgress { it.answered == 0 }
        val p = progress()
        assertEquals(0, p.answered)
        assertEquals(0, p.streak)
        assertTrue(p.mocks.isEmpty())
        assertTrue(p.seen.isEmpty())
        assertEquals("bookmarks survive", listOf(2, 7), p.bookmarks)
        assertTrue("and it stays past onboarding", p.onboarded)
    }

    @Test
    fun cancelling_reset_changes_nothing() =
        inSettings(ProgressState(onboarded = true, answered = 340)) {
            onView(settingRow("Reset all progress")).perform(scrollTo(), click())
            onView(withText("Cancel")).perform(click())
            assertEquals(340, progress().answered)
        }

    @Test
    fun clearing_bookmarks_leaves_progress_alone() = inSettings(
        ProgressState(onboarded = true, answered = 50, bookmarks = listOf(1, 2, 3)),
    ) {
        onView(settingRow("Clear bookmarks")).perform(scrollTo(), click())
        onView(withText("Clear them")).perform(click())
        assertTrue(progress().bookmarks.isEmpty())
        assertEquals(50, progress().answered)
    }

    @Test
    fun the_about_block_states_the_offline_and_affiliation_facts() =
        inSettings(ProgressState(onboarded = true)) {
            onView(withText("Question bank downloaded")).perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(withText("535 questions · works offline")).perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(withText("Everything stays on device")).perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(withText("English · Français coming")).perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(withText(containsString("Not affiliated with the Government of Canada")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
        }

    @Test
    fun the_debug_only_seeder_produces_a_usable_dataset() =
        inSettings(ProgressState(onboarded = true)) {
            onView(settingRow("Load demo data")).perform(scrollTo(), click())
            awaitProgress { it.answered == 340 }
            val p = progress()
            assertEquals(340, p.answered)
            assertEquals(279, p.correct)
            assertEquals(3, p.mocks.size)
            assertEquals(listOf(2, 7, 10, 44, 120), p.bookmarks)

            onView(settingRow("Reset to fresh install")).perform(scrollTo(), click())
            awaitProgress { it.answered == 0 }
            assertFalse("a fresh install has not been onboarded", progress().onboarded)
        }
}
