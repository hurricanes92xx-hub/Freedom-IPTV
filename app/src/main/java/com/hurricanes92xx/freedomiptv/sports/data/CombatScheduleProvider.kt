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
            official.map { e -> e.copy(posterUrl = e.posterUrl ?: fallback.firstOrNull { Instant.ofEpochMilli(it.startUtcMillis).atZone(ZoneOffset.UTC).toLocalDate() == Instant.ofEpochMilli(e.startUtcMillis).atZone(ZoneOffset.UTC).toLocalDate() && (normalize(it.title).contains(normalize(e.title).take(8)) || normalize(e.title).contains(normalize(it.title).take(8))) }?.posterUrl) } +
                fallback.filterNot { old -> official.any { Instant.ofEpochMilli(it.startUtcMillis).atZone(ZoneOffset.UTC).toLocalDate() == Instant.ofEpochMilli(old.startUtcMillis).atZone(ZoneOffset.UTC).toLocalDate() && normalize(it.title)==normalize(old.title) } }
        }
        schedule.filter { it.startUtcMillis in fromUtcMillis..toUtcMillis }
            .distinctBy { it.id }.sortedBy { it.startUtcMillis }
    }

    private fun officialCalendarUrl(promotion: CombatPromotion): String? = when (promotion) { CombatPromotion.WWE -> "https://www.wwe.com/events"; CombatPromotion.AEW -> "https://www.allelitewrestling.com/aew-events"; CombatPromotion.TNA -> "https://tnawrestling.com/events/"; else -> null }

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
    /** Parse real dated cards from each promotion's official event calendar. */
    private fun fetchOfficial(p: CombatPromotion, from: LocalDate, to: LocalDate): List<CombatEvent> {
        val url = officialCalendarUrl(p) ?: return emptyList()
        val doc = Jsoup.connect(url).userAgent("FreedomIPTV/1.0").timeout(12000).get()
        val out = mutableListOf<CombatEvent>()
        fun add(date: LocalDate, title: String, localTime: java.time.LocalTime = java.time.LocalTime.of(20,0), zone: String = "America/New_York") {
            if (date !in from.minusDays(1)..to.plusDays(1) || title.isBlank()) return
            val clean = title.trim().replace(Regex("\\s+"), " ")
            out += CombatEvent("official-${p.id}-${date}-${normalize(clean)}", p.id, clean,
                date.atTime(localTime).atZone(ZoneId.of(zone)).toInstant().toEpochMilli())
        }
        if (p == CombatPromotion.AEW) {
            // AEW calendar uses a date heading followed by event title, city and local start.
            val nodes = doc.select("h1,h2,h3,h4")
            for (i in nodes.indices) {
                val dateText = nodes[i].text().trim()
                val m = Regex("(?i)(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(\\d{1,2}),?\\s+(\\d{4})").find(dateText) ?: continue
                val date = runCatching { LocalDate.of(m.groupValues[3].toInt(), java.time.Month.valueOf(m.groupValues[1].uppercase()), m.groupValues[2].toInt()) }.getOrNull() ?: continue
                val title = (i+1 until minOf(i+5,nodes.size)).map { nodes[it].text().trim() }.firstOrNull { it.contains("AEW",true) || it.contains("ROH",true) || it.contains("Collision",true) || it.contains("Dynamite",true) || it.contains("Wrestling",true) } ?: continue
                val block = nodes[i].parent()?.parent()?.text().orEmpty()
                val tm = Regex("(\\d{1,2}):(\\d{2})\\s*(AM|PM)?\\s*(ET|CT|MT|PT)",RegexOption.IGNORE_CASE).find(block)
                val local = tm?.let { runCatching { java.time.LocalTime.of((it.groupValues[1].toInt()%12)+(if(it.groupValues[3].equals("PM",true))12 else 0),it.groupValues[2].toInt()) }.getOrNull() } ?: java.time.LocalTime.of(20,0)
                val zone = when(tm?.groupValues?.get(4)?.uppercase()) { "CT"->"America/Chicago"; "MT"->"America/Denver"; "PT"->"America/Los_Angeles"; else->"America/New_York" }
                add(date,title,local,zone)
            }
        } else {
            // WWE/TNA pages expose event listings in readable text; parse each dated line
            // and use the adjacent event label, never recurring weekday placeholders.
            val text = doc.text()
            val month = "(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)"
            val rx = Regex("(?i)$month\\s+(\\d{1,2})(?:,\\s*(\\d{4}))?")
            for (m in rx.findAll(text)) {
                val mon = runCatching { java.time.Month.from(DateTimeFormatter.ofPattern("MMM",java.util.Locale.US).parse(m.groupValues[1].take(3))) }.getOrNull() ?: continue
                val day = m.groupValues[2].toIntOrNull() ?: continue
                val year = m.groupValues[3].toIntOrNull() ?: from.year
                val date = runCatching { LocalDate.of(year,mon,day) }.getOrNull() ?: continue
                if (date !in from.minusDays(1)..to.plusDays(1)) continue
                val nearby = text.substring(m.range.last+1,minOf(text.length,m.range.last+100)).substringBefore("\\n").trim()
                val title = when(p) {
                    CombatPromotion.TNA -> nearby.takeIf { it.length in 3..100 } ?: "TNA Event"
                    CombatPromotion.WWE -> nearby.takeIf { it.length in 3..100 && (it.contains("WWE",true)||it.contains("SmackDown",true)||it.contains("RAW",true)||it.contains("NXT",true)||it.contains("Series",true)) } ?: "WWE Event"
                    else -> nearby
                }
                add(date,title)
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
