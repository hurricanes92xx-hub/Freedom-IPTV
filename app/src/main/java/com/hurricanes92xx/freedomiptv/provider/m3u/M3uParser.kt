package com.hurricanes92xx.freedomiptv.provider.m3u

import java.io.BufferedReader
import java.io.Reader

data class M3uChannel(val name: String, val url: String, val group: String?, val logo: String?, val tvgId: String?)

/** Streaming parser; does not read the entire playlist into memory. */
class M3uParser {
    fun parse(reader: Reader, onChannel: (M3uChannel) -> Unit) {
        BufferedReader(reader, 64 * 1024).use { input ->
            var pending: String? = null
            var line: String?
            while (input.readLine().also { line = it } != null) {
                val value = line!!.trim()
                if (value.isEmpty() || value.startsWith("#EXTM3U")) continue
                if (value.startsWith("#EXTINF:", ignoreCase = true)) {
                    pending = value
                    continue
                }
                if (value.startsWith("#")) continue
                val ext = pending ?: ""
                val name = ext.substringAfterLast(',', "Unnamed channel").trim().ifBlank { "Unnamed channel" }
                fun attr(key: String): String? = Regex("""(?:^|\s)$key="([^"]*)"""", RegexOption.IGNORE_CASE).find(ext)?.groupValues?.getOrNull(1)
                onChannel(M3uChannel(name, value, attr("group-title"), attr("tvg-logo"), attr("tvg-id")))
                pending = null
            }
        }
    }
}
