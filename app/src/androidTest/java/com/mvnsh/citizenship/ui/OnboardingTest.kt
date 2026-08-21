package com.mvnsh.citizenship.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.domain.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingTest : BaseUiTest() {

    private fun freshInstall(block: () -> Unit) =
        withProgress(ProgressState(onboarded = false), block)

    private fun currentPage(): Int {
        var page = -1
        rule.onActivity { activity ->
            val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.pager)
            page = pager?.currentItem ?: -1
        }
        return page
    }

    @Test
    fun a_fresh_install_lands_on_onboarding_not_home() = freshInstall {
        assertEquals(R.id.onboardingFragment, currentDestinationId())
        onView(withText("Pass the citizenship test.")).check(matches(isDisplayed()))
    }

    @Test
    fun the_first_page_states_what_the_app_offers() = freshInstall {
        // Formatted from the loaded bank, which is the point: the design hardcoded it.
        onView(withText("535 questions across seven topics")).check(matches(isDisplayed()))
        onView(withText("Timed mock tests, marked like the real one")).check(matches(isDisplayed()))
    }

    @Test
    fun choosing_a_pace_persists_and_finishing_reaches_home() = freshInstall {
        onView(withText("Get started")).perform(click())
        onView(withText("Set a pace")).check(matches(isDisplayed()))

        onView(withText("In about a month")).perform(click())
        onView(withText("30 a day")).perform(click())
        onView(withText("Continue")).perform(click())

        onView(withText("How this works")).check(matches(isDisplayed()))
        onView(withText("Start studying")).perform(click())

        assertEquals(R.id.homeFragment, currentDestinationId())
        val p = progress()
        assertTrue("onboarding should be marked complete", p.onboarded)
        assertEquals(30, p.goalTarget)
        assertEquals(DateUtils.shiftDay(30), p.testDate)
    }

    @Test
    fun not_scheduled_yet_clears_the_test_date() = freshInstall {
        onView(withText("Get started")).perform(click())
        onView(withText("In about 3 months")).perform(click())
        assertEquals(DateUtils.shiftDay(90), progress().testDate)

        onView(withText("Not scheduled yet")).perform(click())
        assertNull("choosing 'not scheduled' must clear the date", progress().testDate)
    }

    @Test
    fun the_shortcut_skips_straight_to_home_and_marks_onboarded() = freshInstall {
        onView(withText("I've used this before")).perform(click())
        assertEquals(R.id.homeFragment, currentDestinationId())
        assertTrue(progress().onboarded)
    }

    @Test
    fun skip_for_now_also_finishes_onboarding() = freshInstall {
        onView(withText("Get started")).perform(click())
        onView(withText("Skip for now")).perform(click())
        assertEquals(R.id.homeFragment, currentDestinationId())
        assertTrue(progress().onboarded)
    }

    @Test
    fun back_from_page_two_returns_to_page_one() = freshInstall {
        onView(withText("Get started")).perform(click())
        onView(withText("Set a pace")).check(matches(isDisplayed()))
        onView(withId(R.id.ob_pace_back)).perform(click())
        assertEquals("pager should be back on page 0", 0, currentPage())
        onView(withText("Pass the citizenship test.")).check(matches(isDisplayed()))
    }

    @Test
    fun onboarding_is_not_shown_again_after_it_is_finished() {
        freshInstall {
            onView(withText("I've used this before")).perform(click())
            assertEquals(R.id.homeFragment, currentDestinationId())
        }
        // Relaunching with the persisted state must go straight to home.
        onHome {
            assertEquals(R.id.homeFragment, currentDestinationId())
        }
    }
}
