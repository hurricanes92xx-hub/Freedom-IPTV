package com.hurricanes92xx.freedomiptv.sports.matching

import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamLiveChannel
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamProgramme
import com.hurricanes92xx.freedomiptv.sports.model.ChannelCandidate

/** Explainable lineup matcher. A channel/network match is never treated as proof of a specific event. */
object ChannelMatcher {
    private val aliases = mapOf(
        "espn" to setOf("espn", "espn us", "espn hd", "espn fhd"),
        "espn 2" to setOf("espn 2", "espn2"),
        "espnu" to setOf("espnu", "espn u"),
        "espn plus" to setOf("espn plus", "espn+"),
        "fs1" to setOf("fs1", "fox sports 1", "foxsports 1"),
        "fs2" to setOf("fs2", "fox sports 2", "foxsports 2"),
        "btn" to setOf("btn", "big ten network"),
        "sec network" to setOf("sec network", "sec"),
        "acc network" to setOf("acc network", "accn"),
        "tnt" to setOf("tnt", "tnt sports"),
        "tbs" to setOf("tbs"),
        "cbs sports" to setOf("cbs sports network", "cbssn", "cbs sports"),
        "nbc sports" to setOf("nbc sports network", "nbcsn", "nbc sports"),
        "abc" to setOf("abc"),
        "fox" to setOf("fox", "fox network")
    )

    fun normalize(value: String): String = value.lowercase()
        .replace("+", " plus ")
        .replace(Regex("\\b(4k|uhd|fhd|hd|sd|east|west)\\b"), " ")
        .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

    fun match(channels: List<XtreamLiveChannel>, network: String, eventTerms: List<String> = emptyList()): List<ChannelCandidate> {
        val key = normalize(network)
        val accepted = (aliases[key] ?: setOf(key)).map(::normalize).toSet()
        return channels.mapNotNull { channel ->
            val name = normalize(channel.name)
            val networkExact = name in accepted || accepted.any { name == it }
            val networkBoundary = accepted.any { alias -> (name.startsWith(alias + " ") && name.removePrefix(alias + " ").firstOrNull()?.isDigit() != true) || name.endsWith(" " + alias) || name.contains(" " + alias + " ") }
            val hits = eventTerms.map(::normalize).filter { it.length >= 3 && name.contains(it) }.distinct()
            var score = when { networkExact -> 90; networkBoundary -> 72; else -> 0 }
            val reasons = mutableListOf<String>()
            if (networkExact) reasons += "Exact network/alias"
            else if (networkBoundary) reasons += "Network alias"
            if (hits.isNotEmpty()) { score += minOf(10, hits.size * 5); reasons += "Channel-name terms: ${hits.joinToString()}" }
            if (Regex("\\b(sports|sport|event)\\b").containsMatchIn(name)) { score += 3; reasons += "Generic sports label" }
            if (score == 0) null else ChannelCandidate(channel.id, channel.name, score.coerceAtMost(100), reasons)
        }.sortedByDescending { it.confidence }.take(8)
    }

    /** Strong only when EPG title and event time overlap; network availability alone is not confirmed. */
    fun verifyEpg(programmes: List<XtreamProgramme>, eventTerms: List<String>, eventStartUtcMillis: Long, toleranceMinutes: Long = 180): EpgMatch {
        val terms = eventTerms.map(::normalize).filter { it.length >= 3 }.distinct()
        val target = eventStartUtcMillis / 1000
        val rows = programmes.filter { it.startEpochSeconds <= target + toleranceMinutes * 60 && it.endEpochSeconds >= target - toleranceMinutes * 60 }
        val matched = rows.firstOrNull { row ->
            val title = normalize(row.title + " " + row.description)
            terms.isNotEmpty() && terms.any(title::contains)
        }
        return if (matched != null) EpgMatch(EpgConfidence.CONFIRMED, matched.title, matched.startEpochSeconds, "EPG title matches event near scheduled time")
        else if (rows.isNotEmpty()) EpgMatch(EpgConfidence.TIME_ONLY, rows.first().title, rows.first().startEpochSeconds, "EPG overlaps event time; title does not identify event")
        else EpgMatch(EpgConfidence.UNVERIFIED, null, null, "No EPG programme overlaps event window")
    }
}

enum class EpgConfidence { CONFIRMED, TIME_ONLY, UNVERIFIED }
data class EpgMatch(val confidence: EpgConfidence, val programmeTitle: String?, val programmeStartEpochSeconds: Long?, val explanation: String)
