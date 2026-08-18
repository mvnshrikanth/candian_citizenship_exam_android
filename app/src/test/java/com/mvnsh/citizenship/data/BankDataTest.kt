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
    fun bank_has_501_questions() {
        assertEquals(501, bank.size)
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
    fun ids_are_unique_and_must_not_be_assumed_contiguous() {
        val ids = bank.map { it.id }
        assertEquals("ids must be unique", ids.size, ids.toSet().size)
        // As shipped the bank runs 1..502 with 39 absent, so id != index. Anything keyed
        // by list position would silently shift for every question after 38. This asserts
        // the gap still exists so nobody "tidies" the data and hides the hazard.
        assertTrue("ids are expected to be sparse", ids.max() - ids.min() + 1 > ids.size)
    }

    @Test
    fun question_4_answer_key_is_corrected_to_1982() {
        val q = bank.first { it.id == 4 }
        assertEquals("1982", q.options[q.answer])
    }

    @Test
    fun every_question_has_a_topic_from_the_designs_taxonomy() {
        // The taxonomy is joined in from the design's bank.json at build time, so a
        // blank or unknown topic means tools/build_bank.py silently missed a row.
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
    fun an_explanation_free_question_is_a_supported_state() {
        // q4 has no authored explanation; the quiz screen renders its "Answer" panel
        // instead of "Why". If this ever becomes false the test is fine to delete, but
        // the no-explanation branch still has to stay reachable.
        assertTrue("q4 unexpectedly has an explanation", "4" !in explanations.keys)
    }
}
