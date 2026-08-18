package com.mvnsh.citizenship.domain

import com.mvnsh.citizenship.data.model.MockAttempt
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.Question
import com.mvnsh.citizenship.data.model.SeenStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {

    private fun q(id: Int, topic: String) =
        Question(id = id, topic = topic, question = "q$id", options = listOf("a", "b", "c", "d"), answer = 1)

    private val bank = listOf(
        q(1, "rights"), q(2, "rights"), q(3, "rights"), q(4, "rights"),
        q(5, "history"), q(6, "history"),
    )
    private val byId = bank.associateBy { it.id }
    private val today = "2026-08-18"
    private val yesterday = "2026-08-17"

    // ---- accuracy -------------------------------------------------------

    @Test
    fun accuracy_is_zero_before_any_answer() {
        assertEquals(0, Stats.accuracy(ProgressState()))
    }

    @Test
    fun accuracy_rounds_to_a_whole_percent() {
        assertEquals(83, Stats.accuracy(ProgressState(answered = 6, correct = 5)))
        assertEquals(100, Stats.accuracy(ProgressState(answered = 3, correct = 3)))
    }

    // ---- topic stats ----------------------------------------------------

    @Test
    fun topic_accuracy_uses_attempts_not_distinct_questions() {
        // q1 attempted 4x missing 1, q2 attempted 2x missing 1.
        // (6 attempts - 2 misses) / 6 = 67%.
        val p = ProgressState(seen = mapOf(1 to SeenStat(4, 1), 2 to SeenStat(2, 1)))
        val rights = Stats.topicStats(bank, p).first { it.key == "rights" }
        assertEquals(67, rights.accuracy)
        assertEquals("67%", rights.accuracyText)
        assertEquals(4, rights.total)
        assertEquals(2, rights.done)
        assertEquals(50, rights.coveragePct)
        assertTrue("below 70 percent counts as weakish", rights.weakish)
    }

    @Test
    fun an_untouched_topic_shows_an_em_dash_not_zero_percent() {
        val history = Stats.topicStats(bank, ProgressState()).first { it.key == "history" }
        assertEquals("—", history.accuracyText)
        assertEquals(0, history.done)
        assertEquals(0, history.coveragePct)
        assertFalse("an untouched topic is not weakish", history.weakish)
    }

    @Test
    fun topic_stats_are_returned_in_the_designs_topic_order() {
        assertEquals(Topics.all.map { it.key }, Stats.topicStats(bank, ProgressState()).map { it.key })
    }

    @Test
    fun a_topic_absent_from_the_bank_reports_zero_rather_than_dividing_by_zero() {
        val economy = Stats.topicStats(bank, ProgressState()).first { it.key == "economy" }
        assertEquals(0, economy.total)
        assertEquals(0, economy.coveragePct)
    }

    // ---- streak ---------------------------------------------------------

    @Test
    fun first_ever_answer_starts_the_streak_at_one() {
        val out = Stats.registerAnswer(ProgressState(), 1, correct = true, today = today, yesterday = yesterday)
        assertEquals(1, out.streak)
        assertEquals(today, out.lastDay)
        assertEquals(1, out.best)
    }

    @Test
    fun answering_on_a_consecutive_day_extends_the_streak() {
        val p = ProgressState(streak = 4, best = 4, lastDay = yesterday)
        val out = Stats.registerAnswer(p, 1, correct = true, today = today, yesterday = yesterday)
        assertEquals(5, out.streak)
        assertEquals(5, out.best)
    }

    @Test
    fun answering_after_a_gap_resets_the_streak_but_keeps_the_best() {
        val p = ProgressState(streak = 9, best = 14, lastDay = "2026-08-10")
        val out = Stats.registerAnswer(p, 1, correct = true, today = today, yesterday = yesterday)
        assertEquals(1, out.streak)
        assertEquals("best is a high-water mark", 14, out.best)
    }

    @Test
    fun answering_twice_in_one_day_does_not_double_the_streak() {
        val once = Stats.registerAnswer(
            ProgressState(streak = 3, best = 3, lastDay = yesterday), 1,
            correct = true, today = today, yesterday = yesterday,
        )
        val twice = Stats.registerAnswer(once, 2, correct = true, today = today, yesterday = yesterday)
        assertEquals(4, twice.streak)
        assertEquals(2, twice.answered)
    }

    // ---- goal, counters, weak list --------------------------------------

    @Test
    fun a_stale_goal_date_resets_the_daily_count() {
        val p = ProgressState(goalDone = 17, goalDate = yesterday)
        val out = Stats.registerAnswer(p, 1, correct = true, today = today, yesterday = yesterday)
        assertEquals(1, out.goalDone)
        assertEquals(today, out.goalDate)
    }

    @Test
    fun a_wrong_answer_increments_seen_and_missed_but_not_correct() {
        val out = Stats.registerAnswer(ProgressState(), 1, correct = false, today = today, yesterday = yesterday)
        assertEquals(1, out.answered)
        assertEquals(0, out.correct)
        assertEquals(1, out.seen.getValue(1).s)
        assertEquals(1, out.seen.getValue(1).m)
    }

    @Test
    fun missing_a_question_twice_puts_it_on_the_weak_list() {
        var p = ProgressState()
        p = Stats.registerAnswer(p, 1, correct = false, today = today, yesterday = yesterday)
        assertEquals(emptyList<Int>(), StudyEngine.weakIds(p))
        p = Stats.registerAnswer(p, 1, correct = false, today = today, yesterday = yesterday)
        assertEquals(listOf(1), StudyEngine.weakIds(p))
    }

    // ---- mock submission ------------------------------------------------

    @Test
    fun submitting_a_mock_records_the_percentage_and_folds_in_every_answer() {
        val ids = listOf(1, 2, 3, 4)
        // The bank's answer is index 1, so marks on 1 and 2 are right.
        val marks = mapOf(1 to 1, 2 to 1, 3 to 0, 4 to 2)
        val (out, right) = Stats.registerMock(ProgressState(), ids, marks, byId, today, yesterday)
        assertEquals(2, right)
        assertEquals(listOf(MockAttempt(50, today)), out.mocks)
        assertEquals(4, out.answered)
        assertEquals(2, out.correct)
        assertEquals(0, out.seen.getValue(1).m)
        assertEquals(1, out.seen.getValue(3).m)
    }

    @Test
    fun an_unanswered_mock_question_counts_as_missed() {
        val (out, right) = Stats.registerMock(ProgressState(), listOf(1, 2), mapOf(1 to 1), byId, today, yesterday)
        assertEquals(1, right)
        assertEquals(1, out.seen.getValue(2).m)
    }

    @Test
    fun a_mock_also_advances_the_streak() {
        val p = ProgressState(streak = 2, best = 2, lastDay = yesterday)
        val (out, _) = Stats.registerMock(p, listOf(1), mapOf(1 to 1), byId, today, yesterday)
        assertEquals(3, out.streak)
    }

    // ---- milestones -----------------------------------------------------

    @Test
    fun milestones_unlock_at_the_designs_thresholds() {
        assertEquals(emptySet<String>(), Stats.achievements(bank, ProgressState()))

        val p = ProgressState(
            answered = 200, correct = 150, streak = 7, best = 7,
            mocks = listOf(MockAttempt(70, "2026-08-10"), MockAttempt(80, "2026-08-14")),
            seen = bank.associate { it.id to SeenStat(1, 0) },
        )
        val got = Stats.achievements(bank, p)
        assertTrue(got.containsAll(setOf("first", "s3", "q50", "s7", "q200", "mock1", "pass")))
        assertFalse("only two of seven topics exist in this test bank", "all7" in got)
    }

    @Test
    fun a_mock_below_seventy_five_percent_does_not_unlock_pass() {
        val p = ProgressState(answered = 20, mocks = listOf(MockAttempt(70, "2026-08-10")))
        assertTrue("mock1" in Stats.achievements(bank, p))
        assertFalse("pass" in Stats.achievements(bank, p))
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
