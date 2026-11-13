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
 private data class BracketRoute(val sport:String,val division:String)
 private val bracketRoutes=mapOf(
  Competition.NCAAM_D1 to BracketRoute("basketball-men","d1"), Competition.NCAAM_D2 to BracketRoute("basketball-men","d2"), Competition.NCAAM_D3 to BracketRoute("basketball-men","d3"),
  Competition.NCAAW_D1 to BracketRoute("basketball-women","d1"), Competition.NCAAW_D2 to BracketRoute("basketball-women","d2"), Competition.NCAAW_D3 to BracketRoute("basketball-women","d3"),
  Competition.NCAAVB_W to BracketRoute("volleyball-women","d1"), Competition.NCAABASE to BracketRoute("baseball-men","d1")
 )
 private fun bracketGames(root:JSONObject,c:Competition,from:Long,to:Long):List<Game>{
  val out=mutableListOf<Game>()
  val championships=root.optJSONArray("championships")?:org.json.JSONArray()
  for(ci in 0 until championships.length()){
   val championship=championships.optJSONObject(ci)?:continue
   val games=championship.optJSONArray("games")?:continue
   for(gi in 0 until games.length()){
    val game=games.optJSONObject(gi)?:continue
    val teams=game.optJSONArray("teams")?:continue
    if(teams.length()<2)continue
    val first=teams.optJSONObject(0)?:continue
    val second=teams.optJSONObject(1)?:continue
    val home=if(first.optBoolean("isHome"))first else if(second.optBoolean("isHome"))second else second
    val away=if(home===first)second else first
    val homeName=home.optString("nameShort").ifBlank{home.optString("nameFull")}
    val awayName=away.optString("nameShort").ifBlank{away.optString("nameFull")}
    val epoch=game.optLong("startTimeEpoch",0L)
    val millis=if(epoch>0)if(epoch<100000000000L)epoch*1000 else epoch else 0L
    if(homeName.isBlank()||awayName.isBlank()||millis !in from..to)continue
    fun team(obj:JSONObject,name:String)=Team(obj.optString("seoname").ifBlank{name},name,obj.optString("nameShort").ifBlank{null},obj.optString("logoUrl").ifBlank{null},c.id)
    val status=game.optString("statusCodeDisplay").ifBlank{game.optString("gameState")}.lowercase()
    val state=when{status.contains("live")||status=="p"||status=="in progress"->GameState.LIVE;status.contains("final")||status=="f"||status=="complete"->GameState.FINAL;else->GameState.SCHEDULED}
    val id=game.optString("contestId").ifBlank{game.optString("gameID").ifBlank{c.id+"_"+millis+"_"+homeName+"_"+awayName}}
    val position=game.optInt("bracketPositionId",0)
    val inferredRound=when(position/100){1->"First Round";2->"Second Round";3->"Sweet 16";4->"Elite Eight";5->"Final Four";6->"Championship";else->""}
    val round=game.optString("roundName").ifBlank{game.optString("round").ifBlank{inferredRound.ifBlank{"NCAA Tournament"}}}
    out.add(Game(id,c.id,team(home,homeName),team(away,awayName),millis,state,home.optInt("score").takeIf{home.has("score")},away.optInt("score").takeIf{away.has("score")},game.optString("currentPeriod").ifBlank{null},game.optString("contestClock").ifBlank{null},null,round))
   }
  }
  return out.distinctBy{it.id}.sortedBy{it.startUtcMillis}
 }
 private suspend fun brackets(c:Competition,from:Long,to:Long):List<Game>{
  val route=bracketRoutes[c]?:return emptyList()
  val year=Instant.ofEpochMilli(to).atZone(ZoneOffset.UTC).year
  return (year-1..year+1).flatMap{y->
   runCatching{
    val url="https://ncaa-api.henrygd.me/brackets/"+route.sport+"/"+route.division+"/"+y
    val request=Request.Builder().url(url).header("User-Agent","FreedomIPTV/1.0").build()
    client.newCall(request).execute().use{response->if(!response.isSuccessful)emptyList()else bracketGames(JSONObject(response.body?.string().orEmpty()),c,from,to)}
   }.getOrDefault(emptyList())
  }.distinctBy{it.id}
 }
 suspend fun schedule(competition:Competition,fromUtcMillis:Long,toUtcMillis:Long):List<Game> = withContext(Dispatchers.IO){
  val route=routes[competition]?:return@withContext emptyList()
  // NCAA football scoreboard uses season/week paths, not calendar dates. ESPN is the primary football feed.
  if(competition==Competition.NCAAF_FBS||competition==Competition.NCAAF_FCS)return@withContext emptyList()
  if(toUtcMillis<fromUtcMillis)return@withContext emptyList()
  val bracket=brackets(competition,fromUtcMillis,toUtcMillis)
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
  (out+bracket).distinctBy{it.id}.sortedBy{it.startUtcMillis}
 }
}
