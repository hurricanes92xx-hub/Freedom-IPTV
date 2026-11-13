package com.hurricanes92xx.freedomiptv.sports.data

import com.hurricanes92xx.freedomiptv.sports.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.time.Instant
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
        if (apiKey.isBlank() || endDate.year - startDate.year > 1) return@withContext emptyList()

        val seasonYear = if (startDate.monthValue < 2) endDate.year else startDate.year
        val classification = if (division == Competition.NCAAF_FCS) "fcs" else "fbs"
        val url = "https://api.collegefootballdata.com/games?year=$seasonYear&seasonType=both&classification=$classification&startDate=$startDate&endDate=$endDate"

        runCatching {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList<Game>()
                val games = JSONArray(response.body?.string().orEmpty())
                buildList {
                    for (index in 0 until games.length()) {
                        val game = games.optJSONObject(index) ?: continue
                        val kickoff = runCatching {
                            Instant.parse(game.optString("startDate")).toEpochMilli()
                        }.getOrDefault(0L)
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
                        add(
                            Game(
                                game.optString("id"), division.id,
                                Team(game.optString("homeId"), homeName, null, null, division.id),
                                Team(game.optString("awayId"), awayName, null, null, division.id),
                                kickoff, state,
                                game.optInt("homePoints").takeIf { !game.isNull("homePoints") },
                                game.optInt("awayPoints").takeIf { !game.isNull("awayPoints") },
                                null, game.optString("week"), game.optString("venue")
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}
