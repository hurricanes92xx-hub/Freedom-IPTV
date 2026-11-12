package com.hurricanes92xx.freedomiptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.media3.ui.PlayerView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.hurricanes92xx.freedomiptv.player.FreedomPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import android.content.Context
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamApi
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamCredentials
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamCategory
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamLiveChannel

private val Context.favoriteStore by preferencesDataStore(name = "freedom_favorites")
private val FAVORITE_IDS = stringSetPreferencesKey("channel_ids")
private val PROVIDER_SERVER = stringPreferencesKey("provider_server")
private val PROVIDER_USERNAME = stringPreferencesKey("provider_username")
private val PROVIDER_PASSWORD = stringPreferencesKey("provider_password")

private val FreedomRed = Color(0xFFF04452)
private val FreedomBlue = Color(0xFF2878FF)
private val FreedomBlack = Color(0xFF080B10)
private val Panel = Color(0xFF111722)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { FreedomApp() } }
}

@Composable
private fun FreedomApp() {
    var page by remember { mutableStateOf("Home") }
    var server by remember { mutableStateOf("https://") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var providerLoaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf(false) }
    var credentials by remember { mutableStateOf<XtreamCredentials?>(null) }
    var channels by remember { mutableStateOf<List<XtreamLiveChannel>>(emptyList()) }
    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("all") }
    var search by remember { mutableStateOf("") }
    var currentChannel by remember { mutableStateOf<XtreamLiveChannel?>(null) }
    var guideBusy by remember { mutableStateOf(false) }
    var guideMessage by remember { mutableStateOf("Connect a provider to load the guide.") }
    var guide by remember { mutableStateOf<Map<String, List<com.hurricanes92xx.freedomiptv.provider.xtream.XtreamProgramme>>>(emptyMap()) }
    var favorites by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val prefs = context.favoriteStore.data.first()
        favorites = prefs[FAVORITE_IDS] ?: emptySet()
        server = prefs[PROVIDER_SERVER] ?: "https://"
        username = prefs[PROVIDER_USERNAME] ?: ""
        password = prefs[PROVIDER_PASSWORD] ?: ""
        providerLoaded = true
    }
    val api = remember { XtreamApi() }
    LaunchedEffect(providerLoaded) {
        if (providerLoaded && username.isNotBlank() && password.isNotBlank() && server.startsWith("https://")) {
            busy = true
            result = "Restoring saved provider…"
            try {
                val saved = XtreamCredentials(server.trim(), username.trim(), password)
                val auth = api.authenticate(saved)
                val catalog = api.loadLiveCatalog(saved)
                credentials = saved; categories = catalog.categories; channels = catalog.channels; connected = true
                result = "Restored ${catalog.channels.size} channels • ${auth.status ?: "Active"}"
            } catch (e: Exception) {
                connected = false
                result = "Saved provider needs attention: ${e.message ?: "connection failed"}"
            } finally { busy = false }
        }
    }
    fun updateFavorites(next: Set<String>) { favorites = next; scope.launch { context.favoriteStore.edit { prefs -> prefs[FAVORITE_IDS] = next } } }
    val mediaPlayer = remember { FreedomPlayer(context) }
    DisposableEffect(mediaPlayer) { onDispose { mediaPlayer.release() } }
    LaunchedEffect(currentChannel?.id, currentChannel?.streamUrl) { currentChannel?.let { mediaPlayer.play(it.id, it.streamUrl) } }

    val filtered = channels.filter { (selectedCategory == "all" || it.categoryId == selectedCategory) && it.name.contains(search.trim(), true) }
    val categoryCounts = channels.groupingBy { it.categoryId }.eachCount()
    fun refreshProvider() {
        if (credentials == null || busy) return
        busy = true
        scope.launch {
            try {
                val creds = credentials!!
                val catalog = api.loadLiveCatalog(creds)
                categories = catalog.categories
                channels = catalog.channels
                result = "Refreshed ${catalog.channels.size} channels."
            } catch (e: Exception) {
                result = "Refresh failed: ${e.message ?: "connection error"}"
            } finally { busy = false }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = FreedomRed, secondary = FreedomBlue, background = FreedomBlack, surface = Panel)) {
        Row(Modifier.fillMaxSize().background(FreedomBlack)) {
            Column(Modifier.width(210.dp).fillMaxHeight().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🦅 FREEDOM", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text("IPTV", color = FreedomRed, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                listOf("Home", "Live TV", "Guide", "Sports", "Favorites", "Search", "Settings", "Add Provider").forEach { item ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (page == item) FreedomRed.copy(alpha = 0.18f) else Color.Transparent).clickable { page = item }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item, color = if (page == item) FreedomRed else Color.LightGray, fontWeight = if (page == item) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Column(Modifier.fillMaxSize().padding(30.dp)) {
                Text(page.uppercase(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                when (page) {
                    "Add Provider" -> Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Connect your Xtream provider", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Use the HTTPS server address and credentials from your IPTV service.", color = Color.LightGray)
                            OutlinedTextField(server, { server = it; result = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Server URL (https://)") }, singleLine = true)
                            OutlinedTextField(username, { username = it; result = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
                            OutlinedTextField(password, { password = it; result = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(enabled = !busy, onClick = {
                                    busy = true; result = null
                                    scope.launch {
                                        try {
                                            val creds = XtreamCredentials(server.trim(), username.trim(), password)
                                            val auth = api.authenticate(creds)
                                            val catalog = api.loadLiveCatalog(creds)
                                            credentials = creds; categories = catalog.categories; channels = catalog.channels
                                            context.favoriteStore.edit { prefs -> prefs[PROVIDER_SERVER] = creds.server; prefs[PROVIDER_USERNAME] = creds.username; prefs[PROVIDER_PASSWORD] = creds.password }
                                            connected = true; selectedCategory = "all"; page = "Live TV"
                                            result = "Loaded ${catalog.channels.size} channels • ${catalog.categories.size} categories • ${auth.status ?: "Active"}"
                                        } catch (e: Exception) { connected = false; result = e.message ?: "Connection failed." }
                                        finally { busy = false }
                                    }
                                }) { Text(if (busy) "Loading provider…" else "Connect & Load") }
                                OutlinedButton(onClick = { server = "https://"; username = ""; password = ""; result = null }) { Text("Clear") }
                                OutlinedButton(enabled = !busy, onClick = {
                                    scope.launch {
                                        context.favoriteStore.edit { prefs ->
                                            prefs.remove(PROVIDER_SERVER)
                                            prefs.remove(PROVIDER_USERNAME)
                                            prefs.remove(PROVIDER_PASSWORD)
                                        }
                                        mediaPlayer.exoPlayer.stop()
                                        currentChannel = null
                                        credentials = null
                                        channels = emptyList()
                                        categories = emptyList()
                                        guide = emptyMap()
                                        connected = false
                                        server = "https://"; username = ""; password = ""
                                        result = "Provider disconnected and saved credentials removed."
                                    }
                                }) { Text("Disconnect") }
                            }
                            result?.let { Text(it, color = if (connected) Color(0xFF70D6A0) else Color(0xFFFF8A92)) }
                        }
                    }
                    "Live TV", "Search" -> {
                        if (!connected || channels.isEmpty()) EmptyState("Connect a provider to load channels.") { page = "Add Provider" }
                        else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(search, { search = it }, modifier = Modifier.weight(1f), label = { Text("Search ${channels.size} channels") }, singleLine = true)
                                IconButton(onClick = { refreshProvider() }, enabled = connected && !busy) { Text("↻", color = FreedomRed, fontSize = 24.sp) }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item { FilterChip(selected = selectedCategory == "all", onClick = { selectedCategory = "all" }, label = { Text("All") }) }
                                items(categories, key = { it.id }) { cat ->
                                    FilterChip(
                                        selected = selectedCategory == cat.id,
                                        onClick = { selectedCategory = cat.id },
                                        label = { Text(cat.name + " (" + (categoryCounts[cat.id] ?: 0) + ")", maxLines = 1) }
                                    )
                                }
                            }
                            Text("${filtered.size} channels", color = Color.LightGray)
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                items(filtered, key = { it.id }) { channel ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().clickable { currentChannel = channel; page = "Player" }) {
                                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(channel.name, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                            TextButton(onClick = { updateFavorites(if (channel.id in favorites) favorites - channel.id else favorites + channel.id) }) { Text(if (channel.id in favorites) "★" else "☆", color = FreedomRed) }
                                            Text("▶", color = FreedomRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "Home" -> Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (busy) "Restoring provider…" else if (connected) "Provider connected" else "Welcome to Freedom IPTV", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text(if (connected) "${channels.size} channels loaded and ready." else result ?: "Connect your IPTV provider to get started.", color = Color.LightGray)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { page = "Add Provider" }) { Text(if (connected) "Manage Provider" else "Add Provider") }
                                OutlinedButton(onClick = { page = "Live TV" }) { Text("Browse Channels") }
                            }
                        }
                    }
                    "Player" -> Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(currentChannel?.name ?: "Channel", color = Color.White, fontSize = 22.sp)
                            AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = mediaPlayer.exoPlayer; useController = true } }, modifier = Modifier.fillMaxWidth().height(420.dp), update = { it.player = mediaPlayer.exoPlayer })
                            Button(onClick = { mediaPlayer.exoPlayer.stop(); currentChannel = null; page = "Live TV" }) { Text("Back to channels") }
                        }
                    }
                    "Favorites" -> {
                        val favoriteChannels = channels.filter { it.id in favorites }
                        if (favoriteChannels.isEmpty()) EmptyState("Your favorites will appear here. Tap ☆ on any channel to save it.") { page = "Live TV" }
                        else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(favoriteChannels, key = { it.id }) { channel ->
                                Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().clickable { currentChannel = channel; page = "Player" }) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(channel.name, color = Color.White, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { updateFavorites(favorites - channel.id) }) { Text("★", color = FreedomRed) }
                                    }
                                }
                            }
                        }
                    }
                    "Sports" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Sports channels from your provider", color = Color.LightGray)
                            val sports = channels.filter { channel -> (channel.name + " " + channel.categoryId).contains(Regex("sport|nba|nfl|nhl|mlb|soccer|football|racing", RegexOption.IGNORE_CASE)) }
                            if (sports.isEmpty()) EmptyState("No sports channels detected in the loaded lineup.") { page = "Live TV" }
                            else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(sports, key = { it.id }) { channel ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().clickable { currentChannel = channel; page = "Player" }) {
                                        Text("▶  " + channel.name, color = Color.White, modifier = Modifier.padding(14.dp))
                                    }
                                }
                            }
                        }
                    }
                    "Guide" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(enabled = connected && !guideBusy, onClick = {
                                guideBusy = true
                                scope.launch {
                                    try {
                                        val creds = credentials ?: error("Connect first.")
                                        val epgChannels = channels.take(60)
                                        val limiter = Semaphore(6)
                                        guide = coroutineScope {
                                            epgChannels.map { channel ->
                                                async { limiter.withPermit { channel.id to api.getShortEpg(creds, channel.id, 8) } }
                                            }.awaitAll().toMap()
                                        }
                                        guideMessage = "Guide loaded."
                                    } catch (e: Exception) {
                                        guideMessage = e.message ?: "Guide failed."
                                    } finally { guideBusy = false }
                                }
                            }) { Text(if (guideBusy) "Loading…" else "Refresh guide") }
                            Text(guideMessage, color = Color.LightGray)
                            LazyColumn {
                                items(channels.filter { guide[it.id]?.isNotEmpty() == true }, key = { it.id }) { channel ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(channel.name, color = FreedomRed, fontWeight = FontWeight.Bold)
                                            guide[channel.id].orEmpty().forEach { programme ->
                                                Text(programme.title, color = Color.White)
                                                if (programme.description.isNotBlank()) Text(programme.description, color = Color.LightGray, maxLines = 2)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> EmptyState("This section is queued for implementation.") { page = "Live TV" }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String, action: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = Color.LightGray)
            Button(onClick = action) { Text("Continue") }
        }
    }
}
