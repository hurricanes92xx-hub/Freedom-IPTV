package com.hurricanes92xx.freedomiptv.sports.model

/** User-approved Freedom Sports Hub scope; excluded categories are intentionally absent. */
enum class HubSection(val title:String) { FOR_YOU("For You"),LIVE("Live"),UPCOMING("Upcoming"),REPLAYS("Replays") }
enum class HubSport(val id:String,val title:String,val emoji:String) {
 NFL("football","Football","🏈"), BASKETBALL("basketball","Basketball","🏀"), BASEBALL("baseball","Baseball","⚾"),
 HOCKEY("hockey","Hockey","🏒"), SOCCER("soccer","Soccer","⚽"), VOLLEYBALL("volleyball","Volleyball","🏐"),
 GYMNASTICS("gymnastics","Gymnastics","🤸"), MMA("mma","MMA","🥊"), BOXING("boxing","Boxing","🥊"), WRESTLING("wrestling","Wrestling","🎟")
}
enum class HubPromotion(val id:String,val label:String,val accentHex:Long) {
 WWE("wwe","WWE",0xFFE32636), AEW("aew","AEW",0xFF171717), TNA("tna","TNA",0xFF1768D5)
}
enum class HubEventPresentation { TEAM_MATCHUP,INDIVIDUAL_MATCHUP,TOURNAMENT,COMBAT_CARD }
data class HubEventCard(
 val id:String,val title:String,val subtitle:String,val startUtcMillis:Long,
 val sportId:String,val presentation:HubEventPresentation,
 val homeName:String?=null,val awayName:String?=null,val homeLogo:String?=null,val awayLogo:String?=null,
 val posterUrl:String?=null,val network:String?=null,val channelId:String?=null,
 val isLive:Boolean=false,val score:String?=null,val status:String?=null
)
/** High-level screen state can be rendered immediately from local cache before refresh completes. */
data class SportsHubSnapshot(val events:List<HubEventCard> = emptyList(),val updatedAtUtcMillis:Long?=null,val refreshing:Boolean=false,val error:String?=null)
