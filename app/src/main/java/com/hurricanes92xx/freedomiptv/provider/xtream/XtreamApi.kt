package com.hurricanes92xx.freedomiptv.provider.xtream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class XtreamCredentials(val server: String, val username: String, val password: String)
data class XtreamCategory(val id: String, val name: String)
data class XtreamLiveChannel(val id: String, val name: String, val categoryId: String?, val icon: String?, val epgId: String?, val streamUrl: String)
data class XtreamLiveCatalog(val categories: List<XtreamCategory>, val channels: List<XtreamLiveChannel>)
data class XtreamProgramme(val id: String?, val title: String, val description: String, val startEpochSeconds: Long, val endEpochSeconds: Long, val language: String?)

class XtreamApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).build()
) {
    private fun base(credentials: XtreamCredentials): String {
        val value = credentials.server.trim().trimEnd('/')
        require(value.startsWith("https://")) { "Use an HTTPS provider URL." }
        require(credentials.username.isNotBlank() && credentials.password.isNotBlank()) { "Enter username and password." }
        return value
    }
    private fun url(credentials: XtreamCredentials, action: String? = null): HttpUrl {
        val builder = (base(credentials) + "/player_api.php").toHttpUrl().newBuilder()
            .addQueryParameter("username", credentials.username).addQueryParameter("password", credentials.password)
        if (action != null) builder.addQueryParameter("action", action)
        return builder.build()
    }
    private suspend fun getJson(requestUrl: HttpUrl): JSONObject = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(requestUrl).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Provider returned HTTP ${response.code}.")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IllegalStateException("Provider returned an empty response.")
            JSONObject(body)
        }
    }
    suspend fun authenticate(credentials: XtreamCredentials): XtreamAuth {
        val auth = parseXtreamAuth(getJson(url(credentials)))
        if (!auth.authenticated) throw IllegalStateException(auth.message ?: "Login rejected. Check your details.")
        return auth
    }
    suspend fun loadLiveCatalog(credentials: XtreamCredentials): XtreamLiveCatalog = withContext(Dispatchers.IO) {
        val cats = client.newCall(Request.Builder().url(url(credentials, "get_live_categories")).build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Categories request failed (HTTP ${response.code}).")
            JSONArray(response.body?.string().orEmpty())
        }
        val channels = client.newCall(Request.Builder().url(url(credentials, "get_live_streams")).build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Channels request failed (HTTP ${response.code}).")
            JSONArray(response.body?.string().orEmpty())
        }
        val categoryList = buildList {
            for (i in 0 until cats.length()) {
                val item = cats.optJSONObject(i) ?: continue
                val id = item.opt("category_id")?.toString()?.takeIf { it.isNotBlank() } ?: continue
                add(XtreamCategory(id, item.optString("category_name", "Category $id")))
            }
        }
        val channelList = buildList {
            for (i in 0 until channels.length()) {
                val item = channels.optJSONObject(i) ?: continue
                val id = item.opt("stream_id")?.toString()?.takeIf { it.isNotBlank() } ?: continue
                val name = item.optString("name", "Channel $id").ifBlank { "Channel $id" }
                add(XtreamLiveChannel(id, name, item.opt("category_id")?.toString(), item.optString("stream_icon").takeIf(String::isNotBlank), item.optString("epg_channel_id").takeIf(String::isNotBlank), streamUrl(credentials, id)))
            }
        }
        XtreamLiveCatalog(categoryList, channelList)
    }
    suspend fun getShortEpg(credentials: XtreamCredentials, streamId: String, limit: Int = 10): List<XtreamProgramme> = getEpg(credentials, streamId, "get_short_epg", limit.coerceIn(1, 100))
    suspend fun getChannelEpg(credentials: XtreamCredentials, streamId: String): List<XtreamProgramme> = getEpg(credentials, streamId, "get_simple_data_table", null)

    private suspend fun getEpg(credentials: XtreamCredentials, streamId: String, action: String, limit: Int?): List<XtreamProgramme> = withContext(Dispatchers.IO) {
        require(streamId.isNotBlank()) { "Channel ID is required." }
        val requestUrl = url(credentials, action).newBuilder().addQueryParameter("stream_id", streamId)
            .apply { if (limit != null) addQueryParameter("limit", limit.toString()) }.build()
        client.newCall(Request.Builder().url(requestUrl).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("EPG request failed (HTTP ${response.code}).")
            val root = JSONObject(response.body?.string().orEmpty())
            val rows = root.optJSONArray("epg_listings") ?: root.optJSONArray("data") ?: JSONArray()
            buildList {
                for (i in 0 until rows.length()) {
                    val item = rows.optJSONObject(i) ?: continue
                    val start = item.optLong("start_timestamp", 0L).takeIf { it > 0 } ?: parseEpgTime(item.optString("start"))
                    val end = item.optLong("stop_timestamp", 0L).takeIf { it > 0 } ?: parseEpgTime(item.optString("end"))
                    if (start <= 0 || end <= start) continue
                    val rawTitle = item.optString("title")
                    val rawDescription = item.optString("description")
                    add(XtreamProgramme(item.optString("id").takeIf(String::isNotBlank), decodeEpgText(rawTitle), decodeEpgText(rawDescription), start, end, item.optString("lang").takeIf(String::isNotBlank)))
                }
            }.sortedBy { it.startEpochSeconds }
        }
    }
    private fun decodeEpgText(value: String): String = runCatching { String(java.util.Base64.getDecoder().decode(value), Charsets.UTF_8) }.getOrDefault(value)
    private fun parseEpgTime(value: String): Long = runCatching {
        java.time.LocalDateTime.parse(value, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toEpochSecond(java.time.ZoneOffset.UTC)
    }.getOrDefault(0L)

    private fun streamUrl(credentials: XtreamCredentials, id: String): String =
        "${base(credentials)}/live/${credentials.username.encode()}/${credentials.password.encode()}/$id.m3u8"
    private fun String.encode(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}
