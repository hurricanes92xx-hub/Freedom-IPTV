package com.hurricanes92.freedomiptv.sports.data
import com.hurricanes92.freedomiptv.sports.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
class TheSportsDbProvider(private val client:OkHttpClient=OkHttpClient.Builder().connectTimeout(6,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS).build(),private val apiKey:String="3"){
 private val sportNames=mapOf(Sport.FOOTBALL to "American Football",Sport.BASKETBALL to "Basketball",Sport.BASEBALL to "Baseball",Sport.HOCKEY to "Ice Hockey",Sport.SOCCER to "Soccer",Sport.VOLLEYBALL to "Volleyball",Sport.GYMNASTICS to "Gymnastics")
 suspend fun schedule(competition:Competition,dayUtcMillis:Long):List<Game> = withContext(Dispatchers.IO){
  val sport=sportNames[competition.sport]?:return@withContext emptyList()
  val date=Instant.ofEpochMilli(dayUtcMillis).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
  val url="https://www.thesportsdb.com/api/v1/json/$apiKey/eventsday.php?d=$date&s="+java.net.URLEncoder.encode(sport,"UTF-8")
  runCatching{fetch(url,competition)}.getOrDefault(emptyList()).sortedBy{it.startUtcMillis}
 }
 suspend fun eventArtwork(eventId:String):List<String> = withContext(Dispatchers.IO){runCatching{
  val e=request("https://www.thesportsdb.com/api/v1/json/$apiKey/lookupevent.php?id="+eventId.trim()).optJSONArray("events")?.optJSONObject(0)?:return@runCatching emptyList()
  listOf("strPoster","strFanart","strThumb","strBanner").mapNotNull{e.optString(it).takeIf(String::isNotBlank)}.distinct()
 }.getOrDefault(emptyList())}
 private fun fetch(url:String,c:Competition):List<Game>{
  val a=request(url).optJSONArray("events")?:return emptyList()
  return buildList{for(i in 0 until a.length()){
   val e=a.optJSONObject(i)?:continue;val hn=e.optString("strHomeTeam").trim();val an=e.optString("strAwayTeam").trim();if(hn.isEmpty()||an.isEmpty())continue
   val date=e.optString("dateEvent");val time=e.optString("strTime").ifBlank{"00:00:00"}.take(8);val start=runCatching{Instant.parse("${date}T${time}Z").toEpochMilli()}.getOrDefault(0L);if(start<=0)continue
   val id=e.optString("idEvent").ifBlank{"${c.id}_$start"};val st=e.optString("strStatus").lowercase()
   val state=when{st.contains("live")||st.contains("progress")->GameState.LIVE;st.contains("final")||st.contains("finished")->GameState.FINAL;st.contains("postpon")->GameState.POSTPONED;st.contains("cancel")->GameState.CANCELED;else->GameState.SCHEDULED}
   val h=Team(e.optString("idHomeTeam",hn),hn,e.optString("strHomeTeamShort").ifBlank{null},e.optString("strHomeTeamBadge").ifBlank{null},c.id);val away=Team(e.optString("idAwayTeam",an),an,e.optString("strAwayTeamShort").ifBlank{null},e.optString("strAwayTeamBadge").ifBlank{null},c.id)
   add(Game(id,c.id,h,away,start,state,e.optString("intHomeScore").toIntOrNull(),e.optString("intAwayScore").toIntOrNull(),e.optString("strProgress").ifBlank{null},e.optString("intRound").ifBlank{null},e.optString("strVenue").ifBlank{null}))
  }}.distinctBy{it.id}
 }
 private fun request(url:String):JSONObject{val q=Request.Builder().url(url).header("User-Agent","FreedomIPTV/1.0").build();client.newCall(q).execute().use{r->if(!r.isSuccessful)return JSONObject();return JSONObject(r.body?.string().orEmpty())}}
}