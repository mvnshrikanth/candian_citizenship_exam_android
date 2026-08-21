package com.mvnsh.citizenship.ui

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.IdlingPolicies
import com.mvnsh.citizenship.CitizenshipApp
import com.mvnsh.citizenship.MainActivity
import com.mvnsh.citizenship.R
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SeenStat
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

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

    private companion object {
        const val IDLE_TIMEOUT_SECONDS = 60L
    }

    /** Seeds state, launches the Activity, runs [block], then tears down. */
    protected fun withProgress(state: ProgressState, block: () -> Unit) {
        // Espresso gives the UI thread 5s to go idle before it gives up on an action.
        // That is generous on real hardware and not always enough on this emulator, which
        // has been measured taking 48s to first frame; when it is exceeded the action
        // fails and every later one reports NoActivityResumedException, which reads like
        // a broken screen rather than a slow machine.
        IdlingPolicies.setMasterPolicyTimeout(IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        IdlingPolicies.setIdlingResourceTimeout(IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        runBlocking { app().progressRepository.replace(state) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            awaitReady()
            block()
        } finally {
            scenario?.close()
            scenario = null
        }
    }

    /**
     * Waits for the screen to be genuinely ready: the activity RESUMED and the bank
     * parsed.
     *
     * Both halves matter. The bank is parsed off the main thread, so Espresso's idling
     * does not cover it and an assertion can race a screen with no questions yet. And
     * ActivityScenario.launch returning does not guarantee RESUMED on a slow emulator -
     * this one has been seen taking 48s to first frame - where Espresso then refuses
     * every action with NoActivityResumedException, which reads like a broken screen
     * rather than a slow one.
     */
    protected fun awaitReady(timeoutMs: Long = 30_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastState: Lifecycle.State? = null
        while (System.currentTimeMillis() < deadline) {
            lastState = rule.state
            if (lastState == Lifecycle.State.RESUMED) {
                var settled = false
                rule.onActivity { activity ->
                    val vm = ViewModelProvider(activity)[AppViewModel::class.java]
                    settled = vm.bank.value != null || vm.loadFailed.value
                }
                if (settled) return
            }
            Thread.sleep(25)
        }
        throw AssertionError(
            "screen not ready within ${timeoutMs}ms (activity state: $lastState)",
        )
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

    /**
     * Builds `seen` records that derive to exactly [answered] answers, [correct] of them
     * right, spread over [days] consecutive days ending today - so the streak is [days].
     *
     * Totals and the streak are no longer stored, so a test cannot simply assert them into
     * existence; it has to seed the history they come from. That is the point of the
     * refactor, and this keeps the seeding honest rather than reintroducing the fields.
     */
    protected fun seenRecords(
        answered: Int,
        correct: Int,
        days: Int = 1,
        questions: Int = answered,
    ): Map<Int, SeenStat> {
        require(questions in 1..answered) { "need 1..$answered questions, got $questions" }
        require(days in 1..questions) { "need 1..$questions days, got $days" }

        var attemptsLeft = answered
        var missesLeft = answered - correct
        return (0 until questions).associate { i ->
            val remaining = questions - i
            val s = if (i == questions - 1) attemptsLeft else (attemptsLeft / remaining).coerceAtLeast(1)
            attemptsLeft -= s
            val m = minOf(missesLeft, s)
            missesLeft -= m
            (i + 1) to SeenStat(s = s, m = m, lastAttempted = isoDaysAgo(i % days))
        }
    }

    /** Midday, [daysAgo] days back, in the device's own zone - reliably that local day. */
    protected fun isoDaysAgo(daysAgo: Int): String =
        LocalDate.now().minusDays(daysAgo.toLong())
            .atTime(12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toString()

    protected fun progress(): ProgressState = app().progressRepository.state.value

    /**
     * Waits for persisted state to satisfy [predicate].
     *
     * Some actions land through a coroutine that writes to DataStore - resetProgress and
     * seedDemo both replace the whole blob - and Espresso's idling does not cover that,
     * so reading progress() straight after the tap is a race.
     */
    protected fun awaitProgress(timeoutMs: Long = 5_000, predicate: (ProgressState) -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate(progress())) return
            Thread.sleep(25)
        }
        throw AssertionError("progress did not settle within ${timeoutMs}ms: ${progress()}")
    }

    /**
     * Matches only the first view carrying [id] in traversal order.
     *
     * List rows repeat their child ids by design, so a bare withId matches every row and
     * Espresso refuses the whole match as ambiguous.
     *
     * It latches the view rather than counting: Espresso walks the hierarchy more than
     * once per interaction, so a "have I seen one yet" flag matches on the first pass and
     * nothing at all on the second.
     */
    protected fun firstWithId(id: Int): Matcher<View> = object : TypeSafeMatcher<View>() {
        private var latched: View? = null

        override fun describeTo(description: Description) {
            description.appendText("first view with id ").appendValue(id)
        }

        override fun matchesSafely(view: View): Boolean {
            latched?.let { return view === it }
            if (view.id != id) return false
            latched = view
            return true
        }
    }
}
