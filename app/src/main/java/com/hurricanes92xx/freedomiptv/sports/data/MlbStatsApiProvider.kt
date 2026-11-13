package com.hurricanes92xx.freedomiptv.sports.data
import com.hurricanes92xx.freedomiptv.sports.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit
class MlbStatsApiProvider(private val client:OkHttpClient=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(7,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS).build()){
 suspend fun schedule(from:Long,to:Long):List<Game> = withContext(Dispatchers.IO){
 val s=Instant.ofEpochMilli(from).toString().take(10);val e=Instant.ofEpochMilli(to).toString().take(10)
 runCatching{client.newCall(Request.Builder().url("https://statsapi.mlb.com/api/v1/schedule?sportId=1&startDate=$s&endDate=$e&hydrate=team,venue").build()).execute().use{r->
 if(!r.isSuccessful)return@use emptyList<Game>();val ds=JSONObject(r.body?.string().orEmpty()).optJSONArray("dates")?:return@use emptyList<Game>()
 buildList{for(d in 0 until ds.length()){val gs=ds.optJSONObject(d)?.optJSONArray("games")?:continue;for(i in 0 until gs.length()){val g=gs.optJSONObject(i)?:continue;val ts=g.optJSONObject("teams")?:continue;val h=ts.optJSONObject("home")?:continue;val a=ts.optJSONObject("away")?:continue;val ht=h.optJSONObject("team")?:continue;val at=a.optJSONObject("team")?:continue;val t=runCatching{Instant.parse(g.optString("gameDate")).toEpochMilli()}.getOrDefault(0L);if(t !in from..to)continue;val st=g.optJSONObject("status")?.optString("abstractGameState");val state=when(st){"Live"->GameState.LIVE;"Final"->GameState.FINAL;else->GameState.SCHEDULED};add(Game(g.optString("gamePk"),"mlb",Team(ht.optString("id"),ht.optString("name"),null,null,"mlb"),Team(at.optString("id"),at.optString("name"),null,null,"mlb"),t,state,h.optInt("score").takeIf{!h.isNull("score")},a.optInt("score").takeIf{!a.isNull("score")},g.optJSONObject("status")?.optString("detailedState"),null,g.optJSONObject("venue")?.optString("name"),g.optString("seriesDescription").takeIf{it.isNotBlank()}))}}}
 }}.getOrDefault(emptyList())
 }
}