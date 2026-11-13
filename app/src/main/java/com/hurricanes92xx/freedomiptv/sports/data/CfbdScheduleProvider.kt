package com.hurricanes92xx.freedomiptv.sports.data
import com.hurricanes92xx.freedomiptv.sports.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray
import java.time.*
import java.util.concurrent.TimeUnit
class CfbdScheduleProvider(private val apiKey:String,private val client:OkHttpClient=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(7,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS).build()){
 suspend fun schedule(from:Long,to:Long,division:Competition=Competition.NCAAF_FBS):List<Game> = withContext(Dispatchers.IO){
 val a=Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC).toLocalDate();val b=Instant.ofEpochMilli(to).atZone(ZoneOffset.UTC).toLocalDate()
 if(apiKey.isBlank() || b.year-a.year>1)return@withContext emptyList()
 val seasonYear=if(a.monthValue<2)b.year else a.year
 runCatching{client.newCall(Request.Builder().url("https://api.collegefootballdata.com/games?year="+seasonYear+"&seasonType=both&startDate="+a+"&endDate="+b).header("Authorization","Bearer "+apiKey).build()).execute().use{r->
 if(!r.isSuccessful)return@use emptyList<Game>();val arr=JSONArray(r.body?.string().orEmpty())
 buildList{for(i in 0 until arr.length()){val g=arr.optJSONObject(i)?:continue;val t=runCatching{Instant.parse(g.optString("startDate")).toEpochMilli()}.getOrDefault(0L);if(t !in from..to)continue;val h=g.optString("homeTeam");val v=g.optString("awayTeam");if(h.isBlank()||v.isBlank())continue;val hc=g.optString("homeClassification").lowercase();val ac=g.optString("awayClassification").lowercase();val hasFbs=hc=="fbs"||ac=="fbs";val hasFcs=hc=="fcs"||ac=="fcs";if(division==Competition.NCAAF_FCS&&!hasFcs)continue;if(division==Competition.NCAAF_FBS&&!hasFbs)continue;val status=g.optString("status").lowercase();val state=when{g.optBoolean("completed")||status.contains("final")||status.contains("complete")->GameState.FINAL;status.contains("in progress")||status.contains("live")->GameState.LIVE;status.contains("postpon")->GameState.POSTPONED;status.contains("cancel")->GameState.CANCELED;else->GameState.SCHEDULED};add(Game(g.optString("id"),division.id,Team(g.optString("homeId"),h,null,null,division.id),Team(g.optString("awayId"),v,null,null,division.id),t,state,g.optInt("homePoints").takeIf{!g.isNull("homePoints")},g.optInt("awayPoints").takeIf{!g.isNull("awayPoints")},null,g.optString("week"),g.optString("venue")))}}
 }}.getOrDefault(emptyList())
 }
}