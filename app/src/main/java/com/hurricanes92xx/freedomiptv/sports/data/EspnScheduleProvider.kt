package com.hurricanes92xx.freedomiptv.sports.data

import com.hurricanes92xx.freedomiptv.sports.model.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * Public ESPN scoreboard adapter. Schedules are fetched by UTC date and cached by SportsHubCache/UI.
 * Feed availability varies by competition; unsupported competitions return an empty list gracefully.
 */
class EspnScheduleProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS).build()
) : SportsProvider {
    override val id: String = "espn"
    override suspend fun teams(competition: Competition): List<Team> = emptyList()

    private data class Feed(val sport: String, val league: String)
    private val feeds = mapOf(
        "nfl" to Feed("football", "nfl"),
        "ncaaf-fbs" to Feed("football", "college-football"),
        "ncaaf-fcs" to Feed("football", "college-football"),
        "ncaam-d1" to Feed("basketball", "mens-college-basketball"),
        "ncaaw-d1" to Feed("basketball", "womens-college-basketball"),
        "ncaavb-w" to Feed("volleyball", "womens-college-volleyball"),
        "ncaabase" to Feed("baseball", "college-baseball"),
        "ncaagym-w" to Feed("gymnastics", "womens-college-gymnastics"),
        "nba" to Feed("basketball", "nba"), "wnba" to Feed("basketball", "wnba"),
        "mlb" to Feed("baseball", "mlb"), "nhl" to Feed("hockey", "nhl"),
        "mls" to Feed("soccer", "usa.1"), "epl" to Feed("soccer", "eng.1"),
        "ucl" to Feed("soccer", "uefa.champions")
    )

    override suspend fun schedule(competition: Competition, fromUtcMillis: Long, toUtcMillis: Long): List<Game> = withContext(Dispatchers.IO) {
        val feed = feeds[competition.id] ?: return@withContext emptyList()
        if (toUtcMillis < fromUtcMillis) return@withContext emptyList()
        val start = Instant.ofEpochMilli(fromUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val end = Instant.ofEpochMilli(toUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
        kotlinx.coroutines.coroutineScope {
            dates.chunked(4).flatMap { batch -> batch.map { date -> async(Dispatchers.IO) {
                val day = date.format(DateTimeFormatter.BASIC_ISO_DATE)
                val url = "https://site.api.espn.com/apis/site/v2/sports/${feed.sport}/${feed.league}/scoreboard?dates=$day"
                runCatching { fetch(url, competition.id) }.getOrDefault(emptyList())
            } }.awaitAll().flatten() }
        }.filter { it.startUtcMillis in fromUtcMillis..toUtcMillis }.distinctBy { it.id }.sortedBy { it.startUtcMillis }
    }

    override suspend fun liveScores(competition: Competition): List<Game> = withContext(Dispatchers.IO) {
        val feed = feeds[competition.id] ?: return@withContext emptyList()
        val url = "https://site.api.espn.com/apis/site/v2/sports/${feed.sport}/${feed.league}/scoreboard"
        runCatching { fetch(url, competition.id) }.getOrDefault(emptyList()).filter { it.state == GameState.LIVE }
    }

    private fun fetch(url: String, competitionId: String): List<Game> {
        val request = Request.Builder().url(url).header("User-Agent", "FreedomIPTV/1.0 (sports schedules)").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val root = JSONObject(response.body?.string().orEmpty())
            val events = root.optJSONArray("events") ?: return emptyList()
            return buildList {
                for (i in 0 until events.length()) {
                    val event = events.optJSONObject(i) ?: continue
                    val comp = event.optJSONArray("competitions")?.optJSONObject(0) ?: continue
                    val competitors = comp.optJSONArray("competitors") ?: continue
                    if (competitors.length() < 2) continue
                    val homeObj = (0 until competitors.length()).mapNotNull { competitors.optJSONObject(it) }.firstOrNull { it.optString("homeAway") == "home" } ?: competitors.optJSONObject(0)
                    val awayObj = (0 until competitors.length()).mapNotNull { competitors.optJSONObject(it) }.firstOrNull { it.optString("homeAway") == "away" } ?: competitors.optJSONObject(1)
                    val home = team(homeObj, competitionId); val away = team(awayObj, competitionId)
                    val start = runCatching { Instant.parse(event.optString("date")).toEpochMilli() }.getOrDefault(0L)
                    if (start <= 0L) continue
                    val status = comp.optJSONObject("status")?.optJSONObject("type")
                    val seasonType = event.optJSONObject("season")?.optJSONObject("type")
                    val seasonName = seasonType?.optString("name").orEmpty()
                    val postseason = seasonType?.optInt("type", 0) == 3 || seasonName.contains("post", true) || seasonName.contains("playoff", true)
                    val eventName = event.optString("name")
                    val competitionNotes = comp.optJSONArray("notes")?.let { notes ->
                        (0 until notes.length()).mapNotNull { notes.optJSONObject(it)?.optString("headline")?.takeIf(String::isNotBlank) }.joinToString(" ")
                    }.orEmpty()
                    val competitionType = comp.optJSONObject("type")?.let { type ->
                        listOf(type.optString("abbreviation"), type.optString("text"), type.optString("name"))
                            .filter(String::isNotBlank).distinct().joinToString(" ")
                    }.orEmpty()
                    val roundSources = listOf(eventName, competitionNotes, competitionType)
                    val roundText = (roundSources + seasonName).firstOrNull { text ->
                        Regex("(?i)(first four|sweet sixteen|elite eight|final four|quarterfinal|semifinal|semi-final|championship|\\bowl\\b|round\\s*[0-9ivx]+|regional|super regional)").containsMatchIn(text)
                    }
                    val playoffRound = roundSources.firstOrNull { text ->
                        Regex("(?i)(first round|wild card|divisional|conference championship|quarterfinal|semifinal|semi-final|championship|national championship|round\\s*[0-9ivx]+|regional|super regional)").containsMatchIn(text)
                    }
                    val round = if (postseason) (playoffRound ?: roundText)?.trim()?.take(80) ?: seasonName.ifBlank { "Postseason" } else null
                    val state = when (status?.optString("name")?.lowercase()) {
                        "STATUS_IN_PROGRESS", "STATUS_HALFTIME", "STATUS_END_PERIOD" -> GameState.LIVE
                        "STATUS_FINAL", "STATUS_FULL_TIME" -> GameState.FINAL
                        "STATUS_POSTPONED" -> GameState.POSTPONED
                        "STATUS_CANCELED" -> GameState.CANCELED
                        else -> GameState.SCHEDULED
                    }
                    add(Game(event.optString("id", "${competitionId}_$start"), competitionId, home, away, start, state,
                        homeObj.optString("score").toIntOrNull(), awayObj.optString("score").toIntOrNull(),
                        status?.optString("detail"), status?.optString("shortDetail"), comp.optString("venue"), round))
                }
            }
        }
    }

    private fun team(obj: JSONObject, competitionId: String): Team {
        val t = obj.optJSONObject("team") ?: JSONObject()
        return Team(t.optString("id", t.optString("name")), t.optString("displayName", t.optString("name", "TBD")),
            t.optString("abbreviation").ifBlank { null }, t.optString("logo").ifBlank { null }, competitionId)
    }
}
