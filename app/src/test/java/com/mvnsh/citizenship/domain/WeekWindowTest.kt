package com.mvnsh.citizenship.domain

import com.mvnsh.citizenship.data.model.ProgressState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The design stored a fixed seven-slot array, always incremented slot 6 and hardcoded
 * the labels "M T W T F S S", so its bars never shifted and its labels were wrong on six
 * days out of seven. These tests pin the corrected behaviour.
 */
class WeekWindowTest {

    @Test
    fun the_window_is_seven_days_ending_today() {
        val p = ProgressState(week = listOf(1, 2, 3, 4, 5, 6, 7), weekDate = "2026-08-18")
        val out = Stats.weekWindow(p, today = "2026-08-18")
        assertEquals(7, out.size)
        assertEquals("2026-08-12", out.first().date)
        assertEquals("2026-08-18", out.last().date)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), out.map { it.count })
    }

    @Test
    fun labels_come_from_the_real_dates_not_a_fixed_string() {
        // 2026-08-18 is a Tuesday, so the window runs Wednesday..Tuesday.
        val p = ProgressState(week = List(7) { 0 }, weekDate = "2026-08-18")
        assertEquals(
            listOf("W", "T", "F", "S", "S", "M", "T"),
            Stats.weekWindow(p, "2026-08-18").map { it.label },
        )
    }

    @Test
    fun a_one_day_gap_shifts_the_window_and_zeroes_today() {
        val p = ProgressState(week = listOf(1, 2, 3, 4, 5, 6, 7), weekDate = "2026-08-17")
        val out = Stats.weekWindow(p, today = "2026-08-18")
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 0), out.map { it.count })
        assertEquals("2026-08-18", out.last().date)
    }

    @Test
    fun a_gap_of_a_week_or_more_clears_the_window() {
        val p = ProgressState(week = listOf(9, 9, 9, 9, 9, 9, 9), weekDate = "2026-07-01")
        assertEquals(List(7) { 0 }, Stats.weekWindow(p, today = "2026-08-18").map { it.count })
    }

    @Test
    fun a_backwards_clock_leaves_the_window_alone() {
        val p = ProgressState(week = listOf(1, 2, 3, 4, 5, 6, 7), weekDate = "2026-08-18")
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), Stats.rollWeek(p.week, p.weekDate, "2026-08-16"))
    }

    @Test
    fun answering_after_a_gap_credits_today_not_the_stale_slot() {
        val p = ProgressState(
            week = listOf(1, 2, 3, 4, 5, 6, 7),
            weekDate = "2026-08-16",
            goalDate = "2026-08-16",
        )
        val out = Stats.registerAnswer(p, 1, correct = true, today = "2026-08-18", yesterday = "2026-08-17")
        assertEquals("2026-08-18", out.weekDate)
        assertEquals(listOf(3, 4, 5, 6, 7, 0, 1), out.week)
    }

    @Test
    fun an_absent_anchor_is_treated_as_today_so_older_blobs_keep_their_counts() {
        val p = ProgressState(week = listOf(0, 0, 0, 0, 0, 0, 5), weekDate = "")
        assertEquals(5, Stats.weekWindow(p, "2026-08-18").last().count)
    }

    @Test
    fun a_malformed_week_array_is_normalised_to_seven_slots() {
        assertEquals(7, Stats.rollWeek(listOf(1, 2), "", "2026-08-18").size)
        assertEquals(7, Stats.rollWeek(List(12) { 1 }, "", "2026-08-18").size)
    }
}
