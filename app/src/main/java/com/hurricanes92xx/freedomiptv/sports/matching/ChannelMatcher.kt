package com.hurricanes92xx.freedomiptv.sports.matching

import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamLiveChannel
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamProgramme
import com.hurricanes92xx.freedomiptv.sports.model.ChannelCandidate
import com.hurricanes92xx.freedomiptv.sports.model.Game

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


    /** Playlist index created once per catalog refresh; match many games without renormalizing every channel. */
    class GameIndex(channels: List<XtreamLiveChannel>) {
        private data class Indexed(val channel: XtreamLiveChannel, val name: String, val tokens: Set<String>)
        private val rows = channels.map { Indexed(it, normalize(it.name), normalize(it.name).split(' ').filter { t -> t.length >= 2 }.toSet()) }
        private val byToken = rows.flatMap { row -> row.tokens.map { it to row } }.groupBy({ it.first }, { it.second })

        fun matchGame(game: Game, networks: List<String> = emptyList(),
                       epgByChannel: Map<String, List<XtreamProgramme>> = emptyMap(), limit: Int = 8): List<ChannelCandidate> {
            val home = teamTerms(game.home.name, game.home.shortName)
            val away = teamTerms(game.away.name, game.away.shortName)
            val terms = (home + away).filter { it.length >= 3 }.distinct()
            val networkCandidates = networks.flatMap { networkRows(normalize(it)) }.toSet()
            val lookupTokens = terms.flatMap { it.split(' ') }.filter { it.length >= 3 }.distinct()
            val candidates = lookupTokens.flatMap { byToken[it].orEmpty() }.toMutableSet().apply { addAll(networkCandidates) }
            return candidates.mapNotNull { row ->
                val h = home.any { containsWhole(row.name, it) }
                val a = away.any { containsWhole(row.name, it) }
                val both = h && a
                val matchupLabel = both && listOf(" vs ", " at ", " @ ", " - ").any(row.name::contains)
                val epgRows = epgByChannel[row.channel.id].orEmpty().filter { it.startEpochSeconds <= game.startUtcMillis / 1000 + 180 * 60 && it.endEpochSeconds >= game.startUtcMillis / 1000 - 180 * 60 }
                val epgMatch = epgRows.firstOrNull { p -> val title = normalize(p.title + " " + p.description); home.any { containsWhole(title,it) } && away.any { containsWhole(title,it) } }
                val epg = if (epgMatch != null) EpgMatch(EpgConfidence.CONFIRMED,epgMatch.title,epgMatch.startEpochSeconds,"Both teams in EPG near game time") else if (epgRows.isNotEmpty()) EpgMatch(EpgConfidence.TIME_ONLY,epgRows.first().title,epgRows.first().startEpochSeconds,"EPG overlaps game window only") else EpgMatch(EpgConfidence.UNVERIFIED,null,null,"No EPG overlap")
                var score = when { matchupLabel -> 98; both -> 92; epg.confidence == EpgConfidence.CONFIRMED -> 88; h || a -> 48; row in networkCandidates -> 35; else -> 0 }
                val reasons = mutableListOf<String>()
                if (both) reasons += "Both game teams in channel name"
                if (matchupLabel) reasons += "Matchup-style channel label"
                if (epg.confidence == EpgConfidence.CONFIRMED) { score = maxOf(score,96); reasons += "EPG matchup + event time" }
                else if (epg.confidence == EpgConfidence.TIME_ONLY) reasons += "EPG time overlap only (not confirmed)"
                if (row in networkCandidates) reasons += "Broadcast network fallback"
                if (score == 0) null else ChannelCandidate(row.channel.id,row.channel.name,score,reasons.distinct())
            }.sortedByDescending { it.confidence }.take(limit.coerceIn(1,20))
        }

        private fun networkRows(key: String): List<Indexed> {
            if (key.isBlank()) return emptyList()
            val accepted = (aliases[key] ?: setOf(key)).map(::normalize)
            return rows.filter { row -> accepted.any { row.name == it || row.name.startsWith("$it ") || row.name.contains(" $it ") || row.name.endsWith(" $it") } }
        }
    }

    private fun teamTerms(name: String, shortName: String?): List<String> =
        listOfNotNull(name, shortName).map(::normalize).filter { it.length >= 3 }.distinct()

    private fun containsWhole(value: String, term: String): Boolean =
        value == term || value.startsWith("$term ") || value.endsWith(" $term") || value.contains(" $term ")

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
