package com.mvnsh.citizenship.data

import android.content.Context
import com.mvnsh.citizenship.data.model.Explanation
import com.mvnsh.citizenship.data.model.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Loads the bundled bank. There is no network path: both files are assets. */
class BankRepository(private val context: Context) {

    data class Bank(
        val questions: List<Question>,
        val byId: Map<Int, Question>,
        val explanations: Map<Int, Explanation>,
    ) {
        val size: Int get() = questions.size
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: Bank? = null

    suspend fun load(): Bank = cached ?: withContext(Dispatchers.IO) {
        val questions: List<Question> = json.decodeFromString(read("bank.json"))

        // Explanations are authored for only a handful of questions. A missing or
        // unreadable file is not fatal: the quiz shows its "Answer" panel instead.
        val explanations: Map<Int, Explanation> = runCatching {
            json.decodeFromString<Map<String, Explanation>>(read("explanations.json"))
                .mapKeys { it.key.toInt() }
        }.getOrElse { emptyMap() }

        Bank(questions, questions.associateBy { it.id }, explanations).also { cached = it }
    }

    private fun read(name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }
}
