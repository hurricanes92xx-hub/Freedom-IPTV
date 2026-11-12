package com.hurricanes92xx.freedomiptv.provider.m3u

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

/** Imports a user-selected playlist URI without loading the full file into memory. */
class M3uCatalogImporter(private val resolver:ContentResolver,private val parser:M3uParser=M3uParser()) {
 suspend fun import(uri:Uri,onChannel:(M3uChannel)->Unit):Int=withContext(Dispatchers.IO) {
  val stream=resolver.openInputStream(uri)?:error("Could not open playlist.")
  var count=0
  InputStreamReader(stream,Charsets.UTF_8).use { reader -> parser.parse(reader){ channel ->
   if(channel.url.startsWith("http://")||channel.url.startsWith("https://")){onChannel(channel);count++}
  } }
  count
 }
}
