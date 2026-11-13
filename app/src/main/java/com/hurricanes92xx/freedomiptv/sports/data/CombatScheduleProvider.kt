package com.hurricanes92xx.freedomiptv.sports.data

import com.hurricanes92xx.freedomiptv.sports.model.CombatEvent
import com.hurricanes92xx.freedomiptv.sports.model.CombatPromotion
import com.hurricanes92xx.freedomiptv.sports.model.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Event-style combat calendar from TheSportsDB Fighting schedule; no fake team scoreboards. */
class CombatScheduleProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS).build(),
    private val apiKey: String = "3"
) {
    suspend fun events(promotion: CombatPromotion, fromUtcMillis: Long, toUtcMillis: Long): List<CombatEvent> = withContext(Dispatchers.IO) {
        if (toUtcMillis < fromUtcMillis) return@withContext emptyList()
        val start = Instant.ofEpochMilli(fromUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val end = Instant.ofEpochMilli(toUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.take(31).toList()
        // Bound concurrency: faster than 31 serial requests, without flooding the free API.
        days.chunked(4).flatMap { batch ->
            batch.map { day -> async(Dispatchers.IO) {
                if (promotion == CombatPromotion.UFC) {
                    val espn = runCatching { fetchEspnUfc(day) }.getOrDefault(emptyList())
                    if (espn.isNotEmpty()) espn else {
                        val date = day.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val url = "https://www.thesportsdb.com/api/v1/json/$apiKey/eventsday.php?d=$date&s=" + URLEncoder.encode("Fighting", "UTF-8")
                        runCatching { fetch(url, promotion) }.getOrDefault(emptyList())
                    }
                } else {
                    val date = day.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val url = "https://www.thesportsdb.com/api/v1/json/$apiKey/eventsday.php?d=$date&s=" + URLEncoder.encode("Fighting", "UTF-8")
                    runCatching { fetch(url, promotion) }.getOrDefault(emptyList())
                }
            } }.awaitAll().flatten()
        }.filter { it.startUtcMillis in fromUtcMillis..toUtcMillis }
            .plus(weeklyTelevision(promotion, start, end).filter { it.startUtcMillis in fromUtcMillis..toUtcMillis })
            .plus(officialSpecials(promotion).filter { it.startUtcMillis in fromUtcMillis..toUtcMillis })
            .distinctBy { it.id }.sortedBy { it.startUtcMillis }
    }

    /** Confirmed major cards supplement sparse third-party Fighting listings. Keep only official announced dates. */
    private fun officialSpecials(promotion: CombatPromotion): List<CombatEvent> {
        val zone = ZoneId.of("America/New_York")
        fun event(id: String, title: String, date: LocalDate, hour: Int, poster: String? = null) =
            CombatEvent(id, promotion.id, title, date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(), poster)
        return when (promotion) {
            CombatPromotion.WWE -> listOf(
                event("wwe-survivor-series-2026", "WWE Survivor Series: WarGames 2026", LocalDate.of(2026,11,28),18,
                    "https://commons.wikimedia.org/wiki/Special:FilePath/WWE_Survivor_Series_logo.svg")
            )
            CombatPromotion.AEW -> listOf(
                event("aew-full-gear-2026", "AEW Full Gear 2026", LocalDate.of(2026,11,14),18,
                    "https://commons.wikimedia.org/wiki/Special:FilePath/AEW_Full_Gear_logo.svg")
            )
            CombatPromotion.TNA -> listOf(
                event("tna-destination-x-countdown-2026", "TNA Destination X Countdown", LocalDate.of(2026,11,15),18,
                    "https://commons.wikimedia.org/wiki/Special:FilePath/Impact_Wrestling_Logo.svg"),
                event("tna-destination-x-2026", "TNA Destination X 2026", LocalDate.of(2026,11,15),19,
                    "https://commons.wikimedia.org/wiki/Special:FilePath/Impact_Wrestling_Logo.svg")
            )
            CombatPromotion.UFC -> listOf(
                event("ufc-334-early-prelims-2026", "UFC 334 Early Prelims", LocalDate.of(2026,11,14),17,
                    "https://commons.wikimedia.org/wiki/Special:FilePath/UFC_logo.svg"),
                event("ufc-334-prelims-2026", "UFC 334 Prelims", LocalDate.of(2026,11,14),19,
                    "https://commons.wikimedia.org/wiki/Special:FilePath/UFC_logo.svg"),
                event("ufc-334-2026", "UFC 334: Gane vs Hokit", LocalDate.of(2026,11,14),21,
                    "https://commons.wikimedia.org/wiki/Special:FilePath/UFC_logo.svg"),
                event("ufc-fn-2026-11-21-prelims", "UFC Fight Night: Prochazka vs Stirling Prelims", LocalDate.of(2026,11,21),10,
                    "https://commons.wikimedia.org/wiki/Special:FilePath/UFC_logo.svg"),
                event("ufc-fn-2026-11-21", "UFC Fight Night: Prochazka vs Stirling", LocalDate.of(2026,11,21),13,
                    "https://commons.wikimedia.org/wiki/Special:FilePath/UFC_logo.svg"),
                event("ufc-fn-2026-12-05-prelims", "UFC Fight Night: Adesanya vs Magomedov Prelims", LocalDate.of(2026,12,5),10,
                    "https://commons.wikimedia.org/wiki/Special:FilePath/UFC_logo.svg"),
                event("ufc-fn-2026-12-05", "UFC Fight Night: Adesanya vs Magomedov", LocalDate.of(2026,12,5),13,
                    "https://commons.wikimedia.org/wiki/Special:FilePath/UFC_logo.svg"),
                event("ufc-335-prelims-2026", "UFC 335 Prelims", LocalDate.of(2026,12,12),19,
                    "https://commons.wikimedia.org/wiki/Special:FilePath/UFC_logo.svg"),
                event("ufc-335-2026", "UFC 335: Oliveira vs Lopes", LocalDate.of(2026,12,12),21,
                    "https://commons.wikimedia.org/wiki/Special:FilePath/UFC_logo.svg")
            )
            else -> emptyList()
        }
    }

    /** Weekly TV slots supplement omitted event listings; these are broadcast times, not venue bell times. */
    private fun weeklyTelevision(promotion: CombatPromotion, start: LocalDate, end: LocalDate): List<CombatEvent> {
        val zone = ZoneId.of("America/New_York"); val out = mutableListOf<CombatEvent>(); var day = start
        while (!day.isAfter(end)) {
            val slot = when (promotion) {
                CombatPromotion.WWE -> when (day.dayOfWeek) {
                    DayOfWeek.MONDAY -> Triple("Monday Night Raw",20,"https://upload.wikimedia.org/wikipedia/commons/thumb/d/d8/WWE_Raw_Logo_2019.svg/1280px-WWE_Raw_Logo_2019.svg.png")
                    DayOfWeek.TUESDAY -> Triple("WWE NXT",20,"https://commons.wikimedia.org/wiki/Special:FilePath/WWE_NXT_2024_Logo.svg")
                    DayOfWeek.FRIDAY -> Triple("WWE SmackDown",20,"https://commons.wikimedia.org/wiki/Special:FilePath/WWE_SmackDown_%282024%29_Logo.svg")
                    else -> null
                }
                CombatPromotion.AEW -> when (day.dayOfWeek) {
                    DayOfWeek.WEDNESDAY -> Triple("AEW Dynamite",20,"https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/AEW_Dynamite_logo_%28simplified%29.jpg/1280px-AEW_Dynamite_logo_%28simplified%29.jpg")
                    DayOfWeek.SATURDAY -> Triple("AEW Collision",20,"https://commons.wikimedia.org/wiki/Special:FilePath/AEW_Collision_logo.svg")
                    else -> null
                }
                CombatPromotion.TNA -> if (day.dayOfWeek == DayOfWeek.THURSDAY) Triple("TNA Thursday Night iMPACT!",21,"https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Thursday_Night_iMPACT%21.png/1280px-Thursday_Night_iMPACT%21.png") else null
                else -> null
            }
            if (slot != null) out += CombatEvent("tv-\${promotion.id}-\$day",promotion.id,slot.first,day.atTime(slot.second,0).atZone(zone).toInstant().toEpochMilli(),slot.third)
            day=day.plusDays(1)
        }
        return out
    }

    /** ESPN UFC scoreboard contains each bout as an event competition. */
    private fun fetchEspnUfc(day: LocalDate): List<CombatEvent> {
        val date = day.format(DateTimeFormatter.BASIC_ISO_DATE)
        val request = Request.Builder().url("https://site.api.espn.com/apis/site/v2/sports/mma/ufc/scoreboard?dates=$date")
            .header("User-Agent", "FreedomIPTV/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val events = JSONObject(response.body?.string().orEmpty()).optJSONArray("events") ?: return emptyList()
            return buildList {
                for (i in 0 until events.length()) {
                    val event = events.optJSONObject(i) ?: continue
                    val id = event.optString("id")
                    val title = event.optString("name").ifBlank { event.optString("shortName") }
                    val comps = event.optJSONArray("competitions") ?: continue
                    val bouts = buildList {
                        for (j in 0 until comps.length()) {
                            val comp = comps.optJSONObject(j) ?: continue
                            val competitors = comp.optJSONArray("competitors") ?: continue
                            val names = buildList {
                                for (k in 0 until competitors.length()) {
                                    val name = competitors.optJSONObject(k)?.optJSONObject("athlete")?.optString("displayName").orEmpty().trim()
                                    if (name.isNotBlank()) add(name)
                                }
                            }
                            if (names.size == 2) add(com.hurricanes92xx.freedomiptv.sports.model.CombatBout(
                                comp.optString("id", "$id-$j"), names,
                                comp.optJSONObject("type")?.optString("abbreviation")?.takeIf { it.isNotBlank() },
                                j == comps.length() - 1
                            ))
                        }
                    }
                    if (id.isBlank() || title.isBlank() || bouts.isEmpty()) continue
                    val start = runCatching { Instant.parse(event.optString("date")).toEpochMilli() }.getOrDefault(0L)
                    if (start <= 0L) continue
                    val state = when (event.optJSONObject("status")?.optJSONObject("type")?.optString("state")) {
                        "in" -> GameState.LIVE
                        "post" -> GameState.FINAL
                        else -> GameState.SCHEDULED
                    }
                    add(CombatEvent("espn-ufc-$id", CombatPromotion.UFC.id, title, start,
                        "https://a.espncdn.com/i/teamlogos/leagues/500/ufc.png", bouts, state))
                }
            }
        }
    }

    private fun matchesPromotion(event: JSONObject, title: String, promotion: CombatPromotion): Boolean {
        val metadata = listOf("strLeague", "strSport", "strEventAlternate", "strEvent").joinToString(" ") {
            event.optString(it)
        }.lowercase()
        return when (promotion) {
            CombatPromotion.UFC -> "ufc" in metadata || "ultimate fighting championship" in metadata
            CombatPromotion.BOXING -> "boxing" in metadata || "box" in event.optString("strSport").lowercase()
            CombatPromotion.WWE -> "wwe" in metadata || "world wrestling entertainment" in metadata
            CombatPromotion.AEW -> "aew" in metadata || "all elite wrestling" in metadata
            CombatPromotion.TNA -> "tna" in metadata || "impact wrestling" in metadata || "total nonstop action" in metadata
        }
    }

    private fun fetch(url: String, promotion: CombatPromotion): List<CombatEvent> {
        val request = Request.Builder().url(url).header("User-Agent", "FreedomIPTV/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val events = JSONObject(response.body?.string().orEmpty()).optJSONArray("events") ?: return emptyList()
            return buildList {
                for (i in 0 until events.length()) {
                    val event = events.optJSONObject(i) ?: continue
                    val title = event.optString("strEvent").trim()
                    if (title.isBlank() || !matchesPromotion(event, title, promotion)) continue
                    val date = event.optString("dateEvent")
                    val time = event.optString("strTime").ifBlank { "00:00:00" }.take(8)
                    val start = runCatching { Instant.parse("${date}T${time}Z").toEpochMilli() }.getOrDefault(0L)
                    if (start <= 0L) continue
                    val status = event.optString("strStatus").lowercase()
                    val state = when {
                        status.contains("live") || status.contains("progress") -> GameState.LIVE
                        status.contains("final") || status.contains("finished") -> GameState.FINAL
                        status.contains("postpon") -> GameState.POSTPONED
                        status.contains("cancel") -> GameState.CANCELED
                        else -> GameState.SCHEDULED
                    }
                    val poster = listOf("strPoster", "strFanart", "strEventThumb", "strThumb", "strBanner", "strLogo", "strLeagueLogo", "strBadge")
                        .map { event.optString(it) }.firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
                    add(CombatEvent(event.optString("idEvent", "${promotion.id}_${start}"), promotion.id, title, start, poster, state = state))
                }
            }
        }
    }
}
