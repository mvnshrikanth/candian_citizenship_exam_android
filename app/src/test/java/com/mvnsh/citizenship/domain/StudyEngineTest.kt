package com.mvnsh.citizenship.domain

import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.Question
import com.mvnsh.citizenship.data.model.SeenStat
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyEngineTest {

    private fun q(id: Int, topic: String = "history", options: List<String> = listOf("a", "b", "c", "d")) =
        Question(id = id, topic = topic, question = "q$id", options = options, answer = 0)

    private val bank = (1..40).map { q(it, if (it <= 10) "rights" else "history") }
    private val bankIds = bank.map { it.id }.toSet()

    @Test
    fun quick_session_is_ten_unique_questions_from_the_bank() {
        val ids = StudyEngine.buildSession(StudyEngine.Mode.QUICK, bank, ProgressState(), random = Random(7))
        assertEquals(10, ids.size)
        assertEquals("must not repeat a question", 10, ids.toSet().size)
        assertTrue(ids.all { it in bankIds })
    }

    @Test
    fun all_session_is_the_whole_bank_in_order() {
        assertEquals(bank.map { it.id }, StudyEngine.buildSession(StudyEngine.Mode.ALL, bank, ProgressState()))
    }

    @Test
    fun continue_session_is_the_first_twenty_in_order() {
        assertEquals((1..20).toList(), StudyEngine.buildSession(StudyEngine.Mode.CONTINUE, bank, ProgressState()))
    }

    @Test
    fun weak_session_is_questions_missed_twice_or_more() {
        val p = ProgressState(seen = mapOf(1 to SeenStat(3, 2), 2 to SeenStat(5, 1), 3 to SeenStat(2, 4)))
        assertEquals(listOf(1, 3), StudyEngine.buildSession(StudyEngine.Mode.WEAK, bank, p))
    }

    @Test
    fun weak_session_drops_ids_that_are_no_longer_in_the_bank() {
        // A shrunk upstream bank must not crash a session built from stored progress.
        val p = ProgressState(seen = mapOf(1 to SeenStat(3, 2), 9999 to SeenStat(3, 3)))
        assertEquals(listOf(1), StudyEngine.buildSession(StudyEngine.Mode.WEAK, bank, p))
    }

    @Test
    fun unseen_session_skips_answered_questions_and_caps_at_twenty() {
        val p = ProgressState(seen = (1..5).associateWith { SeenStat(1, 0) })
        assertEquals((6..25).toList(), StudyEngine.buildSession(StudyEngine.Mode.UNSEEN, bank, p))
    }

    @Test
    fun topic_session_is_capped_at_twenty_of_that_topic() {
        assertEquals(
            (1..10).toList(),
            StudyEngine.buildSession(StudyEngine.Mode.TOPIC, bank, ProgressState(), topicKey = "rights"),
        )
    }

    @Test
    fun bookmark_session_preserves_bookmark_order() {
        val p = ProgressState(bookmarks = listOf(7, 2, 30))
        assertEquals(listOf(7, 2, 30), StudyEngine.buildSession(StudyEngine.Mode.MARKS, bank, p))
    }

    @Test
    fun mock_session_is_twenty_unique_questions() {
        val ids = StudyEngine.buildSession(StudyEngine.Mode.MOCK, bank, ProgressState(), random = Random(1))
        assertEquals(20, ids.size)
        assertEquals(20, ids.toSet().size)
    }

    @Test
    fun an_empty_source_yields_an_empty_session() {
        assertEquals(emptyList<Int>(), StudyEngine.buildSession(StudyEngine.Mode.WEAK, bank, ProgressState()))
        assertEquals(emptyList<Int>(), StudyEngine.buildSession(StudyEngine.Mode.MARKS, bank, ProgressState()))
    }

    @Test
    fun a_sample_larger_than_the_bank_returns_what_exists() {
        val tiny = bank.take(3)
        assertEquals(3, StudyEngine.buildSession(StudyEngine.Mode.MOCK, tiny, ProgressState()).size)
    }

    @Test
    fun option_order_rotates_deterministically_by_id() {
        // Rotating by id % optionCount keeps the correct answer off one fixed slot while
        // staying stable for a given question across sessions.
        assertEquals(listOf(1, 2, 3, 0), StudyEngine.optionOrder(q(1)))
        assertEquals(listOf(2, 3, 0, 1), StudyEngine.optionOrder(q(2)))
        assertEquals(listOf(0, 1, 2, 3), StudyEngine.optionOrder(q(4)))
        assertEquals(StudyEngine.optionOrder(q(17)), StudyEngine.optionOrder(q(17)))
    }

    @Test
    fun option_order_is_left_alone_when_an_option_refers_to_the_others() {
        // "Both A and B" only means anything if A and B stayed where they were.
        listOf("Both A and B", "None of the above", "All of these", "none of them").forEach { text ->
            val question = q(3, options = listOf("x", "y", text, "z"))
            assertEquals("must not shuffle '$text'", listOf(0, 1, 2, 3), StudyEngine.optionOrder(question))
        }
    }

    @Test
    fun session_labels_match_the_design() {
        assertEquals("Quick practice", StudyEngine.label(StudyEngine.Mode.QUICK))
        assertEquals("All questions", StudyEngine.label(StudyEngine.Mode.ALL))
        assertEquals("Weak questions", StudyEngine.label(StudyEngine.Mode.WEAK))
        assertEquals("Bookmarked", StudyEngine.label(StudyEngine.Mode.MARKS))
        assertEquals("New questions", StudyEngine.label(StudyEngine.Mode.UNSEEN))
        assertEquals("Mock test", StudyEngine.label(StudyEngine.Mode.MOCK))
        assertEquals("Economy", StudyEngine.label(StudyEngine.Mode.TOPIC, "economy"))
    }
}
