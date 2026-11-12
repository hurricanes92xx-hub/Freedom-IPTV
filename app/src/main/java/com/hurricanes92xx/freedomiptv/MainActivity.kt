package com.hurricanes92xx.freedomiptv

import android.os.Bundle
import android.net.Uri
import org.json.JSONArray
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.media3.ui.PlayerView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.hurricanes92xx.freedomiptv.player.FreedomPlayer
import com.hurricanes92.freedomiptv.security.CredentialVault
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.hurricanes92xx.freedomiptv.provider.m3u.M3uCatalogImporter
import com.hurricanes92xx.freedomiptv.provider.m3u.M3uChannel
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamApi
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamCredentials
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamCategory
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamLiveChannel
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamVodApi
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamVodItem
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamVodCategory
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamSeason
import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamEpisode

private val Context.favoriteStore by preferencesDataStore(name = "freedom_favorites")
private val FAVORITE_IDS = stringSetPreferencesKey("channel_ids")
private val PLAYLIST_DATA = stringSetPreferencesKey("m3u_channels")
private val PROVIDER_SERVER = stringPreferencesKey("provider_server")
private val PROVIDER_USERNAME = stringPreferencesKey("provider_username")
private val LEGACY_PROVIDER_PASSWORD = stringPreferencesKey("provider_password")

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
    var playlistChannels by remember { mutableStateOf<List<M3uChannel>>(emptyList()) }
    var playlistBusy by remember { mutableStateOf(false) }
    var playlistMessage by remember { mutableStateOf("Import an M3U playlist to browse its channels.") }
    var playlistSearch by remember { mutableStateOf("") }
    var guideSearch by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("all") }
    var search by remember { mutableStateOf("") }
    var currentChannel by remember { mutableStateOf<XtreamLiveChannel?>(null) }
    var vodItems by remember { mutableStateOf<List<XtreamVodItem>>(emptyList()) }
    var vodCategories by remember { mutableStateOf<List<XtreamVodCategory>>(emptyList()) }
    var vodCategory by remember { mutableStateOf("all") }
    var vodSearch by remember { mutableStateOf("") }
    var vodBusy by remember { mutableStateOf(false) }
    var vodMessage by remember { mutableStateOf("Connect your Xtream provider to browse.") }
    var vodSeriesMode by remember { mutableStateOf(false) }
    var selectedVod by remember { mutableStateOf<XtreamVodItem?>(null) }
    var seriesSeasons by remember { mutableStateOf<List<XtreamSeason>>(emptyList()) }
    var selectedSeason by remember { mutableStateOf<String?>(null) }
    var seriesBusy by remember { mutableStateOf(false) }
    var vodPlayingUrl by remember { mutableStateOf<String?>(null) }
    var guideBusy by remember { mutableStateOf(false) }
    var guideMessage by remember { mutableStateOf("Connect a provider to load the guide.") }
    var guideUpdatedAt by remember { mutableStateOf<String?>(null) }
    var guide by remember { mutableStateOf<Map<String, List<com.hurricanes92xx.freedomiptv.provider.xtream.XtreamProgramme>>>(emptyMap()) }
    var favorites by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playlistPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) { playlistBusy=true; scope.launch { try { val list=mutableListOf<M3uChannel>(); val n=M3uCatalogImporter(context.contentResolver).import(uri){ list.add(it) }; playlistChannels=list; context.favoriteStore.edit { prefs -> prefs[PLAYLIST_DATA] = list.map { JSONArray().put(it.name).put(it.url).put(it.group ?: "").put(it.logo ?: "").put(it.tvgId ?: "").toString() }.toSet() }; playlistMessage="Imported $n playlist channels."; page="Playlist" } catch(e:Exception){playlistMessage=e.message?: "Playlist import failed"} finally{playlistBusy=false} } } }
    LaunchedEffect(Unit) {
        val prefs = context.favoriteStore.data.first()
        favorites = prefs[FAVORITE_IDS] ?: emptySet()
        playlistChannels = (prefs[PLAYLIST_DATA] ?: emptySet()).mapNotNull { raw -> try { val j=JSONArray(raw); M3uChannel(j.getString(0),j.getString(1),j.optString(2).ifBlank { null },j.optString(3).ifBlank { null },j.optString(4).ifBlank { null }) } catch(_:Exception){null} }
        server = prefs[PROVIDER_SERVER] ?: "https://"
        username = prefs[PROVIDER_USERNAME] ?: ""
        // Migrate plaintext once: write encrypted storage first, then delete legacy DataStore value.
        val vault = CredentialVault(context)
        val legacyPassword = prefs[LEGACY_PROVIDER_PASSWORD].orEmpty()
        val encryptedPassword = vault.getPassword()
        password = if (!encryptedPassword.isNullOrBlank()) {
            if (legacyPassword.isNotBlank()) context.favoriteStore.edit { it.remove(LEGACY_PROVIDER_PASSWORD) }
            encryptedPassword
        } else if (legacyPassword.isNotBlank()) {
            if (vault.savePassword(legacyPassword)) {
                context.favoriteStore.edit { it.remove(LEGACY_PROVIDER_PASSWORD) }
            }
            legacyPassword
        } else ""
        providerLoaded = true
    }
    val api = remember { XtreamApi() }
    val vodApi = remember { XtreamVodApi() }
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
    LaunchedEffect(currentChannel?.id, currentChannel?.streamUrl, vodPlayingUrl) { val url = vodPlayingUrl ?: currentChannel?.streamUrl; if (url != null) mediaPlayer.play(currentChannel?.id ?: selectedVod?.id ?: "vod", url) }

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
                listOf("Home", "Live TV", "Playlist", "Movies", "Series", "Guide", "Sports", "Favorites", "Search", "Settings", "Add Provider").forEach { item ->
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
                                            // Save secret first; publish metadata only after encrypted write succeeds.
                                            check(CredentialVault(context).savePassword(creds.password)) { "Could not securely save provider password." }
                                            context.favoriteStore.edit { prefs ->
                                                prefs[PROVIDER_SERVER] = creds.server
                                                prefs[PROVIDER_USERNAME] = creds.username
                                                prefs.remove(LEGACY_PROVIDER_PASSWORD)
                                            }
                                            connected = true; selectedCategory = "all"; page = "Live TV"
                                            result = "Loaded ${catalog.channels.size} channels • ${catalog.categories.size} categories • ${auth.status ?: "Active"}"
                                        } catch (e: Exception) { connected = false; result = e.message ?: "Connection failed." }
                                        finally { busy = false }
                                    }
                                }) { Text(if (busy) "Loading provider…" else "Connect & Load") }
                                OutlinedButton(onClick = { server = "https://"; username = ""; password = ""; result = null }) { Text("Clear") }
                                OutlinedButton(enabled = !busy, onClick = {
                                    scope.launch {
                                        // Clear encrypted secret and metadata together from the user's perspective.
                                        check(CredentialVault(context).clearPassword()) { "Could not securely clear provider password." }
                                        context.favoriteStore.edit { prefs ->
                                            prefs.remove(PROVIDER_SERVER)
                                            prefs.remove(PROVIDER_USERNAME)
                                            prefs.remove(LEGACY_PROVIDER_PASSWORD)
                                        }
                                        mediaPlayer.stop()
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
                    "Playlist" -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(enabled = !playlistBusy, onClick = { playlistPicker.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain")) }) { Text(if (playlistBusy) "Importing…" else "Import / Replace M3U") }
                            TextButton(enabled = !playlistBusy && playlistChannels.isNotEmpty(), onClick = { playlistChannels = emptyList(); scope.launch { context.favoriteStore.edit { it[PLAYLIST_DATA] = emptySet() } }; playlistMessage = "Playlist removed." }) { Text("Remove", color = FreedomRed) }
                            Text(playlistMessage, color = Color.LightGray)
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(playlistChannels, key = { it.url }) { ch ->
                                Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().clickable {
                                    currentChannel = XtreamLiveChannel(ch.url, ch.name, null, ch.logo, ch.tvgId, ch.url)
                                    page = "Player"
                                }) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(model = ch.logo, contentDescription = null, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(6.dp)))
                                        Text(ch.name, color = Color.White, modifier = Modifier.padding(start = 12.dp).weight(1f))
                                        Text("▶", color = FreedomRed)
                                    }
                                }
                            }
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
                            Text("If the stream stalls or errors, tap Retry to reconnect.", color = Color.LightGray)
                            mediaPlayer.lastPlaybackError?.let { error -> Text("Playback error: $error", color = FreedomRed) }
                            OutlinedButton(onClick = { mediaPlayer.retry() }) { Text("Retry stream") }
                            Button(onClick = { mediaPlayer.stop(); currentChannel = null; page = "Live TV" }) { Text("Back to channels") }
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
                        val categoryNames = categories.associate { it.id to it.name }
                        val sportsTerms = Regex("sport|ncaa|college|basketball|volleyball|nba|wnba|nfl|nhl|mlb|soccer|football|ufc|mma|boxing|wrestling|espn|fox sports|sky sports|bein sports", RegexOption.IGNORE_CASE)
                        val sports = channels.filter { channel -> (channel.name + " " + (categoryNames[channel.categoryId] ?: "")).contains(sportsTerms) }
                        var sportFilter by remember { mutableStateOf("All") }
                        val filterTerms = mapOf("Football" to Regex("football|nfl|college football", RegexOption.IGNORE_CASE), "Basketball" to Regex("basketball|nba|wnba", RegexOption.IGNORE_CASE), "Baseball" to Regex("baseball|mlb", RegexOption.IGNORE_CASE), "Hockey" to Regex("hockey|nhl", RegexOption.IGNORE_CASE), "Soccer" to Regex("soccer|mls|epl|champions league", RegexOption.IGNORE_CASE), "Volleyball" to Regex("volleyball", RegexOption.IGNORE_CASE), "Combat" to Regex("ufc|mma|boxing", RegexOption.IGNORE_CASE), "Wrestling" to Regex("wwe|aew|tna|wrestling", RegexOption.IGNORE_CASE))
                        val visibleSports = sports.filter { sportFilter == "All" || (it.name + " " + (categoryNames[it.categoryId] ?: "")).contains(filterTerms[sportFilter] ?: Regex("(?!)") }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
                            item {
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151D2B)), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                                    Box(Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF242E46), Color(0xFF101620)))).padding(24.dp)) {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("FREEDOM  /  SPORTS", color = FreedomRed, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                                                Spacer(Modifier.weight(1f))
                                                Text("LIVE • YOUR LINEUP", color = Color(0xFF9BA8BA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Text("Every game.\nOne hub.", color = Color.White, fontSize = 36.sp, lineHeight = 39.sp, fontWeight = FontWeight.Black)
                                            Text("A fast, unified home for the sports you follow.", color = Color(0xFFBAC5D5), fontSize = 14.sp)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Surface(color = FreedomRed, shape = RoundedCornerShape(8.dp)) { Text(" ${sports.size} SPORTS CHANNELS ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) }
                                                Surface(color = Color(0xFF273348), shape = RoundedCornerShape(8.dp)) { Text(if (connected) " PROVIDER CONNECTED " else " CONNECT TO WATCH ", color = Color(0xFFD8E2F0), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) }
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                Text("EXPLORE SPORTS", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(9.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(listOf("All", "Football", "Basketball", "Baseball", "Hockey", "Soccer", "Volleyball", "Combat", "Wrestling")) { name ->
                                        FilterChip(selected = sportFilter == name, onClick = { sportFilter = name }, label = { Text(name) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = FreedomRed, selectedLabelColor = Color.White))
                                    }
                                }
                            }
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("YOUR SPORTS CHANNELS", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.weight(1f))
                                    Text("${visibleSports.size} FOUND", color = Color(0xFF8F9DB2), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (visibleSports.isEmpty()) item {
                                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(if (!connected) "Connect your provider" else "No channels in this category", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                        Text(if (!connected) "Your sports lineup will appear here automatically after Xtream connection." else "Try another sport or refresh your Live TV catalog.", color = Color.LightGray)
                                        Button(onClick = { page = if (connected) "Live TV" else "Add Provider" }) { Text(if (connected) "Browse Live TV" else "Connect provider") }
                                    }
                                }
                            } else items(visibleSports, key = { it.id }) { channel ->
                                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().clickable { currentChannel = channel; page = "Player" }) {
                                    Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF202B3D)), contentAlignment = Alignment.Center) { Text("▶", color = FreedomRed, fontSize = 18.sp) }
                                        Column(Modifier.padding(start = 13.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(channel.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                            Text(categoryNames[channel.categoryId] ?: "SPORTS", color = Color(0xFF8F9DB2), fontSize = 11.sp)
                                        }
                                        Text("WATCH  ›", color = FreedomRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            item {
                                Text("Broadcast availability is matched against your provider lineup and guide data. A network match alone does not confirm a specific game.", color = Color(0xFF8290A4), fontSize = 12.sp)
                            }
                        }
                    }

                    "Movies", "Series" -> {
                        val isSeries = page == "Series"
                        LaunchedEffect(page, credentials) {
                            if (credentials != null && (vodItems.isEmpty() || vodSeriesMode != isSeries)) {
                                vodBusy = true; vodMessage = "Loading catalog…"
                                try {
                                    val c = credentials!!
                                    vodCategories = if (isSeries) vodApi.seriesCategories(c) else vodApi.movieCategories(c)
                                    vodItems = if (isSeries) vodApi.series(c) else vodApi.movies(c)
                                    vodSeriesMode = isSeries; vodCategory = "all"
                                    vodMessage = "${vodItems.size} titles loaded"
                                } catch (e: Exception) { vodMessage = e.message ?: "Catalog failed." }
                                finally { vodBusy = false }
                            }
                        }
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(vodSearch, { vodSearch = it }, modifier = Modifier.weight(1f), label = { Text("Search titles") }, singleLine = true)
                                Button(enabled = credentials != null && !vodBusy, onClick = {
                                    scope.launch { vodBusy = true; try {
                                        val c = credentials!!
                                        vodCategories = if (isSeries) vodApi.seriesCategories(c) else vodApi.movieCategories(c)
                                        vodItems = if (isSeries) vodApi.series(c) else vodApi.movies(c)
                                        vodMessage = "${vodItems.size} titles loaded"
                                    } catch (e: Exception) { vodMessage = e.message ?: "Load failed" } finally { vodBusy = false } }
                                }) { Text("↻") }
                            }
                            Text(vodMessage, color = Color.LightGray)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item { FilterChip(selected = vodCategory == "all", onClick = { vodCategory = "all" }, label = { Text("All") }) }
                                items(vodCategories, key = { it.id }) { cat -> FilterChip(selected = vodCategory == cat.id, onClick = { vodCategory = cat.id }, label = { Text(cat.name, maxLines = 1) }) }
                            }
                            val visibleVod = vodItems.filter { (vodCategory == "all" || it.categoryId == vodCategory) && it.name.contains(vodSearch.trim(), true) }
                            if (vodBusy && vodItems.isEmpty()) {
                                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        CircularProgressIndicator(color = FreedomRed)
                                        Text("Loading catalog…", color = Color.LightGray)
                                    }
                                }
                            } else if (visibleVod.isEmpty()) {
                                EmptyState(if (credentials == null) "Connect an Xtream provider to browse titles." else if (vodItems.isEmpty()) "Catalog is empty or failed to load. Tap Retry to try again." else "No titles match this search/category.") {
                                    if (credentials != null) scope.launch {
                                        vodBusy = true
                                        try {
                                            val c = credentials!!
                                            vodCategories = if (isSeries) vodApi.seriesCategories(c) else vodApi.movieCategories(c)
                                            vodItems = if (isSeries) vodApi.series(c) else vodApi.movies(c)
                                            vodSeriesMode = isSeries
                                            vodMessage = "${vodItems.size} titles loaded"
                                        } catch (e: Exception) { vodMessage = e.message ?: "Catalog failed." }
                                        finally { vodBusy = false }
                                    } else page = "Add Provider"
                                }
                            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(visibleVod, key = { it.id }) { item ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().clickable {
                                        selectedVod = item
                                        if (!isSeries) { vodPlayingUrl = credentials?.let { vodApi.movieUrl(it, item) }; currentChannel = null; page = "VOD Player" }
                                        else { seriesBusy = true; seriesSeasons = emptyList(); selectedSeason = null; page = "Series Detail"; scope.launch { try { seriesSeasons = vodApi.seriesSeasons(credentials!!, item.id); selectedSeason = seriesSeasons.firstOrNull()?.number; vodMessage = "${seriesSeasons.sumOf { it.episodes.size }} episodes loaded" } catch (e: Exception) { vodMessage = e.message ?: "Series detail failed" } finally { seriesBusy = false } } }
                                    }) {
                                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.width(110.dp).height(160.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF202936)), contentAlignment = Alignment.Center) {
                                                Text("🎬", fontSize = 30.sp)
                                                ArtworkFallbackImage(title = item.name, isSeries = isSeries, providerPoster = item.poster, modifier = Modifier.fillMaxSize())
                                            }
                                            Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                                item.rating?.let { Text("★ $it", color = FreedomRed) }
                                                item.plot?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.LightGray, maxLines = 4) }
                                                Text(if (isSeries) "Open series" else "▶ Play movie", color = FreedomRed, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "Series Detail" -> {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = { page = "Series" }) { Text("← Series") }
                                Text(selectedVod?.name ?: "Series", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
                            }
                            Text(if (seriesBusy) "Loading seasons and episodes…" else vodMessage, color = Color.LightGray)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(seriesSeasons, key = { it.number }) { season ->
                                    FilterChip(selected = selectedSeason == season.number, onClick = { selectedSeason = season.number }, label = { Text(season.name) })
                                }
                            }
                            val episodes = seriesSeasons.firstOrNull { it.number == selectedSeason }?.episodes.orEmpty()
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(episodes, key = { it.id }) { ep ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().clickable {
                                        vodPlayingUrl = credentials?.let { vodApi.episodeUrl(it, ep) }
                                        currentChannel = null
                                        page = "VOD Player"
                                    }) {
                                        Column(Modifier.padding(14.dp)) {
                                            Text("Episode ${ep.episodeNumber}: ${ep.title}", color = Color.White, fontWeight = FontWeight.Bold)
                                            ep.plot?.let { Text(it, color = Color.LightGray, maxLines = 3) }
                                            Text("▶ Play episode", color = FreedomRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "VOD Player" -> Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(selectedVod?.name ?: "Movie", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = mediaPlayer.exoPlayer; useController = true } }, modifier = Modifier.fillMaxWidth().height(420.dp), update = { it.player = mediaPlayer.exoPlayer })
                            mediaPlayer.lastPlaybackError?.let { error ->
                                Text("Playback error: $error", color = FreedomRed)
                                OutlinedButton(onClick = { mediaPlayer.retry() }) { Text("Retry playback") }
                            }
                            Button(onClick = { mediaPlayer.stop(); vodPlayingUrl = null; page = if(vodSeriesMode) "Series Detail" else "Movies" }) { Text(if(vodSeriesMode) "Back to episodes" else "Back to movies") }
                        }
                    }
                    "Guide" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(enabled = connected && !guideBusy, onClick = {
                                guideBusy = true
                                scope.launch {
                                    try {
                                        val creds = credentials ?: error("Connect first.")
                                        val epgChannels = channels
                                        val cache = com.hurricanes92xx.freedomiptv.provider.xtream.EpgCache(context)
                                        val cached = epgChannels.mapNotNull { ch -> cache.read(creds, ch.id)?.let { ch.id to it } }.toMap()
                                        if (cached.isNotEmpty()) { guide = cached; guideMessage = "Showing cached guide while refreshing…" }
                                        val limiter = Semaphore(4)
                                        val fetched = coroutineScope {
                                            epgChannels.map { channel ->
                                                async { limiter.withPermit { channel.id to api.getShortEpg(creds, channel.id, 8) } }
                                            }.awaitAll().toMap()
                                        }
                                        fetched.forEach { (id, programmes) -> cache.write(creds, id, programmes) }
                                        guide = fetched
                                        guideUpdatedAt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date())
                                        guideMessage = "Guide refreshed (${fetched.size} channels)."
                                    } catch (e: Exception) {
                                        val creds = credentials
                                        if (creds != null) {
                                            val cache = com.hurricanes92xx.freedomiptv.provider.xtream.EpgCache(context)
                                            val stale = channels.mapNotNull { ch -> cache.readStale(creds, ch.id)?.let { ch.id to it } }.toMap()
                                            if (stale.isNotEmpty()) guide = stale
                                        }
                                        guideMessage = "Refresh failed; showing saved guide if available. ${e.message ?: "network error"}"
                                    } finally { guideBusy = false }
                                }
                            }) { Text(if (guideBusy) "Loading…" else "Refresh guide") }
                            Text(guideMessage + (guideUpdatedAt?.let { " · Updated $it" } ?: ""), color = Color.LightGray)
                            OutlinedTextField(value = guideSearch, onValueChange = { guideSearch = it }, label = { Text("Search guide channels") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            LazyColumn {
                                items(channels.filter { guide[it.id]?.isNotEmpty() == true && it.name.contains(guideSearch, ignoreCase = true) }, key = { it.id }) { channel ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(channel.name, color = FreedomRed, fontWeight = FontWeight.Bold)
                                            val programmes = guide[channel.id].orEmpty()
                                            val now = System.currentTimeMillis() / 1000
                                            val current = programmes.firstOrNull { it.startEpochSeconds <= now && it.endEpochSeconds > now }
                                            val next = programmes.firstOrNull { it.startEpochSeconds > now }
                                            val clock = SimpleDateFormat("h:mm a", Locale.getDefault())
                                            current?.let { programme ->
                                                Text("NOW · ${programme.title}", color = Color.White, fontWeight = FontWeight.Bold)
                                                Text("${clock.format(Date(programme.startEpochSeconds * 1000))} – ${clock.format(Date(programme.endEpochSeconds * 1000))}", color = FreedomRed)
                                                if (programme.description.isNotBlank()) Text(programme.description, color = Color.LightGray, maxLines = 2)
                                            }
                                            next?.let { programme ->
                                                Text("NEXT · ${programme.title}", color = Color.White)
                                                Text("${clock.format(Date(programme.startEpochSeconds * 1000))} – ${clock.format(Date(programme.endEpochSeconds * 1000))}", color = Color.LightGray)
                                            }
                                            if (current == null && next == null) Text("No current schedule data", color = Color.LightGray)
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


@Composable
private fun ArtworkFallbackImage(title: String, isSeries: Boolean, providerPoster: String?, modifier: Modifier = Modifier) {
    var fallbackUrl by remember(title, isSeries) { mutableStateOf<String?>(null) }
    var providerFailed by remember(title, isSeries) { mutableStateOf(false) }
    LaunchedEffect(title, isSeries, providerPoster, providerFailed) {
        if (providerPoster.isNullOrBlank() || providerFailed) {
            fallbackUrl = if (isSeries) TvmazeArtwork.lookup(title) else CinemetaArtwork.lookup(title)
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("🎬", fontSize = 30.sp)
        AsyncImage(
            model = if (!providerPoster.isNullOrBlank() && !providerFailed) providerPoster else fallbackUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            onError = { if (!providerPoster.isNullOrBlank()) providerFailed = true }
        )
    }
}
private object TvmazeArtwork {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val client = okhttp3.OkHttpClient.Builder().connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS).readTimeout(5, java.util.concurrent.TimeUnit.SECONDS).build()
    suspend fun lookup(title: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val key = title.trim().lowercase()
        cache[key]?.let { return@withContext it }
        try {
            val url = "https://api.tvmaze.com/search/shows?q=" + java.net.URLEncoder.encode(title, "UTF-8")
            val request = okhttp3.Request.Builder().url(url).header("User-Agent", "FreedomIPTV/1.0").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val results = org.json.JSONArray(response.body?.string().orEmpty())
                for (i in 0 until results.length()) {
                    val image = results.optJSONObject(i)?.optJSONObject("show")?.optJSONObject("image")
                    val poster = image?.optString("original")?.takeIf { it.isNotBlank() && it != "null" } ?: image?.optString("medium")?.takeIf { it.isNotBlank() && it != "null" }
                    if (poster != null) { cache[key] = poster; return@withContext poster }
                }
            }
        } catch (_: Exception) { }
        null
    }
}

private object CinemetaArtwork {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val client = okhttp3.OkHttpClient.Builder().connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS).readTimeout(5, java.util.concurrent.TimeUnit.SECONDS).build()
    suspend fun lookup(title: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val key = title.trim().lowercase()
        cache[key]?.let { return@withContext it }
        try {
            val query = java.net.URLEncoder.encode(title.trim(), "UTF-8").replace("+", "%20")
            val url = "https://v3-cinemeta.strem.io/catalog/movie/top/search=$query.json"
            val request = okhttp3.Request.Builder().url(url).header("User-Agent", "FreedomIPTV/1.0").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val metas = org.json.JSONObject(response.body?.string().orEmpty()).optJSONArray("metas") ?: return@withContext null
                for (i in 0 until metas.length()) {
                    val meta = metas.optJSONObject(i) ?: continue
                    val poster = meta.optString("poster").takeIf { it.isNotBlank() && it != "null" }
                    if (poster != null) { cache[key] = poster; return@withContext poster }
                }
            }
        } catch (_: Exception) { }
        null
    }
}
