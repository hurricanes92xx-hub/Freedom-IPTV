package com.hurricanes92.freedomiptv.sports.data
import com.hurricanes92xx.freedomiptv.sports.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray
import java.time.*
import java.util.concurrent.TimeUnit
class CfbdScheduleProvider(private val apiKey:String,private val client:OkHttpClient=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(7,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS).build()){
 suspend fun schedule(from:Long,to:Long):List<Game> = withContext(Dispatchers.IO){
 val a=Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC).toLocalDate();val b=Instant.ofEpochMilli(to).atZone(ZoneOffset.UTC).toLocalDate()
 runCatching{client.newCall(Request.Builder().url("https://api.collegefootballdata.com/games?year="+a.year+"&seasonType=regular&startDate="+a+"&endDate="+b).header("Authorization","Bearer "+apiKey).build()).execute().use{r->
 if(!r.isSuccessful)return@use emptyList<Game>();val arr=JSONArray(r.body?.string().orEmpty())
 buildList{for(i in 0 until arr.length()){val g=arr.optJSONObject(i)?:continue;val t=runCatching{Instant.parse(g.optString("startDate")).toEpochMilli()}.getOrDefault(0L);if(t !in from..to)continue;val h=g.optString("homeTeam");val v=g.optString("awayTeam");if(h.isBlank()||v.isBlank())continue;val state=if(g.optBoolean("completed"))GameState.FINAL else if(t<=System.currentTimeMillis())GameState.LIVE else GameState.SCHEDULED;add(Game(g.optString("id"),"ncaaf-fbs",Team(g.optString("homeId"),h,null,null,"ncaaf-fbs"),Team(g.optString("awayId"),v,null,null,"ncaaf-fbs"),t,state,g.optInt("homePoints").takeIf{!g.isNull("homePoints")},g.optInt("awayPoints").takeIf{!g.isNull("awayPoints")},null,g.optString("week"),g.optString("venue")))}}
 }}.getOrDefault(emptyList())
 }
}