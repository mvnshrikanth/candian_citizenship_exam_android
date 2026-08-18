package com.mvnsh.citizenship.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mvnsh.citizenship.data.model.ProgressState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Stores all progress as a single JSON blob in DataStore.
 *
 * Reads are served from memory so the UI never waits on disk. Writes are conflated on a
 * short debounce, because the whole blob is rewritten on every change and answering a
 * question is one tap: without this, a fast tapper queues a file write per tap.
 */
@OptIn(FlowPreview::class)
class ProgressRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    private val key = stringPreferencesKey(KEY_NAME)

    private val _state = MutableStateFlow(ProgressState())
    val state: StateFlow<ProgressState> = _state.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    /** False until the persisted blob has been read once. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /**
     * What we believe is currently on disk. Comparing against this - rather than
     * dropping the StateFlow's first replayed emission - is what makes the write loop
     * safe: a mutation landing between the load finishing and the collector subscribing
     * would otherwise be dropped and never written.
     */
    @Volatile
    private var lastPersisted: ProgressState? = null

    /**
     * The initial read, kept as a Job so writers can join it.
     *
     * Without that join, a [replace] issued before the read completes is silently
     * clobbered when the read lands and assigns the older on-disk value.
     */
    private val loadJob: Job = scope.launch {
        val initial = ProgressState.decode(dataStore.data.map { it[key] }.first())
        lastPersisted = initial
        _state.value = initial
        _loaded.value = true
    }

    init {
        scope.launch {
            loadJob.join()
            _state.debounce(WRITE_DEBOUNCE_MS).collect { current ->
                if (current != lastPersisted) persist(current)
            }
        }
    }

    /**
     * Updates memory immediately; the file write lands within the debounce window.
     * Only call once [loaded] is true - every caller is user-driven, so by then the
     * initial read has long finished.
     */
    fun mutate(transform: (ProgressState) -> ProgressState) {
        _state.value = transform(_state.value)
    }

    /** Writes the current value now. Call from onStop and at session boundaries. */
    suspend fun flush() = persist(_state.value)

    /** Replaces everything at once (reset, demo seed) and writes immediately. */
    suspend fun replace(next: ProgressState) {
        loadJob.join()
        _state.value = next
        persist(next)
    }

    /** Suspends until the first read from disk has completed. */
    suspend fun awaitLoaded(): ProgressState {
        loadJob.join()
        return _state.value
    }

    private suspend fun persist(state: ProgressState) {
        dataStore.edit { it[key] = ProgressState.encode(state) }
        lastPersisted = state
    }

    companion object {
        const val KEY_NAME = "progress_json"
        const val WRITE_DEBOUNCE_MS = 250L
    }
}
