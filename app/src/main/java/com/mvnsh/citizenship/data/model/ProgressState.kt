package com.mvnsh.citizenship.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Attempts and misses for one question. Missing it twice puts it on the weak list. */
@Serializable
data class SeenStat(val s: Int = 0, val m: Int = 0)

@Serializable
data class MockAttempt(val pct: Int, val date: String)

@Serializable
data class NotifPrefs(
    val daily: Boolean = true,
    val streak: Boolean = false,
    val test: Boolean = false,
)

/**
 * The in-flight practice or mock session.
 *
 * Persisted, unlike the web prototype's in-memory session, for two reasons the design's
 * own copy promises: the exit dialog says "Your place is kept", and the mock intro says
 * "the timer keeps running if you leave the app". An absolute [deadlineEpochMs] is what
 * makes the second one true across backgrounding and process death.
 */
@Serializable
data class SessionState(
    val mode: String,
    val topicKey: String? = null,
    val label: String,
    val ids: List<Int>,
    val index: Int = 0,
    /** Practice only: the option index that revealed the current answer. */
    val picked: Int? = null,
    val marks: Map<Int, Int> = emptyMap(),
    val right: Int = 0,
    val startedAtEpochMs: Long,
    val timed: Boolean = false,
    val deadlineEpochMs: Long? = null,
    val submitted: Boolean = false,
    val finalRight: Int? = null,
    val finalPct: Int? = null,
)

/**
 * Everything the app remembers, stored as one JSON blob in DataStore.
 *
 * Defaults are not encoded, so a fresh install's blob is "{}" and only real progress
 * grows the file. Unknown keys are ignored so a newer build's blob still loads.
 */
@Serializable
data class ProgressState(
    val onboarded: Boolean = false,
    val answered: Int = 0,
    val correct: Int = 0,
    val seen: Map<Int, SeenStat> = emptyMap(),
    val mocks: List<MockAttempt> = emptyList(),
    val bookmarks: List<Int> = emptyList(),
    val goalTarget: Int = 20,
    val goalDone: Int = 0,
    val goalDate: String = "",
    val streak: Int = 0,
    val best: Int = 0,
    val lastDay: String? = null,
    val testDate: String? = null,
    val notif: NotifPrefs = NotifPrefs(),
    val theme: String = "System",
    /** Seven daily answer counts ending on [weekDate]. See Stats.rollWeek. */
    val week: List<Int> = listOf(0, 0, 0, 0, 0, 0, 0),
    /** Anchor date for [week]; blank on a blob written before the window rolled. */
    val weekDate: String = "",
    val session: SessionState? = null,
    val schemaVersion: Int = 1,
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        fun encode(state: ProgressState): String = json.encodeToString(state)

        /** Never throws: a missing, empty or corrupt blob is simply a fresh install. */
        fun decode(raw: String?): ProgressState =
            if (raw.isNullOrBlank()) {
                ProgressState()
            } else {
                runCatching { json.decodeFromString<ProgressState>(raw) }.getOrElse { ProgressState() }
            }
    }
}
