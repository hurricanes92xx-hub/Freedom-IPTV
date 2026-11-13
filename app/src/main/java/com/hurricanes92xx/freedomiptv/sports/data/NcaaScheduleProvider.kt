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
 private val bracketRoutes=mapOf(Competition.NCAAM_D1 to BracketRoute("basketball-men","d1"),Competition.NCAAM_D2 to BracketRoute("basketball-men","d2"),Competition.NCAAM_D3 to BracketRoute("basketball-men","d3"),Competition.NCAAW_D1 to BracketRoute("basketball-women","d1"),Competition.NCAAW_D2 to BracketRoute("basketball-women","d2"),Competition.NCAAW_D3 to BracketRoute("basketball-women","d3"),Competition.NCAAVB_W to BracketRoute("volleyball-women","d1"),Competition.NCAABASE to BracketRoute("baseball-men","d1"))
 private fun bracketGames(root:JSONObject,c:Competition,from:Long,to:Long):List<Game>{
  val out=mutableListOf<Game>();fun walk(v:Any?,round:String?=null){when(v){
   is JSONObject->{val r=listOf("round","roundName","roundTitle","name","title","label","bracketRound").firstNotNullOfOrNull{v.optString(it).takeIf{z->z.isNotBlank()&&z!="null"}}?:round;val h=v.optJSONObject("home")?:v.optJSONObject("homeTeam")?:v.optJSONObject("team1");val a=v.optJSONObject("away")?:v.optJSONObject("awayTeam")?:v.optJSONObject("team2");
    if(h!=null&&a!=null){val hn=listOf("name","teamName","displayName","school").firstNotNullOfOrNull{h.optString(it).takeIf(String::isNotBlank)};val an=listOf("name","teamName","displayName","school").firstNotNullOfOrNull{a.optString(it).takeIf(String::isNotBlank)};val ds=listOf("startDate","startTime","date","gameDate","scheduled").firstNotNullOfOrNull{v.optString(it).takeIf(String::isNotBlank)};val ms=runCatching{Instant.parse(ds).toEpochMilli()}.getOrDefault(0L);if(hn!=null&&an!=null&&ms in from..to){fun team(o:JSONObject,n:String)=Team(o.optString("id").ifBlank{o.optString("teamId").ifBlank{n}},n,o.optString("shortName").ifBlank{null},o.optString("logo").ifBlank{null},c.id);val s=v.optString("gameState").lowercase();val state=when{s.contains("live")||s.contains("progress")->GameState.LIVE;s.contains("final")||s.contains("complete")->GameState.FINAL;else->GameState.SCHEDULED};val gid=v.optString("gameID").ifBlank{v.optString("id").ifBlank{c.id+"_"+ms+"_"+hn+"_"+an}};out.add(Game(gid,c.id,team(h,hn),team(a,an),ms,state,h.optString("score").toIntOrNull(),a.optString("score").toIntOrNull(),null,null,v.optString("venue").ifBlank{null},r))}}
    val keys=v.keys();while(keys.hasNext()){val k=keys.next();val x=v.opt(k);if(x is JSONObject||x is org.json.JSONArray)walk(x,r)}}
   is org.json.JSONArray->for(i in 0 until v.length())walk(v.opt(i),round)}};walk(root);return out.distinctBy{it.id}.sortedBy{it.startUtcMillis}}
 private suspend fun brackets(c:Competition,from:Long,to:Long):List<Game>{val route=bracketRoutes[c]?:return emptyList();val year=Instant.ofEpochMilli(to).atZone(ZoneOffset.UTC).year;return (year-1..year+1).flatMap{y->runCatching{val url="https://ncaa-api.henrygd.me/brackets/"+route.sport+"/"+route.division+"/"+y;val req=Request.Builder().url(url).header("User-Agent","FreedomIPTV/1.0").build();client.newCall(req).execute().use{res->if(!res.isSuccessful)emptyList()else bracketGames(JSONObject(res.body?.string().orEmpty()),c,from,to)}}.getOrDefault(emptyList())}.distinctBy{it.id}}
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
