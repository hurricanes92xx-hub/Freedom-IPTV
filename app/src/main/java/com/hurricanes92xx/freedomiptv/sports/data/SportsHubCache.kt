package com.hurricanes92xx.freedomiptv.sports.data

import android.content.Context
import com.hurricanes92xx.freedomiptv.sports.model.HubEventCard
import com.hurricanes92xx.freedomiptv.sports.model.SportsHubSnapshot
import org.json.JSONArray
import org.json.JSONObject

/** Small local-first cache. Refresh/network work belongs to adapters, never the UI render path. */
class SportsHubCache(context:Context) {
 private val prefs=context.getSharedPreferences("freedom_sports_hub_cache",Context.MODE_PRIVATE)
 fun read():SportsHubSnapshot {
  val raw=prefs.getString("events","[]") ?: "[]"
  val events=runCatching { val a=JSONArray(raw); (0 until a.length()).mapNotNull { i ->
   val o=a.optJSONObject(i) ?: return@mapNotNull null
   runCatching { HubEventCard(o.getString("id"),o.getString("title"),o.optString("subtitle"),o.optLong("start"),o.getString("sport"),com.hurricanes92xx.freedomiptv.sports.model.HubEventPresentation.valueOf(o.getString("presentation")),o.optString("home").ifBlank{null},o.optString("away").ifBlank{null},o.optString("homeLogo").ifBlank{null},o.optString("awayLogo").ifBlank{null},o.optString("poster").ifBlank{null},o.optString("network").ifBlank{null},o.optString("channel").ifBlank{null},o.optBoolean("live"),o.optString("score").ifBlank{null},o.optString("status").ifBlank{null}) }.getOrNull()
  } }.getOrDefault(emptyList())
  return SportsHubSnapshot(events,prefs.getLong("updated",0L).takeIf{it>0},false,prefs.getString("error",null))
 }
 fun write(events:List<HubEventCard>,updatedAtUtcMillis:Long=System.currentTimeMillis()) {
  val a=JSONArray(); events.forEach { e -> a.put(JSONObject().put("id",e.id).put("title",e.title).put("subtitle",e.subtitle).put("start",e.startUtcMillis).put("sport",e.sportId).put("presentation",e.presentation.name).put("home",e.homeName).put("away",e.awayName).put("homeLogo",e.homeLogo).put("awayLogo",e.awayLogo).put("poster",e.posterUrl).put("network",e.network).put("channel",e.channelId).put("live",e.isLive).put("score",e.score).put("status",e.status)) }
  prefs.edit().putString("events",a.toString()).putLong("updated",updatedAtUtcMillis).remove("error").apply()
 }
 fun setError(message:String){prefs.edit().putString("error",message.take(300)).apply()}
 fun clear(){prefs.edit().clear().apply()}
}
