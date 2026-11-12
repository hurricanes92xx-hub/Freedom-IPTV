package com.hurricanes92xx.freedomiptv.sports.model

/** Canonical competition catalog. College scope intentionally stays limited. */
enum class Sport(val label:String) { FOOTBALL("Football"),BASKETBALL("Basketball"),VOLLEYBALL("Volleyball"),BASEBALL("Baseball"),GYMNASTICS("Gymnastics"),SOCCER("Soccer"),HOCKEY("Hockey"),COMBAT("Combat") }
enum class Competition(val id:String,val label:String,val sport:Sport,val college:Boolean=false) {
 NFL("nfl","NFL",Sport.FOOTBALL),NBA("nba","NBA",Sport.BASKETBALL),MLB("mlb","MLB",Sport.BASEBALL),NHL("nhl","NHL",Sport.HOCKEY),WNBA("wnba","WNBA",Sport.BASKETBALL),MLS("mls","MLS",Sport.SOCCER),
 NCAAF_FBS("ncaaf-fbs","NCAA Football FBS",Sport.FOOTBALL,true),NCAAF_FCS("ncaaf-fcs","NCAA Football FCS",Sport.FOOTBALL,true),
 NCAAM_D1("ncaam-d1","NCAA Men's Basketball D1",Sport.BASKETBALL,true),NCAAM_D2("ncaam-d2","NCAA Men's Basketball D2",Sport.BASKETBALL,true),NCAAM_D3("ncaam-d3","NCAA Men's Basketball D3",Sport.BASKETBALL,true),
 NCAAW_D1("ncaaw-d1","NCAA Women's Basketball D1",Sport.BASKETBALL,true),NCAAW_D2("ncaaw-d2","NCAA Women's Basketball D2",Sport.BASKETBALL,true),NCAAW_D3("ncaaw-d3","NCAA Women's Basketball D3",Sport.BASKETBALL,true),
 NCAAVB_W("ncaavb-w","NCAA Women's Volleyball",Sport.VOLLEYBALL,true),NCAABASE("ncaabase","NCAA Baseball",Sport.BASEBALL,true),NCAAGYM_W("ncaagym-w","NCAA Women's Gymnastics",Sport.GYMNASTICS,true),
 EPL("epl","Premier League",Sport.SOCCER),UCL("ucl","Champions League",Sport.SOCCER),UFC("ufc","UFC",Sport.COMBAT),BOXING("boxing","Boxing",Sport.COMBAT),WWE("wwe","WWE",Sport.COMBAT),AEW("aew","AEW",Sport.COMBAT),F1("f1","Formula 1",Sport.MOTORSPORT),NASCAR("nascar","NASCAR",Sport.MOTORSPORT),ATP("atp","ATP Tennis",Sport.TENNIS),WTA("wta","WTA Tennis",Sport.TENNIS),PGA("pga","PGA Tour",Sport.GOLF),LPGA("lpga","LPGA",Sport.GOLF)
}
enum class GameState { SCHEDULED,LIVE,FINAL,POSTPONED,CANCELED,UNKNOWN }
data class Team(val id:String,val name:String,val shortName:String?=null,val logoUrl:String?=null,val competitionId:String)
data class Game(val id:String,val competitionId:String,val home:Team,val away:Team,val startUtcMillis:Long,val state:GameState=GameState.SCHEDULED,val homeScore:Int?=null,val awayScore:Int?=null,val clock:String?=null,val period:String?=null,val venue:String?=null)
data class ChannelCandidate(val channelId:String,val channelName:String,val confidence:Int,val reasons:List<String>)

/** Wrestling cards use promotion/event/bout semantics, not team-vs-team games. */
data class CombatBout(val id:String,val participants:List<String>,val weightClass:String?=null,val isMainEvent:Boolean=false)
data class CombatEvent(val id:String,val promotionId:String,val title:String,val startUtcMillis:Long,val posterUrl:String?=null,val bouts:List<CombatBout> = emptyList(),val state:GameState=GameState.SCHEDULED)
enum class CombatPromotion(val id:String,val label:String){WWE("wwe","WWE"),AEW("aew","AEW"),TNA("tna","TNA Wrestling")}
