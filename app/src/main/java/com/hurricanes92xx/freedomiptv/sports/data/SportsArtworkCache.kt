package com.hurricanes92xx.freedomiptv.sports.data

import android.content.Context
import org.json.JSONObject

/** Persists lightweight artwork URLs; Coil handles image bytes in its own memory/disk cache. */
class SportsArtworkCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("freedom_sports_artwork", Context.MODE_PRIVATE)
    fun get(eventId: String): String? = prefs.getString(eventId, null)?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    fun put(eventId: String, url: String?) {
        if (url.isNullOrBlank() || !(url.startsWith("https://") || url.startsWith("http://"))) return
        prefs.edit().putString(eventId, url).apply()
    }
    fun putAll(entries: Map<String, String>) {
        val edit = prefs.edit()
        entries.forEach { (id, url) -> if (url.startsWith("https://") || url.startsWith("http://")) edit.putString(id, url) }
        edit.apply()
    }
    fun clear() = prefs.edit().clear().apply()
}
