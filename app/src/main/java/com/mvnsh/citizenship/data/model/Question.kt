package com.mvnsh.citizenship.data.model

import kotlinx.serialization.Serializable

/**
 * One bank question. [id] is the identity everywhere in the app: the bank's ids are
 * sparse (1..502 with 39 absent), so id never equals list position.
 */
@Serializable
data class Question(
    val id: Int,
    val topic: String,
    val category: String = "",
    val difficulty: String = "",
    val question: String,
    val options: List<String>,
    val answer: Int,
)

/** Authored commentary for a question. Most questions have none; that is a supported state. */
@Serializable
data class Explanation(
    val why: String = "",
    val tip: String = "",
)
