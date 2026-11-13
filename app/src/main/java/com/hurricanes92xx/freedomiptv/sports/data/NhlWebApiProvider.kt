package com.hurricanes92.freedomiptv.sports.data
import com.hurricanes92xx.freedomiptv.sports.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.time.*
import java.util.concurrent.TimeUnit
class NhLWebApiProvider(private val client:OkHttpClient=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(7,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS).build()){
 suspend fun schedule(from:Long,to:Long):List<Game> = withContext(Dispatchers.IO){
 val a=Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC).toLocalDate();val b=Instant.ofEpochMilli(to).atZone(ZoneOffset.UTC).toLocalDate()
 val dates=generateSequence(a){it.plusDays(1)}.takeWhile{!it.isAfter(b)}.take(15).toList()
 dates.flatMap{date->runCatching{client.newCall(Request.Builder().url("https://api-web.nhle.com/v1/schedule/"+date.toString()).build()).execute().use{r->
 if(!r.isSuccessful)return@use emptyList<Game>();val weeks=JSONObject(r.body?.string().orEmpty()).optJSONArray("gameWeek")?:return@use emptyList<Game>()
 buildList{for(w in 0 until weeks.length()){val gs=weeks.optJSONObject(w)?.optJSONArray("games")?:continue;for(i in 0 until gs.length()){val g=gs.optJSONObject(i)?:continue;val h=g.optJSONObject("homeTeam")?:continue;val v=g.optJSONObject("awayTeam")?:continue;val t=runCatching{Instant.parse(g.optString("startTimeUTC")).toEpochMilli()}.getOrDefault(0L);if(t !in from..to)continue;val state=when(g.optString("gameState")){"LIVE","CRIT"->GameState.LIVE;"OFF"->GameState.FINAL;"PPD"->GameState.POSTPONED;else->GameState.SCHEDULED};add(Game(g.optString("id"),"nhl",Team(h.optString("id"),h.optString("name"),h.optString("abbrev"),null,"nhl"),Team(v.optString("id"),v.optString("name"),v.optString("abbrev"),null,"nhl"),t,state,g.optInt("homeScore").takeIf{!g.isNull("homeScore")},g.optInt("awayScore").takeIf{!g.isNull("awayScore")},g.optString("gameState"),null,null))}}}
 }}.getOrDefault(emptyList())}
 }
}