package com.hurricanes92xx.freedomiptv.sports.data

import com.hurricanes92xx.freedomiptv.sports.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class CfbdScheduleProvider(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    suspend fun schedule(
        from: Long,
        to: Long,
        division: Competition = Competition.NCAAF_FBS
    ): List<Game> = withContext(Dispatchers.IO) {
        val startDate = Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC).toLocalDate()
        val endDate = Instant.ofEpochMilli(to).atZone(ZoneOffset.UTC).toLocalDate()
        if (apiKey.isBlank() || to < from || endDate.year - startDate.year > 1) return@withContext emptyList()

        val classification = if (division == Competition.NCAAF_FCS) "fcs" else "fbs"
        val years = (startDate.year..endDate.year).toList()
        runCatching {
            val gamesById = linkedMapOf<String, Game>()
            for (year in years) {
                val calendarUrl = "https://api.collegefootballdata.com/calendar?year=$year"
                val calendarResponse = client.newCall(
                    Request.Builder().url(calendarUrl)
                        .header("Authorization", "Bearer $apiKey").build()
                ).execute()
                val calendar = calendarResponse.use { response ->
                    if (!response.isSuccessful) return@runCatching emptyList<Game>()
                    JSONArray(response.body?.string().orEmpty())
                }
                val weeks = mutableSetOf<Pair<Int, String>>()
                for (i in 0 until calendar.length()) {
                    val entry = calendar.optJSONObject(i) ?: continue
                    val weekStart = runCatching { Instant.parse(entry.optString("startDate")).toEpochMilli() }.getOrDefault(0L)
                    val weekEnd = runCatching { Instant.parse(entry.optString("endDate")).toEpochMilli() }.getOrDefault(0L)
                    if (weekStart == 0L || weekEnd == 0L || weekEnd < from || weekStart > to) continue
                    val week = entry.optInt("week", -1)
                    val seasonType = entry.optString("seasonType")
                    if (week >= 0 && seasonType.isNotBlank()) weeks.add(week to seasonType)
                }
                for ((week, seasonType) in weeks) {
                    val url = "https://api.collegefootballdata.com/games?year=$year&week=$week&seasonType=$seasonType&classification=$classification"
                    val response = client.newCall(
                        Request.Builder().url(url)
                            .header("Authorization", "Bearer $apiKey").build()
                    ).execute()
                    val games = response.use { res ->
                        if (!res.isSuccessful) JSONArray()
                        else JSONArray(res.body?.string().orEmpty())
                    }
                    for (index in 0 until games.length()) {
                        val game = games.optJSONObject(index) ?: continue
                        val kickoff = runCatching { Instant.parse(game.optString("startDate")).toEpochMilli() }.getOrDefault(0L)
                        if (kickoff !in from..to) continue
                        val homeName = game.optString("homeTeam")
                        val awayName = game.optString("awayTeam")
                        if (homeName.isBlank() || awayName.isBlank()) continue
                        val homeClass = game.optString("homeClassification").lowercase()
                        val awayClass = game.optString("awayClassification").lowercase()
                        val hasFbs = homeClass == "fbs" || awayClass == "fbs"
                        val hasFcs = homeClass == "fcs" || awayClass == "fcs"
                        if (division == Competition.NCAAF_FCS && !hasFcs) continue
                        if (division == Competition.NCAAF_FBS && !hasFbs) continue
                        val statusText = game.optString("status").lowercase()
                        val state = when {
                            game.optBoolean("completed") || statusText.contains("final") || statusText.contains("complete") -> GameState.FINAL
                            statusText.contains("in progress") || statusText.contains("live") -> GameState.LIVE
                            statusText.contains("postpon") -> GameState.POSTPONED
                            statusText.contains("cancel") -> GameState.CANCELED
                            else -> GameState.SCHEDULED
                        }
                        val id = game.optString("id")
                        val playoff = game.optJSONObject("playoff")
                        val notes = game.optString("notes")
                        val explicitRound = playoff?.optString("roundName").orEmpty()
                            .ifBlank { playoff?.optString("round").orEmpty() }
                            .ifBlank { notes.takeIf { it.contains("playoff", ignoreCase = true) || it.contains("championship", ignoreCase = true) }.orEmpty() }
                        val roundLabel = explicitRound.takeIf(String::isNotBlank)
                            ?: if (division == Competition.NCAAF_FCS && seasonType.lowercase() == "postseason") {
                                "FCS Playoffs • Week ${game.optInt("week", week)}"
                            } else null
                        gamesById[id] = Game(
                            id, division.id,
                            Team(game.optString("homeId"), homeName, null, null, division.id),
                            Team(game.optString("awayId"), awayName, null, null, division.id),
                            kickoff, state,
                            game.optInt("homePoints").takeIf { !game.isNull("homePoints") },
                            game.optInt("awayPoints").takeIf { !game.isNull("awayPoints") },
                            null, game.optString("week"), game.optString("venue"), roundLabel
                        )
                    }
                }
            }
            gamesById.values.sortedBy { it.startUtcMillis }
        }.getOrDefault(emptyList())
    }
}
