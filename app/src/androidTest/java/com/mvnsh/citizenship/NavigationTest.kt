package com.mvnsh.citizenship

import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mvnsh.citizenship.data.model.ProgressState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    private lateinit var scenario: ActivityScenario<MainActivity>

    private fun app(): CitizenshipApp = ApplicationProvider.getApplicationContext()

    @Before
    fun launchPastOnboarding() {
        // Without this the onboarding gate intercepts and every assertion below is moot.
        runBlocking { app().progressRepository.replace(ProgressState(onboarded = true)) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    private fun currentDestinationId(): Int {
        var id = 0
        scenario.onActivity { activity ->
            val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
            id = host.navController.currentDestination?.id ?: 0
        }
        return id
    }

    @Test
    fun the_app_opens_on_home() {
        assertEquals(R.id.homeFragment, currentDestinationId())
    }

    @Test
    fun each_tab_reaches_its_own_destination() {
        // Asserting on the destination id rather than on the tab's own label, which
        // would match the navigation item itself and pass without navigating.
        listOf(
            R.id.practiceFragment,
            R.id.topicsFragment,
            R.id.progressFragment,
            R.id.homeFragment,
        ).forEach { destination ->
            onView(withId(destination)).perform(click())
            assertEquals(destination, currentDestinationId())
        }
    }

    @Test
    fun the_bottom_nav_hides_off_the_top_level_and_returns() {
        onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()))

        scenario.onActivity { activity ->
            val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
            host.navController.navigate(R.id.settingsFragment)
        }
        assertEquals(R.id.settingsFragment, currentDestinationId())
        onView(withId(R.id.bottom_nav)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        onView(withId(R.id.nav_divider)).check(matches(withEffectiveVisibility(Visibility.GONE)))

        scenario.onActivity { activity ->
            val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
            host.navController.popBackStack()
        }
        assertEquals(R.id.homeFragment, currentDestinationId())
        onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()))
    }

    @Test
    fun a_fresh_install_is_sent_to_onboarding_instead_of_home() {
        scenario.close()
        runBlocking { app().progressRepository.replace(ProgressState(onboarded = false)) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        assertEquals(R.id.onboardingFragment, currentDestinationId())
    }
}
