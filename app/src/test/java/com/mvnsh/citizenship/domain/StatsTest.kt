package com.mvnsh.citizenship.domain

import com.mvnsh.citizenship.data.model.MockAttempt
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.Question
import com.mvnsh.citizenship.data.model.SeenStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Every figure here is derived from `seen`, by the same rules the web app uses, so that one
 * account reads the same on both platforms. These tests are the contract for that.
 */
class StatsTest {

    private val today: LocalDate = LocalDate.now()

    /**
     * An ISO instant that reliably falls on a given local day.
     *
     * Midday in the device's own zone, because the derivation converts to the local day -
     * an instant at midnight UTC would land on either side of the date line depending on
     * where the test runs.
     */
    private fun iso(daysAgo: Int): String =
        today.minusDays(daysAgo.toLong())
            .atTime(12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toString()

    private fun q(id: Int, topic: String) = Question(
        id = id, topic = topic, question = "q$id",
        options = listOf("a", "b", "c", "d"), answer = 1,
    )

    private val bank = listOf(
        q(1, "rights"), q(2, "rights"), q(3, "rights"), q(4, "rights"),
        q(5, "history"), q(6, "history"),
    )

    // ---- totals ---------------------------------------------------------

    @Test
    fun totals_are_summed_from_the_per_question_records() {
        val p = ProgressState(seen = mapOf(1 to SeenStat(4, 1), 2 to SeenStat(2, 2)))
        assertEquals("every attempt counts", 6, Stats.answered(p))
        assertEquals("attempts minus misses", 3, Stats.correct(p))
        assertEquals(50, Stats.accuracy(p))
        assertEquals("two distinct questions touched", 2, Stats.seenCount(p))
    }

    @Test
    fun a_fresh_state_reports_zero_rather_than_dividing_by_it() {
        val p = ProgressState()
        assertEquals(0, Stats.answered(p))
        assertEquals(0, Stats.accuracy(p))
        assertEquals(0, Stats.streak(p, today))
        assertEquals(0, Stats.bestStreak(p))
    }

    // ---- streak ---------------------------------------------------------

    @Test
    fun the_streak_counts_consecutive_days_ending_today() {
        val p = ProgressState(
            seen = mapOf(
                1 to SeenStat(1, 0, iso(0)),
                2 to SeenStat(1, 0, iso(1)),
                3 to SeenStat(1, 0, iso(2)),
                4 to SeenStat(1, 0, iso(5)), // older, beyond the gap
            ),
        )
        assertEquals(3, Stats.streak(p, today))
    }

    @Test
    fun nothing_answered_today_means_no_streak() {
        // The web's rule, matched deliberately: the run has to reach today.
        val p = ProgressState(seen = mapOf(1 to SeenStat(1, 0, iso(1))))
        assertEquals(0, Stats.streak(p, today))
    }

    @Test
    fun several_questions_on_one_day_are_still_one_day() {
        val p = ProgressState(
            seen = mapOf(
                1 to SeenStat(1, 0, iso(0)),
                2 to SeenStat(1, 0, iso(0)),
                3 to SeenStat(1, 0, iso(0)),
            ),
        )
        assertEquals(1, Stats.streak(p, today))
    }

    @Test
    fun a_record_with_no_timestamp_counts_towards_totals_but_not_the_streak() {
        // Blobs written before sync existed have no timestamp. They must not vanish from
        // the totals, and they cannot honestly contribute a day.
        val p = ProgressState(seen = mapOf(1 to SeenStat(3, 1, null)))
        assertEquals(3, Stats.answered(p))
        assertEquals(0, Stats.streak(p, today))
        assertTrue(Stats.studyDays(p).isEmpty())
    }

    @Test
    fun best_streak_is_the_longest_run_on_record_not_the_current_one() {
        val p = ProgressState(
            seen = mapOf(
                1 to SeenStat(1, 0, iso(10)),
                2 to SeenStat(1, 0, iso(9)),
                3 to SeenStat(1, 0, iso(8)),
                4 to SeenStat(1, 0, iso(7)), // a four-day run, long finished
                5 to SeenStat(1, 0, iso(0)), // today, on its own
            ),
        )
        assertEquals(4, Stats.bestStreak(p))
        assertEquals(1, Stats.streak(p, today))
    }

    @Test
    fun re_answering_an_old_question_moves_its_day_and_can_shorten_the_streak() {
        // Not a bug being introduced here - it is the web app's behaviour, matched on
        // purpose so the two never disagree. A question remembers only its most recent
        // attempt, so the day it used to occupy is lost unless another question holds it.
        val before = ProgressState(
            seen = mapOf(
                1 to SeenStat(1, 0, iso(1)),
                2 to SeenStat(1, 0, iso(0)),
            ),
        )
        assertEquals(2, Stats.streak(before, today))

        val after = Stats.registerAnswer(
            before, questionId = 1, correct = true, nowIso = iso(0), today = today.toString(),
        )
        assertEquals("yesterday is no longer represented", 1, Stats.streak(after, today))
    }

    // ---- the week chart -------------------------------------------------

    @Test
    fun the_week_window_is_seven_days_ending_today() {
        val out = Stats.weekWindow(ProgressState(), today)
        assertEquals(7, out.size)
        assertEquals(today.minusDays(6).toString(), out.first().date)
        assertEquals(today.toString(), out.last().date)
    }

    @Test
    fun the_week_window_counts_questions_per_day() {
        val p = ProgressState(
            seen = mapOf(
                1 to SeenStat(1, 0, iso(0)),
                2 to SeenStat(1, 0, iso(0)),
                3 to SeenStat(1, 0, iso(3)),
                4 to SeenStat(1, 0, iso(30)), // outside the window
            ),
        )
        val out = Stats.weekWindow(p, today)
        assertEquals(2, out.last().count)
        assertEquals(1, out[3].count)
        assertEquals("11 days of history do not leak into a 7-day chart", 3, out.sumOf { it.count })
    }

    @Test
    fun week_labels_come_from_the_real_dates() {
        val out = Stats.weekWindow(ProgressState(), today)
        val expected = (6 downTo 0).map { today.minusDays(it.toLong()) }
            .map { it.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.CANADA).take(1) }
        assertEquals(expected, out.map { it.label })
    }

    // ---- recording ------------------------------------------------------

    @Test
    fun answering_stamps_the_question_and_advances_the_goal() {
        val out = Stats.registerAnswer(
            ProgressState(), questionId = 1, correct = true,
            nowIso = iso(0), today = today.toString(),
        )
        assertEquals(1, out.seen.getValue(1).s)
        assertEquals(0, out.seen.getValue(1).m)
        assertEquals(iso(0), out.seen.getValue(1).lastAttempted)
        assertEquals(1, out.goalDone)
        assertEquals(today.toString(), out.goalDate)
    }

    @Test
    fun a_wrong_answer_records_a_miss() {
        val out = Stats.registerAnswer(
            ProgressState(), 1, correct = false, nowIso = iso(0), today = today.toString(),
        )
        assertEquals(1, out.seen.getValue(1).s)
        assertEquals(1, out.seen.getValue(1).m)
        assertEquals(0, Stats.correct(out))
    }

    @Test
    fun a_goal_counted_on_an_earlier_day_restarts_rather_than_accumulating() {
        val stale = ProgressState(goalDone = 17, goalDate = today.minusDays(1).toString())
        val out = Stats.registerAnswer(
            stale, 1, correct = true, nowIso = iso(0), today = today.toString(),
        )
        assertEquals(1, out.goalDone)
    }

    @Test
    fun missing_a_question_twice_puts_it_on_the_weak_list() {
        var p = ProgressState()
        p = Stats.registerAnswer(p, 1, correct = false, nowIso = iso(0), today = today.toString())
        assertEquals(emptyList<Int>(), StudyEngine.weakIds(p))
        p = Stats.registerAnswer(p, 1, correct = false, nowIso = iso(0), today = today.toString())
        assertEquals(listOf(1), StudyEngine.weakIds(p))
    }

    @Test
    fun submitting_a_mock_records_the_percentage_and_folds_in_every_answer() {
        val ids = listOf(1, 2, 3, 4)
        val marks = mapOf(1 to 1, 2 to 1, 3 to 0, 4 to 2) // the answer is 1
        val (out, right) = Stats.registerMock(
            ProgressState(), ids, marks, bank.associateBy { it.id }, iso(0),
        )
        assertEquals(2, right)
        assertEquals(listOf(MockAttempt(50, iso(0))), out.mocks)
        assertEquals(4, Stats.answered(out))
        assertEquals(2, Stats.correct(out))
        assertEquals(0, out.seen.getValue(1).m)
        assertEquals(1, out.seen.getValue(3).m)
    }

    @Test
    fun an_unanswered_mock_question_counts_as_missed() {
        val (out, right) = Stats.registerMock(
            ProgressState(), listOf(1, 2), mapOf(1 to 1), bank.associateBy { it.id }, iso(0),
        )
        assertEquals(1, right)
        assertEquals(1, out.seen.getValue(2).m)
    }

    // ---- topics ---------------------------------------------------------

    @Test
    fun topic_accuracy_uses_attempts_not_distinct_questions() {
        val p = ProgressState(seen = mapOf(1 to SeenStat(4, 1), 2 to SeenStat(2, 1)))
        val rights = Stats.topicStats(bank, p).first { it.key == "rights" }
        assertEquals(67, rights.accuracy)
        assertEquals("67%", rights.accuracyText)
        assertEquals(4, rights.total)
        assertEquals(2, rights.done)
        assertEquals(50, rights.coveragePct)
    }

    @Test
    fun an_untouched_topic_shows_an_em_dash_not_zero_percent() {
        val history = Stats.topicStats(bank, ProgressState()).first { it.key == "history" }
        assertEquals("—", history.accuracyText)
        assertFalse("an untouched topic is not 'weakish'", history.weakish)
    }

    @Test
    fun topic_stats_are_returned_in_the_taxonomy_order() {
        assertEquals(
            Topics.all.map { it.key },
            Stats.topicStats(bank, ProgressState()).map { it.key },
        )
    }

    // ---- milestones -----------------------------------------------------

    @Test
    fun milestones_unlock_at_the_designs_thresholds() {
        assertEquals(emptySet<String>(), Stats.achievements(bank, ProgressState(), today))

        val days = (0..6).associate { d -> (100 + d) to SeenStat(1, 0, iso(d)) }
        val bulk = (1..60).associateWith { SeenStat(4, 0, iso(0)) } // 240 answers
        val p = ProgressState(
            seen = days + bulk,
            mocks = listOf(MockAttempt(70, iso(10)), MockAttempt(80, iso(4))),
        )
        val got = Stats.achievements(bank, p, today)
        assertTrue(got.containsAll(setOf("first", "s3", "q50", "s7", "q200", "mock1", "pass")))
        assertFalse("only two of seven topics exist in this test bank", "all7" in got)
    }

    @Test
    fun a_mock_below_the_passing_mark_does_not_unlock_pass() {
        val p = ProgressState(
            seen = mapOf(1 to SeenStat(1, 0, iso(0))),
            mocks = listOf(MockAttempt(70, iso(1))),
        )
        val got = Stats.achievements(bank, p, today)
        assertTrue("mock1" in got)
        assertFalse("pass" in got)
    }

    @Test
    fun there_are_exactly_eight_milestones_in_the_designs_order() {
        assertEquals(
            listOf("first", "s3", "q50", "s7", "q200", "mock1", "pass", "all7"),
            Stats.MILESTONES.map { it.key },
        )
        assertEquals(
            listOf(
                "First session", "3-day streak", "50 questions", "7-day streak",
                "200 questions", "First mock", "Mock passed", "All 7 topics",
            ),
            Stats.MILESTONES.map { it.label },
        )
    }
}
