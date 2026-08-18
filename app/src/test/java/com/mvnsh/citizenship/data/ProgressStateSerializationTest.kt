package com.mvnsh.citizenship.data

import com.mvnsh.citizenship.data.model.MockAttempt
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SeenStat
import com.mvnsh.citizenship.data.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressStateSerializationTest {

    @Test
    fun round_trips_without_loss() {
        val original = ProgressState(
            onboarded = true, answered = 340, correct = 279,
            seen = mapOf(1 to SeenStat(2, 1), 502 to SeenStat(1, 0)),
            mocks = listOf(MockAttempt(70, "2026-08-09"), MockAttempt(85, "2026-08-17")),
            bookmarks = listOf(2, 7, 10, 44, 120),
            goalTarget = 30, goalDone = 14, goalDate = "2026-08-18",
            streak = 12, best = 14, lastDay = "2026-08-17", testDate = "2026-09-25",
            theme = "Dark", week = listOf(12, 16, 20, 18, 22, 14, 14), weekDate = "2026-08-18",
            session = SessionState(
                mode = "MOCK", label = "Mock test", ids = listOf(3, 9, 12),
                index = 1, marks = mapOf(3 to 2), startedAtEpochMs = 1_760_000_000_000,
                timed = true, deadlineEpochMs = 1_760_000_180_000,
            ),
        )
        assertEquals(original, ProgressState.decode(ProgressState.encode(original)))
    }

    @Test
    fun a_missing_blob_decodes_to_the_fresh_default() {
        val fresh = ProgressState.decode(null)
        assertEquals(ProgressState(), fresh)
        assertEquals(false, fresh.onboarded)
        assertEquals(20, fresh.goalTarget)
        assertEquals(0, fresh.streak)
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0), fresh.week)
        assertNull(fresh.session)
        assertNull(fresh.lastDay)
        assertEquals(true, fresh.notif.daily)
        assertEquals(false, fresh.notif.streak)
        assertEquals("System", fresh.theme)
    }

    @Test
    fun corrupt_json_decodes_to_the_fresh_default_instead_of_crashing() {
        // A half-written blob must not brick the app on launch.
        assertEquals(ProgressState(), ProgressState.decode("{not json"))
        assertEquals(ProgressState(), ProgressState.decode(""))
        assertEquals(ProgressState(), ProgressState.decode("   "))
        assertEquals(ProgressState(), ProgressState.decode("[]"))
    }

    @Test
    fun an_older_blob_missing_newer_fields_still_loads() {
        val old = """{"onboarded":true,"answered":5,"correct":4,"goalTarget":10}"""
        val out = ProgressState.decode(old)
        assertEquals(true, out.onboarded)
        assertEquals(5, out.answered)
        assertEquals(10, out.goalTarget)
        assertEquals("defaults fill the gap", "System", out.theme)
        assertEquals("", out.weekDate)
    }

    @Test
    fun an_unknown_field_from_a_newer_build_is_ignored() {
        assertEquals(3, ProgressState.decode("""{"answered":3,"somethingNew":{"a":1}}""").answered)
    }

    @Test
    fun the_encoded_blob_omits_defaults_to_stay_small() {
        // Only real progress grows the file, so a fresh install writes almost nothing.
        assertEquals("{}", ProgressState.encode(ProgressState()))
    }

    @Test
    fun a_full_bank_of_seen_records_stays_a_reasonable_size() {
        // Every question answered twice is the realistic worst case for blob size.
        val heavy = ProgressState(
            answered = 1002, correct = 800,
            seen = (1..502).associateWith { SeenStat(2, 1) },
        )
        val encoded = ProgressState.encode(heavy)
        assertEquals(heavy, ProgressState.decode(encoded))
        assertTrue("blob grew to ${encoded.length} chars", encoded.length < 30_000)
    }
}
