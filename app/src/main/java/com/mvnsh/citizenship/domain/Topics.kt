package com.mvnsh.citizenship.domain

/** The seven topics, verbatim from the design's TOPICS constant and in its order. */
object Topics {

    data class Topic(val key: String, val name: String, val blurb: String)

    val all: List<Topic> = listOf(
        Topic("rights", "Rights & Responsibilities", "Rule of law, the Charter, the Oath"),
        Topic("indigenous", "Indigenous Peoples", "First Nations, Inuit and Métis histories"),
        Topic("history", "Canadian History", "Confederation, the wars, milestones"),
        Topic("symbols", "Symbols & Modern Canada", "Flag, anthem, sport and the arts"),
        Topic("government", "Government", "Parliament, elections, the courts"),
        Topic("economy", "Economy", "Trade, industry and the regions"),
        Topic("geography", "Geography", "Provinces, capitals, landscapes"),
    )

    private val byKey: Map<String, Topic> = all.associateBy { it.key }

    val keys: Set<String> = byKey.keys

    fun find(key: String): Topic? = byKey[key]

    /** Falls back to the raw key, matching the design's topicName(). */
    fun name(key: String): String = byKey[key]?.name ?: key
}
