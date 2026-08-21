package com.mvnsh.citizenship.data

import com.mvnsh.citizenship.data.model.MockAttempt
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SeenStat
import com.mvnsh.citizenship.domain.Stats

/**
 * The two-way mapping between [ProgressState] and the Firestore document at `users/{uid}`.
 *
 * Deliberately pure - no Android types, no Firebase types - for three reasons: it is the
 * contract with the web app and therefore the thing most worth unit-testing; it can be
 * exercised on the JVM in milliseconds; and it is the single file a future iOS app has to
 * mirror rather than reinvent.
 *
 * The document already existed before this app did. `progress`, `email`, `displayName` and
 * `createdAt` are the web app's shape and are **not** changed here; everything else is
 * additive, so the web keeps working untouched until it is updated to read the new fields.
 *
 * See docs/sync-schema.md for the field-by-field contract.
 */
object ProgressDocument {

    // Field names are constants because they are a cross-platform contract, not local
    // details - a typo here is a silent divergence between two apps, not a compile error.
    const val FIELD_PROGRESS = "progress"
    const val FIELD_STUDY = "study"
    const val FIELD_MOCKS = "mocks"
    const val FIELD_SCHEMA_VERSION = "schemaVersion"
    const val FIELD_UPDATED_AT = "updatedAt"
    const val FIELD_LAST_DEVICE = "lastDevice"

    const val ATTEMPTS = "attempts"
    const val CORRECT = "correct"
    const val INCORRECT = "incorrect"
    const val LAST_ATTEMPTED = "lastAttempted"
    const val BOOKMARKED = "bookmarked"

    const val GOAL_TARGET = "goalTarget"
    const val TEST_DATE = "testDate"

    const val MOCK_PCT = "pct"
    const val MOCK_DATE = "date"
    const val MOCK_PASSED = "passed"

    const val SCHEMA_VERSION = 2
    const val DEVICE_ANDROID = "Android"

    /**
     * The fields this device owns and will write.
     *
     * `updatedAt` is not here: it has to be a server timestamp, which is a Firebase type,
     * so the sync layer adds it. Everything else is plain Kotlin.
     */
    fun toFirestore(state: ProgressState): Map<String, Any?> = mapOf(
        FIELD_SCHEMA_VERSION to SCHEMA_VERSION,
        FIELD_LAST_DEVICE to DEVICE_ANDROID,
        FIELD_PROGRESS to progressMap(state),
        FIELD_STUDY to mapOf(
            GOAL_TARGET to state.goalTarget,
            TEST_DATE to state.testDate,
        ),
        FIELD_MOCKS to state.mocks.map {
            mapOf(
                MOCK_PCT to it.pct,
                MOCK_DATE to it.date,
                // Stored rather than derived because the web app already reads a `passed`
                // flag from its local history; keeping it means one less thing to change
                // there, and it is written from the same rule both apps use.
                MOCK_PASSED to (it.pct >= Stats.PASS_PCT),
            )
        },
    )

    /**
     * A question's cloud record carries both its attempts and its bookmark, so the map is
     * keyed over every question this device knows anything about - a question that is only
     * bookmarked still needs an entry, which is what the web app's own bookmark write does.
     */
    private fun progressMap(state: ProgressState): Map<String, Any?> =
        (state.seen.keys + state.bookmarks).toSortedSet().associate { id ->
            val rec = state.seen[id] ?: SeenStat()
            id.toString() to mapOf(
                ATTEMPTS to rec.s,
                // The web app stores correct alongside incorrect. Android derives it, but
                // writes it, because the web reads this field directly.
                CORRECT to (rec.s - rec.m),
                INCORRECT to rec.m,
                LAST_ATTEMPTED to rec.lastAttempted,
                BOOKMARKED to (id in state.bookmarks),
            )
        }

    /**
     * Folds a cloud document into local state.
     *
     * [local] supplies everything the cloud does not carry - `onboarded`, `theme`, `notif`,
     * the in-flight `session`, and today's goal count - so a snapshot never wipes a
     * device's own settings or the session the user is in the middle of.
     *
     * Every read is defensive. This document is written by another application on another
     * platform; a field can be absent, null, or a Long where an Int was expected, and none
     * of those is a reason to lose someone's progress.
     */
    fun fromFirestore(data: Map<String, Any?>, local: ProgressState): ProgressState {
        val progress = data[FIELD_PROGRESS] as? Map<*, *> ?: emptyMap<Any?, Any?>()

        val seen = mutableMapOf<Int, SeenStat>()
        val bookmarks = mutableListOf<Int>()

        progress.forEach { (rawId, rawRecord) ->
            val id = (rawId as? String)?.toIntOrNull() ?: return@forEach
            val record = rawRecord as? Map<*, *> ?: return@forEach

            val attempts = record.int(ATTEMPTS)
            val incorrect = record.int(INCORRECT).coerceIn(0, attempts.coerceAtLeast(0))
            if (attempts > 0) {
                seen[id] = SeenStat(
                    s = attempts,
                    m = incorrect,
                    lastAttempted = record[LAST_ATTEMPTED] as? String,
                )
            }
            if (record[BOOKMARKED] == true) bookmarks += id
        }

        val study = data[FIELD_STUDY] as? Map<*, *>
        val mocks = (data[FIELD_MOCKS] as? List<*>).orEmpty().mapNotNull { raw ->
            val entry = raw as? Map<*, *> ?: return@mapNotNull null
            val date = entry[MOCK_DATE] as? String ?: return@mapNotNull null
            MockAttempt(pct = entry.int(MOCK_PCT), date = date)
        }

        return local.copy(
            seen = seen,
            bookmarks = bookmarks.sorted(),
            mocks = mocks,
            goalTarget = study?.int(GOAL_TARGET)?.takeIf { it > 0 } ?: local.goalTarget,
            testDate = study?.get(TEST_DATE) as? String,
            schemaVersion = SCHEMA_VERSION,
        )
    }

    /** True when the document holds nothing worth keeping, so adopting either side is safe. */
    fun isEmpty(data: Map<String, Any?>): Boolean {
        val progress = data[FIELD_PROGRESS] as? Map<*, *>
        val mocks = data[FIELD_MOCKS] as? List<*>
        return progress.isNullOrEmpty() && mocks.isNullOrEmpty()
    }

    /** True when this device has nothing worth uploading. */
    fun isEmpty(state: ProgressState): Boolean =
        state.seen.isEmpty() && state.bookmarks.isEmpty() && state.mocks.isEmpty()

    /** Firestore hands back whole numbers as Long; a hand-edited document may hold anything. */
    private fun Map<*, *>.int(key: String): Int = when (val v = this[key]) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: 0
        else -> 0
    }
}
