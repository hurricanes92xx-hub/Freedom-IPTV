package com.hurricanes92xx.freedomiptv.provider.xtream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class XtreamVodCategory(val id:String,val name:String)
data class XtreamVodItem(val id:String,val name:String,val categoryId:String?,val poster:String?,val plot:String?,val rating:String?,val extension:String?,val containerExtension:String?)
data class XtreamEpisode(val id:String,val title:String,val episodeNumber:String,val seasonNumber:String,val extension:String?,val plot:String?)
data class XtreamSeason(val number:String,val name:String,val episodes:List<XtreamEpisode>)

/** Xtream VOD and series catalog adapter. Credentials are used only in request URLs, never logged. */
class XtreamVodApi(private val client:OkHttpClient=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build()) {
 private fun request(credentials:XtreamCredentials,action:String,extra:Map<String,String> = emptyMap()):Request {
  val base=credentials.server.trim().trimEnd('/')
  require(base.startsWith("https://")) { "Use an HTTPS provider URL." }
  val builder=(base+"/player_api.php").toHttpUrl().newBuilder().addQueryParameter("username",credentials.username).addQueryParameter("password",credentials.password).addQueryParameter("action",action)
  extra.forEach { (k,v)->builder.addQueryParameter(k,v) }
  return Request.Builder().url(builder.build()).get().build()
 }
 private suspend fun json(c:XtreamCredentials,action:String,extra:Map<String,String> = emptyMap())=withContext(Dispatchers.IO) {
  client.newCall(request(c,action,extra)).execute().use { response ->
   if(!response.isSuccessful) error("$action failed (HTTP ${response.code}).")
   JSONObject(response.body?.string().orEmpty())
  }
 }
 private suspend fun array(c:XtreamCredentials,action:String)=withContext(Dispatchers.IO) {
  client.newCall(request(c,action)).execute().use { response ->
   if(!response.isSuccessful) error("$action failed (HTTP ${response.code}).")
   JSONArray(response.body?.string().orEmpty())
  }
 }
 suspend fun movieCategories(c:XtreamCredentials)=categories(array(c,"get_vod_categories"))
 suspend fun seriesCategories(c:XtreamCredentials)=categories(array(c,"get_series_categories"))
 suspend fun movies(c:XtreamCredentials)=items(array(c,"get_vod_streams"),false)
 suspend fun series(c:XtreamCredentials)=items(array(c,"get_series"),true)
 suspend fun seriesSeasons(c:XtreamCredentials,seriesId:String):List<XtreamSeason> = withContext(Dispatchers.IO) {
  val root=json(c,"get_series_info",mapOf("series_id" to seriesId))
  val seasonNames=mutableMapOf<String,String>()
  val seasonArray=root.optJSONArray("seasons")
  if(seasonArray!=null) for(i in 0 until seasonArray.length()) {
   val s=seasonArray.optJSONObject(i)?:continue
   val n=s.opt("season_number")?.toString()?:s.opt("number")?.toString()?:continue
   seasonNames[n]=s.optString("name","Season $n")
  }
  val episodeObj=root.optJSONObject("episodes")?:JSONObject()
  val result=mutableListOf<XtreamSeason>()
  val keys=episodeObj.keys()
  while(keys.hasNext()) {
   val season=keys.next()
   val arr=episodeObj.optJSONArray(season)?:continue
   val eps=mutableListOf<XtreamEpisode>()
   for(i in 0 until arr.length()) {
    val e=arr.optJSONObject(i)?:continue
    val id=e.opt("id")?.toString()?.takeIf{it.isNotBlank()}?:continue
    eps.add(XtreamEpisode(id,e.optString("title","Episode ${e.optString("episode_num",(i+1).toString())}"),e.opt("episode_num")?.toString()?: (i+1).toString(),season,e.optString("container_extension").takeIf{it.isNotBlank()},e.optJSONObject("info")?.optString("plot")?.takeIf{it.isNotBlank()}))
   }
   result.add(XtreamSeason(season,seasonNames[season]?: "Season $season",eps.sortedBy { it.episodeNumber.toIntOrNull()?:0 }))
  }
  result.sortedBy { it.number.toIntOrNull()?:0 }
 }
 private fun categories(a:JSONArray)=buildList { for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;val id=o.opt("category_id")?.toString()?.takeIf{it.isNotBlank()}?:continue;add(XtreamVodCategory(id,o.optString("category_name","Category $id")))} }
 private fun items(a:JSONArray,isSeries:Boolean)=buildList { for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;val id=o.opt(if(isSeries)"series_id" else "stream_id")?.toString()?.takeIf{it.isNotBlank()}?:continue;add(XtreamVodItem(id,o.optString("name","Title $id"),o.opt("category_id")?.toString(),o.optString("cover").takeIf{it.isNotBlank()},o.optString("plot").takeIf{it.isNotBlank()},o.opt("rating")?.toString()?.takeIf{it.isNotBlank()},o.optString("container_extension").takeIf{it.isNotBlank()},o.optString("container_extension").takeIf{it.isNotBlank()}))} }
 fun movieUrl(c:XtreamCredentials,item:XtreamVodItem):String="${c.server.trimEnd('/')}/movie/${enc(c.username)}/${enc(c.password)}/${item.id}.${item.containerExtension?:item.extension?: "mp4"}"
 fun episodeUrl(c:XtreamCredentials,episode:XtreamEpisode):String="${c.server.trimEnd('/')}/series/${enc(c.username)}/${enc(c.password)}/${episode.id}.${episode.extension?: "mp4"}"
 private fun enc(s:String)=URLEncoder.encode(s,Charsets.UTF_8.name())
}
