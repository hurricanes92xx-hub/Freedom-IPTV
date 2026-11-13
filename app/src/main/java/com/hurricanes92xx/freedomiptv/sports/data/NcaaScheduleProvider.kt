package com.hurricanes92xx.freedomiptv.sports.data

import com.hurricanes92xx.freedomiptv.sports.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Unofficial HenryGD NCAA scoreboard adapter; gracefully returns empty on unsupported seasons/sport routes. */
class NcaaScheduleProvider(
 private val client:OkHttpClient=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(7,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS).build()
){
 private val routes=mapOf(
  Competition.NCAAF_FBS to "football/fbs",Competition.NCAAF_FCS to "football/fcs",
  Competition.NCAAM_D1 to "basketball-men/d1",Competition.NCAAM_D2 to "basketball-men/d2",Competition.NCAAM_D3 to "basketball-men/d3",
  Competition.NCAAW_D1 to "basketball-women/d1",Competition.NCAAW_D2 to "basketball-women/d2",Competition.NCAAW_D3 to "basketball-women/d3",
  Competition.NCAAVB_W to "volleyball-women/d1",Competition.NCAABASE to "baseball/d1",Competition.NCAAGYM_W to "gymnastics-women/d1"
 )
 suspend fun schedule(competition:Competition,fromUtcMillis:Long,toUtcMillis:Long):List<Game> = withContext(Dispatchers.IO){
  val route=routes[competition]?:return@withContext emptyList()
  // NCAA football scoreboard uses season/week paths, not calendar dates. ESPN is the primary football feed.
  if(competition==Competition.NCAAF_FBS||competition==Competition.NCAAF_FCS)return@withContext emptyList()
  if(toUtcMillis<fromUtcMillis)return@withContext emptyList()
  val first=Instant.ofEpochMilli(fromUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
  val last=Instant.ofEpochMilli(toUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
  val out=mutableListOf<Game>();var day=first
  while(!day.isAfter(last)&&out.size<500){
   val date=day.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
   runCatching{
    val req=Request.Builder().url("https://ncaa-api.henrygd.me/scoreboard/$route/$date").header("User-Agent","FreedomIPTV/1.0").build()
    client.newCall(req).execute().use{response->
     if(!response.isSuccessful)return@use
     val root=JSONObject(response.body?.string().orEmpty());val games=root.optJSONArray("games")?:return@use
     for(i in 0 until games.length()){
      val g=games.optJSONObject(i)?:continue;val game=g.optJSONObject("game")?:g
      val teams=game.optJSONObject("teams")?:g.optJSONObject("teams")?:continue
      val away=teams.optJSONObject("away")?:continue;val home=teams.optJSONObject("home")?:continue
      val kickoff=runCatching{Instant.parse(game.optString("startTime").ifBlank{game.optString("startDate")}).toEpochMilli()}.getOrDefault(0L)
      if(kickoff !in fromUtcMillis..toUtcMillis)continue
      fun team(o:JSONObject,side:String)=Team(o.optString("id").ifBlank{o.optString("teamId").ifBlank{o.optString("name")}},o.optString("name").ifBlank{o.optString("teamName")},o.optString("shortName").ifBlank{null},o.optString("logo").ifBlank{o.optString("logoUrl").ifBlank{null}},competition.id)
      val status=game.optString("gameState").lowercase()
      val state=when{status.contains("live")||status.contains("progress")->GameState.LIVE;status.contains("final")||status.contains("complete")->GameState.FINAL;status.contains("postpon")->GameState.POSTPONED;else->GameState.SCHEDULED}
      out.add(Game(game.optString("gameID").ifBlank{game.optString("id")},competition.id,team(home,"home"),team(away,"away"),kickoff,state,home.optString("score").toIntOrNull(),away.optString("score").toIntOrNull(),game.optString("currentPeriod").ifBlank{null},game.optString("contestClock").ifBlank{null},game.optString("venue").ifBlank{null},game.optString("bracketRound").takeIf{it.isNotBlank() && it!="0"}?.let{"Round $it"} ?: game.optString("roundDescription").ifBlank{null} ?: game.optString("finalMessage").takeIf{it.contains("champion",true)||it.contains("championship",true)}?.let{"Championship"}))
     }
    }
   }
   day=day.plusDays(1)
  }
  out.distinctBy{it.id}.sortedBy{it.startUtcMillis}
 }
}
