package com.mvnsh.citizenship.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Attempts and misses for one question. Missing it twice puts it on the weak list.
 *
 * [lastAttempted] is an ISO-8601 UTC instant and is the field the streak and the weekly
 * chart are derived from, on this platform and on the web. It is nullable because blobs
 * written before sync existed have no timestamp; those records still count towards totals,
 * they simply cannot contribute a day.
 */
@Serializable
data class SeenStat(
    val s: Int = 0,
    val m: Int = 0,
    val lastAttempted: String? = null,
)

/** [date] is an ISO-8601 UTC instant, matching what the web app records. */
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
 * Totals, streak and the weekly chart are deliberately **not** fields. They are derived
 * from [seen] by `Stats`, using the same rule the web app uses, so the two platforms can
 * never disagree about the same account. Storing them was how they drifted.
 *
 * Defaults are not encoded, so a fresh install's blob is "{}" and only real progress grows
 * the file. Unknown keys are ignored, so a blob written by an older build - which did carry
 * `answered`, `streak`, `week` and friends - still loads, and those stale values are simply
 * dropped in favour of the derivation.
 */
@Serializable
data class ProgressState(
    val onboarded: Boolean = false,
    val seen: Map<Int, SeenStat> = emptyMap(),
    val mocks: List<MockAttempt> = emptyList(),
    val bookmarks: List<Int> = emptyList(),
    val goalTarget: Int = 20,
    val goalDone: Int = 0,
    val goalDate: String = "",
    val testDate: String? = null,
    val notif: NotifPrefs = NotifPrefs(),
    val theme: String = "System",
    val session: SessionState? = null,
    val schemaVersion: Int = 2,
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
