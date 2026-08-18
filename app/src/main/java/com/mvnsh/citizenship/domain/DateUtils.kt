package com.mvnsh.citizenship.domain

import java.time.Clock
import java.time.LocalDate
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

    fun fmtDate(iso: String?): String =
        iso?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it).format(LONG) }.getOrDefault("") }
            ?: ""

    /** Clamped at zero: a past test date reads as "0 days", never negative. */
    fun daysTo(iso: String, clock: Clock = Clock.systemDefaultZone()): Int {
        val days = runCatching {
            LocalDate.parse(iso).toEpochDay() - LocalDate.now(clock).toEpochDay()
        }.getOrDefault(0L)
        return days.coerceAtLeast(0L).toInt()
    }

    fun mmss(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** Truncates with a trailing ellipsis, matching the design's clip(). */
    fun clip(text: String, max: Int): String =
        if (text.length > max) text.take(max - 1).trim() + "…" else text
}
