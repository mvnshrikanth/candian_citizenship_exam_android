package com.mvnsh.citizenship.domain

/**
 * The seven topics, adopted from the upstream bank's own categories.
 *
 * The design named a different seven, built around the nine compound categories upstream
 * used at the time - including an Indigenous topic. Upstream has since replaced both the
 * questions and the taxonomy, so these keys mirror `category` in questions.json one for
 * one and are produced by tools/build_bank.py. Indigenous is gone because the data no
 * longer carries it; Law and Justice is new for the same reason. Adding a topic here
 * without a matching category in TOPIC_BY_CATEGORY would show an empty row forever.
 *
 * Ordered civic, then historical, then practical - the shape the design read in.
 */
object Topics {

    data class Topic(val key: String, val name: String, val blurb: String)

    val all: List<Topic> = listOf(
        Topic("rights", "Rights and Responsibilities", "The Charter, the Oath, and what citizenship asks"),
        Topic("law", "Law and Justice", "The rule of law, the courts and policing"),
        Topic("history", "Canada's History", "Confederation, the wars, milestones"),
        Topic("symbols", "Identity and Symbols", "Flag, anthem, sport and the arts"),
        Topic("government", "Government and Elections", "Parliament, voting and how power is held"),
        Topic("economy", "Economy", "Trade, industry and the regions"),
        Topic("geography", "Geography and Regions", "Provinces, capitals, landscapes"),
    )

    private val byKey: Map<String, Topic> = all.associateBy { it.key }

    val keys: Set<String> = byKey.keys

    fun find(key: String): Topic? = byKey[key]

    /** Falls back to the raw key, matching the design's topicName(). */
    fun name(key: String): String = byKey[key]?.name ?: key
}
