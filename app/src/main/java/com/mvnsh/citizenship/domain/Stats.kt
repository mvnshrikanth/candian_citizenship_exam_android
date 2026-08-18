package com.mvnsh.citizenship.domain

import com.mvnsh.citizenship.data.model.MockAttempt
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.Question
import com.mvnsh.citizenship.data.model.SeenStat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Everything derived from stored progress: accuracy, topic breakdown, streak, milestones. */
object Stats {

    data class Milestone(val key: String, val label: String)

    /** Verbatim from the design's MILES constant, in its order. */
    val MILESTONES: List<Milestone> = listOf(
        Milestone("first", "First session"),
        Milestone("s3", "3-day streak"),
        Milestone("q50", "50 questions"),
        Milestone("s7", "7-day streak"),
        Milestone("q200", "200 questions"),
        Milestone("mock1", "First mock"),
        Milestone("pass", "Mock passed"),
        Milestone("all7", "All 7 topics"),
    )

    /** A mock at or above this percentage counts as a pass (15 of 20). */
    const val PASS_PCT = 75

    /** Below this topic accuracy, the topic is flagged as one to work on. */
    private const val WEAKISH_PCT = 70

    data class TopicStat(
        val key: String,
        val name: String,
        val blurb: String,
        val total: Int,
        val done: Int,
        /** Share of the topic's questions seen at least once, 0..100. */
        val coveragePct: Int,
        /** Correct share of all attempts on this topic, 0..100. */
        val accuracy: Int,
        /** "—" when never attempted, so an untouched topic is not shown as 0%. */
        val accuracyText: String,
        val weakish: Boolean,
    )

    data class DayCount(val date: String, val count: Int, val label: String)

    fun accuracy(p: ProgressState): Int =
        if (p.answered == 0) 0 else (p.correct * 100.0 / p.answered).roundToInt()

    fun topicStats(bank: List<Question>, p: ProgressState): List<TopicStat> =
        Topics.all.map { topic ->
            val qs = bank.filter { it.topic == topic.key }
            val seenQs = qs.filter { p.seen.containsKey(it.id) }

            var attempts = 0
            var misses = 0
            seenQs.forEach { q ->
                val rec = p.seen.getValue(q.id)
                attempts += rec.s
                misses += rec.m
            }

            val acc = if (attempts == 0) 0 else ((attempts - misses) * 100.0 / attempts).roundToInt()
            TopicStat(
                key = topic.key,
                name = topic.name,
                blurb = topic.blurb,
                total = qs.size,
                done = seenQs.size,
                coveragePct = if (qs.isEmpty()) 0 else (seenQs.size * 100.0 / qs.size).roundToInt(),
                accuracy = acc,
                accuracyText = if (attempts == 0) "—" else "$acc%",
                weakish = attempts > 0 && acc < WEAKISH_PCT,
            )
        }

    fun touchedTopics(bank: List<Question>, p: ProgressState): Int {
        val byId = bank.associateBy { it.id }
        return p.seen.keys.mapNotNullTo(HashSet()) { byId[it]?.topic }.size
    }

    fun achievements(bank: List<Question>, p: ProgressState): Set<String> = buildSet {
        if (p.answered > 0) add("first")
        if (p.streak >= 3) add("s3")
        if (p.answered >= 50) add("q50")
        if (p.streak >= 7) add("s7")
        if (p.answered >= 200) add("q200")
        if (p.mocks.isNotEmpty()) add("mock1")
        if (p.mocks.any { it.pct >= PASS_PCT }) add("pass")
        if (touchedTopics(bank, p) >= Topics.all.size) add("all7")
    }

    fun registerAnswer(
        p: ProgressState,
        questionId: Int,
        correct: Boolean,
        today: String,
        yesterday: String,
    ): ProgressState {
        val rec = p.seen[questionId] ?: SeenStat()
        val seen = p.seen + (questionId to SeenStat(s = rec.s + 1, m = rec.m + if (correct) 0 else 1))

        val goalIsStale = p.goalDate != today
        val week = rollWeek(p.week, p.weekDate, today).toMutableList()
        week[6] = week[6] + 1

        return advanceDay(p, today, yesterday).copy(
            seen = seen,
            answered = p.answered + 1,
            correct = p.correct + if (correct) 1 else 0,
            goalDate = today,
            goalDone = if (goalIsStale) 1 else p.goalDone + 1,
            week = week,
            weekDate = today,
        )
    }

    /** Returns the updated progress and how many of the mock's questions were correct. */
    fun registerMock(
        p: ProgressState,
        ids: List<Int>,
        marks: Map<Int, Int>,
        byId: Map<Int, Question>,
        today: String,
        yesterday: String,
    ): Pair<ProgressState, Int> {
        val right = ids.count { id -> marks[id] != null && marks[id] == byId[id]?.answer }
        val pct = if (ids.isEmpty()) 0 else (right * 100.0 / ids.size).roundToInt()

        val seen = p.seen.toMutableMap()
        ids.forEach { id ->
            val rec = seen[id] ?: SeenStat()
            // An unanswered question counts as missed; the real test marks it wrong too.
            val wrong = marks[id] == null || marks[id] != byId[id]?.answer
            seen[id] = SeenStat(s = rec.s + 1, m = rec.m + if (wrong) 1 else 0)
        }

        val out = advanceDay(p, today, yesterday).copy(
            seen = seen,
            mocks = p.mocks + MockAttempt(pct = pct, date = today),
            answered = p.answered + ids.size,
            correct = p.correct + right,
        )
        return out to right
    }

    /**
     * Rolls the stored seven-day window forward so its last slot is [today].
     *
     * The design kept a fixed seven-slot array, always incremented slot 6 and labelled
     * the slots "M T W T F S S", so the bars never moved and the labels were wrong six
     * days out of seven. Anchoring to a date fixes both.
     */
    fun rollWeek(week: List<Int>, from: String, today: String): List<Int> {
        val counts = week.normalisedTo7()
        if (from.isBlank() || from == today) return counts

        val gap = runCatching {
            (LocalDate.parse(today).toEpochDay() - LocalDate.parse(from).toEpochDay()).toInt()
        }.getOrDefault(0)

        return when {
            gap <= 0 -> counts // clock moved backwards; leave the window alone
            gap >= 7 -> List(7) { 0 }
            else -> counts.drop(gap) + List(gap) { 0 }
        }
    }

    /** The seven days ending today, each labelled with its own day-of-week initial. */
    fun weekWindow(p: ProgressState, today: String): List<DayCount> {
        val counts = rollWeek(p.week, p.weekDate, today)
        val end = LocalDate.parse(today)
        return counts.mapIndexed { i, n ->
            val date = end.minusDays((6 - i).toLong())
            DayCount(
                date = date.toString(),
                count = n,
                label = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.CANADA).take(1),
            )
        }
    }

    /**
     * The streak's three branches in one place: a consecutive day extends it, a gap
     * restarts it at one, and a second visit on the same day changes nothing.
     */
    private fun advanceDay(p: ProgressState, today: String, yesterday: String): ProgressState {
        if (p.lastDay == today) return p
        val streak = if (p.lastDay == yesterday) p.streak + 1 else 1
        return p.copy(streak = streak, lastDay = today, best = max(p.best, streak))
    }

    private fun List<Int>.normalisedTo7(): List<Int> =
        if (size == 7) this else (this + List(7) { 0 }).take(7)
}
