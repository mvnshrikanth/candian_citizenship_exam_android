package com.mvnsh.citizenship.ui

import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.mvnsh.citizenship.CitizenshipApp
import com.mvnsh.citizenship.MainActivity
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.model.ProgressState
import kotlinx.coroutines.runBlocking

/**
 * Shared setup for screen tests.
 *
 * Every test seeds the persisted state before the Activity launches, because the app
 * gates on `onboarded` at startup: without seeding, onboarding intercepts and the
 * assertions silently measure the wrong screen.
 */
abstract class BaseUiTest {

    private var scenario: ActivityScenario<MainActivity>? = null

    protected fun app(): CitizenshipApp = ApplicationProvider.getApplicationContext()

    /** Seeds state, launches the Activity, runs [block], then tears down. */
    protected fun withProgress(state: ProgressState, block: () -> Unit) {
        runBlocking { app().progressRepository.replace(state) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            block()
        } finally {
            scenario?.close()
            scenario = null
        }
    }

    /** Convenience for the common "past onboarding, nothing else set" case. */
    protected fun onHome(block: () -> Unit) = withProgress(ProgressState(onboarded = true), block)

    protected val rule: ActivityScenario<MainActivity>
        get() = checkNotNull(scenario) { "withProgress has not launched an Activity" }

    /** Jumps to a destination not reachable from the current screen. */
    protected fun navigateTo(destinationId: Int) {
        rule.onActivity { activity ->
            val host = activity.supportFragmentManager
                .findFragmentById(R.id.nav_host) as NavHostFragment
            host.navController.navigate(destinationId)
        }
    }

    protected fun currentDestinationId(): Int {
        var id = 0
        rule.onActivity { activity ->
            val host = activity.supportFragmentManager
                .findFragmentById(R.id.nav_host) as NavHostFragment
            id = host.navController.currentDestination?.id ?: 0
        }
        return id
    }

    protected fun progress(): ProgressState = app().progressRepository.state.value
}
