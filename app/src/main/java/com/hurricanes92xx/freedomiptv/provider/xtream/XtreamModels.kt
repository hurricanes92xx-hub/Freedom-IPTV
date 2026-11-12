package com.hurricanes92xx.freedomiptv.provider.xtream

import org.json.JSONObject

data class XtreamAuth(val authenticated: Boolean, val status: String?, val message: String?)
data class XtreamChannel(val id: String, val name: String, val categoryId: String?, val streamIcon: String?, val epgId: String?)

/** Defensive helpers: panels often encode IDs as JSON numbers OR strings. */
internal fun JSONObject.stringOrNull(key: String): String? = when {
    isNull(key) -> null
    has(key) -> opt(key)?.toString()?.takeIf { it != "null" && it.isNotBlank() }
    else -> null
}

fun parseXtreamAuth(json: JSONObject): XtreamAuth {
    val user = json.optJSONObject("user_info") ?: JSONObject()
    val authRaw = user.opt("auth")?.toString()
    val authenticated = authRaw == "1" || authRaw.equals("true", true)
    return XtreamAuth(authenticated, user.stringOrNull("status"), user.stringOrNull("message"))
}

fun parseXtreamChannels(jsonArray: org.json.JSONArray): List<XtreamChannel> = buildList {
    for (i in 0 until jsonArray.length()) {
        val item = jsonArray.optJSONObject(i) ?: continue
        val id = item.stringOrNull("stream_id") ?: continue
        val name = item.stringOrNull("name")?.trim()?.takeIf(String::isNotEmpty) ?: "Channel $id"
        add(XtreamChannel(id, name, item.stringOrNull("category_id"), item.stringOrNull("stream_icon"), item.stringOrNull("epg_channel_id")))
    }
}


/** Programme row persisted by the guide. Times are UTC epoch seconds. */
data class GuideProgramme(
    val channelId: String,
    val title: String,
    val description: String,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long
)
