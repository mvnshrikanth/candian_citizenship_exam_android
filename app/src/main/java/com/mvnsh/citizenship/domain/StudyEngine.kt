package com.mvnsh.citizenship.domain

import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.Question
import kotlin.random.Random

/** Builds practice and mock sessions, and decides the order options are shown in. */
object StudyEngine {

    enum class Mode { CONTINUE, QUICK, ALL, WEAK, MARKS, UNSEEN, TOPIC, MOCK }

    const val SESSION_CAP = 20
    const val QUICK_SIZE = 10
    const val MOCK_SIZE = 20
    const val MOCK_SECONDS = 1800
    const val MOCK_PASS_CORRECT = 15

    /** A question joins the weak list once it has been missed this many times. */
    const val WEAK_THRESHOLD = 2

    /**
     * Options whose text refers to the other options ("Both A and B", "None of the
     * above") stop making sense if the order changes, so those questions are never
     * rotated.
     */
    private val POSITIONAL = Regex("both|none of|all of|above", RegexOption.IGNORE_CASE)

    /**
     * Display order for a question's options, as source indices.
     *
     * Rotating by `id % optionCount` keeps the correct answer off any one fixed slot
     * while staying stable for a given question, so a repeat looks the same as before.
     */
    fun optionOrder(q: Question): List<Int> {
        val n = q.options.size
        if (n == 0) return emptyList()
        if (q.options.any { POSITIONAL.containsMatchIn(it) }) return q.options.indices.toList()
        val r = q.id % n
        return q.options.indices.map { (it + r) % n }
    }

    fun weakIds(progress: ProgressState): List<Int> =
        progress.seen.filterValues { it.m >= WEAK_THRESHOLD }.keys.sorted()

    fun buildSession(
        mode: Mode,
        bank: List<Question>,
        progress: ProgressState,
        topicKey: String? = null,
        random: Random = Random,
    ): List<Int> {
        // Stored progress can outlive the bank it was recorded against, so anything
        // sourced from progress rather than the bank is filtered back through it.
        val present = bank.mapTo(HashSet()) { it.id }
        return when (mode) {
            Mode.CONTINUE -> bank.map { it.id }.take(SESSION_CAP)
            Mode.QUICK -> sample(bank.map { it.id }, QUICK_SIZE, random)
            Mode.ALL -> bank.map { it.id }
            Mode.WEAK -> weakIds(progress).filter { it in present }
            Mode.MARKS -> progress.bookmarks.filter { it in present }
            Mode.UNSEEN -> bank.filter { it.id !in progress.seen }.map { it.id }.take(SESSION_CAP)
            Mode.TOPIC -> bank.filter { it.topic == topicKey }.map { it.id }.take(SESSION_CAP)
            Mode.MOCK -> sample(bank.map { it.id }, MOCK_SIZE, random)
        }
    }

    /** Session labels, verbatim from the design. */
    fun label(mode: Mode, topicKey: String? = null): String = when (mode) {
        Mode.CONTINUE -> "Continue"
        Mode.QUICK -> "Quick practice"
        Mode.ALL -> "All questions"
        Mode.WEAK -> "Weak questions"
        Mode.MARKS -> "Bookmarked"
        Mode.UNSEEN -> "New questions"
        Mode.TOPIC -> Topics.name(topicKey.orEmpty())
        Mode.MOCK -> "Mock test"
    }

    private fun sample(ids: List<Int>, n: Int, random: Random): List<Int> =
        ids.shuffled(random).take(n)
}
