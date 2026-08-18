package com.mvnsh.citizenship.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilsTest {

    private val toronto: ZoneId = ZoneId.of("America/Toronto")
    private fun clockAt(iso: String) = Clock.fixed(Instant.parse(iso), toronto)

    @Test
    fun today_is_the_local_calendar_date_not_utc() {
        // 03:30 UTC on the 19th is still 23:30 on the 18th in Toronto. Getting this
        // wrong would silently break the streak for anyone studying late at night.
        assertEquals("2026-08-18", DateUtils.today(clockAt("2026-08-19T03:30:00Z")))
    }

    @Test
    fun shiftDay_moves_by_calendar_days() {
        val c = clockAt("2026-08-18T16:00:00Z")
        assertEquals("2026-08-17", DateUtils.shiftDay(-1, c))
        assertEquals("2026-08-19", DateUtils.shiftDay(1, c))
        assertEquals("2026-09-25", DateUtils.shiftDay(38, c))
    }

    @Test
    fun shiftDay_crosses_a_daylight_saving_boundary_cleanly() {
        // Toronto leaves DST on 2026-11-01. Day arithmetic must stay calendar-based.
        val c = clockAt("2026-10-31T16:00:00Z")
        assertEquals("2026-11-01", DateUtils.shiftDay(1, c))
        assertEquals("2026-11-02", DateUtils.shiftDay(2, c))
    }

    @Test
    fun daysTo_never_returns_negative() {
        val c = clockAt("2026-08-18T16:00:00Z")
        assertEquals(38, DateUtils.daysTo("2026-09-25", c))
        assertEquals(0, DateUtils.daysTo("2026-08-18", c))
        assertEquals(0, DateUtils.daysTo("2026-08-01", c))
    }

    @Test
    fun fmtDate_uses_the_designs_long_form() {
        assertEquals("25 September 2026", DateUtils.fmtDate("2026-09-25"))
        assertEquals("1 January 2027", DateUtils.fmtDate("2027-01-01"))
        assertEquals("", DateUtils.fmtDate(null))
        assertEquals("", DateUtils.fmtDate(""))
    }

    @Test
    fun mmss_pads_seconds() {
        assertEquals("30:00", DateUtils.mmss(1800))
        assertEquals("4:59", DateUtils.mmss(299))
        assertEquals("0:07", DateUtils.mmss(7))
        assertEquals("0:00", DateUtils.mmss(0))
        assertEquals("a negative remainder clamps rather than showing a minus", "0:00", DateUtils.mmss(-5))
    }

    @Test
    fun clip_adds_an_ellipsis_only_when_it_shortens() {
        assertEquals("abc", DateUtils.clip("abc", 5))
        assertEquals("abc", DateUtils.clip("abc", 3))
        assertEquals("ab" + "…", DateUtils.clip("abcdef", 3))
    }
}
