package com.mvnsh.citizenship.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.mvnsh.citizenship.data.model.ProgressState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** What the Settings screen shows about syncing. */
sealed interface SyncState {
    data object Off : SyncState
    data object Syncing : SyncState
    data class Synced(val atEpochMs: Long?, val fromDevice: String?) : SyncState
    data class Failed(val message: String) : SyncState
}

/**
 * Mirrors local progress to `users/{uid}` and back.
 *
 * Attaches at the one seam - [ProgressRepository] is still the only thing that writes
 * locally, and every screen keeps reading its StateFlow - so nothing above this layer
 * knows that sync exists.
 *
 * Signed out it does nothing at all: no listener, no writes, no network. That is what
 * keeps signing in genuinely optional.
 *
 * Conflict resolution is last-write-wins, per `docs/sync-schema.md`. Writes use
 * `SetOptions.merge()` so a field only the web app writes is never erased by this one.
 */
@OptIn(FlowPreview::class)
class SyncRepository(
    private val firestore: FirebaseFirestore,
    private val progress: ProgressRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SyncState>(SyncState.Off)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private var running: Job? = null

    /**
     * Starts mirroring for [uid], first resolving what to do about progress that already
     * exists on both sides. Call again with a different uid to switch accounts.
     */
    fun start(uid: String, email: String, resolution: StartResolution) {
        stop()
        running = scope.launch {
            _state.value = SyncState.Syncing
            runCatching { adopt(uid, email, resolution) }
                .onFailure { _state.value = SyncState.Failed(FAILED_MESSAGE) }
                .onSuccess { observe(uid) }
        }
    }

    /** Stops mirroring. Local progress is left exactly as it is. */
    fun stop() {
        running?.cancel()
        running = null
        _state.value = SyncState.Off
    }

    /** Reads the account's document once, so a caller can ask the user how to reconcile. */
    suspend fun peek(uid: String): Map<String, Any?> =
        firestore.document(uid).get().await().data.orEmpty()

    /**
     * Decides the starting point. This is the only moment last-write-wins would otherwise
     * destroy something without warning, so it is settled explicitly rather than by
     * whichever write happens to land second.
     */
    enum class StartResolution { KEEP_LOCAL, USE_CLOUD }

    private suspend fun adopt(uid: String, email: String, resolution: StartResolution) {
        val doc = firestore.document(uid)
        val remote = doc.get().await()

        if (!remote.exists()) {
            // Same four fields js/auth.js writes at registration, so an account created
            // here is indistinguishable from one created on the web.
            doc.set(
                mapOf(
                    "email" to email,
                    "displayName" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    ProgressDocument.FIELD_PROGRESS to emptyMap<String, Any?>(),
                ),
                SetOptions.merge(),
            ).await()
        }

        when (resolution) {
            StartResolution.KEEP_LOCAL -> push(uid)
            StartResolution.USE_CLOUD ->
                progress.replace(
                    ProgressDocument.fromFirestore(remote.data.orEmpty(), progress.state.value),
                )
        }
    }

    private suspend fun observe(uid: String) {
        scope.launch {
            snapshots(uid).collect { data ->
                // Local device settings and the in-flight session are preserved by the
                // mapper; only account data is taken from the snapshot.
                progress.replace(ProgressDocument.fromFirestore(data, progress.state.value))
                _state.value = SyncState.Synced(
                    atEpochMs = (data[ProgressDocument.FIELD_UPDATED_AT] as? com.google.firebase.Timestamp)
                        ?.toDate()?.time,
                    fromDevice = data[ProgressDocument.FIELD_LAST_DEVICE] as? String,
                )
            }
        }

        // drop(1): the current value is already what we just adopted, so pushing it back
        // would be a pointless write - and, on the USE_CLOUD path, an immediate echo.
        progress.state.drop(1).debounce(PUSH_DEBOUNCE_MS).collectLatest {
            runCatching { push(uid) }
                .onFailure { _state.value = SyncState.Failed(FAILED_MESSAGE) }
        }
    }

    private suspend fun push(uid: String) {
        val payload = ProgressDocument.toFirestore(progress.state.value) +
            mapOf(ProgressDocument.FIELD_UPDATED_AT to FieldValue.serverTimestamp())
        firestore.document(uid).set(payload, SetOptions.merge()).await()
    }

    /** The document as a flow, with local edits included so the UI reacts immediately. */
    private fun snapshots(uid: String): Flow<Map<String, Any?>> = callbackFlow {
        val registration = firestore.document(uid).addSnapshotListener { snapshot, error ->
            when {
                error != null -> _state.value = SyncState.Failed(FAILED_MESSAGE)
                snapshot != null && snapshot.exists() -> trySend(snapshot.data.orEmpty())
            }
        }
        awaitClose { registration.remove() }
    }

    private fun FirebaseFirestore.document(uid: String) = collection(USERS).document(uid)

    private companion object {
        const val USERS = "users"

        /**
         * Longer than the local write debounce on purpose. Answering is one tap, and a
         * study session should not be one network write per question - the web app does
         * exactly that today, and it is the thing least worth copying from it.
         */
        const val PUSH_DEBOUNCE_MS = 2_000L

        const val FAILED_MESSAGE = "Not synced. Your progress is safe on this device."
    }
}
