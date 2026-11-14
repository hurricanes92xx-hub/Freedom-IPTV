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
import org.jsoup.Jsoup
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
        val fallback = if (promotion == CombatPromotion.UFC) emptyList() else days.chunked(4).flatMap { batch ->
            batch.map { day -> async(Dispatchers.IO) {
                val date = day.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val url = "https://www.thesportsdb.com/api/v1/json/$apiKey/eventsday.php?d=$date&s=" + URLEncoder.encode("Fighting", "UTF-8")
                runCatching { fetch(url, promotion) }.getOrDefault(emptyList())
            } }.awaitAll().flatten()
        }
        val schedule = if (promotion == CombatPromotion.UFC) runCatching { fetchEspnUfcRange(start, end) }.getOrDefault(emptyList()) else {
            val official = runCatching { fetchOfficial(promotion, start, end) }.getOrDefault(emptyList())
            official.map { e -> e.copy(posterUrl = e.posterUrl ?: fallback.firstOrNull { it.startUtcMillis == e.startUtcMillis }?.posterUrl) } +
                fallback.filterNot { old -> official.any { it.startUtcMillis == old.startUtcMillis && normalize(it.title)==normalize(old.title) } }
        }
        schedule.filter { it.startUtcMillis in fromUtcMillis..toUtcMillis }
            .distinctBy { it.id }.sortedBy { it.startUtcMillis }
    }

    private fun officialCalendarUrl(promotion: CombatPromotion): String? = when (promotion) { CombatPromotion.WWE -> "https://www.wwe.com/events"; CombatPromotion.AEW -> "https://www.allelitewrestling.com/aew-events"; CombatPromotion.TNA -> "https://tnawrestling.com/events/"; else -> null }

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
    private fun fetchOfficial(p: CombatPromotion, from: LocalDate, to: LocalDate): List<CombatEvent> {
        val url = officialCalendarUrl(p) ?: return emptyList()
        val doc = Jsoup.connect(url).userAgent("FreedomIPTV/1.0").timeout(9000).get()
        val hs = doc.select("h2").map { it.text().trim() }
        val out = mutableListOf<CombatEvent>()
        if (p == CombatPromotion.AEW) for (i in hs.indices) {
            val m = Regex("(?i)([A-Z]+)\\s+(\\d{1,2}),?\\s+(\\d{4})").find(hs[i]) ?: continue
            val date = runCatching { LocalDate.of(m.groupValues[3].toInt(), java.time.Month.valueOf(m.groupValues[1].uppercase()), m.groupValues[2].toInt()) }.getOrNull() ?: continue
            val title = hs.getOrNull(i+1) ?: continue
            if (date !in from.minusDays(1)..to.plusDays(1)) continue
            val time = hs.getOrNull(i+7).orEmpty()
            val zone = when { time.contains("MT") -> "America/Denver"; time.contains("CT") -> "America/Chicago"; time.contains("PT") -> "America/Los_Angeles"; else -> "America/New_York" }
            val tm = Regex("(\\d{1,2}:\\d{2})(AM|PM)?", RegexOption.IGNORE_CASE).find(time)
            val lt = tm?.let { runCatching { java.time.LocalTime.parse(it.groupValues[1]) }.getOrNull()?.let { t -> if(it.groupValues[2].isNotBlank()) t.withHour(t.hour%12+if(it.groupValues[2].equals("PM",true))12 else 0) else t } } ?: java.time.LocalTime.NOON
            out += CombatEvent("official-aew-$date-${normalize(title)}", p.id, title, date.atTime(lt).atZone(java.time.ZoneId.of(zone)).toInstant().toEpochMilli())
        }
        if (p == CombatPromotion.TNA || p == CombatPromotion.WWE) {
            val text = doc.text()
            val rx = if(p==CombatPromotion.TNA) Regex("(?i)(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+(\\d{1,2}).{0,100}?(Destination X|Thursday Night iMPACT|Hardy Party|\u005bA-Za-z0-9 !:’'-]{4,})") else Regex("(?i)(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(\\d{1,2}),?\\s*(\\d{4})?.{0,80}?(Raw|SmackDown|NXT|WrestleMania|SummerSlam|Survivor Series|Royal Rumble|\u005bA-Za-z][^\\n]{3,})")
            for(m in rx.findAll(text)) {
                val mon=runCatching { java.time.Month.from(DateTimeFormatter.ofPattern("MMM",java.util.Locale.US).parse(m.groupValues[1])) }.getOrNull() ?: continue
                val day=m.groupValues[2].toIntOrNull() ?: continue; val yr=m.groupValues.getOrNull(3)?.toIntOrNull() ?: from.year
                val d=runCatching { LocalDate.of(yr,mon,day) }.getOrNull() ?: continue; if(d !in from.minusDays(1)..to.plusDays(1)) continue
                out += CombatEvent("official-${p.id}-$d-${normalize(m.groupValues.last())}",p.id,m.groupValues.last().trim(),d.atTime(20,0).atZone(java.time.ZoneId.of("America/New_York")).toInstant().toEpochMilli())
            }
        }
        return out.distinctBy { it.startUtcMillis to normalize(it.title) }
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
