package com.hurricanes92xx.freedomiptv.sports.data

import com.hurricanes92xx.freedomiptv.sports.model.Game
import com.hurricanes92xx.freedomiptv.sports.model.GameState
import com.hurricanes92xx.freedomiptv.sports.model.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/** NHL schedule API returns a gameWeek; walk week-by-week instead of requesting every day. */
class NhLWebApiProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    suspend fun schedule(from: Long, to: Long): List<Game> = withContext(Dispatchers.IO) {
        if (to < from) return@withContext emptyList()
        val start = Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC).toLocalDate()
        val end = Instant.ofEpochMilli(to).atZone(ZoneOffset.UTC).toLocalDate()
        val games = linkedMapOf<String, Game>()
        var requestDate = start

        // Each response contains the surrounding game week. Advance seven days to avoid
        // repeating the same week for every date in an extended playoff window.
        while (!requestDate.isAfter(end)) {
            val weekGames = runCatching {
                val request = Request.Builder()
                    .url("https://api-web.nhle.com/v1/schedule/$requestDate")
                    .header("User-Agent", "FreedomIPTV/1.0")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<Game>()
                    val weeks = JSONObject(response.body?.string().orEmpty()).optJSONArray("gameWeek")
                        ?: return@use emptyList<Game>()
                    buildList {
                        for (w in 0 until weeks.length()) {
                            val dayGames = weeks.optJSONObject(w)?.optJSONArray("games") ?: continue
                            for (i in 0 until dayGames.length()) {
                                val game = dayGames.optJSONObject(i) ?: continue
                                val home = game.optJSONObject("homeTeam") ?: continue
                                val away = game.optJSONObject("awayTeam") ?: continue
                                val kickoff = runCatching { Instant.parse(game.optString("startTimeUTC")).toEpochMilli() }
                                    .getOrDefault(0L)
                                if (kickoff !in from..to) continue
                                val state = when (game.optString("gameState")) {
                                    "LIVE", "CRIT" -> GameState.LIVE
                                    "OFF" -> GameState.FINAL
                                    "PPD" -> GameState.POSTPONED
                                    else -> GameState.SCHEDULED
                                }
                                add(Game(
                                    game.optString("id"), "nhl",
                                    Team(home.optString("id"), home.optString("name"), home.optString("abbrev"), null, "nhl"),
                                    Team(away.optString("id"), away.optString("name"), away.optString("abbrev"), null, "nhl"),
                                    kickoff, state,
                                    game.optInt("homeScore").takeIf { !game.isNull("homeScore") },
                                    game.optInt("awayScore").takeIf { !game.isNull("awayScore") },
                                    game.optString("gameState"), null, null
                                ))
                            }
                        }
                    }
                }
            }.getOrDefault(emptyList())
            weekGames.forEach { games[it.id] = it }
            requestDate = requestDate.plusDays(7)
        }
        games.values.sortedBy { it.startUtcMillis }
    }
}
