package com.hurricanes92xx.freedomiptv.provider.xtream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class XtreamVodCategory(val id:String,val name:String)
data class XtreamVodItem(val id:String,val name:String,val categoryId:String?,val poster:String?,val plot:String?,val rating:String?,val extension:String?,val containerExtension:String?)\ndata class XtreamEpisode(val id:String,val title:String,val season:Int,val episode:Int,val containerExtension:String?,val plot:String?,val image:String?)

/** Xtream VOD and series catalog adapter. Credentials are used only in request URLs, never logged. */
class XtreamVodApi(private val client:OkHttpClient=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build()) {
 private fun request(credentials:XtreamCredentials,action:String):Request {
  val base=credentials.server.trim().trimEnd('/')
  require(base.startsWith("https://")) { "Use an HTTPS provider URL." }
  val url=(base+"/player_api.php").toHttpUrl().newBuilder().addQueryParameter("username",credentials.username).addQueryParameter("password",credentials.password).addQueryParameter("action",action).build()
  return Request.Builder().url(url).get().build()
 }
 private suspend fun array(credentials:XtreamCredentials,action:String)=withContext(Dispatchers.IO) {
  client.newCall(request(credentials,action)).execute().use { response ->
   if(!response.isSuccessful) error("$action failed (HTTP ${response.code}).")
   JSONArray(response.body?.string().orEmpty())
  }
 }
 suspend fun movieCategories(c:XtreamCredentials)=categories(array(c,"get_vod_categories"))
 suspend fun seriesCategories(c:XtreamCredentials)=categories(array(c,"get_series_categories"))
 suspend fun movies(c:XtreamCredentials)=items(array(c,"get_vod_streams"),false)
 suspend fun series(c:XtreamCredentials)=items(array(c,"get_series"),true)\n suspend fun seriesInfo(c:XtreamCredentials,seriesId:String):List<XtreamEpisode> = withContext(Dispatchers.IO) {\n  val base=c.server.trim().trimEnd().toHttpUrl().newBuilder().addPathSegment("player_api.php").addQueryParameter("username",c.username).addQueryParameter("password",c.password).addQueryParameter("action","get_series_info").addQueryParameter("series_id",seriesId).build()\n  client.newCall(Request.Builder().url(base).get().build()).execute().use { response ->\n   if(!response.isSuccessful) error("Series details failed (HTTP ${response.code}).")\n   val root=org.json.JSONObject(response.body?.string().orEmpty()); val seasons=root.optJSONArray("episodes")?:JSONArray()\n   buildList { for(si in 0 until seasons.length()){val arr=seasons.optJSONArray(si)?:continue;for(ei in 0 until arr.length()){val o=arr.optJSONObject(ei)?:continue;val id=o.opt("id")?.toString()?.takeIf{it.isNotBlank()}?:continue;add(XtreamEpisode(id,o.optString("title","Episode $id"),o.optInt("season",0),o.optInt("episode_num",ei+1),o.optString("container_extension").takeIf{it.isNotBlank()},o.optString("plot").takeIf{it.isNotBlank()},o.optString("info").let{try{org.json.JSONObject(it).optString("movie_image").takeIf{v->v.isNotBlank()}}catch(_:Exception){null}}))}}\n  }\n }\n fun episodeUrl(c:XtreamCredentials,e:XtreamEpisode):String="${c.server.trimEnd()}/series/${enc(c.username)}/${enc(c.password)}/${e.id}.${e.containerExtension?: "mp4"}"
 private fun categories(a:JSONArray)=buildList { for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;val id=o.opt("category_id")?.toString()?.takeIf{it.isNotBlank()}?:continue;add(XtreamVodCategory(id,o.optString("category_name","Category $id")))} }
 private fun items(a:JSONArray,isSeries:Boolean)=buildList { for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;val id=o.opt(if(isSeries)"series_id" else "stream_id")?.toString()?.takeIf{it.isNotBlank()}?:continue;add(XtreamVodItem(id,o.optString("name","Title $id"),o.opt("category_id")?.toString(),o.optString("cover").takeIf{it.isNotBlank()},o.optString("plot").takeIf{it.isNotBlank()},o.opt("rating")?.toString()?.takeIf{it.isNotBlank()},o.optString("container_extension").takeIf{it.isNotBlank()},o.optString("container_extension").takeIf{it.isNotBlank()}))} }
 fun movieUrl(c:XtreamCredentials,item:XtreamVodItem):String="${c.server.trimEnd('/')}/movie/${enc(c.username)}/${enc(c.password)}/${item.id}.${item.containerExtension?:item.extension?: "mp4"}"
 private fun enc(s:String)=URLEncoder.encode(s,Charsets.UTF_8.name())
}
