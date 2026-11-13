package com.hurricanes92xx.freedomiptv.sports.data

import com.hurricanes92xx.freedomiptv.sports.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.ZoneOffset

/**
 * ESPN-first schedule orchestration with NCAA alternate coverage and resilient
 * TheSportsDB/API-Football fallbacks. Provider failures never fail the screen.
 */
class SportsScheduleRepository(
    private val espn: EspnScheduleProvider = EspnScheduleProvider(),
    private val sportsDb: TheSportsDbProvider = TheSportsDbProvider(),
    private val apiFootball: ApiFootballScheduleProvider = ApiFootballScheduleProvider(),
    private val ncaa: NcaaScheduleProvider = NcaaScheduleProvider()
) {
    private data class CachedSchedule(val savedAt: Long, val games: List<Game>)
    private val scheduleCache = java.util.concurrent.ConcurrentHashMap<String, CachedSchedule>()
    private val cacheTtlMillis = 90_000L
    private val mlb = MlbStatsApiProvider()
    private val nhl = NhLWebApiProvider()
    private val cfbd = CfbdScheduleProvider(apiKey = com.hurricanes92.freedomiptv.BuildConfig.CFBD_API_KEY)
    private val combat = CombatScheduleProvider()
    private val roadToNationalsGymnastics = RoadToNationalsGymnasticsProvider()

    suspend fun schedule(competition: Competition, fromUtcMillis: Long, toUtcMillis: Long, bypassCache: Boolean = false): List<Game> = coroutineScope {
        if (toUtcMillis < fromUtcMillis) return@coroutineScope emptyList()
        val cacheKey = "${competition.id}:$fromUtcMillis:$toUtcMillis"
        if (!bypassCache) scheduleCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.savedAt < cacheTtlMillis) return@coroutineScope cached.games
        }
        if (competition.sport == Sport.COMBAT) {
            val promotion = when (competition) {
                Competition.UFC -> CombatPromotion.UFC
                Competition.BOXING -> CombatPromotion.BOXING
                Competition.WWE -> CombatPromotion.WWE
                Competition.AEW -> CombatPromotion.AEW
                Competition.TNA -> CombatPromotion.TNA
                else -> return@coroutineScope emptyList()
            }
            // Combat events are available through combatEvents(); never fabricate team games.
            return@coroutineScope emptyList()
        }
        val primary = async { if (competition == Competition.NCAAF_FCS) runCatching { cfbd.schedule(fromUtcMillis, toUtcMillis, Competition.NCAAF_FCS) }.getOrDefault(emptyList()).ifEmpty { runCatching { espn.schedule(competition, fromUtcMillis, toUtcMillis) }.getOrDefault(emptyList()) } else runCatching { espn.schedule(competition, fromUtcMillis, toUtcMillis) }.getOrDefault(emptyList()) }
        // NCAA's alternate feed supplements ESPN instead of being hidden whenever ESPN has partial data.
        val college = async {
            if (competition.college) runCatching { ncaa.schedule(competition, fromUtcMillis, toUtcMillis) }.getOrDefault(emptyList())
            else emptyList()
        }
        val espnGames = primary.await()
        val ncaaGames = college.await()
        // RTN is the dedicated women's gymnastics schedule and postseason source.
        val rtnGymnastics = if (competition == Competition.NCAAGYM_W) runCatching {
            roadToNationalsGymnastics.schedule(fromUtcMillis, toUtcMillis)
        }.getOrDefault(emptyList()) else emptyList()
        val days = generateSequence(Instant.ofEpochMilli(fromUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()) { it.plusDays(1) }
            .takeWhile { !it.isAfter(Instant.ofEpochMilli(toUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()) }.toList()
        // Combat feeds are routinely absent from ESPN; query the supplement there even with primary results.
        val secondary = if ((espnGames.isEmpty() && ncaaGames.isEmpty()) || competition.sport == Sport.COMBAT) {
            coroutineScope {
                days.chunked(4).flatMap { batch -> batch.map { day -> async {
                    runCatching { sportsDb.schedule(competition, day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) }.getOrDefault(emptyList())
                } }.awaitAll().flatten() }
            }
        } else emptyList()
        // Dedicated free league APIs supplement ESPN's broad scoreboard coverage.
        val leagueSupplement = when (competition) {
            Competition.MLB -> runCatching { mlb.schedule(fromUtcMillis, toUtcMillis) }.getOrDefault(emptyList())
            Competition.NHL -> runCatching { nhl.schedule(fromUtcMillis, toUtcMillis) }.getOrDefault(emptyList())
            Competition.NCAAF_FBS -> runCatching { cfbd.schedule(fromUtcMillis, toUtcMillis, Competition.NCAAF_FBS) }.getOrDefault(emptyList())
            else -> emptyList()
        }
        val soccerBase = espnGames + ncaaGames + rtnGymnastics + secondary + leagueSupplement
        // API-Football supplements partial ESPN soccer dates; dedupe merged games below.
        val football = if (competition.sport == Sport.SOCCER) {
            runCatching { apiFootball.schedule(competition, fromUtcMillis, toUtcMillis) }.getOrDefault(emptyList())
        } else emptyList()
        val merged = (soccerBase + football).distinctBy { game ->
            "${game.competitionId}:${game.startUtcMillis / 60000}:${game.home.name.lowercase().replace(Regex("[^a-z0-9]"), "")}:${game.away.name.lowercase().replace(Regex("[^a-z0-9]"), "")}"
        }.sortedBy { it.startUtcMillis }
        if (!bypassCache) scheduleCache[cacheKey] = CachedSchedule(System.currentTimeMillis(), merged)
        if (scheduleCache.size > 120) {
            val now = System.currentTimeMillis()
            scheduleCache.entries.removeIf { now - it.value.savedAt >= cacheTtlMillis }
            if (scheduleCache.size > 120) {
                scheduleCache.entries.sortedBy { it.value.savedAt }
                    .take(scheduleCache.size - 120)
                    .forEach { scheduleCache.remove(it.key, it.value) }
            }
        }
        merged
    }

    suspend fun liveScores(competition: Competition): List<Game> {
        val espnLive = runCatching { espn.liveScores(competition) }.getOrDefault(emptyList())
        if (espnLive.isNotEmpty()) return espnLive
        val now = System.currentTimeMillis()
        return schedule(competition, now - 6 * 60 * 60_000L, now + 2 * 60 * 60_000L, bypassCache = true).filter { it.state == GameState.LIVE }
    }
}

/** API-Football (API-Sports) adapter. Set API_FOOTBALL_KEY in app configuration to enable. */
class ApiFootballScheduleProvider(
    private val apiKey: String = "",
    private val client: okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(7, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
) {
    suspend fun schedule(competition: Competition, fromUtcMillis: Long, toUtcMillis: Long): List<Game> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (apiKey.isBlank() || competition.sport != Sport.SOCCER) return@withContext emptyList()
            val league = when (competition) { Competition.EPL -> "39"; Competition.UCL -> "2"; Competition.MLS -> "253"; else -> return@withContext emptyList() }
            val start = java.time.Instant.ofEpochMilli(fromUtcMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
            val end = java.time.Instant.ofEpochMilli(toUtcMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
            val out = mutableListOf<Game>()
            var date = start
            while (!date.isAfter(end) && out.size < 500) {
                // European leagues use seasons spanning two calendar years; MLS follows calendar years.
                val seasonYear = if (competition == Competition.MLS || date.monthValue >= 7) date.year else date.year - 1
                val url = "https://v3.football.api-sports.io/fixtures?league=$league&season=$seasonYear&date=$date"
                runCatching {
                    val req = okhttp3.Request.Builder().url(url).header("x-apisports-key",apiKey).build()
                    client.newCall(req).execute().use { response ->
                        if (!response.isSuccessful) return@use
                        val arr = org.json.JSONObject(response.body?.string().orEmpty()).optJSONArray("response") ?: return@use
                        for (i in 0 until arr.length()) {
                            val item=arr.optJSONObject(i) ?: continue; val fixture=item.optJSONObject("fixture") ?: continue
                            val teams=item.optJSONObject("teams") ?: continue; val home=teams.optJSONObject("home") ?: continue; val away=teams.optJSONObject("away") ?: continue
                            val kickoff=runCatching { java.time.Instant.parse(fixture.optString("date")).toEpochMilli() }.getOrDefault(0L); if(kickoff !in fromUtcMillis..toUtcMillis) continue
                            val status=fixture.optJSONObject("status")?.optString("short").orEmpty()
                            val state=when(status){"1H","2H","HT","ET","P","LIVE"->GameState.LIVE;"FT","AET","PEN"->GameState.FINAL;"PST"->GameState.POSTPONED;"CANC"->GameState.CANCELED;else->GameState.SCHEDULED}
                            val score=item.optJSONObject("goals") ?: org.json.JSONObject()
                            out.add(Game(fixture.optString("id"),competition.id,Team(home.optString("id"),home.optString("name"),null,home.optString("logo"),competition.id),Team(away.optString("id"),away.optString("name"),null,away.optString("logo"),competition.id),kickoff,state,score.optInt("home").takeIf{!score.isNull("home")},score.optInt("away").takeIf{!score.isNull("away")},fixture.optJSONObject("status")?.optString("elapsed"),null,fixture.optJSONObject("venue")?.optString("name")))
                        }
                    }
                }
                date=date.plusDays(1)
            }
            out
        }
}
