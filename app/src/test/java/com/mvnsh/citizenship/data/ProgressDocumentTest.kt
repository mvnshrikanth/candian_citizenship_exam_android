package com.mvnsh.citizenship.data

import com.mvnsh.citizenship.data.model.MockAttempt
import com.mvnsh.citizenship.data.model.NotifPrefs
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SeenStat
import com.mvnsh.citizenship.data.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract between this app and the web app.
 *
 * A failure here is not a test problem - it means the two applications have stopped
 * agreeing about the shape of a document they both write, and somebody's progress is
 * about to be misread or dropped.
 */
class ProgressDocumentTest {

    private val sample = ProgressState(
        seen = mapOf(
            1 to SeenStat(4, 1, "2026-08-20T12:00:00Z"),
            7 to SeenStat(2, 0, "2026-08-21T09:30:00Z"),
        ),
        bookmarks = listOf(7, 12),
        mocks = listOf(
            MockAttempt(70, "2026-08-12T10:00:00Z"),
            MockAttempt(85, "2026-08-20T18:00:00Z"),
        ),
        goalTarget = 30,
        testDate = "2026-09-25",
    )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.progress(): Map<String, Map<String, Any?>> =
        get(ProgressDocument.FIELD_PROGRESS) as Map<String, Map<String, Any?>>

    // ---- outbound -------------------------------------------------------

    @Test
    fun a_question_is_written_in_the_shape_the_web_app_reads() {
        val record = ProgressDocument.toFirestore(sample).progress().getValue("1")
        assertEquals(4, record[ProgressDocument.ATTEMPTS])
        assertEquals("correct is written even though Android derives it", 3, record[ProgressDocument.CORRECT])
        assertEquals(1, record[ProgressDocument.INCORRECT])
        assertEquals("2026-08-20T12:00:00Z", record[ProgressDocument.LAST_ATTEMPTED])
        assertEquals(false, record[ProgressDocument.BOOKMARKED])
    }

    @Test
    fun a_bookmark_on_an_unanswered_question_still_gets_a_record() {
        // Question 12 was never answered, only saved. The web app's own bookmark write
        // creates an entry the same way, so leaving it out would lose the bookmark.
        val record = ProgressDocument.toFirestore(sample).progress().getValue("12")
        assertEquals(0, record[ProgressDocument.ATTEMPTS])
        assertEquals(true, record[ProgressDocument.BOOKMARKED])
        assertNull(record[ProgressDocument.LAST_ATTEMPTED])
    }

    @Test
    fun mocks_carry_the_pass_flag_the_web_app_expects() {
        @Suppress("UNCHECKED_CAST")
        val mocks = ProgressDocument.toFirestore(sample)[ProgressDocument.FIELD_MOCKS]
            as List<Map<String, Any?>>
        assertEquals(2, mocks.size)
        assertEquals(false, mocks[0][ProgressDocument.MOCK_PASSED])
        assertEquals(true, mocks[1][ProgressDocument.MOCK_PASSED])
        assertEquals("2026-08-20T18:00:00Z", mocks[1][ProgressDocument.MOCK_DATE])
    }

    @Test
    fun study_settings_travel_with_the_account() {
        @Suppress("UNCHECKED_CAST")
        val study = ProgressDocument.toFirestore(sample)[ProgressDocument.FIELD_STUDY]
            as Map<String, Any?>
        assertEquals(30, study[ProgressDocument.GOAL_TARGET])
        assertEquals("2026-09-25", study[ProgressDocument.TEST_DATE])
    }

    @Test
    fun device_settings_are_never_written() {
        val keys = ProgressDocument.toFirestore(
            sample.copy(theme = "Dark", onboarded = true, notif = NotifPrefs(daily = false)),
        ).keys
        assertFalse("theme is a device preference", keys.any { it.contains("theme", true) })
        assertFalse("so are notifications", keys.any { it.contains("notif", true) })
        assertFalse("and so is onboarding", keys.any { it.contains("onboard", true) })
    }

    // ---- round trip -----------------------------------------------------

    @Test
    fun everything_that_syncs_survives_a_round_trip() {
        val out = ProgressDocument.fromFirestore(
            ProgressDocument.toFirestore(sample), ProgressState(),
        )
        assertEquals(sample.seen, out.seen)
        assertEquals(sample.bookmarks.sorted(), out.bookmarks)
        assertEquals(sample.mocks, out.mocks)
        assertEquals(sample.goalTarget, out.goalTarget)
        assertEquals(sample.testDate, out.testDate)
    }

    @Test
    fun an_inbound_snapshot_leaves_this_devices_own_settings_alone() {
        val local = ProgressState(
            onboarded = true,
            theme = "Dark",
            notif = NotifPrefs(daily = false, streak = true),
            goalDone = 6,
            goalDate = "2026-08-21",
            session = SessionState(
                mode = "QUICK", label = "Quick practice", ids = listOf(1, 2, 3),
                startedAtEpochMs = 1_760_000_000_000,
            ),
        )
        val out = ProgressDocument.fromFirestore(ProgressDocument.toFirestore(sample), local)

        assertTrue("onboarding is per install", out.onboarded)
        assertEquals("Dark", out.theme)
        assertEquals(false, out.notif.daily)
        assertEquals("the session in progress must survive a sync", local.session, out.session)
        assertEquals("today's goal count is not account data", 6, out.goalDone)
    }

    // ---- inbound tolerance ---------------------------------------------

    @Test
    fun a_document_written_by_the_web_app_alone_reads_cleanly() {
        // Exactly what the web writes today: no study, no mocks, no schemaVersion.
        val web = mapOf<String, Any?>(
            "email" to "someone@example.com",
            "displayName" to "",
            ProgressDocument.FIELD_PROGRESS to mapOf(
                "3" to mapOf(
                    "attempts" to 5L, // Firestore hands back whole numbers as Long
                    "correct" to 4L,
                    "incorrect" to 1L,
                    "lastAttempted" to "2026-08-21T08:00:00.000Z",
                    "bookmarked" to true,
                ),
            ),
        )
        val out = ProgressDocument.fromFirestore(web, ProgressState(goalTarget = 20))
        assertEquals(SeenStat(5, 1, "2026-08-21T08:00:00.000Z"), out.seen.getValue(3))
        assertEquals(listOf(3), out.bookmarks)
        assertEquals("no study block means keep what this device had", 20, out.goalTarget)
        assertTrue(out.mocks.isEmpty())
    }

    @Test
    fun unknown_fields_from_a_newer_build_are_ignored() {
        val future = ProgressDocument.toFirestore(sample) + mapOf(
            "somethingNew" to mapOf("a" to 1),
            "streakFreezes" to 3,
        )
        val out = ProgressDocument.fromFirestore(future, ProgressState())
        assertEquals(sample.seen, out.seen)
    }

    @Test
    fun malformed_records_are_skipped_rather_than_crashing() {
        val junk = mapOf<String, Any?>(
            ProgressDocument.FIELD_PROGRESS to mapOf(
                "notANumber" to mapOf("attempts" to 3),
                "4" to "this should be a map",
                "5" to mapOf("attempts" to "7", "incorrect" to 2),
                "6" to mapOf("attempts" to 1, "incorrect" to 99), // more misses than tries
            ),
            ProgressDocument.FIELD_MOCKS to listOf("nonsense", mapOf("pct" to 80)),
        )
        val out = ProgressDocument.fromFirestore(junk, ProgressState())

        assertEquals("only the readable records survive", setOf(5, 6), out.seen.keys)
        assertEquals(SeenStat(7, 2, null), out.seen.getValue(5))
        assertEquals("a miss count cannot exceed the attempts", 1, out.seen.getValue(6).m)
        assertTrue("a mock with no date is not a mock", out.mocks.isEmpty())
    }

    @Test
    fun a_zero_attempt_record_is_a_bookmark_not_a_history() {
        val data = mapOf<String, Any?>(
            ProgressDocument.FIELD_PROGRESS to mapOf(
                "9" to mapOf("attempts" to 0, "incorrect" to 0, "bookmarked" to true),
            ),
        )
        val out = ProgressDocument.fromFirestore(data, ProgressState())
        assertTrue("nothing was answered, so nothing is seen", out.seen.isEmpty())
        assertEquals(listOf(9), out.bookmarks)
    }

    // ---- emptiness, which decides the first sign-in --------------------

    @Test
    fun emptiness_is_what_makes_the_first_sign_in_silent() {
        assertTrue(ProgressDocument.isEmpty(ProgressState()))
        assertFalse(ProgressDocument.isEmpty(sample))
        assertTrue(ProgressDocument.isEmpty(emptyMap()))
        assertTrue(ProgressDocument.isEmpty(mapOf("email" to "a@b.c")))
        assertFalse(ProgressDocument.isEmpty(ProgressDocument.toFirestore(sample)))
    }

    @Test
    fun a_bookmark_alone_still_counts_as_progress_worth_keeping() {
        assertFalse(ProgressDocument.isEmpty(ProgressState(bookmarks = listOf(4))))
    }
}
