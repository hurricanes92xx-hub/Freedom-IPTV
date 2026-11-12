package com.hurricanes92xx.freedomiptv.provider.xtream

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent EPG snapshot cache. Six-hour TTL; stale snapshots remain available on refresh failures. */
class EpgCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("freedom_epg_cache", Context.MODE_PRIVATE)
    private fun key(provider: XtreamCredentials, channelId: String) =
        "epg:${provider.server.trim().trimEnd('/')}:${provider.username}:$channelId"

    fun read(provider: XtreamCredentials, channelId: String, nowMillis: Long = System.currentTimeMillis()): List<XtreamProgramme>? {
        val raw = prefs.getString(key(provider, channelId), null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val saved = obj.optLong("savedAt", 0L)
            if (saved <= 0 || nowMillis - saved > TTL_MS) return null
            decode(obj.optJSONArray("programmes") ?: JSONArray())
        } catch (_: Exception) { null }
    }

    fun write(provider: XtreamCredentials, channelId: String, programmes: List<XtreamProgramme>, nowMillis: Long = System.currentTimeMillis()) {
        val rows = JSONArray()
        programmes.forEach { p ->
            rows.put(JSONObject().put("id", p.id ?: "").put("title", p.title).put("description", p.description)
                .put("start", p.startEpochSeconds).put("end", p.endEpochSeconds).put("language", p.language ?: ""))
        }
        prefs.edit().putString(key(provider, channelId), JSONObject().put("savedAt", nowMillis).put("programmes", rows).toString()).apply()
    }

    fun readStale(provider: XtreamCredentials, channelId: String): List<XtreamProgramme>? {
        val raw = prefs.getString(key(provider, channelId), null) ?: return null
        return try { decode(JSONObject(raw).optJSONArray("programmes") ?: JSONArray()) } catch (_: Exception) { null }
    }

    private fun decode(rows: JSONArray) = buildList {
        for (i in 0 until rows.length()) {
            val p = rows.optJSONObject(i) ?: continue
            val start = p.optLong("start"); val end = p.optLong("end")
            if (start > 0 && end > start) add(XtreamProgramme(p.optString("id").ifBlank { null }, p.optString("title"), p.optString("description"), start, end, p.optString("language").ifBlank { null }))
        }
    }.sortedBy { it.startEpochSeconds }

    companion object { const val TTL_MS = 6 * 60 * 60 * 1000L }
}
