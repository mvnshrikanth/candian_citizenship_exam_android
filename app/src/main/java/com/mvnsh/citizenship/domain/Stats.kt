package com.mvnsh.citizenship.domain

import com.mvnsh.citizenship.data.model.MockAttempt
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.Question
import com.mvnsh.citizenship.data.model.SeenStat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Everything derived from stored progress: totals, accuracy, topic breakdown, the streak,
 * the weekly chart and the milestones.
 *
 * **Nothing here is stored.** Every figure is computed from `ProgressState.seen`, which is
 * the same map the web app keeps in Firestore, using the same rules the web app uses. That
 * is what lets one account show the same streak and the same totals on both platforms. An
 * earlier version of this app kept `answered`, `correct`, `streak`, `best` and a rolling
 * `week` array as fields; they are gone, because two devices maintaining their own copies
 * of the same counters is exactly how they drift apart.
 */
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

    private const val WEEK_DAYS = 7

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

    // ---- totals ---------------------------------------------------------

    /** Every answer ever given, across all questions. */
    fun answered(p: ProgressState): Int = p.seen.values.sumOf { it.s }

    /** Every correct answer. A miss is recorded per attempt, so this is attempts - misses. */
    fun correct(p: ProgressState): Int = p.seen.values.sumOf { it.s - it.m }

    fun accuracy(p: ProgressState): Int {
        val total = answered(p)
        return if (total == 0) 0 else (correct(p) * 100.0 / total).roundToInt()
    }

    /** Questions touched at least once - the web app's "attempted". */
    fun seenCount(p: ProgressState): Int = p.seen.count { it.value.s > 0 }

    // ---- days, streak, week --------------------------------------------

    /**
     * The distinct local days on which something was answered.
     *
     * Derived from each question's `lastAttempted`, which is what the web app does. Note
     * the consequence, because it is not obvious and it is shared by both platforms: a
     * question only remembers its *most recent* attempt, so re-answering an old question
     * moves its day forward and the day it used to occupy disappears unless another
     * question still points at it. A streak can therefore shrink retroactively. Matching
     * the web exactly was the explicit goal; fixing it means changing both apps and the
     * schema together, not this function alone.
     */
    fun studyDays(p: ProgressState): Set<LocalDate> =
        p.seen.values.mapNotNullTo(HashSet()) { DateUtils.localDayOf(it.lastAttempted) }

    /**
     * Consecutive days ending today. Zero if nothing was answered today, which is the
     * web's rule.
     */
    fun streak(p: ProgressState, today: LocalDate): Int {
        val days = studyDays(p)
        var count = 0
        var cursor = today
        while (cursor in days) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /**
     * The longest run of consecutive study days on record.
     *
     * Android-only: the web has no concept of a best streak. Derived rather than stored so
     * it cannot disagree with [streak], and so it survives a device change.
     */
    fun bestStreak(p: ProgressState): Int {
        val days = studyDays(p).sorted()
        if (days.isEmpty()) return 0

        var best = 1
        var run = 1
        for (i in 1 until days.size) {
            run = if (days[i - 1].plusDays(1) == days[i]) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    /**
     * The seven days ending today, each labelled with its day-of-week initial.
     *
     * The count is **questions last attempted that day**, not answers given that day -
     * again matching the web, whose chart is built the same way.
     */
    fun weekWindow(p: ProgressState, today: LocalDate): List<DayCount> {
        val perDay = p.seen.values
            .mapNotNull { DateUtils.localDayOf(it.lastAttempted) }
            .groupingBy { it }
            .eachCount()

        return (0 until WEEK_DAYS).map { i ->
            val date = today.minusDays((WEEK_DAYS - 1 - i).toLong())
            DayCount(
                date = date.toString(),
                count = perDay[date] ?: 0,
                label = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.CANADA).take(1),
            )
        }
    }

    // ---- topics and milestones -----------------------------------------

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

    fun achievements(bank: List<Question>, p: ProgressState, today: LocalDate): Set<String> =
        buildSet {
            val total = answered(p)
            val run = streak(p, today)
            if (total > 0) add("first")
            if (run >= 3) add("s3")
            if (total >= 50) add("q50")
            if (run >= 7) add("s7")
            if (total >= 200) add("q200")
            if (p.mocks.isNotEmpty()) add("mock1")
            if (p.mocks.any { it.pct >= PASS_PCT }) add("pass")
            if (touchedTopics(bank, p) >= Topics.all.size) add("all7")
        }

    // ---- recording ------------------------------------------------------

    /**
     * Records one answer.
     *
     * Only two things change: the question's own record, and the daily goal counter. The
     * totals and the streak follow from [seen] and need no maintenance, which is the whole
     * point - there is no counter left to increment twice or forget to reset.
     */
    fun registerAnswer(
        p: ProgressState,
        questionId: Int,
        correct: Boolean,
        nowIso: String,
        today: String,
    ): ProgressState {
        val rec = p.seen[questionId] ?: SeenStat()
        val seen = p.seen + (
            questionId to SeenStat(
                s = rec.s + 1,
                m = rec.m + if (correct) 0 else 1,
                lastAttempted = nowIso,
            )
            )

        val goalIsStale = p.goalDate != today
        return p.copy(
            seen = seen,
            goalDate = today,
            goalDone = if (goalIsStale) 1 else p.goalDone + 1,
        )
    }

    /** Returns the updated progress and how many of the mock's questions were correct. */
    fun registerMock(
        p: ProgressState,
        ids: List<Int>,
        marks: Map<Int, Int>,
        byId: Map<Int, Question>,
        nowIso: String,
    ): Pair<ProgressState, Int> {
        val right = ids.count { id -> marks[id] != null && marks[id] == byId[id]?.answer }
        val pct = if (ids.isEmpty()) 0 else (right * 100.0 / ids.size).roundToInt()

        val seen = p.seen.toMutableMap()
        ids.forEach { id ->
            val rec = seen[id] ?: SeenStat()
            // An unanswered question counts as missed; the real test marks it wrong too.
            val wrong = marks[id] == null || marks[id] != byId[id]?.answer
            seen[id] = SeenStat(
                s = rec.s + 1,
                m = rec.m + if (wrong) 1 else 0,
                lastAttempted = nowIso,
            )
        }

        val out = p.copy(
            seen = seen,
            mocks = p.mocks + MockAttempt(pct = pct, date = nowIso),
        )
        return out to right
    }
}
