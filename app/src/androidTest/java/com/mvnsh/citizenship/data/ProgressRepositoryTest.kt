package com.mvnsh.citizenship.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SeenStat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** DataStore needs a real filesystem, so these run on device. */
@RunWith(AndroidJUnit4::class)
class ProgressRepositoryTest {

    private lateinit var file: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        file = File(ctx.cacheDir, "test-progress-${System.nanoTime()}.preferences_pb")
        store = PreferenceDataStoreFactory.create { file }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun a_mutation_survives_a_new_repository_instance() = runBlocking {
        val first = ProgressRepository(store, scope)
        first.awaitLoaded()
        first.mutate { it.copy(answered = 7, seen = mapOf(4 to SeenStat(1, 1))) }
        first.flush()

        val second = ProgressRepository(store, scope)
        val restored = second.awaitLoaded()
        assertEquals(7, restored.answered)
        assertEquals(SeenStat(1, 1), restored.seen[4])
    }

    @Test
    fun a_burst_of_mutations_settles_on_the_last_value() = runBlocking {
        val repo = ProgressRepository(store, scope)
        repo.awaitLoaded()
        repeat(50) { n -> repo.mutate { it.copy(answered = n + 1) } }
        assertEquals("memory is immediate", 50, repo.state.value.answered)
        repo.flush()

        val reloaded = ProgressRepository(store, scope)
        assertEquals(50, reloaded.awaitLoaded().answered)
    }

    @Test
    fun a_debounced_write_lands_without_an_explicit_flush() = runBlocking {
        val repo = ProgressRepository(store, scope)
        repo.awaitLoaded()
        repo.mutate { it.copy(streak = 9) }
        delay(ProgressRepository.WRITE_DEBOUNCE_MS * 4)

        val reloaded = ProgressRepository(store, scope)
        assertEquals(9, reloaded.awaitLoaded().streak)
    }

    @Test
    fun replace_overwrites_everything_immediately() = runBlocking {
        val repo = ProgressRepository(store, scope)
        repo.awaitLoaded()
        repo.mutate { it.copy(answered = 100, bookmarks = listOf(1, 2, 3)) }
        repo.flush()

        repo.replace(ProgressState(onboarded = true))
        val reloaded = ProgressRepository(store, scope)
        val restored = reloaded.awaitLoaded()
        assertEquals(0, restored.answered)
        assertTrue(restored.bookmarks.isEmpty())
        assertTrue(restored.onboarded)
    }

    @Test
    fun a_fresh_store_loads_the_default_state() = runBlocking {
        assertEquals(ProgressState(), ProgressRepository(store, scope).awaitLoaded())
    }
}
