package com.hurricanes92xx.freedomiptv.provider.xtream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class XtreamCredentials(val server: String, val username: String, val password: String)

class XtreamApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    suspend fun authenticate(credentials: XtreamCredentials): XtreamAuth = withContext(Dispatchers.IO) {
        val base = credentials.server.trim().trimEnd('/')
        require(base.startsWith("https://")) { "Use an HTTPS provider URL." }
        require(credentials.username.isNotBlank() && credentials.password.isNotBlank()) { "Enter username and password." }
        val url = "$base/player_api.php?username=${credentials.username.encode()}&password=${credentials.password.encode()}"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Provider returned HTTP ${response.code}.")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IllegalStateException("Provider returned an empty response.")
            val auth = parseXtreamAuth(JSONObject(body))
            if (!auth.authenticated) throw IllegalStateException(auth.message ?: "Login rejected. Check your details.")
            auth
        }
    }

    private fun String.encode(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}
