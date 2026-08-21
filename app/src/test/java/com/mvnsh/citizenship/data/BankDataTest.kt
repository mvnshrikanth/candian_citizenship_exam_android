package com.mvnsh.citizenship.data

import com.mvnsh.citizenship.data.model.Explanation
import com.mvnsh.citizenship.data.model.Question
import com.mvnsh.citizenship.domain.Topics
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs on the JVM against a copy of the shipped assets, so the bank is validated
 * without an emulator. The count is a deliberate regression guard: if the upstream
 * bank legitimately changes size, update it here and remember the two UI strings
 * that quote it are formatted from the real size, not hardcoded.
 */
class BankDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun res(name: String): String =
        checkNotNull(javaClass.classLoader!!.getResourceAsStream(name)) {
            "$name missing from test resources"
        }.bufferedReader().readText()

    private val bank: List<Question> = json.decodeFromString(res("bank.json"))
    private val explanations: Map<String, Explanation> = json.decodeFromString(res("explanations.json"))

    @Test
    fun bank_has_535_questions() {
        assertEquals(535, bank.size)
    }

    @Test
    fun every_question_has_four_options_and_an_in_range_answer() {
        bank.forEach { q ->
            assertEquals("q${q.id} option count", 4, q.options.size)
            assertTrue("q${q.id} answer out of range: ${q.answer}", q.answer in q.options.indices)
            assertTrue("q${q.id} has blank text", q.question.isNotBlank())
            assertTrue("q${q.id} has a blank option", q.options.none { it.isBlank() })
        }
    }

    @Test
    fun ids_are_unique() {
        val ids = bank.map { it.id }
        assertEquals("ids must be unique", ids.size, ids.toSet().size)
    }

    @Test
    fun question_identity_is_the_id_and_nothing_keys_on_position() {
        // The bank used to run 1..502 with 39 absent, and a test asserted that gap so
        // nobody "tidied" it away and hid the id != index hazard. Upstream renumbered:
        // ids are now contiguous 1..535, so that guard is gone rather than weakened.
        //
        // The hazard it guarded has not gone anywhere. Bookmarks, seen records, sessions
        // and mock marks are all keyed by id and survive in DataStore across upgrades, so
        // an id must keep meaning one question. This asserts the ids are a set of distinct
        // positive numbers and leaves it at that - contiguity is not something to rely on.
        assertTrue("ids must be positive", bank.all { it.id > 0 })
        assertEquals("ids must be unique", bank.size, bank.map { it.id }.toSet().size)
    }

    @Test
    fun every_question_has_a_topic_from_the_taxonomy() {
        // Topics are mapped from the upstream category by tools/build_bank.py, which
        // aborts on an unknown one. A topic outside this set means Topics.kt and the
        // script's TOPIC_BY_CATEGORY have drifted apart.
        bank.forEach { q ->
            assertTrue("q${q.id} has topic '${q.topic}' outside ${Topics.keys}", q.topic in Topics.keys)
        }
    }

    @Test
    fun all_seven_topics_are_populated() {
        val counts = bank.groupingBy { it.topic }.eachCount()
        Topics.all.forEach { t ->
            assertTrue("topic ${t.key} is empty", (counts[t.key] ?: 0) > 0)
        }
    }

    @Test
    fun explanation_keys_all_reference_real_questions() {
        val ids = bank.map { it.id }.toSet()
        explanations.keys.forEach { k ->
            val id = k.toIntOrNull()
            assertTrue("explanation key '$k' is not an int", id != null)
            assertTrue("explanation $id has no question", id in ids)
        }
    }

    @Test
    fun every_question_now_carries_an_explanation() {
        // Upstream authors why/tip for all of them; it used to be 11 of 501. The quiz's
        // "not written yet" panel is therefore unreachable from the shipped data - it is
        // kept as a fallback because the asset is the only thing guaranteeing this, and
        // OptionStyleTest still covers the branch.
        assertEquals(bank.size, explanations.size)
        assertTrue(
            "an explanation was written but left blank",
            explanations.values.all { it.why.isNotBlank() },
        )
    }
}
