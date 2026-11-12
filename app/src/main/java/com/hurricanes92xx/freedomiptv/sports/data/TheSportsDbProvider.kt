package com.hurricanes92xx.freedomiptv.sports.data

import com.hurricanes92xx.freedomiptv.sports.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** TheSportsDB schedule and event-artwork adapter. ESPN remains primary for detailed scores. */
class TheSportsDbProvider(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build(),
    private val apiKey: String = "3"
) {
    private val sportNames = mapOf(Sport.FOOTBALL to "American Football", Sport.BASKETBALL to "Basketball",
        Sport.BASEBALL to "Baseball", Sport.HOCKEY to "Ice Hockey", Sport.SOCCER to "Soccer",
        Sport.VOLLEYBALL to "Volleyball", Sport.GYMNASTICS to "Gymnastics")

    suspend fun schedule(competition: Competition, dayUtcMillis: Long): List<Game> = withContext(Dispatchers.IO) {
        val sportName = sportNames[competition.sport] ?: return@withContext emptyList()
        val date = Instant.ofEpochMilli(dayUtcMillis).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val url = "https://www.thesportsdb.com/api/v1/json/$apiKey/eventsday.php?d=$date&s=" + java.net.URLEncoder.encode(sportName, "UTF-8")
        runCatching { fetch(url, competition) }.getOrDefault(emptyList()).filter { it.startUtcMillis > 0L }.sortedBy { it.startUtcMillis }
    }

    /** Poster, fanart, thumbnail, and banner URLs for a TheSportsDB event ID. */
    suspend fun eventArtwork(eventId: String): List<String> = withContext(Dispatchers.IO) {
        val url = "https://www.thesportsdb.com/api/v1/json/$apiKey/lookupevent.php?id=${eventId.trim()}"
        runCatching {
            val event = request(url).optJSONArray("events")?.optJSONObject(0) ?: return@runCatching emptyList()
            listOf("strPoster", "strFanart", "strThumb", "strBanner").mapNotNull { event.optString(it).takeIf(String::isNotBlank) }.distinct()
        }.getOrDefault(emptyList())
    }

    private fun fetch(url: String, competition: Competition): List<Game> {
        val events = request(url).optJSONArray("events") ?: return emptyList()
        return buildList {
            for (i in 0 until events.length()) {
                val e = events.optJSONObject(i) ?: continue
                val homeName = e.optString("strHomeTeam").ifBlank { continue }
                val awayName = e.optString("strAwayTeam").ifBlank { continue }
                val date = e.optString("dateEvent")
                val time = e.optString("strTime").ifBlank { "00:00:00" }.take(8)
                val start = runCatching { Instant.parse("${date}T${time}Z").toEpochMilli() }.getOrDefault(0L)
                if (start <= 0L) continue
                val id = e.optString("idEvent").ifBlank { "${competition.id}_$start" }
                val status = e.optString("strStatus").lowercase()
                val state = when {
                    status.contains("live") || status.contains("progress") -> GameState.LIVE
                    status.contains("final") || status.contains("finished") -> GameState.FINAL
                    status.contains("postpon") -> GameState.POSTPONED
                    status.contains("cancel") -> GameState.CANCELED
                    else -> GameState.SCHEDULED
                }
                val home = Team(e.optString("idHomeTeam", homeName), homeName, e.optString("strHomeTeamShort").ifBlank { null }, e.optString("strHomeTeamBadge").ifBlank { null }, competition.id)
                val away = Team(e.optString("idAwayTeam", awayName), awayName, e.optString("strAwayTeamShort").ifBlank { null }, e.optString("strAwayTeamBadge").ifBlank { null }, competition.id)
                add(Game(id, competition.id, home, away, start, state, e.optString("intHomeScore").toIntOrNull(), e.optString("intAwayScore").toIntOrNull(),
                    e.optString("strProgress").ifBlank { null }, e.optString("intRound").ifBlank { null }, e.optString("strVenue").ifBlank { null }))
            }
        }.distinctBy { it.id }
    }

    private fun request(url: String): JSONObject {
        val req = Request.Builder().url(url).header("User-Agent", "FreedomIPTV/1.0 (sports schedules and artwork)").build()
        client.newCall(req).execute().use { response -> if (!response.isSuccessful) return JSONObject(); return JSONObject(response.body?.string().orEmpty()) }
    }
}
