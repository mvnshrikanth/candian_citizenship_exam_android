package com.mvnsh.citizenship.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Calendar helpers for the streak, the daily goal and the test countdown.
 *
 * Every function that needs "now" takes an injectable [Clock] defaulting to the system
 * clock. The streak has three distinct branches and they are far too easy to get wrong
 * to test by sleeping, so they are driven by a fixed clock instead.
 */
object DateUtils {

    /** The design's en-CA long form, e.g. "25 September 2026". */
    private val LONG: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.CANADA)

    fun today(clock: Clock = Clock.systemDefaultZone()): String =
        LocalDate.now(clock).toString()

    fun shiftDay(n: Int, clock: Clock = Clock.systemDefaultZone()): String =
        LocalDate.now(clock).plusDays(n.toLong()).toString()

    /**
     * Formats either a bare "YYYY-MM-DD" or a full ISO-8601 instant, because mock attempts
     * now carry an instant (the shape the web app writes) while test dates stay bare days.
     */
    fun fmtDate(iso: String?): String = localDayOf(iso)?.format(LONG) ?: ""

    /** Clamped at zero: a past test date reads as "0 days", never negative. */
    fun daysTo(iso: String, clock: Clock = Clock.systemDefaultZone()): Int {
        val days = runCatching {
            LocalDate.parse(iso).toEpochDay() - LocalDate.now(clock).toEpochDay()
        }.getOrDefault(0L)
        return days.coerceAtLeast(0L).toInt()
    }

    /**
     * "Now" as an ISO-8601 UTC instant, e.g. "2026-08-21T14:03:22.481Z".
     *
     * This is the exact shape the web app writes into `lastAttempted`
     * (`new Date().toISOString()`), and both platforms derive the streak and the weekly
     * chart from those strings, so the format is a contract rather than a preference.
     */
    fun nowIso(clock: Clock = Clock.systemDefaultZone()): String = Instant.now(clock).toString()

    /**
     * The local calendar day an ISO-8601 instant falls on, or null if it will not parse.
     *
     * Local, not UTC, and deliberately so: the web uses `new Date(iso).toDateString()`,
     * which is the browser's local day. Reading these as UTC would put the two platforms
     * a day apart either side of midnight.
     */
    fun localDayOf(iso: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
        iso?.takeIf { it.isNotBlank() }?.let { text ->
            runCatching { Instant.parse(text).atZone(zone).toLocalDate() }
                // Tolerate a bare "YYYY-MM-DD", which older Android blobs may carry.
                .recoverCatching { LocalDate.parse(text) }
                .getOrNull()
        }

    fun mmss(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** Truncates with a trailing ellipsis, matching the design's clip(). */
    fun clip(text: String, max: Int): String =
        if (text.length > max) text.take(max - 1).trim() + "…" else text
}
