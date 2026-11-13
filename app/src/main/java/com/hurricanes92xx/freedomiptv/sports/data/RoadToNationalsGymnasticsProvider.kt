package com.hurricanes92xx.freedomiptv.sports.data

import com.hurricanes92xx.freedomiptv.sports.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/** Road to Nationals public women's schedule API; ESPN/HenryGD remain supplements. */
class RoadToNationalsGymnasticsProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS).build()
) {
    private val base = "https://www.roadtonationals.com/api/women"
    private val teamCache = mutableMapOf<Int, Set<String>>()

    suspend fun schedule(fromUtcMillis: Long, toUtcMillis: Long): List<Game> = withContext(Dispatchers.IO) {
        if (toUtcMillis < fromUtcMillis) return@withContext emptyList()
        val firstYear = Instant.ofEpochMilli(fromUtcMillis).atZone(ZoneOffset.UTC).year
        val lastYear = Instant.ofEpochMilli(toUtcMillis).atZone(ZoneOffset.UTC).year
        (firstYear..lastYear).flatMap { year ->
            runCatching { season(year, fromUtcMillis, toUtcMillis) }.getOrDefault(emptyList())
        }.distinctBy { it.id }.sortedBy { it.startUtcMillis }
    }

    private fun season(year: Int, from: Long, to: Long): List<Game> {
        val d1 = d1Teams(year)
        val root = getJson("$base/schedule/$year/1")
        val out = mutableListOf<Game>()
        val dates = root.keys()
        while (dates.hasNext()) {
            val date = dates.next()
            val day = root.optJSONObject(date) ?: continue
            val meets = day.optJSONArray("meets") ?: continue
            for (i in 0 until meets.length()) {
                val m = meets.optJSONObject(i) ?: continue
                val meetDate = m.optString("d").ifBlank { date }
                val time = m.optString("time").ifBlank { "12:00:00" }
                val epoch = runCatching {
                    java.time.LocalDateTime.parse("$meetDateT$time")
                        .atZone(java.time.ZoneId.of("America/New_York")).toInstant().toEpochMilli()
                }.getOrDefault(0L)
                if (epoch !in from..to) continue
                val homeNames = split(m.optString("home_teams"))
                val awayNames = split(m.optString("away_teams"))
                val participants = homeNames + awayNames
                if (participants.none { it in d1 }) continue
                val host = homeNames.firstOrNull() ?: participants.firstOrNull() ?: continue
                val away = (awayNames.ifEmpty { homeNames.drop(1) }).joinToString(", ").ifBlank { "TBD" }
                val desc = decode(m.optString("meet_desc").ifBlank { m.optString("newdesc") })
                val round = postseasonRound(desc)
                val id = m.optString("meet_id").ifBlank { "rtn-$meetDate-$host-$away" }
                out += Game(
                    id = "rtn-$id", competitionId = Competition.NCAAGYM_W.id,
                    home = Team("rtn-${slug(host)}", host, host, null, Competition.NCAAGYM_W.id),
                    away = Team("rtn-${slug(away)}", away, away, null, Competition.NCAAGYM_W.id),
                    startUtcMillis = epoch, state = GameState.SCHEDULED,
                    venue = decode(m.optString("location")).ifBlank { null }, postseasonRound = round
                )
            }
        }
        return out
    }

    private fun d1Teams(year: Int): Set<String> = teamCache.getOrPut(year) {
        runCatching {
            val week = getJson("$base/currentweek/$year").optInt("max")
            if (week <= 0) return@runCatching emptySet()
            val rows = getJson("$base/results/$year/$week/0/5").optJSONArray("data") ?: return@runCatching emptySet()
            buildSet { for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                if (row.optString("div") == "Div I") add(decode(row.optString("name")))
            } }
        }.getOrDefault(emptySet())
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", "FreedomIPTV/1.0 (women's gymnastics schedule)").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("RTN HTTP ${response.code}")
            return JSONObject(response.body?.string().orEmpty())
        }
    }
    private fun split(value: String) = decode(value).split(",").map(String::trim).filter(String::isNotBlank)
    private fun decode(value: String) = value.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").trim()
    private fun slug(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    private fun postseasonRound(value: String): String? {
        val s = value.lowercase()
        return when {
            "national championship" in s || "championship final" in s -> "NCAA Championship"
            "national semifinal" in s || "semifinal" in s -> "NCAA Semifinal"
            "regional final" in s -> "NCAA Regional Final"
            "regional" in s -> "NCAA Regional"
            "conference championship" in s || "championship" in s -> "Conference Championship"
            else -> null
        }
    }
}
