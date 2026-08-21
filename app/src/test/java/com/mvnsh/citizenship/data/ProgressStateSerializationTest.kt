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
            onboarded = true,
            seen = mapOf(
                1 to SeenStat(2, 1, "2026-08-18T14:03:22.481Z"),
                502 to SeenStat(1, 0, "2026-08-17T09:00:00Z"),
            ),
            mocks = listOf(
                MockAttempt(70, "2026-08-09T11:00:00Z"),
                MockAttempt(85, "2026-08-17T18:30:00Z"),
            ),
            bookmarks = listOf(2, 7, 10, 44, 120),
            goalTarget = 30, goalDone = 14, goalDate = "2026-08-18",
            testDate = "2026-09-25",
            theme = "Dark",
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
        assertNull(fresh.session)
        assertNull(fresh.testDate)
        assertEquals(emptyMap<Int, SeenStat>(), fresh.seen)
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
    fun a_blob_from_the_build_that_stored_totals_still_loads() {
        // Those fields are derived now. An upgrading user's blob still carries them, and
        // ignoreUnknownKeys is what stops that being a crash on first launch after update.
        val old = """{"onboarded":true,"answered":5,"correct":4,"streak":12,"best":14,"lastDay":"2026-08-17","week":[1,2,3,4,5,6,7],"weekDate":"2026-08-18","goalTarget":10}"""
        val out = ProgressState.decode(old)
        assertEquals(true, out.onboarded)
        assertEquals(10, out.goalTarget)
        assertEquals("defaults fill the gap", "System", out.theme)
    }

    @Test
    fun a_seen_record_without_a_timestamp_still_loads() {
        // Everything written before sync existed looks like this.
        val out = ProgressState.decode("""{"seen":{"7":{"s":3,"m":1}}}""")
        assertEquals(SeenStat(3, 1, null), out.seen.getValue(7))
    }

    @Test
    fun an_unknown_field_from_a_newer_build_is_ignored() {
        val out = ProgressState.decode("""{"goalTarget":30,"somethingNew":{"a":1}}""")
        assertEquals(30, out.goalTarget)
    }

    @Test
    fun the_encoded_blob_omits_defaults_to_stay_small() {
        // Only real progress grows the file, so a fresh install writes almost nothing.
        assertEquals("{}", ProgressState.encode(ProgressState()))
    }

    @Test
    fun a_full_bank_of_seen_records_stays_a_reasonable_size() {
        // Every question answered twice is the realistic worst case for blob size.
        // Every question answered twice, each carrying a timestamp - the realistic worst
        // case now that records are stamped for sync.
        val heavy = ProgressState(
            seen = (1..535).associateWith { SeenStat(2, 1, "2026-08-18T14:03:22.481Z") },
        )
        val encoded = ProgressState.encode(heavy)
        assertEquals(heavy, ProgressState.decode(encoded))
        assertTrue("blob grew to ${encoded.length} chars", encoded.length < 60_000)
    }
}
