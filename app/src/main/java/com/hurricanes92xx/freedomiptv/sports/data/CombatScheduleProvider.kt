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
        // ESPN's scoreboard accepts a date range. One range request is faster and keeps
        // UFC event dates/times sourced from ESPN instead of a hand-maintained calendar.
        val schedule = if (promotion == CombatPromotion.UFC) {
            runCatching { fetchEspnUfcRange(start, end) }.getOrDefault(emptyList())
        } else {
            days.chunked(4).flatMap { batch ->
                batch.map { day -> async(Dispatchers.IO) {
                    val date = day.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val url = "https://www.thesportsdb.com/api/v1/json/$apiKey/eventsday.php?d=$date&s=" + URLEncoder.encode("Fighting", "UTF-8")
                    runCatching { fetch(url, promotion) }.getOrDefault(emptyList())
                } }.awaitAll().flatten()
            }
        }
        schedule.filter { it.startUtcMillis in fromUtcMillis..toUtcMillis }
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
            // UFC dates and times are exclusively ESPN scoreboard data; no manually
            // entered future cards that can drift when the promotion changes its schedule.
            CombatPromotion.UFC -> emptyList()
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

    /** ESPN scoreboard range query: dates=YYYYMMDD-YYYYMMDD. */
    private fun fetchEspnUfcRange(start: LocalDate, end: LocalDate): List<CombatEvent> {
        val dates = "${start.format(DateTimeFormatter.BASIC_ISO_DATE)}-${end.format(DateTimeFormatter.BASIC_ISO_DATE)}"
        val request = Request.Builder().url("https://site.api.espn.com/apis/site/v2/sports/mma/ufc/scoreboard?dates=$dates")
            .header("User-Agent", "FreedomIPTV/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val events = JSONObject(response.body?.string().orEmpty()).optJSONArray("events") ?: return emptyList()
            return buildList {
                for (i in 0 until events.length()) {
                    val event = events.optJSONObject(i) ?: continue
                    val id = event.optString("id")
                    val title = event.optString("name").ifBlank { event.optString("shortName") }
                    // Fightcenter card buckets label broadcast segments; scoreboard order alone cannot.
                    val fightcenter = runCatching { fetchUfcFightcenter(id) }.getOrNull()
                    val bouts = fightcenter?.takeIf { it.isNotEmpty() } ?: parseScoreboardBouts(event, id)
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

    /** ESPN Fightcenter groups bouts by Early Prelims, Prelims, and Main Card. */
    private fun fetchUfcFightcenter(eventId: String): List<com.hurricanes92xx.freedomiptv.sports.model.CombatBout> {
        val url = "https://site.web.api.espn.com/apis/common/v3/sports/mma/ufc/fightcenter/$eventId?region=us&lang=en&contentorigin=espn&showAirings=buy%2Clive%2Creplay&buyWindow=1m"
        val request = Request.Builder().url(url).header("User-Agent", "FreedomIPTV/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val root = JSONObject(response.body?.string().orEmpty())
            val cards = root.optJSONObject("cards") ?: root.optJSONObject("fightCards") ?: return emptyList()
            val segments = listOf("earlyprelims" to "Early Prelims", "earlyPrelims" to "Early Prelims",
                "prelims1" to "Prelims", "prelims2" to "Prelims", "prelims" to "Prelims",
                "maincard" to "Main Card", "mainCard" to "Main Card")
            val out = mutableListOf<com.hurricanes92xx.freedomiptv.sports.model.CombatBout>()
            for ((key, label) in segments) {
                val card = cards.optJSONObject(key) ?: continue
                val competitions = card.optJSONArray("competitions") ?: continue
                for (i in 0 until competitions.length()) {
                    val bout = competitions.optJSONObject(i) ?: continue
                    val competitors = bout.optJSONArray("competitors") ?: continue
                    val fighters = buildList {
                        for (j in 0 until competitors.length()) {
                            val competitor = competitors.optJSONObject(j) ?: continue
                            val athlete = competitor.optJSONObject("athlete") ?: competitor
                            val name = athlete.optString("displayName").ifBlank { athlete.optString("fullName") }.trim()
                            if (name.isNotBlank()) add(name)
                        }
                    }
                    if (fighters.size != 2) continue
                    val type = bout.optJSONObject("type")
                    val weight = type?.optString("text").orEmpty().ifBlank { type?.optString("abbreviation").orEmpty() }
                    out += com.hurricanes92xx.freedomiptv.sports.model.CombatBout(
                        bout.optString("id", "$eventId-$key-$i"), fighters,
                        weight.takeIf { it.isNotBlank() }, label == "Main Card" && i == competitions.length() - 1, label)
                }
            }
            return out
        }
    }

    /** Scoreboard fallback deliberately leaves segment unknown instead of guessing from order. */
    private fun parseScoreboardBouts(event: JSONObject, eventId: String): List<com.hurricanes92xx.freedomiptv.sports.model.CombatBout> {
        val comps = event.optJSONArray("competitions") ?: return emptyList()
        return buildList {
            for (i in 0 until comps.length()) {
                val comp = comps.optJSONObject(i) ?: continue
                val competitors = comp.optJSONArray("competitors") ?: continue
                val names = buildList {
                    for (j in 0 until competitors.length()) {
                        val name = competitors.optJSONObject(j)?.optJSONObject("athlete")?.optString("displayName").orEmpty().trim()
                        if (name.isNotBlank()) add(name)
                    }
                }
                if (names.size == 2) add(com.hurricanes92xx.freedomiptv.sports.model.CombatBout(
                    comp.optString("id", "$eventId-$i"), names,
                    comp.optJSONObject("type")?.optString("abbreviation")?.takeIf { it.isNotBlank() },
                    i == comps.length() - 1, null))
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
