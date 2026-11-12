package com.hurricanes92.freedomiptv.provider.xtream

import com.hurricanes92.freedomiptv.provider.xtream.XtreamCredentials

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class XtreamVodCategory(val id: String, val name: String)
data class XtreamVodItem(
    val id: String, val name: String, val categoryId: String?,
    val poster: String?, val plot: String?, val rating: String?,
    val extension: String?, val containerExtension: String?
)
data class XtreamEpisode(
    val id: String, val title: String, val season: Int, val episode: Int,
    val containerExtension: String?, val plot: String?
)

/** Xtream VOD/series catalog and playback URL adapter. Never logs credentials. */
class XtreamVodApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private fun actionRequest(c: XtreamCredentials, action: String, extra: Map<String, String> = emptyMap()): Request {
        require(c.server.trim().startsWith("https://")) { "Use an HTTPS provider URL." }
        val builder = (c.server.trim().trimEnd('/') + "/player_api.php").toHttpUrl()
            .newBuilder()
            .addQueryParameter("username", c.username)
            .addQueryParameter("password", c.password)
            .addQueryParameter("action", action)
        extra.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return Request.Builder().url(builder.build()).get().build()
    }

    private suspend fun json(c: XtreamCredentials, action: String): JSONArray = withContext(Dispatchers.IO) {
        client.newCall(actionRequest(c, action)).execute().use { response ->
            if (!response.isSuccessful) error("$action failed (HTTP ${response.code}).")
            JSONArray(response.body?.string().orEmpty())
        }
    }

    suspend fun movieCategories(c: XtreamCredentials) = parseCategories(json(c, "get_vod_categories"))
    suspend fun seriesCategories(c: XtreamCredentials) = parseCategories(json(c, "get_series_categories"))
    suspend fun movies(c: XtreamCredentials) = parseItems(json(c, "get_vod_streams"), false)
    suspend fun series(c: XtreamCredentials) = parseItems(json(c, "get_series"), true)

    suspend fun seriesInfo(c: XtreamCredentials, seriesId: String): List<XtreamEpisode> = withContext(Dispatchers.IO) {
        client.newCall(actionRequest(c, "get_series_info", mapOf("series_id" to seriesId))).execute().use { response ->
            if (!response.isSuccessful) error("Series details failed (HTTP ${response.code}).")
            val root = JSONObject(response.body?.string().orEmpty())
            val seasons = root.optJSONObject("episodes") ?: JSONObject()
            buildList {
                val keys = seasons.keys()
                while (keys.hasNext()) {
                    val seasonKey = keys.next()
                    val list = seasons.optJSONArray(seasonKey) ?: continue
                    for (index in 0 until list.length()) {
                        val episode = list.optJSONObject(index) ?: continue
                        val id = episode.opt("id")?.toString()?.takeIf(String::isNotBlank) ?: continue
                        add(XtreamEpisode(
                            id = id,
                            title = episode.optString("title", "Episode $id"),
                            season = episode.optInt("season", seasonKey.toIntOrNull() ?: 0),
                            episode = episode.optInt("episode_num", index + 1),
                            containerExtension = episode.optString("container_extension").takeIf(String::isNotBlank),
                            plot = episode.optString("plot").takeIf(String::isNotBlank)
                        ))
                    }
                }
            }
        }
    }

    private fun parseCategories(array: JSONArray) = buildList {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.opt("category_id")?.toString()?.takeIf(String::isNotBlank) ?: continue
            add(XtreamVodCategory(id, obj.optString("category_name", "Category $id")))
        }
    }

    private fun parseItems(array: JSONArray, isSeries: Boolean) = buildList {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.opt(if (isSeries) "series_id" else "stream_id")?.toString()?.takeIf(String::isNotBlank) ?: continue
            val extension = obj.optString("container_extension").takeIf(String::isNotBlank)
            add(XtreamVodItem(
                id = id,
                name = obj.optString("name", "Title $id"),
                categoryId = obj.opt("category_id")?.toString(),
                poster = obj.optString("cover").takeIf(String::isNotBlank),
                plot = obj.optString("plot").takeIf(String::isNotBlank),
                rating = obj.opt("rating")?.toString()?.takeIf(String::isNotBlank),
                extension = extension,
                containerExtension = extension
            ))
        }
    }

    fun movieUrl(c: XtreamCredentials, item: XtreamVodItem) =
        "${c.server.trimEnd('/')}/movie/${encode(c.username)}/${encode(c.password)}/${item.id}.${item.containerExtension ?: item.extension ?: "mp4"}"

    fun episodeUrl(c: XtreamCredentials, episode: XtreamEpisode) =
        "${c.server.trimEnd('/')}/series/${encode(c.username)}/${encode(c.password)}/${episode.id}.${episode.containerExtension ?: "mp4"}"

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
}
